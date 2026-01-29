# Changelog

All notable changes to the WhisperType Backend are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.0] - 2024-XX-XX

### Added
- **Groq Transcription Endpoint** (`/transcribeAudioGroq`)
  - Support for `whisper-large-v3-turbo` (free tier, AUTO)
  - Support for `whisper-large-v3` (standard tier, STANDARD)
  - OpenAI SDK compatibility for seamless integration

- **Credit-Based Billing System**
  - Three-tier model pricing: AUTO (0x), STANDARD (1x), PREMIUM (2x)
  - Credit calculation: `(audio_seconds / 6) × tier_multiplier`
  - Atomic credit deduction using Firestore transactions
  - Usage logging for audit trail

- **Pro Subscription Management**
  - Google Play subscription verification (`/verifySubscription`)
  - Anniversary-based billing cycles
  - Automatic subscription renewal detection
  - Monthly credit reset on billing anniversary

- **Enhanced Status Endpoints**
  - `/getTrialStatus` - Free trial status with warning levels
  - `/getSubscriptionStatus` - Comprehensive subscription info
  - Warning levels: 50%, 80%, 95% usage thresholds

- **Remote Config Integration**
  - Dynamic configuration for credit limits
  - Configurable trial duration
  - Enable/disable Pro plan remotely
  - 5-minute cache for performance

- **Multi-Region Deployment**
  - us-central1 (Americas)
  - asia-south1 (South Asia)
  - europe-west1 (Europe)

### Changed
- Migrated from minutes-based to credits-based billing
- OpenAI endpoint now uses PREMIUM tier (2x credits)
- Improved error responses with detailed status objects
- Enhanced warmup request handling

### Fixed
- Race conditions in credit deduction (now using transactions)
- Subscription expiry false positives (added re-verification)

## [2.0.0] - 2024-XX-XX

### Added
- **Firebase Authentication**
  - All endpoints now require `Authorization: Bearer <token>` header
  - Support for Anonymous, Google, and Email/Password auth
  - Automatic user document creation on first request

- **User Management**
  - User documents in Firestore `users` collection
  - Free trial tracking with expiry dates
  - Usage logging to `transcriptions` collection

- **Account Deletion** (`/deleteAccount`)
  - Complete data removal (user doc, usage logs, auth account)
  - CORS support for web-based deletion page

### Changed
- All transcription endpoints now require authentication
- Added `uid` field to all request logs

### Security
- Firebase ID token verification on all protected endpoints
- Secrets management via Firebase Functions secrets

## [1.0.0] - 2024-XX-XX

### Added
- **Initial Release**
- **Transcription Endpoint** (`/transcribeAudio`)
  - OpenAI Whisper API integration
  - Base64 audio input
  - Support for mp3, mp4, m4a, wav, webm, mpeg, mpga formats

- **Health Check** (`/health`)
  - Simple availability check
  - Returns "OK" on success

- **Basic Error Handling**
  - 400 for invalid requests
  - 500 for server errors
  - Detailed error messages

### Infrastructure
- Firebase Cloud Functions v2
- Node.js runtime
- TypeScript for type safety
- ESLint for code quality

---

## Version History Summary

| Version | Release | Highlights |
|---------|---------|------------|
| 3.0.0 | Current | Groq integration, credit billing, Pro subscriptions |
| 2.0.0 | - | Firebase Auth, user management, account deletion |
| 1.0.0 | - | Initial release, OpenAI transcription |

## Upgrade Notes

### Upgrading from 2.x to 3.x

1. **Set new secrets**:
   ```bash
   firebase functions:secrets:set GROQ_API_KEY
   firebase functions:secrets:set GOOGLE_PLAY_KEY
   ```

2. **Update Remote Config** with new parameters:
   - `free_tier_credits`
   - `pro_tier_credits`
   - `seconds_per_credit`
   - `pro_product_id`
   - `pro_plan_enabled`

3. **Client updates required**:
   - Handle new response format with `creditsUsed`, `subscriptionStatus`
   - Use new `/transcribeAudioGroq` endpoint for free tier
   - Implement subscription status checking

### Upgrading from 1.x to 2.x

1. **Authentication required**:
   - All clients must implement Firebase Auth
   - Add `Authorization: Bearer <token>` header to all requests

2. **Handle 401 responses**:
   - Implement token refresh logic
   - Redirect to login on auth failures

## API Compatibility

| Endpoint | v1.0 | v2.0 | v3.0 |
|----------|------|------|------|
| `/health` | GET | GET | GET |
| `/transcribeAudio` | POST (no auth) | POST (auth) | POST (auth, credits) |
| `/transcribeAudioGroq` | - | - | POST (auth, credits) |
| `/getTrialStatus` | - | - | GET/POST |
| `/getSubscriptionStatus` | - | - | GET/POST |
| `/verifySubscription` | - | - | POST |
| `/deleteAccount` | - | POST | POST |
