/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 * import {onDocumentWritten} from "firebase-functions/v2/firestore";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

import {setGlobalOptions} from "firebase-functions";
import {onRequest} from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";
import OpenAI from "openai";
import * as fs from "fs";
import * as os from "os";
import * as path from "path";

// Initialize Firebase Admin
admin.initializeApp();
const db = admin.firestore();
const remoteConfig = admin.remoteConfig();

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
 */
interface PlanLimits {
  // Trial limits
  freeTrialSeconds: number;
  trialDurationMonths: number;
  // Pro limits
  proSecondsLimit: number;
  proProductId: string;
  proPlanEnabled: boolean;
}

// Cache for Remote Config values (5 minute TTL)
let cachedPlanLimits: PlanLimits | null = null;
let cacheTimestamp = 0;
const CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

// Default fallback values if Remote Config is unavailable
const DEFAULT_FREE_TRIAL_MINUTES = 20;
const DEFAULT_TRIAL_DURATION_MONTHS = 3;
const DEFAULT_PRO_MINUTES = 150;
const DEFAULT_PRO_PRODUCT_ID = "whispertype_pro_monthly";

/**
 * Get plan limits from Remote Config with caching
 * Includes both Trial and Pro limits
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

    // Extract trial parameters with defaults
    const freeTrialParam = template.parameters?.free_trial_minutes;
    const freeTrialMinutes = freeTrialParam &&
      freeTrialParam.defaultValue &&
      "value" in freeTrialParam.defaultValue ?
      Number(freeTrialParam.defaultValue.value) :
      DEFAULT_FREE_TRIAL_MINUTES;

    const trialDurationParam = template.parameters?.trial_duration_months;
    const trialDurationMonths = trialDurationParam &&
      trialDurationParam.defaultValue &&
      "value" in trialDurationParam.defaultValue ?
      Number(trialDurationParam.defaultValue.value) :
      DEFAULT_TRIAL_DURATION_MONTHS;

    // Extract Pro parameters with defaults
    const proMinutesParam = template.parameters?.pro_minutes_limit;
    const proMinutes = proMinutesParam &&
      proMinutesParam.defaultValue &&
      "value" in proMinutesParam.defaultValue ?
      Number(proMinutesParam.defaultValue.value) :
      DEFAULT_PRO_MINUTES;

    const proProductIdParam = template.parameters?.pro_product_id;
    const proProductId = proProductIdParam &&
      proProductIdParam.defaultValue &&
      "value" in proProductIdParam.defaultValue ?
      String(proProductIdParam.defaultValue.value) :
      DEFAULT_PRO_PRODUCT_ID;

    const proPlanEnabledParam = template.parameters?.pro_plan_enabled;
    const proPlanEnabled = proPlanEnabledParam &&
      proPlanEnabledParam.defaultValue &&
      "value" in proPlanEnabledParam.defaultValue ?
      String(proPlanEnabledParam.defaultValue.value) === "true" :
      true;

    // Cache the values
    cachedPlanLimits = {
      freeTrialSeconds: freeTrialMinutes * 60,
      trialDurationMonths: trialDurationMonths,
      proSecondsLimit: proMinutes * 60,
      proProductId: proProductId,
      proPlanEnabled: proPlanEnabled,
    };
    cacheTimestamp = now;

    logger.info(
      `Remote Config loaded: ${freeTrialMinutes} min trial, ` +
      `${trialDurationMonths} months, Pro: ${proMinutes} min`
    );

    return cachedPlanLimits;
  } catch (error) {
    logger.warn(
      "Failed to fetch Remote Config, using defaults",
      error
    );

    // Fallback to defaults
    const fallbackLimits: PlanLimits = {
      freeTrialSeconds: DEFAULT_FREE_TRIAL_MINUTES * 60,
      trialDurationMonths: DEFAULT_TRIAL_DURATION_MONTHS,
      proSecondsLimit: DEFAULT_PRO_MINUTES * 60,
      proProductId: DEFAULT_PRO_PRODUCT_ID,
      proPlanEnabled: true,
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
  freeTrialSeconds: number;
  trialDurationMonths: number;
}> {
  const limits = await getPlanLimits();
  return {
    freeTrialSeconds: limits.freeTrialSeconds,
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
  proSecondsUsed: number; // Resets each billing period
}

/**
 * User document interface for Firestore users collection
 */
interface UserDocument {
  createdAt: admin.firestore.Timestamp;
  country?: string;
  plan: "free_trial" | "pro";
  // Trial fields
  freeTrialStart: admin.firestore.Timestamp;
  freeSecondsUsed: number; // Lifetime usage in seconds
  trialExpiryDate: admin.firestore.Timestamp; // 3 months from freeTrialStart
  // Pro subscription fields (Iteration 3)
  proSubscription?: ProSubscription;
  // Legacy field (for migration)
  freeMinutesRemaining?: number;
}

/**
 * Trial status result interface
 */
interface TrialStatusResult {
  isValid: boolean;
  status: "active" | "expired_time" | "expired_usage";
  freeSecondsUsed: number;
  freeSecondsRemaining: number;
  trialExpiryDate: admin.firestore.Timestamp;
  trialExpiryDateMs: number;
  warningLevel: "none" | "fifty_percent" |
  "eighty_percent" | "ninety_five_percent";
}

/**
 * Calculate trial status for a user
 * @param {UserDocument} user - The user document
 * @param {number} freeTrialSeconds - Trial limit in seconds from Remote Config
 * @return {TrialStatusResult} The trial status
 */
function checkTrialStatus(
  user: UserDocument,
  freeTrialSeconds: number
): TrialStatusResult {
  const now = admin.firestore.Timestamp.now();
  const freeSecondsUsed = user.freeSecondsUsed || 0;
  const freeSecondsRemaining = Math.max(
    0, freeTrialSeconds - freeSecondsUsed
  );
  const trialExpiryDate = user.trialExpiryDate;
  const trialExpiryDateMs = trialExpiryDate.toMillis();

  // Check if trial expired by time
  if (now.toMillis() > trialExpiryDateMs) {
    return {
      isValid: false,
      status: "expired_time",
      freeSecondsUsed,
      freeSecondsRemaining: 0,
      trialExpiryDate,
      trialExpiryDateMs,
      warningLevel: "none",
    };
  }

  // Check if trial expired by usage
  if (freeSecondsUsed >= freeTrialSeconds) {
    return {
      isValid: false,
      status: "expired_usage",
      freeSecondsUsed,
      freeSecondsRemaining: 0,
      trialExpiryDate,
      trialExpiryDateMs,
      warningLevel: "none",
    };
  }

  // Calculate warning level based on usage percentage
  const usagePercent = (freeSecondsUsed / freeTrialSeconds) * 100;
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
    freeSecondsUsed,
    freeSecondsRemaining,
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
  proSecondsUsed: number;
  proSecondsRemaining: number;
  proSecondsLimit: number;
  currentPeriodEndMs: number; // Reset date
  hasMinutesRemaining: boolean;
}

/**
 * Check Pro subscription status for a user
 * @param {UserDocument} user - The user document
 * @param {number} proSecondsLimit - Pro limit in seconds from Remote Config
 * @return {ProStatusResult} The Pro status
 */
function checkProStatus(
  user: UserDocument,
  proSecondsLimit: number
): ProStatusResult {
  const sub = user.proSubscription;

  // Not a Pro user or no active subscription
  if (!sub || sub.status !== "active") {
    return {
      isActive: false,
      proSecondsUsed: 0,
      proSecondsRemaining: 0,
      proSecondsLimit: proSecondsLimit,
      currentPeriodEndMs: 0,
      hasMinutesRemaining: false,
    };
  }

  const remaining = proSecondsLimit - sub.proSecondsUsed;
  return {
    isActive: true,
    proSecondsUsed: sub.proSecondsUsed,
    proSecondsRemaining: Math.max(0, remaining),
    proSecondsLimit: proSecondsLimit,
    currentPeriodEndMs: sub.currentPeriodEnd.toMillis(),
    hasMinutesRemaining: remaining > 0,
  };
}

/**
 * Usage log entry interface
 */
interface UsageLogEntry {
  secondsUsed: number;
  timestamp: admin.firestore.Timestamp;
  source: "free_trial" | "pro" | "overage" | "recharge";
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
 * Handles migration from legacy users (freeMinutesRemaining -> freeSecondsUsed)
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

    // Check if migration needed (legacy user without new trial fields)
    if (userData.freeSecondsUsed === undefined) {
      logger.info(`Migrating legacy user ${uid} to new trial system`);

      // Convert legacy freeMinutesRemaining to freeSecondsUsed
      // Old system: 60 minutes remaining = 0 used
      // New system: Track seconds USED, not remaining
      const legacyRemaining = userData.freeMinutesRemaining ?? 60;
      const legacyUsedMinutes = 60 - legacyRemaining;
      const freeSecondsUsed = Math.max(0, legacyUsedMinutes * 60);

      // Calculate trial expiry date from freeTrialStart
      const freeTrialStart = userData.freeTrialStart ||
        admin.firestore.Timestamp.now();
      const trialExpiryDate = calculateTrialExpiryDate(
        freeTrialStart,
        trialLimits.trialDurationMonths
      );

      // Update the user document with new fields
      const updatedUser: UserDocument = {
        ...userData,
        freeTrialStart,
        freeSecondsUsed,
        trialExpiryDate,
      };

      await userRef.update({
        freeSecondsUsed,
        trialExpiryDate,
        freeTrialStart,
      });

      logger.info(
        `Migrated user ${uid}: ${freeSecondsUsed}s used, ` +
        `expires ${trialExpiryDate.toDate().toISOString()}`
      );

      return updatedUser;
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
    plan: "free_trial",
    freeTrialStart: now,
    freeSecondsUsed: 0,
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
 * @param {number} secondsUsed - Seconds of audio transcribed
 * @param {string} source - Usage source (free_trial, pro, etc.)
 * @return {Promise<void>}
 */
async function logUsage(
  uid: string,
  secondsUsed: number,
  source: UsageLogEntry["source"] = "free_trial"
): Promise<void> {
  try {
    const entry: Omit<UsageLogEntry, "timestamp"> & {
      timestamp: admin.firestore.FieldValue;
    } = {
      secondsUsed: secondsUsed,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      source: source,
    };

    await db
      .collection("usage_logs")
      .doc(uid)
      .collection("entries")
      .add(entry);

    logger.info(`Logged ${secondsUsed} seconds usage for user ${uid}`);
  } catch (error) {
    // Don't fail the request if logging fails
    logger.error("Failed to log usage", error);
  }
}

/**
 * Deduct usage from user's trial quota
 * Rounds up audio duration to nearest minute before deducting
 * @param {string} uid - User ID
 * @param {number} audioDurationMs - Audio duration in milliseconds
 * @return {Promise<TrialStatusResult>} Updated trial status
 */
async function deductTrialUsage(
  uid: string,
  audioDurationMs: number
): Promise<TrialStatusResult> {
  const userRef = db.collection("users").doc(uid);

  // Bill exact seconds (rounded up from milliseconds)
  // e.g., 5248ms -> 6 seconds, 3264ms -> 4 seconds
  const secondsUsed = Math.ceil(audioDurationMs / 1000);

  // Use a transaction to safely increment usage
  const updatedUser = await db.runTransaction(async (transaction) => {
    const userDoc = await transaction.get(userRef);
    if (!userDoc.exists) {
      throw new Error("User document not found");
    }

    const userData = userDoc.data() as UserDocument;
    const newSecondsUsed = (userData.freeSecondsUsed || 0) + secondsUsed;

    transaction.update(userRef, {
      freeSecondsUsed: newSecondsUsed,
    });

    // Return updated user data
    return {
      ...userData,
      freeSecondsUsed: newSecondsUsed,
    };
  });

  // Log the usage entry for audit trail
  await logUsage(uid, secondsUsed);

  logger.info(
    `Deducted ${secondsUsed}s (${Math.ceil(secondsUsed / 60)} min) ` +
    `from user ${uid}, total: ${updatedUser.freeSecondsUsed}s`
  );

  // Fetch trial limits and check status
  const trialLimits = await getTrialLimits();
  return checkTrialStatus(updatedUser, trialLimits.freeTrialSeconds);
}

/**
 * Deduct usage from user's Pro subscription quota
 * @param {string} uid - User ID
 * @param {number} audioDurationMs - Audio duration in milliseconds
 * @return {Promise<ProStatusResult>} Updated Pro status
 */
async function deductProUsage(
  uid: string,
  audioDurationMs: number
): Promise<ProStatusResult> {
  const userRef = db.collection("users").doc(uid);
  const limits = await getPlanLimits();

  // Bill exact seconds (rounded up from milliseconds)
  const secondsUsed = Math.ceil(audioDurationMs / 1000);

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

    const newProSecondsUsed =
      userData.proSubscription.proSecondsUsed + secondsUsed;

    transaction.update(userRef, {
      "proSubscription.proSecondsUsed": newProSecondsUsed,
    });

    // Return updated user data
    return {
      ...userData,
      proSubscription: {
        ...userData.proSubscription,
        proSecondsUsed: newProSecondsUsed,
      },
    };
  });

  // Log the usage entry for audit trail (with Pro source)
  await logUsage(uid, secondsUsed, "pro");

  logger.info(
    `Pro: Deducted ${secondsUsed}s from user ${uid}, ` +
    `total: ${updatedUser.proSubscription?.proSecondsUsed}s`
  );

  return checkProStatus(updatedUser, limits.proSecondsLimit);
}

/**
 * Get total usage for the current billing period (anniversary-based)
 * Billing period resets on the same day of the month as the user's signup date
 * @param {string} uid - User ID
 * @return {Promise<number>} Total seconds used this billing period
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

    let totalSeconds = 0;
    snapshot.forEach((doc) => {
      const data = doc.data() as UsageLogEntry;
      totalSeconds += data.secondsUsed || 0;
    });

    return totalSeconds;
  } catch (error) {
    logger.error("Failed to get billing period usage", error);
    return 0;
  }
}

/**
 * Get total lifetime usage from usage_logs (all time)
 * Used to sync freeSecondsUsed when there's a mismatch
 * @param {string} uid - User ID
 * @return {Promise<number>} Total seconds used lifetime
 */
async function getTotalLifetimeUsage(uid: string): Promise<number> {
  try {
    const snapshot = await db
      .collection("usage_logs")
      .doc(uid)
      .collection("entries")
      .get();

    let totalSeconds = 0;
    snapshot.forEach((doc) => {
      const data = doc.data() as UsageLogEntry;
      totalSeconds += data.secondsUsed || 0;
    });

    return totalSeconds;
  } catch (error) {
    logger.error("Failed to get lifetime usage", error);
    return 0;
  }
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
  {secrets: ["OPENAI_API_KEY"]},
  async (request, response) => {
    const startTime = Date.now();
    let uid: string | null = null;

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

      uid = decodedToken.uid;
      logger.info(`Authenticated request from user: ${uid}`);

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
      const validFormats = ["wav", "m4a", "mp3", "webm", "mp4", "mpeg", "mpga"];
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

      // === ITERATION 3: Check validity with priority: Trial → Pro → Block ===
      const user = await getOrCreateUser(uid);
      const limits = await getPlanLimits();

      let canProceed = false;
      let usageSource: "free_trial" | "pro" = "free_trial";

      // Priority 1: Check free trial (if user is on trial plan)
      if (user.plan === "free_trial") {
        const trialStatus = checkTrialStatus(user, limits.freeTrialSeconds);
        if (trialStatus.isValid) {
          canProceed = true;
          usageSource = "free_trial";
          logger.info(
            `Trial valid for ${uid}: ` +
            `${trialStatus.freeSecondsRemaining}s remaining`
          );
        }
      }

      // Priority 2: Check Pro subscription (if trial exhausted or user is Pro)
      if (!canProceed && user.plan === "pro" && user.proSubscription) {
        const proStatus = checkProStatus(user, limits.proSecondsLimit);
        if (proStatus.isActive && proStatus.hasMinutesRemaining) {
          canProceed = true;
          usageSource = "pro";
          logger.info(
            `Pro valid for ${uid}: ${proStatus.proSecondsRemaining}s remaining`
          );
        }
      }

      // Priority 3: Block if no valid quota
      if (!canProceed) {
        const trialStatus = checkTrialStatus(user, limits.freeTrialSeconds);
        logger.warn(
          `No valid quota for user ${uid}: plan=${user.plan}`
        );
        await logTranscriptionRequest(uid, false, Date.now() - startTime);

        // Return appropriate error message
        const isProWithNoMinutes = user.plan === "pro" &&
          user.proSubscription?.status === "active";

        response.status(403).json({
          error: isProWithNoMinutes ? "PRO_LIMIT_REACHED" : "TRIAL_EXPIRED",
          message: isProWithNoMinutes ?
            "You have used all your Pro minutes for this month" :
            (trialStatus.status === "expired_time" ?
              "Your free trial period has ended" :
              "You have used all your free trial minutes"),
          plan: user.plan,
          trialStatus: user.plan === "free_trial" ? {
            status: trialStatus.status,
            freeSecondsUsed: trialStatus.freeSecondsUsed,
            freeSecondsRemaining: 0,
            trialExpiryDateMs: trialStatus.trialExpiryDateMs,
            warningLevel: "none",
          } : undefined,
          proStatus: user.plan === "pro" && user.proSubscription ? {
            proSecondsRemaining: 0,
            resetDateMs: user.proSubscription.currentPeriodEnd.toMillis(),
          } : undefined,
          // Include Pro plan info for upgrade flow
          proPlanEnabled: limits.proPlanEnabled,
        });
        return;
      }

      logger.info(`Proceeding with transcription using ${usageSource} quota`);


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
        `audio-${Date.now()}.${format}`
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

        // === ITERATION 3: Deduct usage from correct quota ===
        // (Whisper succeeded, so we charge the user from trial or Pro)
        let responseStatus: {
          plan: string;
          status: string;
          secondsRemaining: number;
          resetDateMs?: number;
          warningLevel: string;
        };

        if (uid) {
          if (usageSource === "pro") {
            // Deduct from Pro subscription
            const proStatus = await deductProUsage(uid, durationMs);
            responseStatus = {
              plan: "pro",
              status: proStatus.isActive ? "active" : "expired",
              secondsRemaining: proStatus.proSecondsRemaining,
              resetDateMs: proStatus.currentPeriodEndMs,
              warningLevel:
                proStatus.proSecondsRemaining < limits.proSecondsLimit * 0.1 ?
                  "ninety_percent" : "none",
            };
          } else {
            // Deduct from free trial
            const trialStatus = await deductTrialUsage(uid, durationMs);
            responseStatus = {
              plan: "free_trial",
              status: trialStatus.status,
              secondsRemaining: trialStatus.freeSecondsRemaining,
              warningLevel: trialStatus.warningLevel,
            };
          }
          await logTranscriptionRequest(uid, true, Date.now() - startTime);
        } else {
          // Fallback for no uid
          responseStatus = {
            plan: "free_trial",
            status: "active",
            secondsRemaining: 0,
            warningLevel: "none",
          };
        }

        // Get total usage this billing period for backward compatibility
        const totalSecondsThisMonth = uid ?
          await getTotalUsageThisPeriod(uid) :
          Math.ceil(durationMs / 1000);

        const secondsDeducted = Math.ceil(durationMs / 1000);
        logger.info(
          `Transcription success: deducted ${secondsDeducted}s ` +
          `from ${usageSource}, remaining: ${responseStatus.secondsRemaining}s`
        );

        // Return transcription with status (Iteration 3 response format)
        response.status(200).json({
          text: transcription.text,
          secondsUsed: secondsDeducted,
          totalSecondsThisMonth: totalSecondsThisMonth,
          // Iteration 3: Unified status format
          plan: responseStatus.plan,
          subscriptionStatus: {
            status: responseStatus.status,
            secondsRemaining: responseStatus.secondsRemaining,
            resetDateMs: responseStatus.resetDateMs,
            warningLevel: responseStatus.warningLevel,
          },
          // Backward compatibility for trial-only clients
          trialStatus: responseStatus.plan === "free_trial" ? {
            status: responseStatus.status,
            freeSecondsUsed:
              limits.freeTrialSeconds - responseStatus.secondsRemaining,
            freeSecondsRemaining: responseStatus.secondsRemaining,
            trialExpiryDateMs: user.trialExpiryDate.toMillis(),
            warningLevel: responseStatus.warningLevel,
          } : undefined,
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
 * Get trial status for the current user
 * GET or POST /getTrialStatus
 * Headers: Authorization: Bearer <firebase_id_token>
 * Response: {
 *   status: "active" | "expired_time" | "expired_usage",
 *   freeSecondsUsed: number,
 *   freeSecondsRemaining: number,
 *   trialExpiryDateMs: number,
 *   warningLevel: "none" | "fifty_percent" |
 *     "eighty_percent" | "ninety_five_percent"
 * }
 */
export const getTrialStatus = onRequest(async (request, response) => {
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

    // Get lifetime usage from logs to check for sync issues
    const lifetimeUsage = await getTotalLifetimeUsage(uid);

    // Sync freeSecondsUsed if there's a mismatch
    // (e.g., from legacy data or missed updates)
    if (lifetimeUsage > 0 && user.freeSecondsUsed !== lifetimeUsage) {
      logger.info(
        `Syncing freeSecondsUsed for ${uid}: ` +
        `stored=${user.freeSecondsUsed}, actual=${lifetimeUsage}`
      );
      // Update user document with correct lifetime usage
      await db.collection("users").doc(uid).update({
        freeSecondsUsed: lifetimeUsage,
      });
      // Update local user object for response
      user = {...user, freeSecondsUsed: lifetimeUsage};
    }

    // Fetch trial limits and check status
    const trialLimits = await getTrialLimits();
    const trialStatus = checkTrialStatus(user, trialLimits.freeTrialSeconds);

    // Get total usage this billing period for the "Usage This Month" display
    const totalSecondsThisMonth = await getTotalUsageThisPeriod(uid);

    response.status(200).json({
      status: trialStatus.status,
      freeSecondsUsed: trialStatus.freeSecondsUsed,
      freeSecondsRemaining: trialStatus.freeSecondsRemaining,
      trialExpiryDateMs: trialStatus.trialExpiryDateMs,
      warningLevel: trialStatus.warningLevel,
      totalSecondsThisMonth: totalSecondsThisMonth,
    });
  } catch (error) {
    logger.error("Error getting trial status", error);
    response.status(500).json({
      error: "Internal server error",
    });
  }
});

/**
 * Get subscription status for the current user (Iteration 3)
 * Returns unified status for both free trial and Pro users
 * GET or POST /getSubscriptionStatus
 * Headers: Authorization: Bearer <firebase_id_token>
 */
export const getSubscriptionStatus = onRequest(async (request, response) => {
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

    const user = await getOrCreateUser(uid);
    const limits = await getPlanLimits();

    // Build response based on user's current plan
    if (user.plan === "pro" && user.proSubscription) {
      // Pro user response
      const proStatus = checkProStatus(user, limits.proSecondsLimit);

      response.status(200).json({
        plan: "pro",
        isActive: proStatus.isActive,
        // Pro-specific fields
        proSecondsUsed: proStatus.proSecondsUsed,
        proSecondsRemaining: proStatus.proSecondsRemaining,
        proSecondsLimit: proStatus.proSecondsLimit,
        resetDateMs: proStatus.currentPeriodEndMs,
        subscriptionStatus: user.proSubscription.status,
        // For backward compatibility
        status: proStatus.isActive ? "active" : "expired",
        warningLevel:
          proStatus.proSecondsRemaining < limits.proSecondsLimit * 0.1 ?
            "ninety_percent" : "none",
      });
    } else {
      // Free trial user response
      const trialStatus = checkTrialStatus(user, limits.freeTrialSeconds);
      const totalSecondsThisMonth = await getTotalUsageThisPeriod(uid);

      response.status(200).json({
        plan: "free_trial",
        isActive: trialStatus.isValid,
        // Trial-specific fields
        freeSecondsUsed: trialStatus.freeSecondsUsed,
        freeSecondsRemaining: trialStatus.freeSecondsRemaining,
        trialExpiryDateMs: trialStatus.trialExpiryDateMs,
        totalSecondsThisMonth: totalSecondsThisMonth,
        // Common fields
        status: trialStatus.status,
        warningLevel: trialStatus.warningLevel,
        // Pro plan info for upgrade UI
        proPlanEnabled: limits.proPlanEnabled,
        proSecondsLimit: limits.proSecondsLimit,
      });
    }
  } catch (error) {
    logger.error("Error getting subscription status", error);
    response.status(500).json({
      error: "Internal server error",
    });
  }
});
