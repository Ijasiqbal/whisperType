# WhisperType Backend Architecture

Technical architecture documentation for the WhisperType backend system.

## Table of Contents

- [System Overview](#system-overview)
- [Data Model](#data-model)
- [Cloud Functions](#cloud-functions)
- [Billing System](#billing-system)
- [Subscription Management](#subscription-management)
- [External Integrations](#external-integrations)
- [Security](#security)
- [Performance](#performance)

## System Overview

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Client Layer                                   │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐         │
│  │  Android App    │  │   Web Client    │  │   API Client    │         │
│  │  (WhisperType)  │  │  (Admin/Debug)  │  │   (Testing)     │         │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘         │
└───────────┼────────────────────┼────────────────────┼───────────────────┘
            │                    │                    │
            └────────────────────┼────────────────────┘
                                 │ HTTPS
                                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Firebase Platform                                │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Firebase Authentication                       │   │
│  │  • Anonymous Auth  • Google Sign-In  • Email/Password           │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                 │                                       │
│                                 ▼ ID Token                              │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Cloud Functions (v2)                          │   │
│  │  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐          │   │
│  │  │ transcribe    │ │ transcribe    │ │ subscription  │          │   │
│  │  │ Audio         │ │ AudioGroq     │ │ endpoints     │          │   │
│  │  └───────┬───────┘ └───────┬───────┘ └───────┬───────┘          │   │
│  │          │                 │                 │                   │   │
│  │          ▼                 ▼                 ▼                   │   │
│  │  ┌─────────────────────────────────────────────────────────┐    │   │
│  │  │              Shared Business Logic                       │    │   │
│  │  │  • Auth verification  • Credit calculation               │    │   │
│  │  │  • User management    • Subscription validation          │    │   │
│  │  └─────────────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                 │                                       │
│                    ┌────────────┼────────────┐                         │
│                    ▼            ▼            ▼                         │
│  ┌──────────────────┐ ┌──────────────┐ ┌──────────────┐               │
│  │    Firestore     │ │   Remote     │ │   Secrets    │               │
│  │    Database      │ │   Config     │ │   Manager    │               │
│  └──────────────────┘ └──────────────┘ └──────────────┘               │
└─────────────────────────────────────────────────────────────────────────┘
                                 │
            ┌────────────────────┼────────────────────┐
            ▼                    ▼                    ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│    OpenAI API    │  │    Groq API      │  │  Google Play     │
│    (Whisper)     │  │    (Whisper)     │  │  Developer API   │
└──────────────────┘  └──────────────────┘  └──────────────────┘
```

### Technology Stack

| Layer | Technology | Purpose |
|-------|------------|---------|
| Runtime | Node.js 24 | Server execution |
| Language | TypeScript | Type-safe development |
| Framework | Firebase Functions v2 | Serverless compute |
| Database | Cloud Firestore | NoSQL document storage |
| Auth | Firebase Authentication | User identity |
| Config | Firebase Remote Config | Dynamic settings |
| Secrets | Secret Manager | API key storage |

## Data Model

### Firestore Collections

```
firestore/
├── users/                          # User documents
│   └── {uid}/
│       ├── createdAt: Timestamp
│       ├── country: string?
│       ├── plan: "free" | "pro"
│       ├── freeTrialStart: Timestamp
│       ├── freeCreditsUsed: number
│       ├── trialExpiryDate: Timestamp
│       └── proSubscription?: {
│           ├── purchaseToken: string
│           ├── productId: string
│           ├── status: "active" | "cancelled" | "expired" | "pending"
│           ├── startDate: Timestamp
│           ├── currentPeriodStart: Timestamp
│           ├── currentPeriodEnd: Timestamp
│           └── proCreditsUsed: number
│       }
│
├── usage_logs/                     # Credit usage tracking
│   └── {uid}/
│       └── entries/
│           └── {entryId}/
│               ├── creditsUsed: number
│               ├── timestamp: Timestamp (server)
│               ├── source: "free" | "pro" | "overage" | "recharge"
│               └── modelTier: "AUTO" | "STANDARD" | "PREMIUM"
│
└── transcriptions/                 # Request logging
    └── {docId}/
        ├── uid: string
        ├── createdAt: Timestamp
        ├── success: boolean
        └── durationMs: number
```

### User Document Schema

```typescript
interface UserDocument {
  // Core fields
  createdAt: Timestamp;
  country?: string;
  plan: "free" | "pro";

  // Free trial fields
  freeTrialStart: Timestamp;
  freeCreditsUsed: number;
  trialExpiryDate: Timestamp;

  // Pro subscription (optional)
  proSubscription?: ProSubscription;

  // Legacy fields (migrated from minutes-based system)
  freeMinutesRemaining?: number;  // Deprecated
  freeSecondsUsed?: number;       // Deprecated
}

interface ProSubscription {
  purchaseToken: string;
  productId: string;
  status: "active" | "cancelled" | "expired" | "pending";
  startDate: Timestamp;
  currentPeriodStart: Timestamp;
  currentPeriodEnd: Timestamp;
  proCreditsUsed: number;
}
```

### Usage Log Schema

```typescript
interface UsageLogEntry {
  creditsUsed: number;
  timestamp: Timestamp;  // Server timestamp
  source: "free" | "pro" | "overage" | "recharge";
  modelTier: "AUTO" | "STANDARD" | "PREMIUM";
}
```

## Cloud Functions

### Function Overview

| Function | Trigger | Regions | Memory | Purpose |
|----------|---------|---------|--------|---------|
| `health` | HTTP GET | us-central1, asia-south1, europe-west1 | 512 MiB | Health check |
| `transcribeAudio` | HTTP POST | us-central1, asia-south1, europe-west1 | 512 MiB | OpenAI transcription |
| `transcribeAudioGroq` | HTTP POST | us-central1, asia-south1, europe-west1 | 512 MiB | Groq transcription |
| `getTrialStatus` | HTTP GET/POST | us-central1, asia-south1, europe-west1 | 512 MiB | Trial status |
| `getSubscriptionStatus` | HTTP GET/POST | us-central1, asia-south1, europe-west1 | 512 MiB | Full subscription status |
| `verifySubscription` | HTTP POST | us-central1, asia-south1, europe-west1 | 512 MiB | Google Play verification |
| `deleteAccount` | HTTP POST | us-central1, asia-south1, europe-west1 | 512 MiB | Account deletion |

### Function Flow: Transcription

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    Transcription Request Flow                            │
└─────────────────────────────────────────────────────────────────────────┘

  Request
     │
     ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Warmup     │ Yes │  Return     │     │             │
│  Request?   │────►│  {warm}     │     │             │
└──────┬──────┘     └─────────────┘     │             │
       │ No                             │             │
       ▼                                │             │
┌─────────────┐     ┌─────────────┐     │             │
│  Verify     │ No  │  Return     │     │             │
│  Auth Token │────►│  401        │     │             │
└──────┬──────┘     └─────────────┘     │             │
       │ Yes                            │             │
       ▼                                │             │
┌─────────────┐     ┌─────────────┐     │             │
│  Get/Create │     │  Create New │     │             │
│  User Doc   │────►│  User       │     │             │
└──────┬──────┘     └──────┬──────┘     │             │
       │◄──────────────────┘            │             │
       ▼                                │             │
┌─────────────┐     ┌─────────────┐     │             │
│  Check      │ No  │  Return     │     │             │
│  Quota      │────►│  403        │     │             │
└──────┬──────┘     └─────────────┘     │             │
       │ Yes                            │             │
       ▼                                │             │
┌─────────────┐                         │             │
│  Decode     │                         │             │
│  Audio      │                         │             │
└──────┬──────┘                         │             │
       │                                │             │
       ▼                                │             │
┌─────────────┐     ┌─────────────┐     │             │
│  Call       │     │  OpenAI or  │     │             │
│  External   │────►│  Groq API   │     │             │
│  API        │     │             │     │             │
└──────┬──────┘     └─────────────┘     │             │
       │                                │             │
       ▼                                │             │
┌─────────────┐                         │             │
│  Calculate  │                         │             │
│  Credits    │                         │             │
└──────┬──────┘                         │             │
       │                                │             │
       ▼                                │             │
┌─────────────┐     ┌─────────────┐     │             │
│  Deduct     │     │  Firestore  │     │             │
│  Credits    │────►│  Transaction│     │             │
│  (Atomic)   │     │             │     │             │
└──────┬──────┘     └─────────────┘     │             │
       │                                │             │
       ▼                                │             │
┌─────────────┐                         │             │
│  Log        │                         │             │
│  Request    │                         │             │
└──────┬──────┘                         │             │
       │                                │             │
       ▼                                │             │
┌─────────────┐                         │             │
│  Return     │                         │             │
│  Response   │                         │             │
└─────────────┘                         │             │
```

### Quota Check Logic

```typescript
async function checkQuota(uid: string, userData: UserDocument): Promise<QuotaResult> {
  if (userData.plan === "pro") {
    // Pro user: Check subscription validity and credit usage
    const subscription = userData.proSubscription;

    // Try to re-verify with Google Play if expired
    if (subscription?.status === "expired") {
      const renewed = await attemptSubscriptionReverify(uid, subscription);
      if (renewed) return { allowed: true, plan: "pro" };
    }

    if (subscription?.status !== "active") {
      return { allowed: false, error: "PRO_EXPIRED" };
    }

    // Check if in new billing period (anniversary-based reset)
    const periodEnd = subscription.currentPeriodEnd.toDate();
    if (Date.now() > periodEnd.getTime()) {
      // Reset credits for new period
      await resetProCredits(uid);
    }

    const creditsRemaining = proCreditsLimit - subscription.proCreditsUsed;
    if (creditsRemaining <= 0) {
      return { allowed: false, error: "PRO_LIMIT_REACHED" };
    }

    return { allowed: true, plan: "pro" };
  }

  // Free user: Check trial validity
  const trialExpiry = userData.trialExpiryDate.toDate();
  const creditsRemaining = freeTierCredits - userData.freeCreditsUsed;

  if (Date.now() > trialExpiry.getTime()) {
    return { allowed: false, error: "TRIAL_EXPIRED" };
  }

  if (creditsRemaining <= 0) {
    return { allowed: false, error: "TRIAL_CREDITS_EXHAUSTED" };
  }

  return { allowed: true, plan: "free" };
}
```

## Billing System

### Credit Calculation

```typescript
enum ModelTier {
  AUTO = 0,      // Free tier (Groq turbo)
  STANDARD = 1,  // Standard models
  PREMIUM = 2,   // Premium models (OpenAI)
}

function calculateCredits(
  audioDurationMs: number,
  modelTier: ModelTier,
  secondsPerCredit: number = 6
): number {
  const durationSeconds = audioDurationMs / 1000;
  const baseCredits = durationSeconds / secondsPerCredit;
  const multiplier = modelTier;

  return Math.ceil(baseCredits * multiplier);
}

// Examples:
// 30s audio, AUTO tier:     0 credits (free)
// 30s audio, STANDARD tier: 5 credits
// 30s audio, PREMIUM tier:  10 credits
```

### Model Tier Assignment

```typescript
function getModelTier(model: string, provider: "openai" | "groq"): ModelTier {
  if (provider === "groq") {
    switch (model) {
      case "whisper-large-v3-turbo":
        return ModelTier.AUTO;
      case "whisper-large-v3":
        return ModelTier.STANDARD;
      default:
        return ModelTier.STANDARD;
    }
  }

  // OpenAI
  switch (model) {
    case "gpt-4o-transcribe":
      return ModelTier.PREMIUM;
    case "gpt-4o-mini-transcribe":
    default:
      return ModelTier.PREMIUM;
  }
}
```

### Credit Deduction (Atomic Transaction)

```typescript
async function deductCredits(
  uid: string,
  credits: number,
  plan: "free" | "pro",
  modelTier: ModelTier
): Promise<void> {
  const userRef = db.collection("users").doc(uid);
  const usageRef = db.collection("usage_logs").doc(uid)
    .collection("entries").doc();

  await db.runTransaction(async (transaction) => {
    const userDoc = await transaction.get(userRef);
    const userData = userDoc.data() as UserDocument;

    if (plan === "free") {
      transaction.update(userRef, {
        freeCreditsUsed: userData.freeCreditsUsed + credits
      });
    } else {
      transaction.update(userRef, {
        "proSubscription.proCreditsUsed":
          userData.proSubscription.proCreditsUsed + credits
      });
    }

    // Log usage
    transaction.set(usageRef, {
      creditsUsed: credits,
      timestamp: FieldValue.serverTimestamp(),
      source: plan,
      modelTier: ModelTier[modelTier]
    });
  });
}
```

### Usage Synchronization

The system maintains data consistency through periodic sync:

```typescript
async function syncLifetimeUsage(uid: string): Promise<number> {
  // Sum all usage log entries
  const usageSnapshot = await db
    .collection("usage_logs")
    .doc(uid)
    .collection("entries")
    .get();

  const totalFromLogs = usageSnapshot.docs.reduce(
    (sum, doc) => sum + (doc.data().creditsUsed || 0),
    0
  );

  // Compare with stored value
  const userDoc = await db.collection("users").doc(uid).get();
  const storedValue = userDoc.data()?.freeCreditsUsed || 0;

  // Use the higher value to prevent data loss
  if (totalFromLogs > storedValue) {
    await db.collection("users").doc(uid).update({
      freeCreditsUsed: totalFromLogs
    });
    return totalFromLogs;
  }

  return storedValue;
}
```

## Subscription Management

### Google Play Verification Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    Subscription Verification Flow                        │
└─────────────────────────────────────────────────────────────────────────┘

  Android App
       │
       │ purchaseToken, productId
       ▼
┌─────────────┐
│  /verify    │
│  Subscription│
└──────┬──────┘
       │
       ▼
┌─────────────┐     ┌─────────────┐
│  Validate   │ No  │  Return     │
│  Input      │────►│  400        │
└──────┬──────┘     └─────────────┘
       │ Yes
       ▼
┌─────────────┐     ┌─────────────┐
│  Call       │     │  Google     │
│  Google     │────►│  Play API   │
│  Play API   │     │             │
└──────┬──────┘     └─────────────┘
       │
       ▼
┌─────────────┐     ┌─────────────┐
│  Verify     │ No  │  Return     │
│  Payment    │────►│  403        │
│  State      │     │             │
└──────┬──────┘     └─────────────┘
       │ Yes
       ▼
┌─────────────┐     ┌─────────────┐
│  Check      │ No  │  Return     │
│  Expiry     │────►│  403        │
│  Time       │     │  (expired)  │
└──────┬──────┘     └─────────────┘
       │ Yes (valid)
       ▼
┌─────────────┐
│  Update     │
│  Firestore  │
│  User Doc   │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  Return     │
│  Success    │
│  + Status   │
└─────────────┘
```

### Subscription Re-verification

When a user's subscription shows as expired, the system attempts automatic re-verification:

```typescript
async function attemptSubscriptionReverify(
  uid: string,
  subscription: ProSubscription
): Promise<boolean> {
  try {
    const playSubscription = await verifyWithGooglePlay(
      subscription.purchaseToken,
      subscription.productId
    );

    // Check if auto-renewed
    if (playSubscription.paymentState === 1) { // Received
      const expiryTime = parseInt(playSubscription.expiryTimeMillis);

      if (expiryTime > Date.now()) {
        // Subscription was renewed! Update Firestore
        await updateSubscriptionStatus(uid, {
          status: "active",
          currentPeriodEnd: Timestamp.fromMillis(expiryTime),
          proCreditsUsed: 0  // Reset for new period
        });
        return true;
      }
    }

    return false;
  } catch (error) {
    console.error("Re-verification failed:", error);
    return false;
  }
}
```

### Anniversary-Based Billing Cycle

Pro credits reset on the monthly anniversary of subscription start:

```typescript
function getNextResetDate(subscriptionStartDate: Date): Date {
  const now = new Date();
  const dayOfMonth = subscriptionStartDate.getDate();

  let nextReset = new Date(
    now.getFullYear(),
    now.getMonth(),
    dayOfMonth
  );

  // If we've passed this month's anniversary, move to next month
  if (nextReset <= now) {
    nextReset.setMonth(nextReset.getMonth() + 1);
  }

  return nextReset;
}
```

## External Integrations

### OpenAI Whisper API

```typescript
import OpenAI from "openai";

const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY
});

async function transcribeWithOpenAI(
  audioBuffer: Buffer,
  format: string,
  model: string
): Promise<string> {
  const file = new File([audioBuffer], `audio.${format}`, {
    type: `audio/${format}`
  });

  const response = await openai.audio.transcriptions.create({
    file,
    model,  // "gpt-4o-transcribe" or "gpt-4o-mini-transcribe"
    response_format: "text"
  });

  return response;
}
```

### Groq Whisper API

```typescript
// Groq uses OpenAI-compatible SDK
const groq = new OpenAI({
  apiKey: process.env.GROQ_API_KEY,
  baseURL: "https://api.groq.com/openai/v1"
});

async function transcribeWithGroq(
  audioBuffer: Buffer,
  format: string,
  model: string
): Promise<string> {
  const file = new File([audioBuffer], `audio.${format}`, {
    type: `audio/${format}`
  });

  const response = await groq.audio.transcriptions.create({
    file,
    model,  // "whisper-large-v3-turbo" or "whisper-large-v3"
    response_format: "text"
  });

  return response;
}
```

### Google Play Developer API

```typescript
import { google } from "googleapis";

async function verifyWithGooglePlay(
  purchaseToken: string,
  productId: string
): Promise<SubscriptionPurchase> {
  const serviceAccountKey = JSON.parse(process.env.GOOGLE_PLAY_KEY);

  const auth = new google.auth.GoogleAuth({
    credentials: serviceAccountKey,
    scopes: ["https://www.googleapis.com/auth/androidpublisher"]
  });

  const androidPublisher = google.androidpublisher({
    version: "v3",
    auth
  });

  const response = await androidPublisher.purchases.subscriptions.get({
    packageName: "com.whispertype.app",
    subscriptionId: productId,
    token: purchaseToken
  });

  return response.data;
}
```

## Security

### Authentication Flow

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │     │  Firebase   │     │   Backend   │
│   App       │     │  Auth       │     │  Function   │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       │  Sign In          │                   │
       │──────────────────►│                   │
       │                   │                   │
       │  ID Token         │                   │
       │◄──────────────────│                   │
       │                   │                   │
       │  API Request + Bearer Token           │
       │──────────────────────────────────────►│
       │                   │                   │
       │                   │  Verify Token     │
       │                   │◄──────────────────│
       │                   │                   │
       │                   │  Token Valid      │
       │                   │──────────────────►│
       │                   │                   │
       │  API Response                         │
       │◄──────────────────────────────────────│
```

### Token Verification

```typescript
import { getAuth } from "firebase-admin/auth";

async function verifyAuthToken(
  authHeader: string | undefined
): Promise<{ uid: string } | null> {
  if (!authHeader?.startsWith("Bearer ")) {
    return null;
  }

  const idToken = authHeader.split("Bearer ")[1];

  try {
    const decodedToken = await getAuth().verifyIdToken(idToken);
    return { uid: decodedToken.uid };
  } catch (error) {
    console.error("Token verification failed:", error);
    return null;
  }
}
```

### Secret Management

Secrets are stored in Firebase Secret Manager:

```bash
# Set secrets
firebase functions:secrets:set OPENAI_API_KEY
firebase functions:secrets:set GROQ_API_KEY
firebase functions:secrets:set GOOGLE_PLAY_KEY

# Access in code
const apiKey = process.env.OPENAI_API_KEY;
```

Function declaration with secrets:

```typescript
export const transcribeAudio = onRequest(
  {
    secrets: ["OPENAI_API_KEY"],
    region: ["us-central1", "asia-south1", "europe-west1"],
    memory: "512MiB"
  },
  async (req, res) => {
    // Function implementation
  }
);
```

### CORS Configuration

```typescript
function setCorsHeaders(res: Response, origin: string): void {
  const allowedOrigins = [
    "https://whispertype-1de9f.web.app",
    "https://whispertype-1de9f.firebaseapp.com"
  ];

  if (allowedOrigins.includes(origin)) {
    res.set("Access-Control-Allow-Origin", origin);
  }

  res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
}
```

## Performance

### Cold Start Mitigation

The system uses warmup requests to reduce cold starts:

```typescript
// Check for warmup request at the start of each function
if (req.method === "GET" || req.body?.warmup === true) {
  res.status(200).json({ status: "warm" });
  return;
}
```

Client-side warmup (Android):

```kotlin
// Send warmup requests on app launch
fun warmupBackend() {
    val endpoints = listOf(
        "transcribeAudio",
        "transcribeAudioGroq"
    )

    endpoints.forEach { endpoint ->
        scope.launch {
            try {
                api.warmup(endpoint)
            } catch (e: Exception) {
                // Ignore warmup failures
            }
        }
    }
}
```

### Resource Allocation

```typescript
const functionConfig = {
  memory: "512MiB",
  maxInstances: 10,
  region: ["us-central1", "asia-south1", "europe-west1"]
};
```

### Multi-Region Deployment

Functions are deployed to multiple regions for lower latency:

| Region | Location | Primary Users |
|--------|----------|---------------|
| us-central1 | Iowa, USA | Americas |
| asia-south1 | Mumbai, India | South Asia |
| europe-west1 | Belgium | Europe |

### Firestore Optimization

- **Composite indexes** for usage log queries
- **Transactions** for atomic credit operations
- **Batch writes** for cleanup operations

## Remote Config

### Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `free_tier_credits` | number | 1000 | Credits for free trial |
| `pro_tier_credits` | number | 10000 | Monthly credits for Pro |
| `seconds_per_credit` | number | 6 | Seconds of audio per credit |
| `trial_duration_months` | number | 3 | Free trial duration |
| `pro_product_id` | string | whispertype_pro_monthly | Google Play product ID |
| `pro_plan_enabled` | boolean | true | Enable Pro subscription |

### Caching

```typescript
let remoteConfigCache: RemoteConfigData | null = null;
let lastFetchTime = 0;
const CACHE_TTL = 5 * 60 * 1000; // 5 minutes

async function getRemoteConfig(): Promise<RemoteConfigData> {
  const now = Date.now();

  if (remoteConfigCache && (now - lastFetchTime) < CACHE_TTL) {
    return remoteConfigCache;
  }

  const template = await remoteConfig.getTemplate();
  remoteConfigCache = parseTemplate(template);
  lastFetchTime = now;

  return remoteConfigCache;
}
```

## Error Handling

### Error Response Format

```typescript
interface ErrorResponse {
  error: string;       // Error code
  message: string;     // Human-readable message
  details?: string;    // Additional context
  trialStatus?: TrialStatus;
  proStatus?: ProStatus;
}
```

### Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `TRIAL_EXPIRED` | 403 | Free trial period ended |
| `TRIAL_CREDITS_EXHAUSTED` | 403 | Free credits used up |
| `PRO_EXPIRED` | 403 | Pro subscription expired |
| `PRO_LIMIT_REACHED` | 403 | Monthly credit limit reached |
| `SUBSCRIPTION_INVALID` | 403 | Google Play verification failed |
| `UNAUTHORIZED` | 401 | Invalid or missing auth token |
| `INVALID_REQUEST` | 400 | Malformed request |
| `SERVER_ERROR` | 500 | Internal server error |

## Logging

### Request Logging

```typescript
async function logTranscriptionRequest(
  uid: string,
  success: boolean,
  durationMs: number
): Promise<void> {
  await db.collection("transcriptions").add({
    uid,
    createdAt: FieldValue.serverTimestamp(),
    success,
    durationMs
  });
}
```

### Cloud Logging

```bash
# View function logs
firebase functions:log

# Filter by function
firebase functions:log --only transcribeAudio

# Stream logs
firebase functions:log --follow
```
