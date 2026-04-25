#!/usr/bin/env node
/**
 * Updates config/<platform>App.latestVersion in Firestore.
 * Usage: node update-firestore-version.js <mac|windows> <version>
 * Requires: gcloud auth application-default login (run once)
 */

const admin = require("firebase-admin");

const [platform, version] = process.argv.slice(2);

if (!platform || !version || !["mac", "windows"].includes(platform)) {
  console.error("Usage: node update-firestore-version.js <mac|windows> <version>");
  process.exit(1);
}

if (!/^\d+(\.\d+)*$/.test(version)) {
  console.error("Invalid version format. Use e.g. 1.0.7");
  process.exit(1);
}

admin.initializeApp({ projectId: "whispertype-1de9f" });

const db = admin.firestore();
const docId = platform === "mac" ? "macApp" : "windowsApp";

db.doc(`config/${docId}`)
  .set({ latestVersion: version }, { merge: true })
  .then(() => {
    console.log(`✓ config/${docId}.latestVersion = ${version}`);
    process.exit(0);
  })
  .catch((err) => {
    console.error("Failed to update Firestore:", err.message);
    process.exit(1);
  });
