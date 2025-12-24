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
// USAGE TRACKING FUNCTIONS
// ============================================================================

// Trial limit constants
const FREE_TRIAL_SECONDS = 20 * 60; // 20 minutes in seconds
const FREE_TRIAL_MONTHS = 3; // 3 months trial period

/**
 * User document interface for Firestore users collection
 */
interface UserDocument {
  createdAt: admin.firestore.Timestamp;
  country?: string;
  plan: "free" | "pro";
  // Trial fields
  freeTrialStart: admin.firestore.Timestamp;
  freeSecondsUsed: number; // Lifetime usage in seconds
  trialExpiryDate: admin.firestore.Timestamp; // 3 months from freeTrialStart
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
 * @return {TrialStatusResult} The trial status
 */
function checkTrialStatus(user: UserDocument): TrialStatusResult {
  const now = admin.firestore.Timestamp.now();
  const freeSecondsUsed = user.freeSecondsUsed || 0;
  const freeSecondsRemaining = Math.max(
    0, FREE_TRIAL_SECONDS - freeSecondsUsed
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
  if (freeSecondsUsed >= FREE_TRIAL_SECONDS) {
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
  const usagePercent = (freeSecondsUsed / FREE_TRIAL_SECONDS) * 100;
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
 * Usage log entry interface
 */
interface UsageLogEntry {
  secondsUsed: number;
  timestamp: admin.firestore.Timestamp;
  source: "free" | "pro" | "overage" | "recharge";
}

/**
 * Calculate trial expiry date (3 months from start)
 * @param {admin.firestore.Timestamp} startDate - The trial start date
 * @return {admin.firestore.Timestamp} The expiry date
 */
function calculateTrialExpiryDate(
  startDate: admin.firestore.Timestamp
): admin.firestore.Timestamp {
  const expiryDate = new Date(startDate.toMillis());
  expiryDate.setMonth(expiryDate.getMonth() + FREE_TRIAL_MONTHS);
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
      const trialExpiryDate = calculateTrialExpiryDate(freeTrialStart);

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
  const trialExpiryDate = calculateTrialExpiryDate(now);

  const newUser: UserDocument = {
    createdAt: now,
    plan: "free",
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
 * @return {Promise<void>}
 */
async function logUsage(uid: string, secondsUsed: number): Promise<void> {
  try {
    const entry: Omit<UsageLogEntry, "timestamp"> & {
      timestamp: admin.firestore.FieldValue;
    } = {
      secondsUsed: secondsUsed,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      source: "free", // For now, all usage is from free tier
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

  // Round up to nearest minute in seconds (e.g., 5 seconds -> 60 seconds)
  const secondsUsed = Math.ceil(audioDurationMs / 1000 / 60) * 60;

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

  return checkTrialStatus(updatedUser);
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

      // === ITERATION 2: Check trial validity BEFORE transcription ===
      const user = await getOrCreateUser(uid);
      const trialStatus = checkTrialStatus(user);

      if (!trialStatus.isValid) {
        logger.warn(
          `Trial expired for user ${uid}: ${trialStatus.status}`
        );
        await logTranscriptionRequest(uid, false, Date.now() - startTime);
        response.status(403).json({
          error: "TRIAL_EXPIRED",
          message: trialStatus.status === "expired_time" ?
            "Your free trial period has ended" :
            "You have used all your free trial minutes",
          trialStatus: {
            status: trialStatus.status,
            freeSecondsUsed: trialStatus.freeSecondsUsed,
            freeSecondsRemaining: 0,
            trialExpiryDateMs: trialStatus.trialExpiryDateMs,
            warningLevel: "none",
          },
        });
        return;
      }

      logger.info(
        `Trial valid for ${uid}: ${trialStatus.freeSecondsRemaining}s remaining`
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
        // If audioDurationMs not provided, default to 60 seconds
        const isValidDuration =
          typeof audioDurationMs === "number" && audioDurationMs > 0;
        const durationMs = isValidDuration ? audioDurationMs : 60000;

        // === ITERATION 2: Deduct usage AFTER successful transcription ===
        // (Whisper succeeded, so we charge the user)
        let updatedTrialStatus = trialStatus;
        if (uid) {
          updatedTrialStatus = await deductTrialUsage(uid, durationMs);
          // Log successful request (existing logging)
          await logTranscriptionRequest(uid, true, Date.now() - startTime);
        }

        // Get total usage this billing period for backward compatibility
        const totalSecondsThisMonth = uid ?
          await getTotalUsageThisPeriod(uid) :
          Math.ceil(durationMs / 1000 / 60) * 60;

        const secondsDeducted = Math.ceil(durationMs / 1000 / 60) * 60;
        logger.info(
          `Transcription success: deducted ${secondsDeducted}s, ` +
          `remaining: ${updatedTrialStatus.freeSecondsRemaining}s`
        );

        // Return transcription with trial status (Iteration 2 response format)
        response.status(200).json({
          text: transcription.text,
          secondsUsed: secondsDeducted,
          totalSecondsThisMonth: totalSecondsThisMonth,
          // New Iteration 2 trial status fields
          trialStatus: {
            status: updatedTrialStatus.status,
            freeSecondsUsed: updatedTrialStatus.freeSecondsUsed,
            freeSecondsRemaining: updatedTrialStatus.freeSecondsRemaining,
            trialExpiryDateMs: updatedTrialStatus.trialExpiryDateMs,
            warningLevel: updatedTrialStatus.warningLevel,
          },
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

    const trialStatus = checkTrialStatus(user);

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
