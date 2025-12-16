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
 *   model?: string
 * }
 * audioFormat: "wav" | "m4a" | "mp3" | "webm" | "mp4" |
 *             "mpeg" | "mpga" (default: "m4a")
 * model: "gpt-4o-transcribe" | "gpt-4o-mini-transcribe"
 *        (default: "gpt-4o-transcribe")
 * Response: { text: string }
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
      const {audioBase64, audioFormat = "m4a", model} = request.body;
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

      // Validate and set model - default to gpt-4o-transcribe
      const validModels = [
        "gpt-4o-transcribe",
        "gpt-4o-mini-transcribe",
      ];
      const selectedModel = model && validModels.includes(model) ?
        model : "gpt-4o-transcribe";

      logger.info(
        `Processing request - format: ${format}, model: ${selectedModel}`
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

        // Log successful request
        if (uid) {
          await logTranscriptionRequest(uid, true, Date.now() - startTime);
        }

        // Return transcription
        response.status(200).json({
          text: transcription.text,
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
