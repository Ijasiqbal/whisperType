# Vozcribe Android — Release Testing Checklist

## 1. Permissions & Setup

- [ ] Fresh install — all permission prompts appear (Accessibility, Overlay, Microphone)
- [ ] Accessibility service enables and survives app restart
- [ ] Overlay permission grants floating mic button
- [ ] Notification permission prompt appears (API 33+)

## 2. Core Recording & Transcription

- [ ] Volume shortcut triggers recording from any text field
- [ ] Floating mic button triggers recording
- [ ] Recording pulse animation displays correctly
- [ ] Short recording (~2s) transcribes successfully
- [ ] Long recording (~30s+) transcribes successfully
- [ ] Cancel recording mid-way works cleanly
- [ ] Transcribed text inserts into focused text field correctly

## 3. Text Insertion Across Apps

- [ ] WhatsApp message field
- [ ] Google Search bar
- [ ] Notes app
- [ ] SMS/Messages app
- [ ] Chrome address/search bar

## 4. Billing & Subscription

- [ ] Guest login is disabled in release build
- [ ] Trial status displays correctly for new users
- [ ] Subscription purchase flow completes via Play Store
- [ ] Unlimited plan shows correct UI with dedicated card
- [ ] Credit usage tracking works
- [ ] Expired subscription handled gracefully

## 5. Model & Retry

- [ ] Default model transcribes correctly
- [ ] Retry model selection popup appears on failure
- [ ] Switching models and retrying works

## 6. Edge Cases

- [ ] No internet — shows appropriate error
- [ ] Microphone in use by another app — handles gracefully
- [ ] Screen off during recording — completes correctly
- [ ] Rapid start/stop recording — no crash
- [ ] Very quiet audio — returns reasonable result or error
- [ ] MIUI/Xiaomi device — AutoStart prompt appears if applicable

## 7. Device Compatibility

- [ ] API 29 device (minimum Opus support)
- [ ] API 34+ device (latest Android)
- [ ] Different screen sizes (phone + tablet if supported)

## 8. Release Build Specifics

- [ ] ProGuard/R8 minification — no crashes from stripped classes
- [ ] No verbose logs in logcat
- [ ] Crashlytics reports arrive in Firebase console
- [ ] App version and versionCode are correct in Settings/About

## 9. Stability

- [ ] Use app for 5+ min continuously — no memory leaks or ANRs
- [ ] Kill app from recents, reopen — state recovers
- [ ] Force stop, reopen — accessibility service re-enables

---

## Automated Testing Options

### Maestro (UI Automation)

YAML-based UI testing framework. No code needed.

**Setup:**
```bash
brew install maestro
```

**Run tests:**
```bash
maestro test .maestro/flows/
```

Useful for automating sections 1-5 above. Cannot test hardware volume buttons or real microphone input directly.

### Firebase Test Lab (Crash Detection)

Robo test crawls the app automatically on real cloud devices.

**Run via CLI:**
```bash
gcloud firebase test android run \
  --type robo \
  --app app/build/outputs/apk/release/app-release.apk \
  --device model=Pixel6,version=33 \
  --device model=Pixel4,version=30 \
  --timeout 300s
```

**Run via Console:**
Firebase Console > Test Lab > Run a test > Robo > Upload APK > Pick devices > Start

Free tier: 15 virtual + 5 physical device tests/day.

### Play Store Pre-launch Report

Runs automatically on upload. Covers basic crash detection on ~5 devices but won't test authenticated flows, permissions, or microphone usage.

---

## Pre-Release Checklist (Quick)

1. [ ] Build release APK/AAB
2. [ ] Run Firebase Test Lab Robo test on 3-4 devices
3. [ ] Manually test core flow: open app > login > grant permissions > record > verify transcription
4. [ ] Test text insertion in at least 2 third-party apps
5. [ ] Verify billing/subscription screens
6. [ ] Upload to Play Store internal track first
7. [ ] Check Pre-launch Report for issues
8. [ ] Promote to production
