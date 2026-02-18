/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 * import {onDocumentWritten} from "firebase-functions/v2/firestore";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

import {setGlobalOptions} from "firebase-functions";

import * as functions from "firebase-functions";
import {onRequest} from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";
import {Response} from "express";
import OpenAI from "openai";
import * as fs from "fs";
import * as os from "os";
import * as path from "path";
import * as crypto from "crypto";
import {google} from "googleapis";

// Initialize Firebase Admin
admin.initializeApp();
const db = admin.firestore();
const remoteConfig = admin.remoteConfig();

/**
 * Model tiers for transcription quality selection
 * - AUTO: Free tier using Groq Turbo (whisper-large-v3-turbo) - 0 credits
 * - STANDARD: 1x credit using Groq Whisper (whisper-large-v3)
 * - PREMIUM: 2x credit using OpenAI (gpt-4o-mini-transcribe)
 */
type ModelTier = "AUTO" | "STANDARD" | "PREMIUM";

/**
 * Get the credit multiplier for a given model tier
 * @param {ModelTier} tier - The model tier
 * @return {number} Credit multiplier (0=free, 1=standard, 2=premium)
 */
function getCreditMultiplier(tier: ModelTier): number {
  switch (tier) {
  case "AUTO":
    return 0; // Free - no credits charged
  case "STANDARD":
    return 1; // 1x credits
  case "PREMIUM":
    return 2; // 2x credits
  default:
    return 1; // Default to standard
  }
}

/**
 * Verify Firebase ID token from Authorization header
 * @param {string | undefined} authHeader - The Authorization header value
 * @return {Promise<admin.auth.DecodedIdToken | null>} Decoded token or null
 */
async function verifyAuthToken(
  authHeader: string | undefined
): Promise<admin.auth.DecodedIdToken | null> {
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    logger.warn("Missing or invalid Authorization header");
    return null;
  }

  const idToken = authHeader.split("Bearer ")[1];
  if (!idToken) {
    logger.warn("Empty token after Bearer prefix");
    return null;
  }

  try {
    const decodedToken = await admin.auth().verifyIdToken(idToken);
    return decodedToken;
  } catch (error) {
    logger.warn("Failed to verify token", error);
    return null;
  }
}

/**
 * Check if a Firebase user is anonymous
 * Anonymous users have firebase.sign_in_provider === "anonymous"
 * or their identities object is empty
 * @param {admin.auth.DecodedIdToken} decodedToken - The decoded Firebase token
 * @return {boolean} True if user is anonymous
 */
function isAnonymousUser(decodedToken: admin.auth.DecodedIdToken): boolean {
  // Check the sign_in_provider in firebase claim
  const firebase = decodedToken.firebase;
  if (firebase?.sign_in_provider === "anonymous") {
    return true;
  }

  // Also check if identities is empty (backup check)
  const identities = firebase?.identities;
  if (!identities || Object.keys(identities).length === 0) {
    return true;
  }

  return false;
}

/**
 * Check if guest/anonymous access is allowed for a user.
 * If the user is anonymous and guest login is disabled, sends 403 response.
 * Returns PlanLimits if access is allowed, null if blocked.
 * @param {admin.auth.DecodedIdToken} decodedToken - The decoded Firebase token
 * @param {Response} response - The HTTP response object
 * @param {string} endpoint - Endpoint name for logging
 * @return {Promise<PlanLimits | null>} PlanLimits if allowed, null if blocked
 */
async function checkGuestAccessAndGetLimits(
  decodedToken: admin.auth.DecodedIdToken,
  response: Response,
  endpoint: string
): Promise<PlanLimits | null> {
  const limits = await getPlanLimits();

  if (isAnonymousUser(decodedToken) && !limits.guestLoginEnabled) {
    logger.warn(
      `[${endpoint}] Anonymous user ${decodedToken.uid} blocked: ` +
      "guest login disabled"
    );
    response.status(403).json({
      error: "GUEST_LOGIN_DISABLED",
      message: "Guest access is currently disabled. " +
        "Please sign in with Google to continue.",
    });
    return null;
  }

  return limits;
}

/**
 * Log transcription request to Firestore
 * @param {string} uid - User ID
 * @param {boolean} success - Whether the transcription was successful
 * @param {number} durationMs - Processing time in milliseconds
 * @return {Promise<void>} Promise that resolves when logging is complete
 */
async function logTranscriptionRequest(
  uid: string,
  success: boolean,
  durationMs: number
): Promise<void> {
  try {
    await db.collection("transcriptions").add({
      uid: uid,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      success: success,
      durationMs: durationMs,
    });
    logger.info(
      `Logged transcription request for user ${uid}, success=${success}`
    );
  } catch (error) {
    // Don't fail the request if logging fails
    logger.error("Failed to log transcription request", error);
  }
}

// ============================================================================
// REMOTE CONFIG & PLAN LIMITS
// ============================================================================

/**
 * Plan limits interface (Trial + Pro from Remote Config)
 * Uses credits instead of seconds/minutes for billing
 */
interface PlanLimits {
  // Credit limits (configurable via Remote Config)
  freeTierCredits: number; // Total credits for free tier
  proTierCredits: number; // Total credits per month for pro tier
  secondsPerCredit: number; // How many seconds of audio equals 1 credit
  // Trial expiry
  trialDurationMonths: number;
  // Pro config
  proProductId: string;
  proPlanEnabled: boolean;
  // Guest/Anonymous login
  guestLoginEnabled: boolean; // Whether anonymous users are allowed
}

// Cache for Remote Config values (5 minute TTL)
let cachedPlanLimits: PlanLimits | null = null;
let cacheTimestamp = 0;
const CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

// Default fallback values if Remote Config is unavailable
const DEFAULT_FREE_TIER_CREDITS = 500;
const DEFAULT_PRO_TIER_CREDITS = 10000;
const DEFAULT_SECONDS_PER_CREDIT = 6;
const DEFAULT_TRIAL_DURATION_MONTHS = 3;
const DEFAULT_PRO_PRODUCT_ID = "whispertype_pro_monthly";
const DEFAULT_GUEST_LOGIN_ENABLED = false;

/**
 * Get credit limit for a specific product ID.
 * Maps subscription product IDs to their tier credit limits.
 * @param {string} productId - Google Play product ID
 * @return {number} Credit limit for this product
 */
function getCreditsForProduct(productId: string): number {
  if (productId.includes("starter")) return 2000;
  if (productId.includes("unlimited")) return 15000;
  return 6000; // Pro tier + legacy fallback
}

/**
 * Get plan limits from Remote Config with caching
 * Includes credit limits for both Trial and Pro tiers
 * Falls back to defaults if Remote Config is unavailable
 * @return {Promise<PlanLimits>} Plan limits configuration
 */
async function getPlanLimits(): Promise<PlanLimits> {
  const now = Date.now();

  // Return cached value if still valid
  if (cachedPlanLimits && (now - cacheTimestamp) < CACHE_TTL_MS) {
    return cachedPlanLimits;
  }

  try {
    // Fetch Remote Config template
    const template = await remoteConfig.getTemplate();

    // Helper to find a parameter in top-level or parameter groups
    const findParam = (name: string) =>
      template.parameters?.[name] ??
      Object.values(template.parameterGroups ?? {})
        .map((g) => g.parameters?.[name])
        .find((p) => p !== undefined);

    // Extract credit parameters with defaults
    const freeTierCreditsParam = findParam("free_tier_credits");
    const freeTierCredits = freeTierCreditsParam &&
      freeTierCreditsParam.defaultValue &&
      "value" in freeTierCreditsParam.defaultValue ?
      Number(freeTierCreditsParam.defaultValue.value) :
      DEFAULT_FREE_TIER_CREDITS;

    const proTierCreditsParam = findParam("pro_tier_credits");
    const proTierCredits = proTierCreditsParam &&
      proTierCreditsParam.defaultValue &&
      "value" in proTierCreditsParam.defaultValue ?
      Number(proTierCreditsParam.defaultValue.value) :
      DEFAULT_PRO_TIER_CREDITS;

    const secondsPerCreditParam = findParam("seconds_per_credit");
    const secondsPerCredit = secondsPerCreditParam &&
      secondsPerCreditParam.defaultValue &&
      "value" in secondsPerCreditParam.defaultValue ?
      Number(secondsPerCreditParam.defaultValue.value) :
      DEFAULT_SECONDS_PER_CREDIT;

    const trialDurationParam = findParam("trial_duration_months");
    const trialDurationMonths = trialDurationParam &&
      trialDurationParam.defaultValue &&
      "value" in trialDurationParam.defaultValue ?
      Number(trialDurationParam.defaultValue.value) :
      DEFAULT_TRIAL_DURATION_MONTHS;

    const proProductIdParam = findParam("pro_product_id");
    const proProductId = proProductIdParam &&
      proProductIdParam.defaultValue &&
      "value" in proProductIdParam.defaultValue ?
      String(proProductIdParam.defaultValue.value) :
      DEFAULT_PRO_PRODUCT_ID;

    const proPlanEnabledParam = findParam("pro_plan_enabled");
    const proPlanEnabled = proPlanEnabledParam &&
      proPlanEnabledParam.defaultValue &&
      "value" in proPlanEnabledParam.defaultValue ?
      String(proPlanEnabledParam.defaultValue.value) === "true" :
      true;

    const guestLoginEnabledParam = findParam("guest_login_enabled");
    const guestLoginEnabled = guestLoginEnabledParam &&
      guestLoginEnabledParam.defaultValue &&
      "value" in guestLoginEnabledParam.defaultValue ?
      String(guestLoginEnabledParam.defaultValue.value) === "true" :
      DEFAULT_GUEST_LOGIN_ENABLED;

    // Cache the values
    cachedPlanLimits = {
      freeTierCredits: freeTierCredits,
      proTierCredits: proTierCredits,
      secondsPerCredit: secondsPerCredit,
      trialDurationMonths: trialDurationMonths,
      proProductId: proProductId,
      proPlanEnabled: proPlanEnabled,
      guestLoginEnabled: guestLoginEnabled,
    };
    cacheTimestamp = now;

    logger.info(
      `Remote Config loaded: ${freeTierCredits} free credits, ` +
      `${proTierCredits} pro credits, ${secondsPerCredit} secs/credit, ` +
      `${trialDurationMonths} months expiry`
    );

    return cachedPlanLimits;
  } catch (error) {
    logger.warn(
      "Failed to fetch Remote Config, using defaults",
      error
    );

    // Fallback to defaults
    const fallbackLimits: PlanLimits = {
      freeTierCredits: DEFAULT_FREE_TIER_CREDITS,
      proTierCredits: DEFAULT_PRO_TIER_CREDITS,
      secondsPerCredit: DEFAULT_SECONDS_PER_CREDIT,
      trialDurationMonths: DEFAULT_TRIAL_DURATION_MONTHS,
      proProductId: DEFAULT_PRO_PRODUCT_ID,
      proPlanEnabled: true,
      guestLoginEnabled: DEFAULT_GUEST_LOGIN_ENABLED,
    };

    // Cache fallback values
    cachedPlanLimits = fallbackLimits;
    cacheTimestamp = now;

    return fallbackLimits;
  }
}

/**
 * Legacy wrapper for backward compatibility
 * @deprecated Use getPlanLimits() instead
 */
async function getTrialLimits(): Promise<{
  freeTierCredits: number;
  trialDurationMonths: number;
}> {
  const limits = await getPlanLimits();
  return {
    freeTierCredits: limits.freeTierCredits,
    trialDurationMonths: limits.trialDurationMonths,
  };
}

/**
 * Pro subscription interface for Firestore
 */
interface ProSubscription {
  purchaseToken: string; // Google Play purchase token
  productId: string; // e.g., "whispertype_pro_monthly"
  status: "active" | "cancelled" | "expired" | "pending";
  startDate: admin.firestore.Timestamp;
  currentPeriodStart: admin.firestore.Timestamp;
  currentPeriodEnd: admin.firestore.Timestamp; // Reset date (predictable!)
  proCreditsUsed: number; // Resets each billing period
}

/**
 * User document interface for Firestore users collection
 */
interface UserDocument {
  createdAt: admin.firestore.Timestamp;
  country?: string;
  plan: "free" | "pro";
  // Trial fields
  freeTrialStart: admin.firestore.Timestamp;
  freeCreditsUsed: number; // Lifetime usage in credits
  // Configurable months from freeTrialStart
  trialExpiryDate: admin.firestore.Timestamp;
  // Pro subscription fields (Iteration 3)
  proSubscription?: ProSubscription;
  // Legacy fields (for migration - will be reset to 0)
  freeMinutesRemaining?: number;
  freeSecondsUsed?: number; // Old field, migrated to freeCreditsUsed
}

/**
 * Trial status result interface
 */
interface TrialStatusResult {
  isValid: boolean;
  status: "active" | "expired_time" | "expired_usage";
  freeCreditsUsed: number;
  freeCreditsRemaining: number;
  freeTierCredits: number; // Total credits limit
  trialExpiryDate: admin.firestore.Timestamp;
  trialExpiryDateMs: number;
  warningLevel: "none" | "fifty_percent" |
  "eighty_percent" | "ninety_five_percent";
}

/**
 * Calculate trial status for a user
 * @param {UserDocument} user - The user document
 * @param {number} freeTierCredits - Credit limit from Remote Config
 * @return {TrialStatusResult} The trial status
 */
function checkTrialStatus(
  user: UserDocument,
  freeTierCredits: number
): TrialStatusResult {
  const now = admin.firestore.Timestamp.now();
  const freeCreditsUsed = user.freeCreditsUsed || 0;
  const freeCreditsRemaining = Math.max(
    0, freeTierCredits - freeCreditsUsed
  );
  const trialExpiryDate = user.trialExpiryDate;
  const trialExpiryDateMs = trialExpiryDate.toMillis();

  // Check if trial expired by time
  if (now.toMillis() > trialExpiryDateMs) {
    return {
      isValid: false,
      status: "expired_time",
      freeCreditsUsed,
      freeCreditsRemaining: 0,
      freeTierCredits,
      trialExpiryDate,
      trialExpiryDateMs,
      warningLevel: "none",
    };
  }

  // Check if trial expired by usage
  if (freeCreditsUsed >= freeTierCredits) {
    return {
      isValid: false,
      status: "expired_usage",
      freeCreditsUsed,
      freeCreditsRemaining: 0,
      freeTierCredits,
      trialExpiryDate,
      trialExpiryDateMs,
      warningLevel: "none",
    };
  }

  // Calculate warning level based on usage percentage
  const usagePercent = (freeCreditsUsed / freeTierCredits) * 100;
  let warningLevel: TrialStatusResult["warningLevel"] = "none";
  if (usagePercent >= 95) {
    warningLevel = "ninety_five_percent";
  } else if (usagePercent >= 80) {
    warningLevel = "eighty_percent";
  } else if (usagePercent >= 50) {
    warningLevel = "fifty_percent";
  }

  return {
    isValid: true,
    status: "active",
    freeCreditsUsed,
    freeCreditsRemaining,
    freeTierCredits,
    trialExpiryDate,
    trialExpiryDateMs,
    warningLevel,
  };
}

/**
 * Pro subscription status result interface
 */
interface ProStatusResult {
  isActive: boolean;
  proCreditsUsed: number;
  proCreditsRemaining: number;
  proCreditsLimit: number;
  currentPeriodEndMs: number; // Reset date
  hasCreditsRemaining: boolean;
  needsReVerification?: boolean; // True if period expired but sub exists
}

/**
 * Check Pro subscription status for a user
 * Verifies both status AND expiration time to prevent expired subscriptions
 * from being treated as active.
 * Credit limit is derived from the user's productId.
 * @param {UserDocument} user - The user document
 * @return {ProStatusResult} The Pro status
 */
function checkProStatus(
  user: UserDocument
): ProStatusResult {
  const sub = user.proSubscription;
  const now = Date.now();
  const creditLimit = sub ?
    getCreditsForProduct(sub.productId) : 6000;

  // Not a Pro user or no active subscription
  if (!sub || sub.status !== "active") {
    // Legacy Pro users may have purchaseToken but status
    // isn't "active" — flag for re-verification with
    // Google Play so their subscription can be restored
    const canReVerify = !!(
      sub?.purchaseToken && sub?.productId
    );
    return {
      isActive: false,
      proCreditsUsed: 0,
      proCreditsRemaining: 0,
      proCreditsLimit: creditLimit,
      currentPeriodEndMs: 0,
      hasCreditsRemaining: false,
      needsReVerification: canReVerify,
    };
  }

  // Safe defaults for legacy users missing credit fields
  const creditsUsed = sub.proCreditsUsed ?? 0;

  // Check if subscription period has expired
  // This catches cases where Firestore status is "active" but the
  // subscription has actually expired in Google Play and not been renewed
  // Legacy users may not have currentPeriodEnd
  // — treat as needing re-verification
  const periodEndMs = sub.currentPeriodEnd?.toMillis() ?? 0;
  if (now > periodEndMs) {
    logger.warn(
      `Pro subscription expired: currentPeriodEnd=${periodEndMs}, now=${now}`
    );
    // Flag that this subscription might have been renewed in Google Play
    // but Firestore hasn't been updated yet - caller should re-verify
    return {
      isActive: false,
      proCreditsUsed: creditsUsed,
      proCreditsRemaining: 0,
      proCreditsLimit: creditLimit,
      currentPeriodEndMs: periodEndMs,
      hasCreditsRemaining: false,
      needsReVerification: true, // Signal to re-verify with Google Play
    };
  }

  const remaining = creditLimit - creditsUsed;
  return {
    isActive: true,
    proCreditsUsed: creditsUsed,
    proCreditsRemaining: Math.max(0, remaining),
    proCreditsLimit: creditLimit,
    currentPeriodEndMs: periodEndMs,
    hasCreditsRemaining: remaining > 0,
  };
}

/**
 * Re-verify Pro subscription with Google Play API
 * Called when local subscription data shows expired but subscription might
 * have been auto-renewed in Google Play
 * @param {string} uid - User ID
 * @param {UserDocument} user - User document with proSubscription
 * @return {Promise<Object>} Result with renewed, proStatus, updatedUser
 */
async function reVerifyProSubscriptionWithGooglePlay(
  uid: string,
  user: UserDocument
): Promise<{
  renewed: boolean;
  proStatus: ProStatusResult;
  updatedUser?: UserDocument;
}> {
  const sub = user.proSubscription;
  if (!sub || !sub.purchaseToken || !sub.productId) {
    logger.warn(`Cannot re-verify: no subscription data for ${uid}`);
    return {
      renewed: false,
      proStatus: checkProStatus(user),
    };
  }

  // Get the service account key from secret
  const keyJson = process.env.GOOGLE_PLAY_KEY;
  if (!keyJson) {
    logger.error("GOOGLE_PLAY_KEY not available for re-verification");
    return {
      renewed: false,
      proStatus: checkProStatus(user),
    };
  }

  try {
    // Parse the service account key
    const credentials = JSON.parse(keyJson);

    // Initialize Google Auth client
    const auth = new google.auth.GoogleAuth({
      credentials: credentials,
      scopes: ["https://www.googleapis.com/auth/androidpublisher"],
    });

    const androidpublisher = google.androidpublisher({
      version: "v3",
      auth: auth,
    });

    // Verify the subscription with Google Play
    logger.info(`Re-verifying subscription for ${uid} with Google Play`);
    const result = await androidpublisher.purchases.subscriptions.get({
      packageName: "com.whispertype.app",
      subscriptionId: sub.productId,
      token: sub.purchaseToken,
    });

    const subscriptionData = result.data;
    const paymentState = subscriptionData.paymentState;
    const expiryTimeMs = parseInt(
      subscriptionData.expiryTimeMillis || "0",
      10
    );
    const now = Date.now();

    logger.info(
      `Re-verify result for ${uid}: paymentState=${paymentState}, ` +
      `expiryTimeMillis=${expiryTimeMs}, now=${now}`
    );

    // Check if subscription is still active (renewed)
    const isActive = (
      (paymentState === 1 || paymentState === 2) &&
      expiryTimeMs > now
    );

    if (!isActive) {
      // Subscription truly expired - update Firestore to reflect this
      logger.info(`Subscription confirmed expired for ${uid}`);
      const userRef = db.collection("users").doc(uid);
      await userRef.update({
        "proSubscription.status": "expired",
      });

      const creditLimit = getCreditsForProduct(sub.productId);
      return {
        renewed: false,
        proStatus: {
          isActive: false,
          proCreditsUsed: sub.proCreditsUsed ?? 0,
          proCreditsRemaining: 0,
          proCreditsLimit: creditLimit,
          currentPeriodEndMs: sub.currentPeriodEnd?.toMillis() ?? 0,
          hasCreditsRemaining: false,
        },
      };
    }

    // Subscription was renewed! Update Firestore with new expiry date
    const expiryDateStr = new Date(expiryTimeMs).toISOString();
    logger.info(
      `Subscription RENEWED for ${uid}! New expiry: ${expiryDateStr}`
    );

    const userRef = db.collection("users").doc(uid);
    const nowTimestamp = admin.firestore.Timestamp.now();
    const expiryTimestamp = admin.firestore.Timestamp.fromMillis(expiryTimeMs);

    // Check if this is a new billing period (reset usage)
    // Legacy users may not have currentPeriodEnd — treat as new period
    const oldPeriodEnd = sub.currentPeriodEnd?.toMillis() ?? 0;
    let proCreditsUsed = sub.proCreditsUsed ?? 0;
    if (now > oldPeriodEnd) {
      // New period - reset usage
      proCreditsUsed = 0;
      logger.info(`New billing period for ${uid}, resetting credits to 0`);
    }

    // Update subscription in Firestore
    const updatedSub: ProSubscription = {
      ...sub,
      status: "active",
      currentPeriodStart: nowTimestamp,
      currentPeriodEnd: expiryTimestamp,
      proCreditsUsed: proCreditsUsed,
    };

    await userRef.update({
      plan: "pro",
      proSubscription: updatedSub,
    });

    const updatedUser: UserDocument = {
      ...user,
      plan: "pro",
      proSubscription: updatedSub,
    };

    const productCreditLimit = getCreditsForProduct(sub.productId);
    const remaining = productCreditLimit - proCreditsUsed;
    return {
      renewed: true,
      proStatus: {
        isActive: true,
        proCreditsUsed: proCreditsUsed,
        proCreditsRemaining: Math.max(0, remaining),
        proCreditsLimit: productCreditLimit,
        currentPeriodEndMs: expiryTimeMs,
        hasCreditsRemaining: remaining > 0,
      },
      updatedUser: updatedUser,
    };
  } catch (error) {
    logger.error(`Failed to re-verify subscription for ${uid}`, error);
    return {
      renewed: false,
      proStatus: checkProStatus(user),
    };
  }
}

/**
 * Usage log entry interface
 */
interface UsageLogEntry {
  creditsUsed: number;
  timestamp: admin.firestore.Timestamp;
  source: "free" | "pro" | "overage" | "recharge";
}

/**
 * Calculate trial expiry date from start date
 * @param {admin.firestore.Timestamp} startDate - The trial start date
 * @param {number} trialMonths - Trial duration in months from Remote Config
 * @return {admin.firestore.Timestamp} The expiry date
 */
function calculateTrialExpiryDate(
  startDate: admin.firestore.Timestamp,
  trialMonths: number
): admin.firestore.Timestamp {
  const expiryDate = new Date(startDate.toMillis());
  expiryDate.setMonth(expiryDate.getMonth() + trialMonths);
  return admin.firestore.Timestamp.fromDate(expiryDate);
}

/**
 * Get or create user document in Firestore
 * Handles migration from legacy users - RESETS ALL USERS
 * TO 0 CREDITS (fresh start)
 * @param {string} uid - User ID
 * @return {Promise<UserDocument>} The user document
 */
async function getOrCreateUser(uid: string): Promise<UserDocument> {
  const userRef = db.collection("users").doc(uid);
  const userDoc = await userRef.get();

  // Fetch trial limits from Remote Config
  const trialLimits = await getTrialLimits();

  if (userDoc.exists) {
    const userData = userDoc.data() as UserDocument;

    // Check if migration needed
    // (user has old freeSecondsUsed or no freeCreditsUsed)
    // MIGRATION: Reset ALL users to 0 credits for fresh start
    if (userData.freeCreditsUsed === undefined) {
      logger.info(`Migrating user ${uid} to credits system (fresh start)`);

      // Calculate trial expiry date from freeTrialStart
      const freeTrialStart = userData.freeTrialStart ||
        admin.firestore.Timestamp.now();
      const trialExpiryDate = calculateTrialExpiryDate(
        freeTrialStart,
        trialLimits.trialDurationMonths
      );

      // FRESH START: Reset credits to 0
      const freeCreditsUsed = 0;

      // Update the user document with new fields
      const updatedUser: UserDocument = {
        ...userData,
        freeTrialStart,
        freeCreditsUsed,
        trialExpiryDate,
      };

      await userRef.update({
        freeCreditsUsed,
        trialExpiryDate,
        freeTrialStart,
      });

      logger.info(
        `Migrated user ${uid}: reset to ${freeCreditsUsed} credits, ` +
        `expires ${trialExpiryDate.toDate().toISOString()}`
      );

      return updatedUser;
    }

    // Ensure plan field is set (might be missing for older users)
    // If user has active proSubscription but no plan field, set to 'pro'
    // Otherwise default to 'free'
    if (userData.plan === undefined) {
      const inferredPlan = (userData.proSubscription?.status === "active") ?
        "pro" : "free";
      logger.info(`Fixing missing plan for ${uid}: setting to ${inferredPlan}`);
      await userRef.update({plan: inferredPlan});
      return {...userData, plan: inferredPlan};
    }

    return userData;
  }

  // Create new user document with trial initialized
  const now = admin.firestore.Timestamp.now();
  const trialExpiryDate = calculateTrialExpiryDate(
    now,
    trialLimits.trialDurationMonths
  );

  const newUser: UserDocument = {
    createdAt: now,
    plan: "free",
    freeTrialStart: now,
    freeCreditsUsed: 0,
    trialExpiryDate,
  };

  await userRef.set(newUser);
  logger.info(
    `Created new user ${uid} with trial expiry ` +
    `${trialExpiryDate.toDate().toISOString()}`
  );

  return newUser;
}

/**
 * Log usage after transcription
 * @param {string} uid - User ID
 * @param {number} creditsUsed - Credits deducted
 * @param {string} source - Usage source (free, pro, etc.)
 * @param {ModelTier} modelTier - Model tier used (AUTO, STANDARD, PREMIUM)
 * @return {Promise<void>}
 */
async function logUsage(
  uid: string,
  creditsUsed: number,
  source: UsageLogEntry["source"] = "free",
  modelTier: ModelTier = "STANDARD"
): Promise<void> {
  try {
    const entry: Omit<UsageLogEntry, "timestamp"> & {
      timestamp: admin.firestore.FieldValue;
      modelTier?: ModelTier;
    } = {
      creditsUsed: creditsUsed,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      source: source,
      modelTier: modelTier,
    };

    await db
      .collection("usage_logs")
      .doc(uid)
      .collection("entries")
      .add(entry);

    logger.info(
      `Logged ${creditsUsed} credits usage for user ${uid} ` +
      `(tier: ${modelTier})`
    );
  } catch (error) {
    // Don't fail the request if logging fails
    logger.error("Failed to log usage", error);
  }
}

/**
 * Deduct credits from user's trial quota
 * Converts audio duration to credits using secondsPerCredit rate
 * @param {string} uid - User ID
 * @param {number} audioDurationMs - Audio duration in milliseconds
 * @param {ModelTier} modelTier - Tier (AUTO=0x, STANDARD=1x, PREMIUM=2x)
 * @return {Promise<TrialStatusResult>} Updated trial status
 */
async function deductTrialUsage(
  uid: string,
  audioDurationMs: number,
  modelTier: ModelTier = "STANDARD"
): Promise<TrialStatusResult> {
  const userRef = db.collection("users").doc(uid);
  const limits = await getPlanLimits();

  // Convert audio duration to credits with tier multiplier
  // e.g., 5248ms -> 6 seconds -> 1 base credit (if secondsPerCredit=6)
  // AUTO tier: 0x multiplier (free)
  // STANDARD tier: 1x multiplier
  // PREMIUM tier: 2x multiplier
  const audioSeconds = Math.ceil(audioDurationMs / 1000);
  const baseCredits = Math.ceil(audioSeconds / limits.secondsPerCredit);
  const multiplier = getCreditMultiplier(modelTier);
  const creditsToDeduct = baseCredits * multiplier;

  // If AUTO tier (free), still log but don't actually deduct
  if (creditsToDeduct === 0) {
    logger.info(
      `[AUTO/Free] No credits deducted for ${audioSeconds}s audio ` +
      `from user ${uid} (tier: ${modelTier})`
    );
    // Log the free usage for audit trail
    await logUsage(uid, 0, "free", modelTier);

    // Return current status without deduction
    const userDoc = await userRef.get();
    const userData = userDoc.data() as UserDocument;
    return checkTrialStatus(userData, limits.freeTierCredits);
  }

  // Use a transaction to safely increment usage
  const updatedUser = await db.runTransaction(async (transaction) => {
    const userDoc = await transaction.get(userRef);
    if (!userDoc.exists) {
      throw new Error("User document not found");
    }

    const userData = userDoc.data() as UserDocument;
    const newCreditsUsed = (userData.freeCreditsUsed || 0) + creditsToDeduct;

    transaction.update(userRef, {
      freeCreditsUsed: newCreditsUsed,
    });

    // Return updated user data
    return {
      ...userData,
      freeCreditsUsed: newCreditsUsed,
    };
  });

  // Log the usage entry for audit trail
  await logUsage(uid, creditsToDeduct, "free", modelTier);

  logger.info(
    `Deducted ${creditsToDeduct} credits (${audioSeconds}s audio, ` +
    `${multiplier}x multiplier, tier: ${modelTier}) ` +
    `from user ${uid}, total: ${updatedUser.freeCreditsUsed} credits`
  );

  // Check status with updated credits
  return checkTrialStatus(updatedUser, limits.freeTierCredits);
}

/**
 * Deduct credits from user's Pro subscription quota
 * Converts audio duration to credits using secondsPerCredit rate
 * @param {string} uid - User ID
 * @param {number} audioDurationMs - Audio duration in milliseconds
 * @param {ModelTier} modelTier - Tier (AUTO=0x, STANDARD=1x, PREMIUM=2x)
 * @return {Promise<ProStatusResult>} Updated Pro status
 */
async function deductProUsage(
  uid: string,
  audioDurationMs: number,
  modelTier: ModelTier = "STANDARD"
): Promise<ProStatusResult> {
  const userRef = db.collection("users").doc(uid);
  const limits = await getPlanLimits();

  // Convert audio duration to credits with tier multiplier
  const audioSeconds = Math.ceil(audioDurationMs / 1000);
  const baseCredits = Math.ceil(audioSeconds / limits.secondsPerCredit);
  const multiplier = getCreditMultiplier(modelTier);
  const creditsToDeduct = baseCredits * multiplier;

  // If AUTO tier (free), still log but don't actually deduct
  if (creditsToDeduct === 0) {
    logger.info(
      `[AUTO/Free] Pro: No credits deducted for ${audioSeconds}s audio ` +
      `from user ${uid} (tier: ${modelTier})`
    );
    // Log the free usage for audit trail
    await logUsage(uid, 0, "pro", modelTier);

    // Return current status without deduction
    const userDoc = await userRef.get();
    const userData = userDoc.data() as UserDocument;
    return checkProStatus(userData);
  }

  // Use a transaction to safely increment usage
  const updatedUser = await db.runTransaction(async (transaction) => {
    const userDoc = await transaction.get(userRef);
    if (!userDoc.exists) {
      throw new Error("User document not found");
    }

    const userData = userDoc.data() as UserDocument;
    if (!userData.proSubscription) {
      throw new Error("User has no Pro subscription");
    }

    const newProCreditsUsed =
      userData.proSubscription.proCreditsUsed + creditsToDeduct;

    transaction.update(userRef, {
      "proSubscription.proCreditsUsed": newProCreditsUsed,
    });

    // Return updated user data
    return {
      ...userData,
      proSubscription: {
        ...userData.proSubscription,
        proCreditsUsed: newProCreditsUsed,
      },
    };
  });

  // Log the usage entry for audit trail (with Pro source)
  await logUsage(uid, creditsToDeduct, "pro", modelTier);

  logger.info(
    `Pro: Deducted ${creditsToDeduct} credits ` +
    `(${audioSeconds}s, ${multiplier}x, tier: ${modelTier}) ` +
    `from ${uid}, total: ${updatedUser.proSubscription?.proCreditsUsed}`
  );

  return checkProStatus(updatedUser);
}

/**
 * Get total credits used for the current billing period (anniversary-based)
 * Billing period resets on the same day of the month as the user's signup date
 * @param {string} uid - User ID
 * @return {Promise<number>} Total credits used this billing period
 */
async function getTotalUsageThisPeriod(uid: string): Promise<number> {
  try {
    // Get user's signup date to determine billing cycle
    const userRef = db.collection("users").doc(uid);
    const userDoc = await userRef.get();

    let billingDayOfMonth = 1; // Default to 1st if no user doc

    if (userDoc.exists) {
      const userData = userDoc.data() as UserDocument;
      if (userData.freeTrialStart) {
        // Get the day of month when user signed up
        billingDayOfMonth = userData.freeTrialStart.toDate().getDate();
      }
    }

    // Calculate start of current billing period
    const now = new Date();
    let billingPeriodStart: Date;

    if (now.getDate() >= billingDayOfMonth) {
      // Billing day passed this month, period started this month
      billingPeriodStart = new Date(
        now.getFullYear(), now.getMonth(), billingDayOfMonth
      );
    } else {
      // Haven't reached billing day, period started last month
      billingPeriodStart = new Date(
        now.getFullYear(), now.getMonth() - 1, billingDayOfMonth
      );
    }

    const startTimestamp = admin.firestore.Timestamp.fromDate(
      billingPeriodStart
    );

    const snapshot = await db
      .collection("usage_logs")
      .doc(uid)
      .collection("entries")
      .where("timestamp", ">=", startTimestamp)
      .get();

    let totalCredits = 0;
    snapshot.forEach((doc) => {
      const data = doc.data() as UsageLogEntry;
      totalCredits += data.creditsUsed || 0;
    });

    return totalCredits;
  } catch (error) {
    logger.error("Failed to get billing period usage", error);
    return 0;
  }
}

/**
 * Get total lifetime credits usage from usage_logs (all time)
 * Used to sync freeCreditsUsed when there's a mismatch
 * @param {string} uid - User ID
 * @return {Promise<number>} Total credits used lifetime
 */
async function getTotalLifetimeUsage(uid: string): Promise<number> {
  try {
    const snapshot = await db
      .collection("usage_logs")
      .doc(uid)
      .collection("entries")
      .get();

    let totalCredits = 0;
    snapshot.forEach((doc) => {
      const data = doc.data() as UsageLogEntry;
      totalCredits += data.creditsUsed || 0;
    });

    return totalCredits;
  } catch (error) {
    logger.error("Failed to get lifetime usage", error);
    return 0;
  }
}

// ============================================================================
// SHARED QUOTA & BILLING HELPERS (used by both transcription endpoints)
// ============================================================================

/**
 * Result of a successful quota check
 */
interface QuotaCheckResult {
  usageSource: "free" | "pro";
  currentUser: UserDocument;
  userPlan: string;
  hasProSubscription: boolean;
}

/**
 * Check user quota and either return access info or send a 403 response.
 * Handles: AUTO bypass -> Trial check -> Pro check with re-verify -> Block.
 *
 * @param {string} uid - User ID
 * @param {ModelTier} modelTier - Model tier (AUTO bypasses quota check)
 * @param {PlanLimits} limits - Plan limits from Remote Config
 * @param {Response} res - Express response (used to send 403 if blocked)
 * @param {number} startTime - Request start time for logging
 * @param {string} logPrefix - Log prefix (e.g. "[Groq] ")
 * @return {Promise<QuotaCheckResult | null>} Result if allowed,
 * null if 403 sent
 */
async function checkQuotaOrBlock(
  uid: string,
  modelTier: ModelTier,
  limits: PlanLimits,
  res: Response,
  startTime: number,
  logPrefix = ""
): Promise<QuotaCheckResult | null> {
  const user = await getOrCreateUser(uid);

  let canProceed = false;
  let usageSource: "free" | "pro" = "free";
  const userPlan = user.plan ?? "free";

  // AUTO tier is always free - bypass quota check entirely
  if (modelTier === "AUTO") {
    canProceed = true;
    usageSource = userPlan === "pro" ? "pro" : "free";
    logger.info(`${logPrefix}AUTO tier - bypassing quota check for ${uid}`);
  }

  // Priority 1: Check free trial (if user is on free plan)
  if (!canProceed && userPlan === "free") {
    const trialStatus = checkTrialStatus(user, limits.freeTierCredits);
    if (trialStatus.isValid) {
      canProceed = true;
      usageSource = "free";
      logger.info(
        `${logPrefix}Trial valid for ${uid}: ` +
        `${trialStatus.freeCreditsRemaining} credits remaining`
      );
    }
  }

  // Priority 2: Check Pro subscription
  const hasProSubscription = user.proSubscription?.status === "active";
  let currentUser = user;
  if (!canProceed && (userPlan === "pro" || hasProSubscription)) {
    let proStatus = checkProStatus(user);

    // If subscription appears expired, try re-verifying with Google Play
    if (!proStatus.isActive && proStatus.needsReVerification) {
      logger.info(
        `${logPrefix}Pro expired for ${uid}, re-verifying with Google Play`
      );
      const reVerifyResult = await reVerifyProSubscriptionWithGooglePlay(
        uid, user
      );
      if (reVerifyResult.renewed) {
        proStatus = reVerifyResult.proStatus;
        if (reVerifyResult.updatedUser) {
          currentUser = reVerifyResult.updatedUser;
        }
        logger.info(
          `${logPrefix}Subscription re-verified for ${uid}: renewed=true`
        );
      }
    }

    if (proStatus.isActive && proStatus.hasCreditsRemaining) {
      canProceed = true;
      usageSource = "pro";
      logger.info(
        `${logPrefix}Pro valid for ${uid}: ` +
        `${proStatus.proCreditsRemaining} credits remaining`
      );
    }
  }

  // Priority 2b: Fallback to trial credits for expired Pro users
  if (!canProceed && userPlan === "pro") {
    const trialStatus = checkTrialStatus(
      user, limits.freeTierCredits
    );
    if (trialStatus.isValid) {
      canProceed = true;
      usageSource = "free";
      logger.info(
        `${logPrefix}Pro expired, falling back to trial ` +
        `for ${uid}: ` +
        `${trialStatus.freeCreditsRemaining} credits left`
      );
    }
  }

  // Priority 3: Block if no valid quota
  if (!canProceed) {
    const trialStatus = checkTrialStatus(currentUser, limits.freeTierCredits);
    const proStatus = checkProStatus(currentUser);
    logger.warn(
      `${logPrefix}No valid quota for user ${uid}: plan=${userPlan}`
    );
    await logTranscriptionRequest(uid, false, Date.now() - startTime);

    const isProUser = userPlan === "pro" || hasProSubscription;
    const proExpiredByTime = isProUser &&
      currentUser.proSubscription &&
      Date.now() > (currentUser.proSubscription
        .currentPeriodEnd?.toMillis() ?? 0);
    const proExpiredByUsage = isProUser &&
      !proStatus.hasCreditsRemaining && !proExpiredByTime;

    let errorCode: string;
    let errorMessage: string;

    if (proExpiredByTime) {
      errorCode = "PRO_EXPIRED";
      errorMessage =
        "Your Pro subscription has expired. Please renew to continue.";
    } else if (proExpiredByUsage) {
      errorCode = "PRO_LIMIT_REACHED";
      errorMessage = "You have used all your Pro credits for this month";
    } else if (trialStatus.status === "expired_time") {
      errorCode = "TRIAL_EXPIRED";
      errorMessage = "Your free trial period has ended";
    } else {
      errorCode = "TRIAL_EXPIRED";
      errorMessage = "You have used all your free credits";
    }

    res.status(403).json({
      error: errorCode,
      message: errorMessage,
      plan: userPlan,
      trialStatus: userPlan === "free" && !hasProSubscription ? {
        status: trialStatus.status,
        freeCreditsUsed: trialStatus.freeCreditsUsed,
        freeCreditsRemaining: 0,
        freeTierCredits: trialStatus.freeTierCredits,
        trialExpiryDateMs: trialStatus.trialExpiryDateMs,
        warningLevel: "none",
      } : undefined,
      proStatus: isProUser && currentUser.proSubscription ? {
        proCreditsUsed: currentUser.proSubscription.proCreditsUsed ?? 0,
        proCreditsRemaining: 0,
        proCreditsLimit: getCreditsForProduct(
          currentUser.proSubscription.productId
        ),
        resetDateMs:
          currentUser.proSubscription.currentPeriodEnd?.toMillis() ?? 0,
        expired: proExpiredByTime,
      } : undefined,
      proPlanEnabled: limits.proPlanEnabled,
    });
    return null;
  }

  return {usageSource, currentUser, userPlan, hasProSubscription};
}

/**
 * Result of credit deduction with pre-built response fields
 */
interface DeductionResult {
  creditsDeducted: number;
  totalCreditsThisMonth: number;
  responseFields: {
    creditsUsed: number;
    totalCreditsThisMonth: number;
    plan: string;
    subscriptionStatus: {
      status: string;
      creditsRemaining: number;
      creditsLimit: number;
      resetDateMs?: number;
      warningLevel: string;
    };
    trialStatus?: object;
    proStatus?: object;
  };
}

/**
 * Deduct credits from the user's quota and build the response status fields.
 * Handles both trial and Pro deduction, logging, and response data assembly.
 *
 * @param {string} uid - User ID
 * @param {string} usageSource - "free" or "pro"
 * @param {UserDocument} currentUser - The (possibly re-verified) user document
 * @param {number} durationMs - Audio duration in milliseconds
 * @param {ModelTier} modelTier - Model tier for credit multiplier
 * @param {PlanLimits} limits - Plan limits from Remote Config
 * @param {number} startTime - Request start time for logging
 * @param {string} logPrefix - Log prefix (e.g. "[Groq] ")
 * @return {Promise<DeductionResult>} Credits deducted and response fields
 */
async function deductAndBuildResponseData(
  uid: string,
  usageSource: "free" | "pro",
  currentUser: UserDocument,
  durationMs: number,
  modelTier: ModelTier,
  limits: PlanLimits,
  startTime: number,
  logPrefix = ""
): Promise<DeductionResult> {
  const audioSeconds = Math.ceil(durationMs / 1000);
  const baseCredits = Math.ceil(audioSeconds / limits.secondsPerCredit);
  const multiplier = getCreditMultiplier(modelTier);
  const creditsDeducted = baseCredits * multiplier;

  logger.info(
    `${logPrefix}Model tier: ${modelTier}, multiplier: ${multiplier}x, ` +
    `base credits: ${baseCredits}, final credits: ${creditsDeducted}`
  );

  let responseStatus: {
    plan: string;
    status: string;
    creditsRemaining: number;
    creditsLimit: number;
    resetDateMs?: number;
    warningLevel: string;
  };

  if (usageSource === "pro") {
    const proStatus = await deductProUsage(uid, durationMs, modelTier);
    responseStatus = {
      plan: "pro",
      status: proStatus.isActive ? "active" : "expired",
      creditsRemaining: proStatus.proCreditsRemaining,
      creditsLimit: proStatus.proCreditsLimit,
      resetDateMs: proStatus.currentPeriodEndMs,
      warningLevel:
        proStatus.proCreditsRemaining <
          proStatus.proCreditsLimit * 0.1 ?
          "ninety_percent" : "none",
    };
  } else {
    const trialStatus = await deductTrialUsage(uid, durationMs, modelTier);
    responseStatus = {
      plan: "free",
      status: trialStatus.status,
      creditsRemaining: trialStatus.freeCreditsRemaining,
      creditsLimit: trialStatus.freeTierCredits,
      warningLevel: trialStatus.warningLevel,
    };
  }

  await logTranscriptionRequest(uid, true, Date.now() - startTime);
  const totalCreditsThisMonth = await getTotalUsageThisPeriod(uid);

  logger.info(
    `${logPrefix}Success: ${creditsDeducted} credits ` +
    `(tier: ${modelTier}) from ${usageSource}, ` +
    `remaining: ${responseStatus.creditsRemaining}`
  );

  // Build response fields
  const responseFields = {
    creditsUsed: creditsDeducted,
    totalCreditsThisMonth,
    plan: responseStatus.plan,
    subscriptionStatus: {
      status: responseStatus.status,
      creditsRemaining: responseStatus.creditsRemaining,
      creditsLimit: responseStatus.creditsLimit,
      resetDateMs: responseStatus.resetDateMs,
      warningLevel: responseStatus.warningLevel,
    },
    trialStatus: responseStatus.plan === "free" ? {
      status: responseStatus.status,
      freeCreditsUsed:
        limits.freeTierCredits - responseStatus.creditsRemaining,
      freeCreditsRemaining: responseStatus.creditsRemaining,
      freeTierCredits: limits.freeTierCredits,
      trialExpiryDateMs: currentUser.trialExpiryDate.toMillis(),
      warningLevel: responseStatus.warningLevel,
    } : undefined,
    proStatus: responseStatus.plan === "pro" &&
      currentUser.proSubscription ? {
        isActive: responseStatus.status === "active",
        proCreditsUsed: getCreditsForProduct(
          currentUser.proSubscription.productId
        ) - responseStatus.creditsRemaining,
        proCreditsRemaining: responseStatus.creditsRemaining,
        proCreditsLimit: getCreditsForProduct(
          currentUser.proSubscription.productId
        ),
        currentPeriodEndMs: responseStatus.resetDateMs,
      } : undefined,
  };

  return {creditsDeducted, totalCreditsThisMonth, responseFields};
}

// Start writing functions
// https://firebase.google.com/docs/functions/typescript

// For cost control, you can set the maximum number of containers that can be
// running at the same time. This helps mitigate the impact of unexpected
// traffic spikes by instead downgrading performance. This limit is a
// per-function limit. You can override the limit for each function using the
// `maxInstances` option in the function's options, e.g.
// `onRequest({ maxInstances: 5 }, (req, res) => { ... })`.
// NOTE: setGlobalOptions does not apply to functions using the v1 API. V1
// functions should each use functions.runWith({ maxInstances: 10 }) instead.
// In the v1 API, each function can only serve one request per container, so
// this will be the maximum concurrent request count.
setGlobalOptions({maxInstances: 10});

// export const helloWorld = onRequest((request, response) => {
//   logger.info("Hello logs!", {structuredData: true});
//   response.send("Hello from Firebase!");
// });

export const health = onRequest((request, response) => {
  response.send("OK");
});

/**
 * Transcribe audio using OpenAI Whisper API
 * POST /transcribeAudio
 * Body: {
 *   audioBase64: string,
 *   audioFormat?: string,
 *   model?: string,
 *   audioDurationMs?: number
 * }
 * audioFormat: "wav" | "m4a" | "mp3" | "webm" | "mp4" |
 *             "mpeg" | "mpga" (default: "m4a")
 * model: "gpt-4o-transcribe" | "gpt-4o-mini-transcribe"
 *        (default: "gpt-4o-transcribe")
 * audioDurationMs: Duration of audio in milliseconds (for usage tracking)
 * Response: { text: string, minutesUsed: number, totalUsedThisMonth: number }
 */
export const transcribeAudio = onRequest(
  {
    region: ["us-central1", "asia-south1", "europe-west1"],
    secrets: ["OPENAI_API_KEY", "GOOGLE_PLAY_KEY"],
    memory: "512MiB",
  },
  async (request, response) => {
    const startTime = Date.now();
    let uid: string | null = null;

    try {
      // Handle warmup requests (GET or POST with warmup flag)
      // This warms up the Cloud Run instance without processing audio
      if (request.method === "GET" ||
        (request.method === "POST" && request.body?.warmup === true)) {
        logger.info("Warmup request received");
        response.status(200).json({warmed: true, timestamp: Date.now()});
        return;
      }

      // Only allow POST requests for actual transcription
      if (request.method !== "POST") {
        response.status(405).send("Method Not Allowed");
        return;
      }

      // Verify Firebase Auth token
      const authHeader = request.headers.authorization;
      const decodedToken = await verifyAuthToken(authHeader);

      if (!decodedToken) {
        response.status(401).json({
          error: "Unauthorized: Invalid or missing authentication token",
        });
        return;
      }

      uid = decodedToken.uid;
      logger.info(`Authenticated request from user: ${uid}`);

      // Check guest access and get plan limits (single fetch, reused below)
      const limits = await checkGuestAccessAndGetLimits(
        decodedToken, response, "transcribe"
      );
      if (!limits) {
        return; // Response already sent by helper
      }

      // Validate request body
      const {
        audioBase64,
        audioFormat = "m4a",
        model,
        audioDurationMs,
      } = request.body;
      if (!audioBase64 || typeof audioBase64 !== "string") {
        // Log failed request
        await logTranscriptionRequest(uid, false, Date.now() - startTime);
        response.status(400).json({
          error: "Missing or invalid audioBase64 field in request body",
        });
        return;
      }

      // Validate audio format - use validated format or default to m4a
      const validFormats = [
        "wav", "m4a", "mp3", "webm", "mp4", "mpeg", "mpga", "ogg",
      ];
      const format = validFormats.includes(audioFormat) ? audioFormat : "m4a";

      // Validate and set model - default to gpt-4o-mini-transcribe
      const validModels = [
        "gpt-4o-transcribe",
        "gpt-4o-mini-transcribe",
      ];
      const selectedModel = model && validModels.includes(model) ?
        model : "gpt-4o-mini-transcribe";

      logger.info(
        `Processing request - format: ${format}, model: ${selectedModel}`
      );

      // === Check quota: Trial → Pro → Block ===
      // OpenAI endpoint always uses PREMIUM tier (2x credits)
      const modelTier: ModelTier = "PREMIUM";
      const quotaResult = await checkQuotaOrBlock(
        uid, modelTier, limits, response, startTime, "[OpenAI] "
      );
      if (!quotaResult) return; // 403 already sent

      const {usageSource, currentUser} = quotaResult;
      logger.info(
        `[OpenAI] Proceeding with transcription using ${usageSource} quota`
      );


      // Check for OpenAI API key
      const apiKey = process.env.OPENAI_API_KEY;
      if (!apiKey) {
        logger.error("OPENAI_API_KEY environment variable is not set");
        response.status(500).json({
          error: "Server configuration error",
        });
        return;
      }

      // Decode base64 to buffer
      let audioBuffer: Buffer;
      try {
        audioBuffer = Buffer.from(audioBase64, "base64");
      } catch (error) {
        logger.error("Failed to decode base64 audio", error);
        response.status(400).json({
          error: "Invalid base64 audio data",
        });
        return;
      }

      // Create temporary file
      const tempDir = os.tmpdir();
      const tempFilePath = path.join(
        tempDir,
        `audio-${Date.now()}-${crypto.randomUUID()}.${format}`
      );

      try {
        // Write buffer to temporary file
        fs.writeFileSync(tempFilePath, audioBuffer);

        // Initialize OpenAI client
        const openai = new OpenAI({
          apiKey: apiKey,
        });

        // Call Whisper API
        logger.info(
          `Calling Whisper API (format: ${format}, ` +
          `model: ${selectedModel})`
        );
        const transcription = await openai.audio.transcriptions.create({
          file: fs.createReadStream(tempFilePath),
          model: selectedModel,
        });

        // Clean up temporary file
        fs.unlinkSync(tempFilePath);

        // Calculate duration for usage deduction
        // Use audioDurationMs from frontend if provided, else default
        const isValidDuration =
          typeof audioDurationMs === "number" &&
          !isNaN(audioDurationMs) &&
          audioDurationMs >= 0;

        let durationMs: number;
        if (isValidDuration) {
          // Use provided duration, but enforce minimum of 1 second
          durationMs = Math.max(audioDurationMs as number, 1000);
          logger.info(
            `Using provided audio duration: ${audioDurationMs}ms ` +
            `(billing: ${durationMs}ms)`
          );
        } else {
          // Default to 60 seconds if not provided
          durationMs = 60000;
          logger.warn(
            "No valid audioDurationMs provided " +
            `(received: ${audioDurationMs}), defaulting to 60 seconds`
          );
        }

        // Deduct credits and build response
        const {responseFields} = await deductAndBuildResponseData(
          uid, usageSource, currentUser, durationMs, modelTier,
          limits, startTime, "[OpenAI] "
        );

        response.status(200).json({
          text: transcription.text,
          ...responseFields,
        });
      } catch (error) {
        // Clean up temporary file if it exists
        if (fs.existsSync(tempFilePath)) {
          fs.unlinkSync(tempFilePath);
        }

        logger.error("Error processing audio transcription", error);

        // Log failed request
        if (uid) {
          await logTranscriptionRequest(uid, false, Date.now() - startTime);
        }

        response.status(500).json({
          error: "Failed to transcribe audio",
        });
      }
    } catch (error) {
      logger.error("Unexpected error in transcribeAudio", error);

      // Log failed request if we have a uid
      if (uid) {
        await logTranscriptionRequest(uid, false, Date.now() - startTime);
      }

      response.status(500).json({
        error: "Internal server error",
      });
    }
  });

/**
 * Transcribe audio using Groq Whisper API
 * POST /transcribeAudioGroq
 * Body: {
 *   audioBase64: string,
 *   audioFormat?: string,
 *   model?: string,
 *   audioDurationMs?: number
 * }
 * audioFormat: "wav" | "m4a" | "mp3" | "webm" | "mp4" |
 *             "mpeg" | "mpga" | "ogg" (default: "m4a")
 * model: "whisper-large-v3" | "whisper-large-v3-turbo"
 *        (default: "whisper-large-v3")
 * audioDurationMs: Duration of audio in milliseconds (for usage tracking)
 * Response: { text: string, secondsUsed: number, ... }
 *
 * This endpoint uses Groq's ultra-fast Whisper implementation.
 * Groq provides OpenAI-compatible API, so we use the OpenAI SDK
 * with Groq's base URL.
 */
export const transcribeAudioGroq = onRequest(
  {
    region: ["us-central1", "asia-south1", "europe-west1"],
    secrets: ["GROQ_API_KEY", "GOOGLE_PLAY_KEY"],
    memory: "512MiB",
  },
  async (request, response) => {
    const startTime = Date.now();
    let uid: string | null = null;

    try {
      // Handle warmup requests (GET or POST with warmup flag)
      if (request.method === "GET" ||
        (request.method === "POST" && request.body?.warmup === true)) {
        logger.info("Groq warmup request received");
        response.status(200).json({warmed: true, timestamp: Date.now()});
        return;
      }

      // Only allow POST requests for actual transcription
      if (request.method !== "POST") {
        response.status(405).send("Method Not Allowed");
        return;
      }

      // Verify Firebase Auth token
      const authHeader = request.headers.authorization;
      const decodedToken = await verifyAuthToken(authHeader);

      if (!decodedToken) {
        response.status(401).json({
          error: "Unauthorized: Invalid or missing authentication token",
        });
        return;
      }

      uid = decodedToken.uid;
      logger.info(`[Groq] Authenticated request from user: ${uid}`);

      // Check guest access and get plan limits (single fetch, reused below)
      const limits = await checkGuestAccessAndGetLimits(
        decodedToken, response, "Groq"
      );
      if (!limits) {
        return; // Response already sent by helper
      }

      // Validate request body
      const {
        audioBase64,
        audioFormat = "m4a",
        model,
        audioDurationMs,
      } = request.body;
      if (!audioBase64 || typeof audioBase64 !== "string") {
        await logTranscriptionRequest(uid, false, Date.now() - startTime);
        response.status(400).json({
          error: "Missing or invalid audioBase64 field in request body",
        });
        return;
      }

      // Validate audio format
      const validFormats = [
        "wav", "m4a", "mp3", "webm", "mp4", "mpeg", "mpga", "ogg",
      ];
      const format = validFormats.includes(audioFormat) ? audioFormat : "m4a";

      // Validate and set Groq model - default to whisper-large-v3
      const validGroqModels = [
        "whisper-large-v3",
        "whisper-large-v3-turbo",
      ];
      const selectedModel = model && validGroqModels.includes(model) ?
        model : "whisper-large-v3";

      // Determine model tier EARLY so AUTO can bypass quota check
      // whisper-large-v3-turbo = AUTO (free), whisper-large-v3 = STANDARD
      const isTurbo = selectedModel === "whisper-large-v3-turbo";
      const modelTier: ModelTier = isTurbo ? "AUTO" : "STANDARD";

      logger.info(
        `[Groq] Processing request - format: ${format}, ` +
        `model: ${selectedModel}, tier: ${modelTier}`
      );

      // === Check quota: Trial → Pro → Block ===
      const quotaResult = await checkQuotaOrBlock(
        uid, modelTier, limits, response, startTime, "[Groq] "
      );
      if (!quotaResult) return; // 403 already sent

      const {usageSource, currentUser} = quotaResult;
      logger.info(
        `[Groq] Proceeding with transcription using ${usageSource} quota`
      );

      // Check for Groq API key
      const groqApiKey = process.env.GROQ_API_KEY;
      if (!groqApiKey) {
        logger.error("GROQ_API_KEY environment variable is not set");
        response.status(500).json({
          error: "Server configuration error",
        });
        return;
      }

      // Decode base64 to buffer
      let audioBuffer: Buffer;
      try {
        audioBuffer = Buffer.from(audioBase64, "base64");
      } catch (error) {
        logger.error("[Groq] Failed to decode base64 audio", error);
        response.status(400).json({
          error: "Invalid base64 audio data",
        });
        return;
      }

      // Create temporary file
      const tempDir = os.tmpdir();
      const tempFilePath = path.join(
        tempDir,
        `audio-groq-${Date.now()}-${crypto.randomUUID()}.${format}`
      );

      try {
        // Write buffer to temporary file
        fs.writeFileSync(tempFilePath, audioBuffer);

        // Initialize Groq client using OpenAI SDK with Groq's base URL
        const groq = new OpenAI({
          apiKey: groqApiKey,
          baseURL: "https://api.groq.com/openai/v1",
        });

        // Context-style prompt to guide Whisper's punctuation style
        // Whisper mimics the style of provided context
        const transcriptionPrompt =
          "Hello, how are you? I'm doing well, thanks! " +
          "What time is the meeting?";

        // Call Groq Whisper API
        logger.info(
          `[Groq] Calling Whisper API (format: ${format}, ` +
          `model: ${selectedModel}, with punctuation prompt)`
        );
        const transcription = await groq.audio.transcriptions.create({
          file: fs.createReadStream(tempFilePath),
          model: selectedModel,
          prompt: transcriptionPrompt,
        });

        // Clean up temporary file
        fs.unlinkSync(tempFilePath);

        // Strip prompt if Whisper echoed it (rare with context-style)
        let cleanedText = transcription.text;
        const promptLower = transcriptionPrompt.toLowerCase();
        if (cleanedText && cleanedText.toLowerCase().startsWith(promptLower)) {
          cleanedText = cleanedText.slice(transcriptionPrompt.length).trim();
        }
        const finalText = cleanedText || transcription.text;

        // Calculate duration for usage deduction
        const isValidDuration =
          typeof audioDurationMs === "number" &&
          !isNaN(audioDurationMs) &&
          audioDurationMs >= 0;

        let durationMs: number;
        if (isValidDuration) {
          durationMs = Math.max(audioDurationMs as number, 1000);
          logger.info(
            `[Groq] Using provided audio duration: ${audioDurationMs}ms ` +
            `(billing: ${durationMs}ms)`
          );
        } else {
          durationMs = 60000;
          logger.warn(
            "[Groq] No valid audioDurationMs provided, defaulting to 60 seconds"
          );
        }

        // Deduct credits and build response
        const {responseFields} = await deductAndBuildResponseData(
          uid, usageSource, currentUser, durationMs, modelTier,
          limits, startTime, "[Groq] "
        );

        response.status(200).json({
          text: finalText,
          ...responseFields,
        });
      } catch (error) {
        // Clean up temporary file if it exists
        if (fs.existsSync(tempFilePath)) {
          fs.unlinkSync(tempFilePath);
        }

        logger.error("[Groq] Error processing audio transcription", error);
        await logTranscriptionRequest(uid, false, Date.now() - startTime);

        response.status(500).json({
          error: "Failed to transcribe audio with Groq",
        });
      }
    } catch (error) {
      logger.error("[Groq] Unexpected error in transcribeAudioGroq", error);

      if (uid) {
        await logTranscriptionRequest(uid, false, Date.now() - startTime);
      }

      response.status(500).json({
        error: "Internal server error",
      });
    }
  });

/**
 * Get trial status for the current user
 * GET or POST /getTrialStatus
 * Headers: Authorization: Bearer <firebase_id_token>
 * Response: {
 *   status: "active" | "expired_time" | "expired_usage",
 *   freeCreditsUsed: number,
 *   freeCreditsRemaining: number,
 *   freeTierCredits: number,
 *   trialExpiryDateMs: number,
 *   warningLevel: "none" | "fifty_percent" |
 *     "eighty_percent" | "ninety_five_percent"
 * }
 */
export const getTrialStatus = onRequest(
  {
    region: ["us-central1", "asia-south1", "europe-west1"],
    secrets: ["GOOGLE_PLAY_KEY"],
  },
  async (request, response) => {
    try {
      // Verify Firebase Auth token
      const authHeader = request.headers.authorization;
      const decodedToken = await verifyAuthToken(authHeader);

      if (!decodedToken) {
        response.status(401).json({
          error: "Unauthorized: Invalid or missing authentication token",
        });
        return;
      }

      const uid = decodedToken.uid;
      logger.info(`Getting trial status for user: ${uid}`);

      // Get or create user document
      let user = await getOrCreateUser(uid);

      // Check if user is a Pro subscriber
      const userPlan = user.plan ?? "free";
      const hasProSubscription = user.proSubscription?.status === "active";

      if (userPlan === "pro" || hasProSubscription) {
        // Pro user - return Pro status instead of trial status
        let proStatus = checkProStatus(user);

        // If subscription appears expired, re-verify with Google Play
        // It might have been auto-renewed, or it's truly canceled
        if (!proStatus.isActive && proStatus.needsReVerification) {
          logger.info(
            `Pro subscription expired for ${uid}, attempting re-verification`
          );
          const reVerifyResult = await reVerifyProSubscriptionWithGooglePlay(
            uid,
            user
          );
          if (reVerifyResult.renewed) {
            proStatus = reVerifyResult.proStatus;
            if (reVerifyResult.updatedUser) {
              user = reVerifyResult.updatedUser;
            }
            logger.info(
              `Subscription re-verified for ${uid}: renewed=true`
            );
          } else {
            // Subscription is truly expired/canceled
            // Downgrade user to free tier so they see the correct UI
            logger.info(
              `Subscription confirmed expired for ${uid}, ` +
              "downgrading to free tier"
            );
            await db.collection("users").doc(uid).update({
              plan: "free",
            });
            user = {...user, plan: "free"};
            // Fall through to free trial logic below
          }
        }

        // Only return Pro response if subscription is still active
        if (proStatus.isActive) {
          logger.info(
            `Pro user ${uid}: active=${proStatus.isActive}, ` +
            `${proStatus.proCreditsRemaining}/` +
            `${proStatus.proCreditsLimit} credits`
          );

          response.status(200).json({
            plan: "pro",
            status: "active",
            proCreditsUsed: proStatus.proCreditsUsed,
            proCreditsRemaining: proStatus.proCreditsRemaining,
            proCreditsLimit: proStatus.proCreditsLimit,
            resetDateMs: proStatus.currentPeriodEndMs,
            warningLevel:
              proStatus.proCreditsRemaining <
                  proStatus.proCreditsLimit * 0.1 ?
                "ninety_percent" : "none",
          });
          return;
        }
        // If not active, fall through to free trial response below
      }

      // Free trial user - existing logic

      // Get lifetime usage from logs to check for sync issues
      const lifetimeUsage = await getTotalLifetimeUsage(uid);

      // Sync freeCreditsUsed if there's a mismatch
      // (e.g., from legacy data or missed updates)
      if (lifetimeUsage > 0 && user.freeCreditsUsed !== lifetimeUsage) {
        logger.info(
          `Syncing freeCreditsUsed for ${uid}: ` +
          `stored=${user.freeCreditsUsed}, actual=${lifetimeUsage}`
        );
        // Update user document with correct lifetime usage
        await db.collection("users").doc(uid).update({
          freeCreditsUsed: lifetimeUsage,
        });
        // Update local user object for response
        user = {...user, freeCreditsUsed: lifetimeUsage};
      }

      // Fetch trial limits and check status
      const trialLimits = await getTrialLimits();
      const trialStatus = checkTrialStatus(user, trialLimits.freeTierCredits);

      // Get total credits this billing period
      // for the "Usage This Month" display
      const totalCreditsThisMonth = await getTotalUsageThisPeriod(uid);

      response.status(200).json({
        plan: "free",
        status: trialStatus.status,
        freeCreditsUsed: trialStatus.freeCreditsUsed,
        freeCreditsRemaining: trialStatus.freeCreditsRemaining,
        freeTierCredits: trialStatus.freeTierCredits,
        trialExpiryDateMs: trialStatus.trialExpiryDateMs,
        warningLevel: trialStatus.warningLevel,
        totalCreditsThisMonth: totalCreditsThisMonth,
      });
    } catch (error) {
      logger.error("Error getting trial status", error);
      response.status(500).json({
        error: "Internal server error",
      });
    }
  }
);

/**
 * Get subscription status for the current user (Iteration 3)
 * Returns unified status for both free trial and Pro users
 * GET or POST /getSubscriptionStatus
 * Headers: Authorization: Bearer <firebase_id_token>
 */
export const getSubscriptionStatus = onRequest(
  {
    region: ["us-central1", "asia-south1", "europe-west1"],
    secrets: ["GOOGLE_PLAY_KEY"],
  },
  async (request, response) => {
    try {
      // Verify Firebase Auth token
      const authHeader = request.headers.authorization;
      const decodedToken = await verifyAuthToken(authHeader);

      if (!decodedToken) {
        response.status(401).json({
          error: "Unauthorized: Invalid or missing authentication token",
        });
        return;
      }

      const uid = decodedToken.uid;
      logger.info(`Getting subscription status for user: ${uid}`);

      // Check guest access and get plan limits (single fetch, reused below)
      const limits = await checkGuestAccessAndGetLimits(
        decodedToken, response, "getSubscriptionStatus"
      );
      if (!limits) {
        return; // Response already sent by helper
      }

      let user = await getOrCreateUser(uid);

      // Normalize plan field (default to 'free' if undefined)
      const userPlan = user.plan ?? "free";
      // Also check for active proSubscription as fallback
      const hasProSubscription = user.proSubscription?.status === "active";

      // Build response based on user's current plan
      if (userPlan === "pro" || hasProSubscription) {
        // Pro user response
        let proStatus = checkProStatus(user);

        // If subscription appears expired, try re-verifying with Google Play
        // The subscription might have been auto-renewed
        if (!proStatus.isActive && proStatus.needsReVerification) {
          logger.info(
            `Pro subscription expired for ${uid}, attempting re-verification`
          );
          const reVerifyResult = await reVerifyProSubscriptionWithGooglePlay(
            uid,
            user
          );
          if (reVerifyResult.renewed) {
            proStatus = reVerifyResult.proStatus;
            if (reVerifyResult.updatedUser) {
              user = reVerifyResult.updatedUser;
            }
            logger.info(
              `Subscription re-verified for ${uid}: renewed=true`
            );
          }
        }

        response.status(200).json({
          plan: "pro",
          isActive: proStatus.isActive,
          // Pro-specific fields (credits-based)
          proCreditsUsed: proStatus.proCreditsUsed,
          proCreditsRemaining: proStatus.proCreditsRemaining,
          proCreditsLimit: proStatus.proCreditsLimit,
          resetDateMs: proStatus.currentPeriodEndMs,
          subscriptionStartDateMs:
            user.proSubscription?.startDate?.toMillis() ?? null,
          subscriptionStatus: user.proSubscription?.status ?? "active",
          // For backward compatibility
          status: proStatus.isActive ? "active" : "expired",
          warningLevel:
            proStatus.proCreditsRemaining <
                proStatus.proCreditsLimit * 0.1 ?
              "ninety_percent" : "none",
        });
      } else {
        // Free trial user response
        const trialStatus = checkTrialStatus(user, limits.freeTierCredits);
        const totalCreditsThisMonth = await getTotalUsageThisPeriod(uid);

        response.status(200).json({
          plan: "free",
          isActive: trialStatus.isValid,
          // Trial-specific fields (credits-based)
          freeCreditsUsed: trialStatus.freeCreditsUsed,
          freeCreditsRemaining: trialStatus.freeCreditsRemaining,
          freeTierCredits: trialStatus.freeTierCredits,
          trialExpiryDateMs: trialStatus.trialExpiryDateMs,
          totalCreditsThisMonth: totalCreditsThisMonth,
          // Common fields
          status: trialStatus.status,
          warningLevel: trialStatus.warningLevel,
          // Pro plan info for upgrade UI
          proPlanEnabled: limits.proPlanEnabled,
        });
      }
    } catch (error) {
      logger.error("Error getting subscription status", error);
      response.status(500).json({
        error: "Internal server error",
      });
    }
  });

/**
 * Verify Google Play subscription purchase
 * POST /verifySubscription
 * Body: {
 *   purchaseToken: string,
 *   productId: string
 * }
 * Response: { success: boolean, plan: string, proStatus: {...} }
 */
export const verifySubscription = onRequest(
  {
    region: ["us-central1", "asia-south1", "europe-west1"],
    secrets: ["GOOGLE_PLAY_KEY"],
  },
  async (request, response) => {
    try {
      // Only allow POST requests
      if (request.method !== "POST") {
        response.status(405).send("Method Not Allowed");
        return;
      }

      // Verify Firebase Auth token
      const authHeader = request.headers.authorization;
      const decodedToken = await verifyAuthToken(authHeader);

      if (!decodedToken) {
        response.status(401).json({
          error: "Unauthorized: Invalid or missing authentication token",
        });
        return;
      }

      const uid = decodedToken.uid;
      logger.info(`Verifying subscription for user: ${uid}`);

      // Validate request body
      const {purchaseToken, productId} = request.body;
      if (!purchaseToken || !productId) {
        response.status(400).json({
          error: "Missing purchaseToken or productId",
        });
        return;
      }

      // Get the service account key from secret
      const keyJson = process.env.GOOGLE_PLAY_KEY;
      if (!keyJson) {
        logger.error("GOOGLE_PLAY_KEY secret not configured");
        response.status(500).json({
          error: "Server configuration error",
        });
        return;
      }

      // Parse the service account key
      let credentials;
      try {
        credentials = JSON.parse(keyJson);
      } catch (parseError) {
        logger.error("Failed to parse GOOGLE_PLAY_KEY", parseError);
        response.status(500).json({
          error: "Server configuration error",
        });
        return;
      }

      // Initialize Google Auth client
      // DEBUG: Log the service account email being used
      if (credentials.client_email) {
        logger.info(`Using service account: ${credentials.client_email}`);
      }

      const auth = new google.auth.GoogleAuth({
        credentials: credentials,
        scopes: ["https://www.googleapis.com/auth/androidpublisher"],
      });

      const androidpublisher = google.androidpublisher({
        version: "v3",
        auth: auth,
      });

      // Verify the subscription with Google Play
      let subscriptionData;
      try {
        const result = await androidpublisher.purchases.subscriptions.get({
          packageName: "com.whispertype.app",
          subscriptionId: productId,
          token: purchaseToken,
        });
        subscriptionData = result.data;
        logger.info(
          "Google Play verification response: " +
          `paymentState=${subscriptionData.paymentState}, ` +
          `expiryTimeMillis=${subscriptionData.expiryTimeMillis}`
        );
      } catch (apiError: unknown) {
        const error = apiError as { message?: string; code?: number };
        logger.error("Google Play API error", error);
        response.status(400).json({
          error: "Invalid purchase",
          details: error.message || "Unknown error",
        });
        return;
      }

      // Check if subscription is valid
      // paymentState: 0=pending, 1=received, 2=free trial, 3=deferred
      const paymentState = subscriptionData.paymentState;
      const expiryTimeMs = parseInt(
        subscriptionData.expiryTimeMillis || "0",
        10
      );
      const now = Date.now();

      // Subscription is valid if payment received and not expired
      // Also allow free trial (paymentState=2) and pending upgrades (0)
      const isActive = (
        (paymentState === 1 || paymentState === 2) &&
        expiryTimeMs > now
      );

      if (!isActive) {
        logger.warn(
          `Subscription not active for ${uid}: ` +
          `paymentState=${paymentState}, expired=${expiryTimeMs < now}`
        );
        response.status(400).json({
          error: "Subscription not active",
          paymentState: paymentState,
          expired: expiryTimeMs < now,
        });
        return;
      }

      // Update user document in Firestore
      const userRef = db.collection("users").doc(uid);
      const nowTimestamp = admin.firestore.Timestamp.now();
      const expiryTimestamp =
        admin.firestore.Timestamp.fromMillis(expiryTimeMs);

      // Check if this is a new subscription or renewal
      const userDoc = await userRef.get();
      const existingUser = userDoc.exists ?
        userDoc.data() as UserDocument : null;
      const existingSub = existingUser?.proSubscription;

      // Determine if this is a new period (reset usage)
      let proCreditsUsed = 0;
      if (existingSub && existingSub.purchaseToken === purchaseToken) {
        // Same purchase token - keep existing usage
        proCreditsUsed = existingSub.proCreditsUsed || 0;
      } else if (existingSub && existingSub.currentPeriodEnd) {
        // Different token but existing sub - check if new period
        const oldPeriodEnd = existingSub.currentPeriodEnd.toMillis();
        if (now > oldPeriodEnd) {
          // New period - reset credits
          proCreditsUsed = 0;
          logger.info(`New billing period for ${uid}, resetting credits`);
        } else {
          // Same period - keep credits
          proCreditsUsed = existingSub.proCreditsUsed || 0;
        }
      }

      // Build the subscription object
      const proSubscription: ProSubscription = {
        purchaseToken: purchaseToken,
        productId: productId,
        status: "active",
        startDate: existingSub?.startDate || nowTimestamp,
        currentPeriodStart: nowTimestamp,
        currentPeriodEnd: expiryTimestamp,
        proCreditsUsed: proCreditsUsed,
      };

      // Update the user document
      await userRef.set(
        {
          plan: "pro",
          proSubscription: proSubscription,
        },
        {merge: true}
      );

      logger.info(
        `Subscription verified and saved for ${uid}: ` +
        `expires ${expiryTimestamp.toDate().toISOString()}`
      );

      // Return success with Pro status (credits-based)
      response.status(200).json({
        success: true,
        plan: "pro",
        proStatus: {
          isActive: true,
          proCreditsUsed: proCreditsUsed,
          proCreditsRemaining: getCreditsForProduct(productId) - proCreditsUsed,
          proCreditsLimit: getCreditsForProduct(productId),
          currentPeriodEndMs: expiryTimeMs,
        },
      });
    } catch (error) {
      logger.error("Error verifying subscription", error);
      response.status(500).json({
        error: "Internal server error",
      });
    }
  }
);

/**
 * Delete user account and all associated data
 * POST /deleteAccount
 * Headers: Authorization: Bearer <firebase_id_token>
 * Response: { success: boolean, message: string }
 */
export const deleteAccount = functions.https.onRequest(
  async (request, response) => {
    // CORS Headers - restrict to app requests only
    const allowedOrigins = [
      "https://whispertype-1de9f.web.app",
      "https://whispertype-1de9f.firebaseapp.com",
    ];
    const origin = request.headers.origin;
    if (origin && allowedOrigins.includes(origin)) {
      response.set("Access-Control-Allow-Origin", origin);
    }
    response.set("Access-Control-Allow-Methods", "POST, OPTIONS");
    response.set("Access-Control-Allow-Headers", "Content-Type, Authorization");

    if (request.method === "OPTIONS") {
      response.status(204).send("");
      return;
    }

    try {
      // Only allow POST requests
      if (request.method !== "POST") {
        response.status(405).send("Method Not Allowed");
        return;
      }

      // Verify Firebase Auth token
      const authHeader = request.headers.authorization;
      const decodedToken = await verifyAuthToken(authHeader);

      if (!decodedToken) {
        response.status(401).json({
          error: "Unauthorized: Invalid or missing authentication token",
        });
        return;
      }

      const uid = decodedToken.uid;
      logger.info(`Deleting account for user: ${uid}`);

      // Delete all user data from Firestore
      const batch = db.batch();

      // Delete user document
      const userRef = db.collection("users").doc(uid);
      batch.delete(userRef);

      // Delete all usage logs
      const usageLogsRef = db.collection("usage_logs").doc(uid);
      const usageEntriesSnapshot = await usageLogsRef
        .collection("entries")
        .get();

      usageEntriesSnapshot.forEach((doc) => {
        batch.delete(doc.ref);
      });
      batch.delete(usageLogsRef);

      // Delete transcription request logs
      const requestLogsRef = db.collection("transcription_requests").doc(uid);
      const requestEntriesSnapshot = await requestLogsRef
        .collection("requests")
        .get();

      requestEntriesSnapshot.forEach((doc) => {
        batch.delete(doc.ref);
      });
      batch.delete(requestLogsRef);

      // Commit all Firestore deletions
      await batch.commit();
      logger.info(`Firestore data deleted for user: ${uid}`);

      // Delete Firebase Auth account
      await admin.auth().deleteUser(uid);
      logger.info(`Firebase Auth account deleted for user: ${uid}`);

      response.status(200).json({
        success: true,
        message: "Account and all associated data have been deleted",
      });
    } catch (error) {
      logger.error("Error deleting account", error);
      response.status(500).json({
        error: "Failed to delete account",
        message: "An error occurred while deleting your account",
      });
    }
  });

// ============================================================================
// ADMIN FUNCTIONS
// ============================================================================

// Admin dashboard allowed origins
const ADMIN_CORS_ORIGINS = [
  "https://whispertype-admin.web.app",
  "https://whispertype-admin.firebaseapp.com",
  "http://localhost:3000",
];

/**
 * Check if a user has admin privileges
 * @param {admin.auth.DecodedIdToken} decodedToken - The decoded Firebase token
 * @return {boolean} True if user has admin claim
 */
function isAdmin(decodedToken: admin.auth.DecodedIdToken): boolean {
  return decodedToken.admin === true;
}

/**
 * Verify Firebase ID token and admin claim
 * @param {string | undefined} authHeader - The Authorization header value
 * @return {Promise<admin.auth.DecodedIdToken | null>} Decoded token if admin
 */
async function verifyAdminToken(
  authHeader: string | undefined
): Promise<admin.auth.DecodedIdToken | null> {
  const decodedToken = await verifyAuthToken(authHeader);
  if (!decodedToken) return null;
  if (!isAdmin(decodedToken)) {
    logger.warn(`Non-admin user ${decodedToken.uid} attempted admin action`);
    return null;
  }
  return decodedToken;
}

/**
 * Set CORS headers for admin endpoints
 * @param {Response} response - Express response object
 * @param {string | undefined} origin - Request origin header
 */
function setAdminCorsHeaders(response: Response, origin: string | undefined) {
  if (origin && ADMIN_CORS_ORIGINS.includes(origin)) {
    response.set("Access-Control-Allow-Origin", origin);
  }
  response.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  response.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
}

/**
 * List users with pagination and optional search
 * GET /adminListUsers
 * Query params: limit, pageToken, search, plan
 */
export const adminListUsers = onRequest(
  {region: ["us-central1"]},
  async (request, response) => {
    const origin = request.headers.origin;
    setAdminCorsHeaders(response, origin);

    if (request.method === "OPTIONS") {
      response.status(204).send("");
      return;
    }

    try {
      const decodedToken = await verifyAdminToken(
        request.headers.authorization
      );
      if (!decodedToken) {
        response.status(403).json({error: "Admin access required"});
        return;
      }

      const limit = Math.min(
        parseInt(request.query.limit as string) || 20,
        100
      );
      const pageToken = request.query.pageToken as string | undefined;
      const search = request.query.search as string | undefined;
      const planFilter =
        request.query.plan as "free" | "pro" | "all" | undefined;

      logger.info(
        `[Admin] Listing users: limit=${limit}, ` +
        `search=${search}, plan=${planFilter}`
      );

      // Get users from Firebase Auth
      const listResult = await admin.auth().listUsers(limit, pageToken);

      // Get Firestore data for each user
      const usersWithData = await Promise.all(
        listResult.users.map(async (userRecord) => {
          const userDoc = await db
            .collection("users").doc(userRecord.uid).get();
          const userData = userDoc.exists ?
            userDoc.data() as UserDocument : null;

          return {
            uid: userRecord.uid,
            email: userRecord.email || null,
            displayName: userRecord.displayName || null,
            photoURL: userRecord.photoURL || null,
            plan: userData?.plan || "free",
            freeCreditsUsed: userData?.freeCreditsUsed || 0,
            createdAt: userRecord.metadata.creationTime ?
              new Date(userRecord.metadata.creationTime).getTime() : 0,
            lastSignInTime: userRecord.metadata.lastSignInTime || null,
            disabled: userRecord.disabled,
          };
        })
      );

      // Filter by search if provided
      let filteredUsers = usersWithData;
      if (search) {
        const searchLower = search.toLowerCase();
        filteredUsers = filteredUsers.filter(
          (u) =>
            u.email?.toLowerCase().includes(searchLower) ||
            u.uid.toLowerCase().includes(searchLower) ||
            u.displayName?.toLowerCase().includes(searchLower)
        );
      }

      // Filter by plan if provided
      if (planFilter && planFilter !== "all") {
        filteredUsers = filteredUsers.filter((u) => u.plan === planFilter);
      }

      response.status(200).json({
        users: filteredUsers,
        nextPageToken: listResult.pageToken || null,
        totalCount: filteredUsers.length,
      });
    } catch (error) {
      logger.error("[Admin] Error listing users", error);
      response.status(500).json({error: "Failed to list users"});
    }
  }
);

/**
 * Get detailed user information
 * GET /adminGetUserDetails?uid=xxx
 */
export const adminGetUserDetails = onRequest(
  {region: ["us-central1"]},
  async (request, response) => {
    const origin = request.headers.origin;
    setAdminCorsHeaders(response, origin);

    if (request.method === "OPTIONS") {
      response.status(204).send("");
      return;
    }

    try {
      const decodedToken = await verifyAdminToken(
        request.headers.authorization
      );
      if (!decodedToken) {
        response.status(403).json({error: "Admin access required"});
        return;
      }

      const uid = request.query.uid as string;
      if (!uid) {
        response.status(400).json({error: "Missing uid parameter"});
        return;
      }

      logger.info(`[Admin] Getting details for user: ${uid}`);

      // Get Firebase Auth user
      let userRecord;
      try {
        userRecord = await admin.auth().getUser(uid);
      } catch {
        response.status(404).json({error: "User not found"});
        return;
      }

      // Get Firestore user document
      const userDoc = await db.collection("users").doc(uid).get();
      const userData = userDoc.exists ?
        userDoc.data() as UserDocument : null;

      // Get plan limits
      const limits = await getPlanLimits();

      // Get recent usage logs
      const usageSnapshot = await db
        .collection("usage_logs")
        .doc(uid)
        .collection("entries")
        .orderBy("timestamp", "desc")
        .limit(20)
        .get();

      const recentUsage = usageSnapshot.docs.map((doc) => ({
        id: doc.id,
        ...doc.data(),
        timestamp: doc.data().timestamp?.toMillis() || 0,
      }));

      // Get stats
      const transcriptionsSnapshot = await db
        .collection("transcriptions")
        .where("uid", "==", uid)
        .get();

      let totalTranscriptions = 0;
      let successfulTranscriptions = 0;
      transcriptionsSnapshot.forEach((doc) => {
        totalTranscriptions++;
        if (doc.data().success) successfulTranscriptions++;
      });

      const totalCreditsUsed = await getTotalLifetimeUsage(uid);
      const creditsThisMonth = await getTotalUsageThisPeriod(uid);

      const user = {
        uid: userRecord.uid,
        email: userRecord.email || null,
        displayName: userRecord.displayName || null,
        photoURL: userRecord.photoURL || null,
        emailVerified: userRecord.emailVerified,
        disabled: userRecord.disabled,
        createdAt: userRecord.metadata.creationTime ?
          new Date(userRecord.metadata.creationTime).getTime() : 0,
        lastSignInTime: userRecord.metadata.lastSignInTime || null,
        plan: userData?.plan || "free",
        freeCreditsUsed: userData?.freeCreditsUsed || 0,
        freeTierCredits: limits.freeTierCredits,
        trialExpiryDate: userData?.trialExpiryDate?.toMillis() || 0,
        freeTrialStart: userData?.freeTrialStart?.toMillis() || 0,
        proSubscription: userData?.proSubscription ? {
          status: userData.proSubscription.status,
          productId: userData.proSubscription.productId,
          proCreditsUsed: userData.proSubscription.proCreditsUsed,
          proCreditsLimit: getCreditsForProduct(
            userData.proSubscription.productId
          ),
          currentPeriodStart:
            userData.proSubscription.currentPeriodStart?.toMillis() || 0,
          currentPeriodEnd:
            userData.proSubscription.currentPeriodEnd?.toMillis() || 0,
        } : undefined,
      };

      response.status(200).json({
        user,
        recentUsage,
        stats: {
          totalTranscriptions,
          successfulTranscriptions,
          totalCreditsUsed,
          creditsThisMonth,
        },
      });
    } catch (error) {
      logger.error("[Admin] Error getting user details", error);
      response.status(500).json({error: "Failed to get user details"});
    }
  }
);

/**
 * Adjust user credits
 * POST /adminAdjustCredits
 * Body: { uid, adjustment, reason }
 */
export const adminAdjustCredits = onRequest(
  {region: ["us-central1"]},
  async (request, response) => {
    const origin = request.headers.origin;
    setAdminCorsHeaders(response, origin);

    if (request.method === "OPTIONS") {
      response.status(204).send("");
      return;
    }

    if (request.method !== "POST") {
      response.status(405).send("Method Not Allowed");
      return;
    }

    try {
      const decodedToken = await verifyAdminToken(
        request.headers.authorization
      );
      if (!decodedToken) {
        response.status(403).json({error: "Admin access required"});
        return;
      }

      const {uid, adjustment, reason} = request.body;
      if (!uid || typeof adjustment !== "number" || !reason) {
        response.status(400).json({
          error: "Missing required fields: uid, adjustment, reason",
        });
        return;
      }

      logger.info(
        `[Admin] Adjusting credits for ${uid}: ${adjustment} (${reason})`
      );

      const userRef = db.collection("users").doc(uid);
      const limits = await getPlanLimits();

      // Use transaction to update credits
      const result = await db.runTransaction(async (transaction) => {
        const userDoc = await transaction.get(userRef);
        if (!userDoc.exists) {
          throw new Error("User not found");
        }

        const userData = userDoc.data() as UserDocument;

        // Adjust based on plan
        if (userData.plan === "pro" && userData.proSubscription) {
          // Adjust pro credits (subtract adjustment from used)
          const newUsed = Math.max(
            0,
            userData.proSubscription.proCreditsUsed - adjustment
          );
          transaction.update(userRef, {
            "proSubscription.proCreditsUsed": newUsed,
          });
          return {
            newCreditsUsed: newUsed,
            creditsRemaining: getCreditsForProduct(
              userData.proSubscription.productId
            ) - newUsed,
          };
        } else {
          // Adjust free credits (subtract adjustment from used)
          const newUsed = Math.max(0, userData.freeCreditsUsed - adjustment);
          transaction.update(userRef, {freeCreditsUsed: newUsed});
          return {
            newCreditsUsed: newUsed,
            creditsRemaining: limits.freeTierCredits - newUsed,
          };
        }
      });

      // Log the admin action
      await db.collection("admin_audit_logs").add({
        action: "adjust_credits",
        adminUid: decodedToken.uid,
        targetUid: uid,
        adjustment,
        reason,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
      });

      response.status(200).json({
        success: true,
        newCreditsUsed: result.newCreditsUsed,
        creditsRemaining: result.creditsRemaining,
      });
    } catch (error) {
      logger.error("[Admin] Error adjusting credits", error);
      response.status(500).json({error: "Failed to adjust credits"});
    }
  }
);

/**
 * Update user plan
 * POST /adminUpdateUserPlan
 * Body: { uid, plan, resetCredits?, extendTrialDays?, proCreditsLimit? }
 */
export const adminUpdateUserPlan = onRequest(
  {region: ["us-central1"]},
  async (request, response) => {
    const origin = request.headers.origin;
    setAdminCorsHeaders(response, origin);

    if (request.method === "OPTIONS") {
      response.status(204).send("");
      return;
    }

    if (request.method !== "POST") {
      response.status(405).send("Method Not Allowed");
      return;
    }

    try {
      const decodedToken = await verifyAdminToken(
        request.headers.authorization
      );
      if (!decodedToken) {
        response.status(403).json({error: "Admin access required"});
        return;
      }

      const {uid, plan, resetCredits, extendTrialDays} = request.body;
      if (!uid || !["free", "pro"].includes(plan)) {
        response.status(400).json({
          error: "Missing required fields: uid, plan (free|pro)",
        });
        return;
      }

      logger.info(`[Admin] Updating plan for ${uid} to ${plan}`);

      const userRef = db.collection("users").doc(uid);
      const userDoc = await userRef.get();

      if (!userDoc.exists) {
        response.status(404).json({error: "User not found"});
        return;
      }

      const updateData: Record<string, unknown> = {plan};

      if (resetCredits) {
        if (plan === "pro") {
          updateData["proSubscription.proCreditsUsed"] = 0;
        } else {
          updateData.freeCreditsUsed = 0;
        }
      }

      if (extendTrialDays && plan === "free") {
        const userData = userDoc.data() as UserDocument;
        const currentExpiry = userData.trialExpiryDate?.toDate() || new Date();
        currentExpiry.setDate(currentExpiry.getDate() + extendTrialDays);
        updateData.trialExpiryDate =
          admin.firestore.Timestamp.fromDate(currentExpiry);
      }

      await userRef.update(updateData);

      // Log the admin action
      await db.collection("admin_audit_logs").add({
        action: "update_plan",
        adminUid: decodedToken.uid,
        targetUid: uid,
        newPlan: plan,
        resetCredits: !!resetCredits,
        extendTrialDays: extendTrialDays || 0,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
      });

      // Fetch updated user
      const updatedDoc = await userRef.get();
      const updatedData = updatedDoc.data() as UserDocument;
      const limits = await getPlanLimits();

      response.status(200).json({
        success: true,
        user: {
          uid,
          plan: updatedData.plan,
          freeCreditsUsed: updatedData.freeCreditsUsed,
          freeTierCredits: limits.freeTierCredits,
          trialExpiryDate: updatedData.trialExpiryDate?.toMillis() || 0,
          proSubscription: updatedData.proSubscription ? {
            status: updatedData.proSubscription.status,
            proCreditsUsed: updatedData.proSubscription.proCreditsUsed,
            proCreditsLimit: getCreditsForProduct(
              updatedData.proSubscription.productId
            ),
          } : undefined,
        },
      });
    } catch (error) {
      logger.error("[Admin] Error updating user plan", error);
      response.status(500).json({error: "Failed to update user plan"});
    }
  }
);

/**
 * Get analytics data
 * GET /adminGetAnalytics?period=30d
 */
export const adminGetAnalytics = onRequest(
  {region: ["us-central1"]},
  async (request, response) => {
    const origin = request.headers.origin;
    setAdminCorsHeaders(response, origin);

    if (request.method === "OPTIONS") {
      response.status(204).send("");
      return;
    }

    try {
      const decodedToken = await verifyAdminToken(
        request.headers.authorization
      );
      if (!decodedToken) {
        response.status(403).json({error: "Admin access required"});
        return;
      }

      const period = request.query.period as string || "30d";
      logger.info(`[Admin] Getting analytics for period: ${period}`);

      // Calculate start date based on period
      const now = new Date();
      let startDate: Date;
      switch (period) {
      case "7d":
        startDate = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
        break;
      case "90d":
        startDate = new Date(now.getTime() - 90 * 24 * 60 * 60 * 1000);
        break;
      case "all":
        startDate = new Date(0);
        break;
      default: // 30d
        startDate = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
      }

      // Get all users
      const usersSnapshot = await db.collection("users").get();
      let totalUsers = 0;
      let freeUsers = 0;
      let proUsers = 0;
      let totalCreditsUsed = 0;

      const userGrowthMap = new Map<string, number>();
      const planDistribution: {plan: string; count: number}[] = [];

      usersSnapshot.forEach((doc) => {
        totalUsers++;
        const data = doc.data() as UserDocument;

        if (data.plan === "pro") {
          proUsers++;
          totalCreditsUsed += data.proSubscription?.proCreditsUsed || 0;
        } else {
          freeUsers++;
          totalCreditsUsed += data.freeCreditsUsed || 0;
        }

        // Track user growth by date
        if (data.createdAt) {
          const dateKey = data.createdAt.toDate().toISOString().split("T")[0];
          userGrowthMap.set(dateKey, (userGrowthMap.get(dateKey) || 0) + 1);
        }
      });

      planDistribution.push({plan: "Free", count: freeUsers});
      planDistribution.push({plan: "Pro", count: proUsers});

      // Get transcription stats
      const startTimestamp = admin.firestore.Timestamp.fromDate(startDate);
      const transcriptionsSnapshot = await db
        .collection("transcriptions")
        .where("createdAt", ">=", startTimestamp)
        .get();

      let totalTranscriptions = 0;
      let successfulTranscriptions = 0;
      const activeUserSet = new Set<string>();

      transcriptionsSnapshot.forEach((doc) => {
        totalTranscriptions++;
        const data = doc.data();
        if (data.success) successfulTranscriptions++;
        if (data.uid) activeUserSet.add(data.uid);
      });

      const successRate = totalTranscriptions > 0 ?
        successfulTranscriptions / totalTranscriptions : 0;

      // Build user growth chart data
      const userGrowth: {date: string; count: number}[] = [];
      const sortedDates = Array.from(userGrowthMap.keys()).sort();
      let cumulativeCount = 0;
      for (const date of sortedDates) {
        cumulativeCount += userGrowthMap.get(date) || 0;
        if (new Date(date) >= startDate) {
          userGrowth.push({date, count: cumulativeCount});
        }
      }

      // Build credits usage chart (simplified - by date)
      const creditsUsage: {date: string; free: number; pro: number}[] = [];
      // For now, return empty array - implementing full chart data would
      // require aggregating usage_logs which is expensive

      response.status(200).json({
        summary: {
          totalUsers,
          activeUsersThisPeriod: activeUserSet.size,
          freeUsers,
          proUsers,
          totalCreditsUsed,
          totalTranscriptions,
          successRate,
        },
        charts: {
          userGrowth,
          creditsUsage,
          transcriptionsByTier: [],
          planDistribution,
        },
      });
    } catch (error) {
      logger.error("[Admin] Error getting analytics", error);
      response.status(500).json({error: "Failed to get analytics"});
    }
  }
);

/**
 * Set admin claim for a user
 * POST /adminSetAdminClaim
 * Body: { uid, isAdmin }
 */
export const adminSetAdminClaim = onRequest(
  {region: ["us-central1"]},
  async (request, response) => {
    const origin = request.headers.origin;
    setAdminCorsHeaders(response, origin);

    if (request.method === "OPTIONS") {
      response.status(204).send("");
      return;
    }

    if (request.method !== "POST") {
      response.status(405).send("Method Not Allowed");
      return;
    }

    try {
      const decodedToken = await verifyAdminToken(
        request.headers.authorization
      );
      if (!decodedToken) {
        response.status(403).json({error: "Admin access required"});
        return;
      }

      const {uid, isAdmin: makeAdmin} = request.body;
      if (!uid || typeof makeAdmin !== "boolean") {
        response.status(400).json({
          error: "Missing required fields: uid, isAdmin (boolean)",
        });
        return;
      }

      // Prevent removing own admin access
      if (uid === decodedToken.uid && !makeAdmin) {
        response.status(400).json({
          error: "Cannot remove your own admin access",
        });
        return;
      }

      logger.info(
        `[Admin] Setting admin claim for ${uid} to ${makeAdmin}`
      );

      await admin.auth().setCustomUserClaims(uid, {admin: makeAdmin});

      // Log the admin action
      await db.collection("admin_audit_logs").add({
        action: "set_admin_claim",
        adminUid: decodedToken.uid,
        targetUid: uid,
        isAdmin: makeAdmin,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
      });

      response.status(200).json({success: true});
    } catch (error) {
      logger.error("[Admin] Error setting admin claim", error);
      response.status(500).json({error: "Failed to set admin claim"});
    }
  }
);
