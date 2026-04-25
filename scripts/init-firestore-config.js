#!/usr/bin/env node
/**
 * One-time setup: creates config/windowsApp and config/macApp in Firestore.
 * Safe to re-run — uses merge so it won't overwrite fields you've changed.
 * Usage: node init-firestore-config.js
 * Requires: gcloud auth application-default login (run once first)
 */

const admin = require("firebase-admin");

admin.initializeApp({ projectId: "whispertype-1de9f" });
const db = admin.firestore();

const configs = {
  windowsApp: {
    latestVersion: "1.0.0",
    minVersion: "1.0.0",
    blockedVersions: [],
    downloadUrl: "https://vozcribe.com/windows",
    blockedMessage:
      "This version of Vozcribe is no longer supported. " +
      "Please download the latest version to continue.",
  },
  macApp: {
    latestVersion: "1.15",   // next release will be 1.15.1 (three-part going forward)
    minVersion: "1.0",
    blockedVersions: [],
    downloadUrl: "https://vozcribe.com/mac",
    blockedMessage:
      "This version of Vozcribe is no longer supported. " +
      "Please download the latest version to continue.",
  },
};

async function run() {
  for (const [docId, data] of Object.entries(configs)) {
    await db.doc(`config/${docId}`).set(data, { merge: true });
    console.log(`✓ config/${docId} written`);
  }
  process.exit(0);
}

run().catch((err) => {
  console.error("Failed:", err.message);
  process.exit(1);
});
