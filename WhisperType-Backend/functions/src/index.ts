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
import OpenAI from "openai";
import * as fs from "fs";
import * as os from "os";
import * as path from "path";

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
 * Body: { audioBase64: string, audioFormat?: string }
 * audioFormat: "wav" | "m4a" | "mp3" | "webm" | "mp4" |
 *             "mpeg" | "mpga" (default: "m4a")
 * Response: { text: string }
 */
export const transcribeAudio = onRequest(
  {secrets: ["OPENAI_API_KEY"]},
  async (request, response) => {
    try {
      // Only allow POST requests
      if (request.method !== "POST") {
        response.status(405).send("Method Not Allowed");
        return;
      }

      // Validate request body
      const {audioBase64, audioFormat = "m4a"} = request.body;
      if (!audioBase64 || typeof audioBase64 !== "string") {
        response.status(400).json({
          error: "Missing or invalid audioBase64 field in request body",
        });
        return;
      }

      // Validate audio format - use validated format or default to m4a
      const validFormats = ["wav", "m4a", "mp3", "webm", "mp4", "mpeg", "mpga"];
      const format = validFormats.includes(audioFormat) ? audioFormat : "m4a";

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
          `Calling Whisper API for transcription (format: ${format})`
        );
        const transcription = await openai.audio.transcriptions.create({
          file: fs.createReadStream(tempFilePath),
          model: "gpt-4o-transcribe",
        });

        // Clean up temporary file
        fs.unlinkSync(tempFilePath);

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
        response.status(500).json({
          error: "Failed to transcribe audio",
        });
      }
    } catch (error) {
      logger.error("Unexpected error in transcribeAudio", error);
      response.status(500).json({
        error: "Internal server error",
      });
    }
  });
