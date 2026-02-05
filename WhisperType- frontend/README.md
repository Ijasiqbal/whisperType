# VoxType Android App

Voice-to-text input for any Android text field using volume button shortcuts.

## Overview

VoxType (WhisperType) is an Android accessibility service that enables voice input in any app. Users activate recording via volume button shortcuts, speak, and the transcribed text is automatically inserted into the focused text field.

```
┌─────────────────────────────────────────────────────────────────┐
│                        User Workflow                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   1. Focus any text field (WhatsApp, Notes, Browser, etc.)      │
│                          ↓                                       │
│   2. Trigger shortcut (double-press volume up/down)             │
│                          ↓                                       │
│   3. Floating mic button appears → tap to record                │
│                          ↓                                       │
│   4. Speak → Audio sent to cloud (Groq/OpenAI)                  │
│                          ↓                                       │
│   5. Transcribed text inserted into text field                  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## Features

- **Universal Text Input**: Works in any app with text fields
- **Volume Button Shortcuts**: Double-press volume up, down, or both buttons
- **Multiple AI Backends**: Groq Whisper (fast) and OpenAI GPT-4o-mini (accurate)
- **Silence Trimming**: Real-time RMS analysis removes silence for faster processing
- **Opus Compression**: Android 10+ uses Opus encoding for 90% smaller uploads
- **Credit System**: Free tier + Pro subscription via Google Play Billing
- **Battery Optimized**: Idle service with minimal background impact

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose + Material 3 |
| Architecture | MVVM with ViewModels |
| DI | Hilt |
| Networking | OkHttp 4.12 |
| Auth | Firebase Authentication (Google Sign-In) |
| Config | Firebase Remote Config |
| Billing | Google Play Billing 7.1.1 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 35 |

## Project Structure

```
app/src/main/java/com/whispertype/app/
├── MainActivity.kt              # Entry point, permission setup
├── Constants.kt                 # App-wide constants
├── ShortcutPreferences.kt       # User shortcut preferences
├── WhisperTypeApplication.kt    # Application class
│
├── ui/                          # Compose UI
│   ├── LoginScreen.kt           # Authentication screen
│   ├── PlanScreen.kt            # Subscription plans
│   ├── ProfileScreen.kt         # User profile & settings
│   ├── components/              # Reusable UI components
│   │   ├── AccessibilityDisclosureDialog.kt
│   │   ├── ForceUpdateDialog.kt
│   │   └── SkeletonLoader.kt
│   ├── viewmodel/               # ViewModels
│   │   ├── MainViewModel.kt
│   │   ├── LoginViewModel.kt
│   │   └── PlanViewModel.kt
│   └── theme/                   # Compose theming
│
├── service/                     # Android Services
│   ├── WhisperTypeAccessibilityService.kt  # Core service
│   └── OverlayService.kt        # Floating mic button
│
├── speech/                      # Transcription
│   ├── TranscriptionFlow.kt     # Flow definitions
│   └── SpeechRecognitionHelper.kt
│
├── audio/                       # Audio recording
│   ├── AudioRecorder.kt         # Raw recording
│   ├── AudioProcessor.kt        # Encoding (WAV, AAC, Opus)
│   ├── ParallelOpusRecorder.kt  # Opus with parallel RMS
│   └── RealtimeRmsRecorder.kt   # Silence detection
│
├── api/                         # Backend communication
│   └── WhisperApiClient.kt      # HTTP client
│
├── repository/                  # Data layer
│   ├── AuthRepository.kt        # Auth interface
│   ├── UserRepository.kt        # User data
│   └── BillingRepository.kt     # Subscription
│
├── billing/                     # In-app purchases
│   ├── BillingManager.kt        # Google Play Billing
│   └── MockBillingManager.kt    # Debug mock
│
├── config/                      # Remote configuration
│   └── RemoteConfigManager.kt   # Firebase Remote Config
│
└── util/                        # Utilities
    ├── ForceUpdateChecker.kt    # Version checking
    └── MiuiHelper.kt            # MIUI-specific fixes
```

## Permissions

The app requires three permissions to function:

| Permission | Purpose |
|------------|---------|
| **Accessibility Service** | Detect volume shortcuts, insert text into apps |
| **Overlay (Draw Over Apps)** | Display floating mic button |
| **Microphone** | Record voice for transcription |

## Quick Start

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 35

### Setup

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle
4. Create `google-services.json` from Firebase Console and place in `app/`
5. Build and run

### Debug vs Release

| Feature | Debug | Release |
|---------|-------|---------|
| Guest Login | Enabled | Disabled |
| Minification | Off | On (R8) |
| Billing | MockBillingManager | Real BillingManager |

## Transcription Flows

The app supports multiple transcription pipelines selectable per-request:

| Flow | Backend | Features | Credit Cost |
|------|---------|----------|-------------|
| **FLOW_3** (Auto) | Groq Turbo | Fastest, basic | 0x (Free) |
| **GROQ_WHISPER** | Groq Large v3 | Fast, accurate | 1x |
| **PARALLEL_OPUS** | OpenAI mini | Opus compression, silence trim | 2x |

Default: `ARAMUS_OPENAI` (Parallel RMS + GPT-4o-mini)

## Architecture

See [ARCHITECTURE.md](./ARCHITECTURE.md) for detailed system design.

## Related Documentation

- [ARCHITECTURE.md](./ARCHITECTURE.md) - System architecture and data flow
- [BATTERY_OPTIMIZATION_SUMMARY.md](./BATTERY_OPTIMIZATION_SUMMARY.md) - Battery improvements
- [CODE_REVIEW_SUMMARY.md](./CODE_REVIEW_SUMMARY.md) - Code quality analysis
- [FORCE_UPDATE_SETUP.md](./FORCE_UPDATE_SETUP.md) - Force update mechanism

## Backend

The app communicates with Firebase Cloud Functions. See [WhisperType-Backend](../WhisperType-Backend/README.md) for API documentation.

## Version

Current: **1.0.18** (Version Code: 18)
