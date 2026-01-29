# Testing Guide

Complete guide for testing the WhisperType backend API.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Local Testing](#local-testing)
- [Testing Endpoints](#testing-endpoints)
- [Authentication](#authentication)
- [Test Scenarios](#test-scenarios)
- [Android Integration](#android-integration)
- [Troubleshooting](#troubleshooting)

## Prerequisites

1. **Node.js 24+** installed
2. **Firebase CLI** installed: `npm install -g firebase-tools`
3. **API Keys** configured (see [ENV_SETUP.md](./functions/ENV_SETUP.md))
4. **Test audio files** in supported formats (m4a, mp3, wav, webm)

## Local Testing

### Start Firebase Emulator

```bash
cd functions
npm install
npm run serve
```

The emulator starts on:
- Functions: `http://localhost:5001/whispertype-1de9f/us-central1/<function>`
- Firestore: `http://localhost:8080`
- Auth: `http://localhost:9099`

### Local Base URL

```
http://localhost:5001/whispertype-1de9f/us-central1
```

### Production Base URL

```
https://us-central1-whispertype-1de9f.cloudfunctions.net
```

## Testing Endpoints

### 1. Health Check

**No authentication required.**

```bash
# Local
curl http://localhost:5001/whispertype-1de9f/us-central1/health

# Production
curl https://us-central1-whispertype-1de9f.cloudfunctions.net/health
```

**Expected Response:**
```
OK
```

### 2. Get Subscription Status

```bash
curl -X GET "$BASE_URL/getSubscriptionStatus" \
  -H "Authorization: Bearer $FIREBASE_TOKEN"
```

**Expected Response (Free User):**
```json
{
  "plan": "free",
  "isActive": true,
  "freeCreditsUsed": 0,
  "freeCreditsRemaining": 1000,
  "freeTierCredits": 1000,
  "trialExpiryDateMs": 1735689600000,
  "totalCreditsThisMonth": 0,
  "status": "active",
  "warningLevel": "none",
  "proPlanEnabled": true,
  "proCreditsLimit": 10000
}
```

### 3. Get Trial Status

```bash
curl -X GET "$BASE_URL/getTrialStatus" \
  -H "Authorization: Bearer $FIREBASE_TOKEN"
```

**Expected Response:**
```json
{
  "status": "active",
  "freeCreditsUsed": 0,
  "freeCreditsRemaining": 1000,
  "freeTierCredits": 1000,
  "trialExpiryDateMs": 1735689600000,
  "warningLevel": "none",
  "totalCreditsThisMonth": 0
}
```

### 4. Transcribe Audio (Groq - Free Tier)

```bash
# Encode audio file
AUDIO_BASE64=$(base64 -i test_audio.m4a)

# Send request
curl -X POST "$BASE_URL/transcribeAudioGroq" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FIREBASE_TOKEN" \
  -d "{
    \"audioBase64\": \"$AUDIO_BASE64\",
    \"audioFormat\": \"m4a\",
    \"model\": \"whisper-large-v3-turbo\"
  }"
```

**Expected Response:**
```json
{
  "text": "Your transcribed text here",
  "creditsUsed": 0,
  "totalCreditsThisMonth": 0,
  "plan": "free",
  "subscriptionStatus": {
    "status": "active",
    "creditsRemaining": 1000,
    "creditsLimit": 1000,
    "warningLevel": "none"
  },
  "trialStatus": {
    "status": "active",
    "freeCreditsUsed": 0,
    "freeCreditsRemaining": 1000,
    "freeTierCredits": 1000,
    "warningLevel": "none"
  }
}
```

### 5. Transcribe Audio (OpenAI - Premium)

```bash
AUDIO_BASE64=$(base64 -i test_audio.m4a)

curl -X POST "$BASE_URL/transcribeAudio" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FIREBASE_TOKEN" \
  -d "{
    \"audioBase64\": \"$AUDIO_BASE64\",
    \"audioFormat\": \"m4a\",
    \"model\": \"gpt-4o-mini-transcribe\"
  }"
```

### 6. Warmup Request

```bash
# GET warmup
curl -X GET "$BASE_URL/transcribeAudioGroq" \
  -H "Authorization: Bearer $FIREBASE_TOKEN"

# POST warmup
curl -X POST "$BASE_URL/transcribeAudioGroq" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FIREBASE_TOKEN" \
  -d '{"warmup": true}'
```

**Expected Response:**
```json
{
  "status": "warm"
}
```

### 7. Verify Subscription

```bash
curl -X POST "$BASE_URL/verifySubscription" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FIREBASE_TOKEN" \
  -d "{
    \"purchaseToken\": \"your-google-play-purchase-token\",
    \"productId\": \"whispertype_pro_monthly\"
  }"
```

### 8. Delete Account

```bash
curl -X POST "$BASE_URL/deleteAccount" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FIREBASE_TOKEN"
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Account and all associated data have been deleted"
}
```

## Authentication

### Getting a Firebase ID Token

#### Option 1: Firebase Auth REST API

```bash
# Sign in with email/password
curl -X POST "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$FIREBASE_API_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"test@example.com\",
    \"password\": \"password123\",
    \"returnSecureToken\": true
  }"
```

Response includes `idToken` which you use as `$FIREBASE_TOKEN`.

#### Option 2: Anonymous Auth

```bash
curl -X POST "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$FIREBASE_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"returnSecureToken": true}'
```

#### Option 3: From Android App

```kotlin
val user = FirebaseAuth.getInstance().currentUser
user?.getIdToken(false)?.addOnSuccessListener { result ->
    val token = result.token
    Log.d("Token", token ?: "null")
}
```

### Token Expiration

Firebase ID tokens expire after 1 hour. Refresh if you see 401 errors.

## Test Scenarios

### Scenario 1: New User Flow

1. Create anonymous user (or sign up)
2. Check subscription status - should show "free" plan
3. Transcribe with Groq turbo (free tier)
4. Verify credits used = 0
5. Transcribe with OpenAI (premium tier)
6. Verify credits are deducted

### Scenario 2: Credit Exhaustion

1. Get trial status
2. Make transcriptions until `freeCreditsRemaining` approaches 0
3. Verify warning levels change (50%, 80%, 95%)
4. After exhaustion, verify 403 response

### Scenario 3: Pro Subscription

1. Sign up for Pro via Google Play
2. Call verifySubscription with purchase token
3. Verify plan changes to "pro"
4. Verify `proCreditsLimit` is 10000
5. Make transcriptions
6. Verify credits deduct from pro balance

### Scenario 4: Subscription Renewal

1. With active Pro subscription
2. Wait for billing period to end
3. Make transcription request
4. System should auto-reverify with Google Play
5. Credits should reset if subscription renewed

### Scenario 5: Error Handling

Test each error condition:

```bash
# Missing audio
curl -X POST "$BASE_URL/transcribeAudioGroq" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FIREBASE_TOKEN" \
  -d '{}'
# Expected: 400 - Missing audioBase64

# Invalid format
curl -X POST "$BASE_URL/transcribeAudioGroq" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $FIREBASE_TOKEN" \
  -d '{"audioBase64": "not-valid-base64", "audioFormat": "xyz"}'
# Expected: 400 - Unsupported format

# No auth token
curl -X POST "$BASE_URL/transcribeAudioGroq" \
  -H "Content-Type: application/json" \
  -d '{"audioBase64": "..."}'
# Expected: 401 - Unauthorized
```

## Postman Testing

### Setup Collection

1. Create new collection "WhisperType API"
2. Set collection variable `baseUrl` to your base URL
3. Set collection variable `token` (update when expired)

### Pre-request Script

```javascript
// Auto-refresh token if needed
const tokenExpiry = pm.collectionVariables.get("tokenExpiry");
if (!tokenExpiry || Date.now() > parseInt(tokenExpiry)) {
    // Token expired - you'll need to get a new one
    console.log("Token may be expired");
}
```

### Test Script

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Response has text field", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('text');
});

pm.test("Credits used is returned", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('creditsUsed');
});
```

## Android Integration

### Test Helper Class

```kotlin
class ApiTester(private val context: Context) {
    private val baseUrl = "https://us-central1-whispertype-1de9f.cloudfunctions.net"
    private val client = OkHttpClient()

    suspend fun testHealth(): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/health")
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            response.isSuccessful && response.body?.string() == "OK"
        }
    }

    suspend fun testTranscription(audioBytes: ByteArray): String? {
        val token = getAuthToken() ?: return null
        val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val json = JSONObject().apply {
            put("audioBase64", audioBase64)
            put("audioFormat", "m4a")
            put("model", "whisper-large-v3-turbo")
        }

        val request = Request.Builder()
            .url("$baseUrl/transcribeAudioGroq")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $token")
            .build()

        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                JSONObject(response.body?.string() ?: "{}").optString("text")
            } else {
                null
            }
        }
    }

    private suspend fun getAuthToken(): String? {
        return FirebaseAuth.getInstance().currentUser
            ?.getIdToken(false)
            ?.await()
            ?.token
    }
}
```

### Instrumentation Test

```kotlin
@RunWith(AndroidJUnit4::class)
class ApiIntegrationTest {

    @Test
    fun testHealthEndpoint() = runBlocking {
        val tester = ApiTester(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue("Health check should pass", tester.testHealth())
    }

    @Test
    fun testTranscription() = runBlocking {
        val tester = ApiTester(InstrumentationRegistry.getInstrumentation().targetContext)

        // Load test audio from assets
        val audioBytes = loadTestAudio()

        val result = tester.testTranscription(audioBytes)
        assertNotNull("Transcription should return text", result)
        assertTrue("Transcription should not be empty", result!!.isNotEmpty())
    }
}
```

## Load Testing

### Using Artillery

```yaml
# artillery-config.yml
config:
  target: "https://us-central1-whispertype-1de9f.cloudfunctions.net"
  phases:
    - duration: 60
      arrivalRate: 5
  defaults:
    headers:
      Authorization: "Bearer YOUR_TOKEN"
      Content-Type: "application/json"

scenarios:
  - name: "Health check"
    requests:
      - get:
          url: "/health"
          expect:
            - statusCode: 200

  - name: "Get subscription status"
    requests:
      - get:
          url: "/getSubscriptionStatus"
          expect:
            - statusCode: 200
            - hasProperty: "plan"
```

Run with:
```bash
artillery run artillery-config.yml
```

## Troubleshooting

### 401 Unauthorized

- Token expired (refresh after 1 hour)
- Token malformed (check "Bearer " prefix)
- Wrong Firebase project

### 403 Forbidden

Check the error code in response:
- `TRIAL_EXPIRED`: Trial period ended
- `TRIAL_CREDITS_EXHAUSTED`: Free credits used up
- `PRO_EXPIRED`: Subscription expired
- `PRO_LIMIT_REACHED`: Monthly limit reached

### 400 Bad Request

- Check `audioBase64` is valid base64
- Check `audioFormat` is supported
- Ensure JSON is properly formatted

### 500 Internal Server Error

- Check Firebase function logs: `firebase functions:log`
- Verify API keys are set correctly
- Check external API status (OpenAI, Groq)

### CORS Errors (Web Testing)

The API is designed for mobile apps. For web testing:
- Use Postman or cURL
- Or use a CORS proxy for development

### Emulator Issues

```bash
# Reset emulator state
firebase emulators:start --clear

# Check emulator logs
firebase emulators:start --debug
```

## Logging

### View Production Logs

```bash
# All functions
firebase functions:log

# Specific function
firebase functions:log --only transcribeAudioGroq

# Stream logs
firebase functions:log --follow

# With time range
firebase functions:log --since 1h
```

### Log Analysis

Look for patterns in errors:
```bash
firebase functions:log 2>&1 | grep -i error
firebase functions:log 2>&1 | grep -i "401\|403\|500"
```
