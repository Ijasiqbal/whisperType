# Testing the Transcription Endpoint

## Prerequisites

1. **OpenAI API Key**: Get one from https://platform.openai.com/api-keys
2. **Set up environment**: Create a `.env` file in the `functions` directory (see `.env.example`)

## Option 1: Test Locally with Firebase Emulator (Recommended)

### Step 1: Set up your API key

Create a `.env` file in `functions/`:
```bash
OPENAI_API_KEY=sk-proj-your-actual-openai-api-key-here
```

### Step 2: Start the emulator

```bash
cd functions
npm run serve
```

The emulator will start and show you the local URLs for your functions, typically:
- `http://localhost:5001/<project-id>/us-central1/transcribeAudio`

### Step 3: Test with cURL

```bash
# First, you need a base64-encoded audio file
# Example: Create a test audio file and encode it
# For this example, let's assume you have an audio file called test.webm

# Encode the audio file to base64 (macOS/Linux)
base64 -i test.webm -o test.txt

# Or use this one-liner to test
curl -X POST http://localhost:5001/<project-id>/us-central1/transcribeAudio \
  -H "Content-Type: application/json" \
  -d "{\"audioBase64\": \"$(base64 -i test.webm)\"}"
```

### Step 4: Test with Postman

1. Open Postman
2. Create a new POST request to `http://localhost:5001/<project-id>/us-central1/transcribeAudio`
3. Set Headers: `Content-Type: application/json`
4. Set Body (raw JSON):
```json
{
  "audioBase64": "<your-base64-encoded-audio-here>"
}
```
5. Send the request
6. You should receive:
```json
{
  "text": "Your transcribed text here"
}
```

## Option 2: Deploy to Firebase and Test

### Step 1: Set the API key for production

```bash
firebase functions:secrets:set OPENAI_API_KEY
# Enter your API key when prompted
```

**Note:** You'll need to update `index.ts` to use secrets. For now, the code reads from `process.env.OPENAI_API_KEY`, so you can alternatively use:

```bash
firebase functions:config:set openai.api_key="your-key-here"
```

And update the code:
```typescript
const apiKey = process.env.OPENAI_API_KEY || functions.config().openai.api_key;
```

### Step 2: Deploy

```bash
npm run deploy
```

### Step 3: Test the deployed function

The deployment will output the URL. It will look like:
```
https://<region>-<project-id>.cloudfunctions.net/transcribeAudio
```

Test with cURL:
```bash
curl -X POST https://<region>-<project-id>.cloudfunctions.net/transcribeAudio \
  -H "Content-Type: application/json" \
  -d "{\"audioBase64\": \"$(base64 -i test.webm)\"}"
```

## Testing from Android App

In your Android app, make a POST request like this:

```kotlin
// Example Kotlin code
val url = "http://localhost:5001/<project-id>/us-central1/transcribeAudio" // or production URL
val audioBytes = // ... your recorded audio bytes
val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

val json = JSONObject()
json.put("audioBase64", audioBase64)

val request = Request.Builder()
    .url(url)
    .post(json.toString().toRequestBody("application/json".toMediaType()))
    .build()

client.newCall(request).enqueue(object : Callback {
    override fun onResponse(call: Call, response: Response) {
        val responseBody = response.body?.string()
        val jsonResponse = JSONObject(responseBody)
        val transcription = jsonResponse.getString("text")
        // Use the transcription
    }
    
    override fun onFailure(call: Call, e: IOException) {
        // Handle error
    }
})
```

## Expected Responses

### Success (200)
```json
{
  "text": "This is the transcribed text from your audio"
}
```

### Missing audioBase64 (400)
```json
{
  "error": "Missing or invalid audioBase64 field in request body"
}
```

### Invalid base64 (400)
```json
{
  "error": "Invalid base64 audio data"
}
```

### Server error (500)
```json
{
  "error": "Failed to transcribe audio"
}
```

### Method not allowed (405)
```
Method Not Allowed
```

## Troubleshooting

### "Server configuration error"
- Make sure `OPENAI_API_KEY` is set in your `.env` file (local) or Firebase config (production)

### "Failed to transcribe audio"
- Check the Firebase logs: `firebase functions:log`
- Verify your audio format is supported by Whisper (mp3, mp4, mpeg, mpga, m4a, wav, webm)
- Check your OpenAI account has available credits

### CORS errors from Android
- You may need to add CORS headers to the function. Let me know if you encounter this!
