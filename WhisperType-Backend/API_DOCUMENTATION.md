# WhisperType API Documentation

Complete API reference for the WhisperType backend services.

## Base URL

```
https://us-central1-whispertype-1de9f.cloudfunctions.net
```

## Authentication

All endpoints (except `/health`) require Firebase Authentication.

**Header Format:**
```
Authorization: Bearer <firebase_id_token>
```

**Getting a Token (Android/Kotlin):**
```kotlin
val user = FirebaseAuth.getInstance().currentUser
val token = user?.getIdToken(false)?.await()?.token
```

**Error Response (401):**
```json
{
  "error": "Unauthorized: Invalid or missing authentication token"
}
```

---

## Endpoints

### 1. Health Check

Check if the service is running.

| Property | Value |
|----------|-------|
| **Endpoint** | `GET /health` |
| **Authentication** | None |

**Response:**
```
OK
```

**Example:**
```bash
curl https://us-central1-whispertype-1de9f.cloudfunctions.net/health
```

---

### 2. Transcribe Audio (OpenAI)

Transcribe audio using OpenAI Whisper API. Uses PREMIUM tier models (2x credits).

| Property | Value |
|----------|-------|
| **Endpoint** | `POST /transcribeAudio` |
| **Authentication** | Required |
| **Content-Type** | `application/json` |

#### Request Body

```json
{
  "audioBase64": "string (required)",
  "audioFormat": "string (optional, default: 'm4a')",
  "model": "string (optional)",
  "audioDurationMs": "number (optional)"
}
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `audioBase64` | string | Yes | Base64-encoded audio file |
| `audioFormat` | string | No | Audio format: `wav`, `m4a`, `mp3`, `webm`, `mp4`, `mpeg`, `mpga`, `ogg`. Default: `m4a` |
| `model` | string | No | Model to use: `gpt-4o-transcribe`, `gpt-4o-mini-transcribe`. Default: `gpt-4o-mini-transcribe` |
| `audioDurationMs` | number | No | Audio duration in milliseconds (for accurate credit calculation) |

#### Success Response (200)

```json
{
  "text": "Transcribed text from your audio",
  "creditsUsed": 2,
  "totalCreditsThisMonth": 150,
  "plan": "free",
  "subscriptionStatus": {
    "status": "active",
    "creditsRemaining": 850,
    "creditsLimit": 1000,
    "resetDateMs": 1735689600000,
    "warningLevel": "none"
  },
  "trialStatus": {
    "status": "active",
    "freeCreditsUsed": 150,
    "freeCreditsRemaining": 850,
    "freeTierCredits": 1000,
    "trialExpiryDateMs": 1735689600000,
    "warningLevel": "none"
  }
}
```

For Pro users, includes `proStatus` instead of `trialStatus`:
```json
{
  "proStatus": {
    "isActive": true,
    "proCreditsUsed": 500,
    "proCreditsRemaining": 9500,
    "proCreditsLimit": 10000,
    "currentPeriodEndMs": 1735689600000
  }
}
```

#### Error Responses

**400 Bad Request:**
```json
{
  "error": "Missing or invalid audioBase64 field in request body"
}
```

**401 Unauthorized:**
```json
{
  "error": "Unauthorized: Invalid or missing authentication token"
}
```

**403 Forbidden - Trial Expired:**
```json
{
  "error": "TRIAL_EXPIRED",
  "message": "Your free trial has ended",
  "details": "Your 3-month free trial period has expired. Subscribe to Pro to continue using WhisperType.",
  "trialStatus": {
    "status": "expired_time",
    "freeCreditsUsed": 500,
    "freeCreditsRemaining": 500,
    "freeTierCredits": 1000,
    "trialExpiryDateMs": 1735689600000
  }
}
```

**403 Forbidden - Pro Expired:**
```json
{
  "error": "PRO_EXPIRED",
  "message": "Your Pro subscription has expired",
  "details": "Please renew your subscription to continue.",
  "proStatus": {
    "isActive": false,
    "status": "expired"
  }
}
```

**403 Forbidden - Pro Limit Reached:**
```json
{
  "error": "PRO_LIMIT_REACHED",
  "message": "Monthly credit limit reached",
  "details": "You've used all 10000 credits this month. Credits reset on your billing anniversary.",
  "proStatus": {
    "proCreditsUsed": 10000,
    "proCreditsRemaining": 0,
    "proCreditsLimit": 10000,
    "resetDateMs": 1735689600000
  }
}
```

**500 Internal Server Error:**
```json
{
  "error": "Failed to transcribe audio"
}
```

#### Example

```bash
curl -X POST https://us-central1-whispertype-1de9f.cloudfunctions.net/transcribeAudio \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <FIREBASE_ID_TOKEN>" \
  -d "{\"audioBase64\": \"$(base64 -i audio.m4a)\", \"audioFormat\": \"m4a\"}"
```

---

### 3. Transcribe Audio (Groq)

Transcribe audio using Groq Whisper API. Offers free (AUTO) and standard tier options.

| Property | Value |
|----------|-------|
| **Endpoint** | `POST /transcribeAudioGroq` |
| **Authentication** | Required |
| **Content-Type** | `application/json` |

#### Request Body

```json
{
  "audioBase64": "string (required)",
  "audioFormat": "string (optional, default: 'm4a')",
  "model": "string (optional)",
  "audioDurationMs": "number (optional)"
}
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `audioBase64` | string | Yes | Base64-encoded audio file |
| `audioFormat` | string | No | Audio format: `wav`, `m4a`, `mp3`, `webm`, `mp4`, `mpeg`, `mpga`, `ogg`. Default: `m4a` |
| `model` | string | No | Model: `whisper-large-v3-turbo` (FREE), `whisper-large-v3` (STANDARD). Default: `whisper-large-v3-turbo` |
| `audioDurationMs` | number | No | Audio duration in milliseconds |

#### Model Tiers

| Model | Tier | Credit Multiplier |
|-------|------|-------------------|
| `whisper-large-v3-turbo` | AUTO | 0x (free) |
| `whisper-large-v3` | STANDARD | 1x |

#### Success Response (200)

Same format as OpenAI endpoint.

#### Example

```bash
curl -X POST https://us-central1-whispertype-1de9f.cloudfunctions.net/transcribeAudioGroq \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <FIREBASE_ID_TOKEN>" \
  -d "{\"audioBase64\": \"$(base64 -i audio.m4a)\", \"model\": \"whisper-large-v3-turbo\"}"
```

---

### 4. Get Trial Status

Get the current user's free trial status.

| Property | Value |
|----------|-------|
| **Endpoint** | `GET/POST /getTrialStatus` |
| **Authentication** | Required |

#### Success Response (200)

```json
{
  "status": "active",
  "freeCreditsUsed": 150,
  "freeCreditsRemaining": 850,
  "freeTierCredits": 1000,
  "trialExpiryDateMs": 1735689600000,
  "warningLevel": "none",
  "totalCreditsThisMonth": 150
}
```

#### Status Values

| Status | Description |
|--------|-------------|
| `active` | Trial is active and has credits remaining |
| `expired_time` | Trial period (3 months) has ended |
| `expired_usage` | All free credits have been used |

#### Warning Levels

| Level | Description |
|-------|-------------|
| `none` | Less than 50% used |
| `fifty_percent` | 50-79% credits used |
| `eighty_percent` | 80-94% credits used |
| `ninety_five_percent` | 95%+ credits used |

#### Example

```bash
curl -X GET https://us-central1-whispertype-1de9f.cloudfunctions.net/getTrialStatus \
  -H "Authorization: Bearer <FIREBASE_ID_TOKEN>"
```

---

### 5. Get Subscription Status

Get comprehensive subscription status for the current user.

| Property | Value |
|----------|-------|
| **Endpoint** | `GET/POST /getSubscriptionStatus` |
| **Authentication** | Required |

#### Response (Free User)

```json
{
  "plan": "free",
  "isActive": true,
  "freeCreditsUsed": 150,
  "freeCreditsRemaining": 850,
  "freeTierCredits": 1000,
  "trialExpiryDateMs": 1735689600000,
  "totalCreditsThisMonth": 150,
  "status": "active",
  "warningLevel": "none",
  "proPlanEnabled": true,
  "proCreditsLimit": 10000
}
```

#### Response (Pro User)

```json
{
  "plan": "pro",
  "isActive": true,
  "proCreditsUsed": 500,
  "proCreditsRemaining": 9500,
  "proCreditsLimit": 10000,
  "resetDateMs": 1735689600000,
  "subscriptionStartDateMs": 1704067200000,
  "subscriptionStatus": "active",
  "status": "active",
  "warningLevel": "none",
  "proPlanEnabled": true,
  "totalCreditsThisMonth": 500
}
```

#### Subscription Status Values

| Status | Description |
|--------|-------------|
| `active` | Subscription is active |
| `cancelled` | User cancelled, still active until period end |
| `expired` | Subscription has expired |
| `pending` | Payment pending |

#### Example

```bash
curl -X GET https://us-central1-whispertype-1de9f.cloudfunctions.net/getSubscriptionStatus \
  -H "Authorization: Bearer <FIREBASE_ID_TOKEN>"
```

---

### 6. Verify Subscription

Verify a Google Play purchase and activate Pro subscription.

| Property | Value |
|----------|-------|
| **Endpoint** | `POST /verifySubscription` |
| **Authentication** | Required |
| **Content-Type** | `application/json` |

#### Request Body

```json
{
  "purchaseToken": "string (required)",
  "productId": "string (required)"
}
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `purchaseToken` | string | Yes | Google Play purchase token |
| `productId` | string | Yes | Product ID (e.g., `whispertype_pro_monthly`) |

#### Success Response (200)

```json
{
  "success": true,
  "plan": "pro",
  "proStatus": {
    "isActive": true,
    "proCreditsUsed": 0,
    "proCreditsRemaining": 10000,
    "proCreditsLimit": 10000,
    "currentPeriodEndMs": 1738281600000
  }
}
```

#### Error Responses

**400 Bad Request:**
```json
{
  "error": "Missing purchaseToken or productId"
}
```

**403 Forbidden:**
```json
{
  "error": "SUBSCRIPTION_INVALID",
  "message": "Subscription verification failed",
  "details": "The subscription could not be verified with Google Play."
}
```

#### Example (Android/Kotlin)

```kotlin
suspend fun verifySubscription(purchaseToken: String, productId: String) {
    val user = FirebaseAuth.getInstance().currentUser
    val idToken = user?.getIdToken(false)?.await()?.token ?: return

    val json = JSONObject().apply {
        put("purchaseToken", purchaseToken)
        put("productId", productId)
    }

    val request = Request.Builder()
        .url("https://us-central1-whispertype-1de9f.cloudfunctions.net/verifySubscription")
        .post(json.toString().toRequestBody("application/json".toMediaType()))
        .addHeader("Authorization", "Bearer $idToken")
        .build()

    // Execute request...
}
```

---

### 7. Delete Account

Permanently delete user account and all associated data.

| Property | Value |
|----------|-------|
| **Endpoint** | `POST /deleteAccount` |
| **Authentication** | Required |

#### Success Response (200)

```json
{
  "success": true,
  "message": "Account and all associated data have been deleted"
}
```

#### What Gets Deleted

- User document in Firestore (`users` collection)
- All usage logs (`usage_logs` collection)
- All transcription request logs (`transcriptions` collection)
- Firebase Authentication account

#### Error Responses

**401 Unauthorized:**
```json
{
  "error": "Unauthorized: Invalid or missing authentication token"
}
```

**500 Internal Server Error:**
```json
{
  "error": "Failed to delete account",
  "message": "An error occurred while deleting your account"
}
```

#### Example

```bash
curl -X POST https://us-central1-whispertype-1de9f.cloudfunctions.net/deleteAccount \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <FIREBASE_ID_TOKEN>"
```

---

## Credit System

### How Credits Work

Credits are consumed when transcribing audio. The calculation is:

```
Base Credits = Audio Duration (seconds) / 6
Final Credits = Base Credits × Model Tier Multiplier
```

### Model Tiers

| Tier | Multiplier | Models |
|------|------------|--------|
| AUTO | 0x (free) | `whisper-large-v3-turbo` (Groq) |
| STANDARD | 1x | `whisper-large-v3` (Groq), `gpt-4o-mini-transcribe` (OpenAI) |
| PREMIUM | 2x | `gpt-4o-transcribe` (OpenAI) |

### Examples

| Audio Duration | Model | Tier | Credits Used |
|----------------|-------|------|--------------|
| 30 seconds | whisper-large-v3-turbo | AUTO | 0 |
| 30 seconds | whisper-large-v3 | STANDARD | 5 |
| 30 seconds | gpt-4o-transcribe | PREMIUM | 10 |
| 60 seconds | gpt-4o-mini-transcribe | STANDARD | 10 |

### Credit Limits

| Plan | Monthly Credits | Reset |
|------|-----------------|-------|
| Free Trial | 1,000 | N/A (one-time) |
| Pro | 10,000 | Monthly (billing anniversary) |

---

## Audio Requirements

### Supported Formats

- mp3
- mp4
- mpeg
- mpga
- m4a
- wav
- webm
- ogg

### Limits

- **Maximum file size**: 25 MB
- **Recommended format**: m4a or webm (good compression)

### Base64 Encoding

- Use standard base64 encoding (not URL-safe)
- Ensure no line breaks in the encoded string
- Use `Base64.NO_WRAP` on Android

---

## Warmup Requests

To prevent cold starts, send warmup requests before user interactions:

**GET Request:**
```bash
curl https://us-central1-whispertype-1de9f.cloudfunctions.net/transcribeAudio \
  -H "Authorization: Bearer <TOKEN>"
```

**POST with warmup flag:**
```json
{
  "warmup": true
}
```

Warmup requests return `200 OK` with `{"status": "warm"}` without processing audio.

---

## Error Handling Best Practices

### Recommended Approach

```kotlin
when (response.code) {
    200 -> handleSuccess(response)
    400 -> handleBadRequest(response)  // Invalid input
    401 -> handleUnauthorized()        // Re-authenticate
    403 -> handleForbidden(response)   // Check error code for specific handling
    500 -> handleServerError()         // Retry with exponential backoff
}
```

### 403 Error Codes

| Error Code | Action |
|------------|--------|
| `TRIAL_EXPIRED` | Prompt user to subscribe |
| `PRO_EXPIRED` | Prompt user to renew subscription |
| `PRO_LIMIT_REACHED` | Show credits reset date |

### Retry Strategy

For 500 errors, implement exponential backoff:

```kotlin
val delays = listOf(1000L, 2000L, 4000L) // milliseconds
for (delay in delays) {
    val response = makeRequest()
    if (response.isSuccessful) break
    delay(delay)
}
```

---

## Rate Limits

- No rate limits enforced by WhisperType backend
- Subject to OpenAI/Groq API rate limits based on account tier
- Recommended: Implement client-side throttling for UX

---

## Code Examples

### Android (Kotlin) - Complete Example

```kotlin
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.util.Base64

class TranscriptionService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://us-central1-whispertype-1de9f.cloudfunctions.net"

    suspend fun transcribe(
        audioBytes: ByteArray,
        format: String = "m4a",
        useGroq: Boolean = true
    ): TranscriptionResult {
        val token = getAuthToken() ?: throw UnauthorizedException()

        val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        val endpoint = if (useGroq) "transcribeAudioGroq" else "transcribeAudio"

        val json = JSONObject().apply {
            put("audioBase64", audioBase64)
            put("audioFormat", format)
            if (useGroq) put("model", "whisper-large-v3-turbo")
        }

        val request = Request.Builder()
            .url("$baseUrl/$endpoint")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $token")
            .build()

        val response = client.newCall(request).execute()
        return parseResponse(response)
    }

    private suspend fun getAuthToken(): String? {
        return FirebaseAuth.getInstance().currentUser
            ?.getIdToken(false)
            ?.await()
            ?.token
    }

    private fun parseResponse(response: Response): TranscriptionResult {
        val body = response.body?.string() ?: "{}"
        val json = JSONObject(body)

        return when (response.code) {
            200 -> TranscriptionResult.Success(
                text = json.getString("text"),
                creditsUsed = json.optInt("creditsUsed", 0)
            )
            403 -> TranscriptionResult.QuotaExceeded(
                error = json.getString("error"),
                message = json.getString("message")
            )
            else -> TranscriptionResult.Error(
                code = response.code,
                message = json.optString("error", "Unknown error")
            )
        }
    }
}

sealed class TranscriptionResult {
    data class Success(val text: String, val creditsUsed: Int) : TranscriptionResult()
    data class QuotaExceeded(val error: String, val message: String) : TranscriptionResult()
    data class Error(val code: Int, val message: String) : TranscriptionResult()
}
```

### Python

```python
import requests
import base64
from typing import Optional

class WhisperTypeClient:
    BASE_URL = "https://us-central1-whispertype-1de9f.cloudfunctions.net"

    def __init__(self, firebase_token: str):
        self.headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {firebase_token}"
        }

    def transcribe(
        self,
        audio_path: str,
        audio_format: str = "m4a",
        use_groq: bool = True,
        model: Optional[str] = None
    ) -> dict:
        with open(audio_path, "rb") as f:
            audio_base64 = base64.b64encode(f.read()).decode("utf-8")

        endpoint = "transcribeAudioGroq" if use_groq else "transcribeAudio"

        payload = {
            "audioBase64": audio_base64,
            "audioFormat": audio_format
        }
        if model:
            payload["model"] = model

        response = requests.post(
            f"{self.BASE_URL}/{endpoint}",
            json=payload,
            headers=self.headers,
            timeout=60
        )

        if response.status_code == 200:
            return response.json()
        else:
            raise Exception(f"Error {response.status_code}: {response.json()}")

    def get_subscription_status(self) -> dict:
        response = requests.get(
            f"{self.BASE_URL}/getSubscriptionStatus",
            headers=self.headers
        )
        return response.json()

# Usage
client = WhisperTypeClient(firebase_token="your_token_here")
result = client.transcribe("audio.m4a", use_groq=True)
print(f"Transcription: {result['text']}")
print(f"Credits used: {result['creditsUsed']}")
```

### cURL

```bash
# Get subscription status
curl -X GET https://us-central1-whispertype-1de9f.cloudfunctions.net/getSubscriptionStatus \
  -H "Authorization: Bearer $FIREBASE_TOKEN"

# Transcribe with Groq (free tier)
curl -X POST https://us-central1-whispertype-1de9f.cloudfunctions.net/transcribeAudioGroq \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FIREBASE_TOKEN" \
  -d "{
    \"audioBase64\": \"$(base64 -i audio.m4a)\",
    \"audioFormat\": \"m4a\",
    \"model\": \"whisper-large-v3-turbo\"
  }"

# Transcribe with OpenAI (premium)
curl -X POST https://us-central1-whispertype-1de9f.cloudfunctions.net/transcribeAudio \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FIREBASE_TOKEN" \
  -d "{
    \"audioBase64\": \"$(base64 -i audio.m4a)\",
    \"audioFormat\": \"m4a\",
    \"model\": \"gpt-4o-transcribe\"
  }"
```

---

## Changelog

### v3.0.0 (Current)
- Added Groq transcription endpoint with free tier
- Implemented credit-based billing system
- Added model tier multipliers (AUTO/STANDARD/PREMIUM)
- Google Play subscription verification
- Pro subscription with monthly credit reset
- Comprehensive subscription status endpoints

### v2.0.0
- Added Firebase Authentication requirement
- Firestore logging for usage tracking
- Anonymous authentication supported

### v1.0.0
- Initial release
- OpenAI Whisper integration
- Basic error handling
