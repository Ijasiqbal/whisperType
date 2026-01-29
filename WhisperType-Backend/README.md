# WhisperType Backend

A Firebase Cloud Functions backend that provides audio transcription services with a credit-based billing system. It supports multiple AI transcription providers (OpenAI, Groq) and integrates with Google Play for subscription management.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [Project Structure](#project-structure)
- [API Endpoints](#api-endpoints)
- [Configuration](#configuration)
- [Deployment](#deployment)
- [Documentation](#documentation)

## Overview

WhisperType Backend serves as the API layer for the WhisperType Android application. It handles:

- **Audio Transcription**: Converts speech to text using OpenAI Whisper and Groq APIs
- **User Management**: Firebase Authentication with automatic user provisioning
- **Billing**: Credit-based usage tracking with free trial and Pro subscription tiers
- **Subscription Management**: Google Play subscription verification and renewal handling

**Base URL**: `https://us-central1-whispertype-1de9f.cloudfunctions.net`

## Features

### Multi-Provider Transcription
- **OpenAI Whisper**: Premium transcription with `gpt-4o-transcribe` and `gpt-4o-mini-transcribe` models
- **Groq Whisper**: Fast, cost-effective transcription with `whisper-large-v3` and `whisper-large-v3-turbo`

### Credit System
| Model Tier | Multiplier | Description |
|------------|------------|-------------|
| AUTO       | 0x (free)  | Groq turbo model - no credits charged |
| STANDARD   | 1x         | Standard quality models |
| PREMIUM    | 2x         | Premium OpenAI models |

**Credit Calculation**: `Credits = (Audio Duration in seconds / 6) × Tier Multiplier`

### User Tiers

| Tier | Credits | Duration | Features |
|------|---------|----------|----------|
| Free Trial | 1,000 | 3 months | All transcription providers |
| Pro | 10,000/month | Subscription | Priority support, higher limits |

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      WhisperType Backend                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │   Firebase   │    │   OpenAI     │    │    Groq      │      │
│  │    Auth      │    │   Whisper    │    │   Whisper    │      │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘      │
│         │                   │                   │               │
│         ▼                   ▼                   ▼               │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              Firebase Cloud Functions (v2)               │   │
│  │  ┌─────────────────────────────────────────────────┐    │   │
│  │  │  • transcribeAudio (OpenAI)                     │    │   │
│  │  │  • transcribeAudioGroq (Groq)                   │    │   │
│  │  │  • getTrialStatus                               │    │   │
│  │  │  • getSubscriptionStatus                        │    │   │
│  │  │  • verifySubscription                           │    │   │
│  │  │  • deleteAccount                                │    │   │
│  │  │  • health                                       │    │   │
│  │  └─────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────┘   │
│         │                                                       │
│         ▼                                                       │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │  Firestore   │    │   Remote     │    │  Google Play │      │
│  │   Database   │    │   Config     │    │     API      │      │
│  └──────────────┘    └──────────────┘    └──────────────┘      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Quick Start

### Prerequisites

- Node.js 24+
- Firebase CLI (`npm install -g firebase-tools`)
- Firebase project with Firestore and Authentication enabled

### Installation

```bash
# Clone and navigate to backend
cd WhisperType-Backend/functions

# Install dependencies
npm install

# Set up environment secrets
firebase functions:secrets:set OPENAI_API_KEY
firebase functions:secrets:set GROQ_API_KEY
firebase functions:secrets:set GOOGLE_PLAY_KEY
```

### Local Development

```bash
# Start Firebase emulator
npm run serve

# The API will be available at:
# http://localhost:5001/whispertype-1de9f/us-central1/<function-name>
```

### Deployment

```bash
# Lint and build
npm run lint && npm run build

# Deploy to Firebase
npm run deploy

# Or deploy everything
firebase deploy
```

## Project Structure

```
WhisperType-Backend/
├── functions/
│   ├── src/
│   │   └── index.ts          # Main backend code (all Cloud Functions)
│   ├── lib/                   # Compiled JavaScript output
│   ├── package.json          # Dependencies and scripts
│   ├── tsconfig.json         # TypeScript configuration
│   └── .eslintrc.js          # ESLint configuration
├── firebase.json             # Firebase deployment configuration
├── public/                   # Firebase Hosting files
│   └── delete-account.html   # Account deletion web page
├── README.md                 # This file
├── API_DOCUMENTATION.md      # Complete API reference
├── ARCHITECTURE.md           # Technical architecture details
└── TESTING.md               # Testing guide
```

## API Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/health` | GET | No | Service health check |
| `/transcribeAudio` | POST | Yes | Transcribe audio via OpenAI |
| `/transcribeAudioGroq` | POST | Yes | Transcribe audio via Groq |
| `/getTrialStatus` | GET/POST | Yes | Get free trial status |
| `/getSubscriptionStatus` | GET/POST | Yes | Get subscription status |
| `/verifySubscription` | POST | Yes | Verify Google Play purchase |
| `/deleteAccount` | POST | Yes | Delete user account and data |

For complete API documentation, see [API_DOCUMENTATION.md](./API_DOCUMENTATION.md).

## Configuration

### Firebase Secrets

| Secret | Description |
|--------|-------------|
| `OPENAI_API_KEY` | OpenAI API key for Whisper transcription |
| `GROQ_API_KEY` | Groq API key for fast transcription |
| `GOOGLE_PLAY_KEY` | Google Play service account JSON for subscription verification |

### Remote Config Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `free_tier_credits` | 1000 | Credits for free trial users |
| `pro_tier_credits` | 10000 | Monthly credits for Pro users |
| `seconds_per_credit` | 6 | Audio seconds per credit |
| `trial_duration_months` | 3 | Free trial duration |
| `pro_product_id` | whispertype_pro_monthly | Google Play product ID |
| `pro_plan_enabled` | true | Enable/disable Pro plan |

## Deployment

### Production Deployment

```bash
cd functions

# Run pre-deployment checks
npm run lint
npm run build

# Deploy functions only
firebase deploy --only functions

# Deploy everything (functions + hosting)
firebase deploy
```

### Multi-Region Setup

The backend deploys to multiple regions for low latency:
- `us-central1` (primary)
- `asia-south1`
- `europe-west1`

### Resource Configuration

- **Memory**: 512 MiB per function
- **Max Instances**: 10 (global limit)
- **Timeout**: 2 minutes (default)

## Documentation

| Document | Description |
|----------|-------------|
| [API_DOCUMENTATION.md](./API_DOCUMENTATION.md) | Complete API reference with request/response formats |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Technical architecture and system design |
| [TESTING.md](./TESTING.md) | Testing guide with examples |
| [functions/ENV_SETUP.md](./functions/ENV_SETUP.md) | Environment variable configuration |

## Tech Stack

- **Runtime**: Node.js 24
- **Language**: TypeScript
- **Framework**: Firebase Cloud Functions v2
- **Database**: Firestore
- **Authentication**: Firebase Auth
- **APIs**: OpenAI, Groq, Google Play Developer API

## License

Proprietary - All rights reserved.
