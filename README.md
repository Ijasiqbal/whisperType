# VoxType (WhisperType)

Voice-to-text input for any Android text field. Speak anywhere, type nowhere.

## Overview

VoxType is a complete voice input solution consisting of:

1. **Android App** - Accessibility service that detects volume shortcuts and inserts transcribed text
2. **Firebase Backend** - Cloud Functions for audio transcription via Groq and OpenAI APIs

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           VoxType System Architecture                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌───────────────────────────────────────────────────────────────────────┐ │
│   │                         Android App (Frontend)                         │ │
│   │                                                                        │ │
│   │  User triggers shortcut → Record audio → Upload → Insert transcription │ │
│   │                                                                        │ │
│   │  Tech: Kotlin, Jetpack Compose, Firebase Auth, Play Billing           │ │
│   └───────────────────────────────────────────────────────────────────────┘ │
│                                      │                                       │
│                                      │ HTTPS (Firebase Auth Token)          │
│                                      ▼                                       │
│   ┌───────────────────────────────────────────────────────────────────────┐ │
│   │                    Firebase Cloud Functions (Backend)                  │ │
│   │                                                                        │ │
│   │  Receive audio → Call Groq/OpenAI API → Return transcript             │ │
│   │  Manage credits → Verify subscriptions → Track usage                  │ │
│   │                                                                        │ │
│   │  Tech: Node.js, TypeScript, Cloud Functions v2, Firestore             │ │
│   └───────────────────────────────────────────────────────────────────────┘ │
│                                      │                                       │
│                                      │ API calls                            │
│                                      ▼                                       │
│   ┌───────────────────────────────────────────────────────────────────────┐ │
│   │                         External AI Services                           │ │
│   │                                                                        │ │
│   │  ┌─────────────────────┐        ┌─────────────────────────────────┐   │ │
│   │  │   Groq Whisper      │        │   OpenAI GPT-4o-mini-transcribe │   │ │
│   │  │   (Fast, Free tier) │        │   (Accurate, Premium tier)      │   │ │
│   │  └─────────────────────┘        └─────────────────────────────────┘   │ │
│   └───────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Repository Structure

```
whisperType/
├── README.md                    # This file
├── PRIVACY_POLICY.md            # Privacy policy for app stores
│
├── WhisperType- frontend/       # Android App
│   ├── README.md                # Frontend overview
│   ├── ARCHITECTURE.md          # Detailed architecture
│   ├── app/
│   │   ├── src/main/java/       # Kotlin source code
│   │   ├── src/main/res/        # Resources
│   │   └── build.gradle.kts     # Build configuration
│   └── [other docs]
│
└── WhisperType-Backend/         # Firebase Backend
    ├── README.md                # Backend overview
    ├── API_DOCUMENTATION.md     # API endpoints
    ├── ARCHITECTURE.md          # System design
    ├── DEPLOYMENT.md            # Deployment guide
    ├── TESTING.md               # Testing guide
    ├── CHANGELOG.md             # Version history
    └── functions/
        └── src/index.ts         # Cloud Functions
```

## Quick Links

### Frontend (Android App)
- [Frontend README](./WhisperType-%20frontend/README.md) - Setup and overview
- [Frontend Architecture](./WhisperType-%20frontend/ARCHITECTURE.md) - Technical deep-dive

### Backend (Firebase)
- [Backend README](./WhisperType-Backend/README.md) - Setup and overview
- [API Documentation](./WhisperType-Backend/API_DOCUMENTATION.md) - API endpoints
- [Backend Architecture](./WhisperType-Backend/ARCHITECTURE.md) - System design
- [Deployment Guide](./WhisperType-Backend/DEPLOYMENT.md) - Production deployment

## Features

### User Experience
- Voice input works in **any app** with text fields
- **Volume button shortcuts** (double-press or both buttons)
- **Floating mic button** appears when activated
- **Real-time waveform** visualization during recording
- Automatic **text insertion** into focused field

### Technical Features
- Multiple transcription backends (Groq fast, OpenAI accurate)
- Silence trimming with real-time RMS analysis
- Opus compression for 90% smaller uploads (Android 10+)
- Credit-based billing with free tier
- Firebase Remote Config for dynamic updates
- Force update mechanism for critical fixes

## Technology Stack

| Layer | Technology |
|-------|------------|
| **Android App** | Kotlin, Jetpack Compose, Hilt, OkHttp |
| **Backend** | Node.js, TypeScript, Firebase Cloud Functions v2 |
| **Database** | Cloud Firestore |
| **Auth** | Firebase Authentication (Google Sign-In) |
| **Billing** | Google Play Billing Library |
| **Config** | Firebase Remote Config |
| **AI Services** | Groq Whisper, OpenAI GPT-4o-mini |

## Billing Model

| Tier | Credits | Transcription | Cost |
|------|---------|---------------|------|
| **Free** | Limited trial | Groq Turbo | $0 |
| **Pro** | Unlimited* | OpenAI + Premium | Subscription |

*Subject to fair use policy. Credits calculated as: `(duration / 6 seconds) × tier_multiplier`

## Development Setup

### Frontend
```bash
cd "WhisperType- frontend"
# Open in Android Studio
# Sync Gradle
# Add google-services.json from Firebase Console
# Build and run
```

### Backend
```bash
cd WhisperType-Backend/functions
npm install
npm run build
firebase emulators:start  # Local testing
firebase deploy --only functions  # Production
```

## Version History

| Version | Date | Highlights |
|---------|------|------------|
| 1.0.18 | Feb 2025 | Firebase Remote Config guest login |
| 1.0.17 | Jan 2025 | Parallel Opus encoding |
| 1.0.16 | Jan 2025 | Unified recording flows |

See [Backend CHANGELOG](./WhisperType-Backend/CHANGELOG.md) for backend changes.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make changes with tests
4. Submit a pull request

## License

Proprietary - All rights reserved.

## Support

For issues, please open a GitHub issue or contact support.
