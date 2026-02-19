# CLAUDE.md

Project context for Claude Code sessions.

## Project Overview

Wozcribe (WhisperType) is a voice-to-text Android app with a Firebase backend. Users trigger recording via volume button shortcuts, speak, and transcribed text is inserted into any focused text field. The app uses batch processing for audio transcription—the entire recording is sent to the AI model at once, giving it full context of the complete audio rather than just 4–5 second chunks as in live/streaming transcription, which significantly improves accuracy.

## Repository Structure

```
whisperType/
├── WhisperType- frontend/     # Android app (Kotlin, Jetpack Compose)
├── WhisperType-Backend/       # Firebase Cloud Functions (TypeScript)
├── VoxType-Mac/               # macOS app (Swift, SwiftUI)
└── whispertype-admin/         # Admin dashboard (Next.js, React)
```

## Frontend (Android)

**Location**: `WhisperType- frontend/`

### Build Commands
```bash
cd "WhisperType- frontend"
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK
./gradlew bundleRelease          # Release AAB for Play Store
./gradlew lint                   # Run lint
```

### Key Paths
- Source: `app/src/main/java/com/whispertype/app/`
- Resources: `app/src/main/res/`
- Build config: `app/build.gradle.kts`
- Manifest: `app/src/main/AndroidManifest.xml`

### Architecture
- **MVVM** with Jetpack ViewModels
- **Jetpack Compose** for UI
- **Hilt** for dependency injection
- **Coroutines** for async operations

### Core Components
| Component | Path | Purpose |
|-----------|------|---------|
| Accessibility Service | `service/WhisperTypeAccessibilityService.kt` | Volume shortcut detection, text insertion |
| Overlay Service | `service/OverlayService.kt` | Floating mic button |
| Audio Recording | `audio/ParallelOpusRecorder.kt` | Opus encoding with RMS analysis |
| API Client | `api/WhisperApiClient.kt` | Backend communication |
| Transcription Flows | `speech/TranscriptionFlow.kt` | Pipeline selection |

### Constants
All magic numbers are in `Constants.kt`. Use these instead of hardcoding values.

## Backend (Firebase)

**Location**: `WhisperType-Backend/`

### Build Commands
```bash
cd WhisperType-Backend/functions
npm install
npm run build                    # Compile TypeScript
npm run serve                    # Local emulator
firebase deploy --only functions # Deploy to production
```

### Key Paths
- Source: `functions/src/index.ts` (monolithic)
- Config: `firebase.json`

### Cloud Functions
| Function | Endpoint | Purpose |
|----------|----------|---------|
| `transcribeAudio` | POST | OpenAI transcription |
| `transcribeAudioGroq` | POST | Groq transcription |
| `getTrialStatus` | GET | Check user credits |
| `verifySubscription` | POST | Validate Play Store purchase |

### Firestore Collections
- `users/{uid}` - User profile, subscription status
- `usage_logs/{uid}/entries` - Credit usage tracking

## macOS App

**Location**: `VoxType-Mac/`

- Swift + SwiftUI
- Xcode project: `VoxType-Mac/VoxType.xcodeproj`
- Build via Xcode (open `.xcodeproj`)

## Admin Dashboard

**Location**: `whispertype-admin/`

### Build Commands
```bash
cd whispertype-admin
npm install
npm run dev                      # Local dev server
npm run build                    # Production build
```

- Next.js 16 + React 19 + Tailwind CSS 4
- Firebase integration for user/analytics management
- Radix UI + shadcn components

## Common Patterns

### Adding a new transcription flow
1. Add enum value in `TranscriptionFlow.kt`
2. Handle in `SpeechRecognitionHelper.kt`
3. Map from `ModelTier` if needed

### Adding a new API endpoint
1. Add function in `WhisperType-Backend/functions/src/index.ts`
2. Add client method in `WhisperApiClient.kt`
3. Deploy backend, then update app

### UI Changes
- Screens are in `ui/` folder as Composables
- Theme colors in `ui/theme/Color.kt`
- Use Material 3 components

## Debug vs Release

| Feature | Debug | Release |
|---------|-------|---------|
| Guest Login | Enabled | Disabled |
| Billing | MockBillingManager | Real BillingManager |
| Minification | Off | On |
| Logs | Verbose | Minimal |

## Testing

### Manual Testing Checklist
1. Grant all permissions (Accessibility, Overlay, Microphone)
2. Test volume shortcut in a text field
3. Verify transcription completes
4. Check text insertion works

### Backend Testing
```bash
cd WhisperType-Backend/functions
npm test
```

## Important Notes

- **OkHttp is singleton** - don't create new instances
- **Accessibility events are not logged** - privacy requirement
- **MIUI devices need AutoStart** - see `MiuiHelper.kt`
- **Opus requires API 29+** - fallback to AAC on older devices
- **Firebase BOM 32.7.0** - newer versions require Kotlin 2.0+

## Version Info

- Android: `versionCode 18`, `versionName 1.0.18`
- Min SDK: 24 (Android 7.0)
- Target SDK: 35
- Kotlin: 1.9.x
- Compose BOM: 2023.10.01
