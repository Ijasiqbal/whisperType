# WhisperType API Documentation

## Base URL
```
https://us-central1-whispertype-1de9f.cloudfunctions.net
```

---

## Endpoints

### 1. Health Check

Check if the service is running.

**Endpoint:** `GET /health`

**URL:**
```
https://health-35dvue2fxa-uc.a.run.app
```

**Response:**
```
OK
```

**Status Codes:**
- `200 OK` - Service is running

**Example:**
```bash
curl https://health-35dvue2fxa-uc.a.run.app
```

---

### 2. Transcribe Audio

Transcribe audio using OpenAI Whisper API.

**Endpoint:** `POST /transcribeAudio`

**URL:**
```
https://us-central1-whispertype-1de9f.cloudfunctions.net/transcribeAudio
```

#### Request

**Method:** `POST`

**Headers:**
```
Content-Type: application/json
```

**Body:**
```json
{
  "audioBase64": "string (required)",
  "audioFormat": "string (optional, default: 'm4a')"
}
```

**Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| audioBase64 | string | Yes | Base64-encoded audio file. Supported formats: mp3, mp4, mpeg, mpga, m4a, wav, webm |
| audioFormat | string | No | Audio file format: `wav`, `m4a`, `mp3`, `webm`, `mp4`, `mpeg`, `mpga`. Defaults to `m4a` for backwards compatibility. Must match the actual audio content. |

#### Response

**Success Response (200 OK):**
```json
{
  "text": "This is the transcribed text from your audio"
}
```

**Error Responses:**

**400 Bad Request** - Missing or invalid parameters:
```json
{
  "error": "Missing or invalid audioBase64 field in request body"
}
```

**400 Bad Request** - Invalid base64 data:
```json
{
  "error": "Invalid base64 audio data"
}
```

**405 Method Not Allowed** - Wrong HTTP method:
```
Method Not Allowed
```

**500 Internal Server Error** - Server configuration error:
```json
{
  "error": "Server configuration error"
}
```

**500 Internal Server Error** - Transcription failed:
```json
{
  "error": "Failed to transcribe audio"
}
```

**500 Internal Server Error** - Unexpected error:
```json
{
  "error": "Internal server error"
}
```

#### Status Codes

| Code | Description |
|------|-------------|
| 200 | Success - Transcription completed |
| 400 | Bad Request - Invalid or missing parameters |
| 405 | Method Not Allowed - Request method is not POST |
| 500 | Internal Server Error - Server or API error |

---

## Code Examples

### cURL

```bash
# Simple test (replace <BASE64_AUDIO> with your actual base64-encoded audio)
curl -X POST https://us-central1-whispertype-1de9f.cloudfunctions.net/transcribeAudio \
  -H "Content-Type: application/json" \
  -d '{"audioBase64": "<BASE64_AUDIO>"}'

# With audio file encoding inline (macOS/Linux)
curl -X POST https://us-central1-whispertype-1de9f.cloudfunctions.net/transcribeAudio \
  -H "Content-Type: application/json" \
  -d "{\"audioBase64\": \"$(base64 -i audio.webm)\"}"
```

### Android (Kotlin)

```kotlin
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.util.Base64

// Encode your audio bytes to Base64
val audioBytes: ByteArray = // ... your recorded audio bytes
val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

// Create JSON request body
val json = JSONObject()
json.put("audioBase64", audioBase64)

// Create HTTP request
val client = OkHttpClient()
val url = "https://us-central1-whispertype-1de9f.cloudfunctions.net/transcribeAudio"

val request = Request.Builder()
    .url(url)
    .post(json.toString().toRequestBody("application/json".toMediaType()))
    .build()

// Execute request
client.newCall(request).enqueue(object : Callback {
    override fun onResponse(call: Call, response: Response) {
        if (response.isSuccessful) {
            val responseBody = response.body?.string()
            val jsonResponse = JSONObject(responseBody ?: "{}")
            val transcription = jsonResponse.getString("text")
            
            // Use the transcription
            println("Transcription: $transcription")
        } else {
            val errorBody = response.body?.string()
            println("Error: $errorBody")
        }
    }
    
    override fun onFailure(call: Call, e: IOException) {
        println("Request failed: ${e.message}")
    }
})
```

### JavaScript (Node.js)

```javascript
const fetch = require('node-fetch');
const fs = require('fs');

async function transcribeAudio(audioFilePath) {
  // Read and encode audio file
  const audioBuffer = fs.readFileSync(audioFilePath);
  const audioBase64 = audioBuffer.toString('base64');
  
  // Make API request
  const response = await fetch(
    'https://us-central1-whispertype-1de9f.cloudfunctions.net/transcribeAudio',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ audioBase64 }),
    }
  );
  
  const data = await response.json();
  
  if (response.ok) {
    console.log('Transcription:', data.text);
    return data.text;
  } else {
    console.error('Error:', data.error);
    throw new Error(data.error);
  }
}

// Usage
transcribeAudio('./audio.webm')
  .then(text => console.log('Result:', text))
  .catch(err => console.error('Failed:', err));
```

### Python

```python
import requests
import base64
import json

def transcribe_audio(audio_file_path):
    # Read and encode audio file
    with open(audio_file_path, 'rb') as audio_file:
        audio_base64 = base64.b64encode(audio_file.read()).decode('utf-8')
    
    # Make API request
    url = 'https://us-central1-whispertype-1de9f.cloudfunctions.net/transcribeAudio'
    headers = {'Content-Type': 'application/json'}
    data = {'audioBase64': audio_base64}
    
    response = requests.post(url, headers=headers, json=data)
    
    if response.status_code == 200:
        result = response.json()
        print('Transcription:', result['text'])
        return result['text']
    else:
        error = response.json()
        print('Error:', error.get('error', 'Unknown error'))
        raise Exception(error.get('error', 'Unknown error'))

# Usage
try:
    text = transcribe_audio('audio.webm')
    print('Result:', text)
except Exception as e:
    print('Failed:', e)
```

---

## Audio Format Requirements

**Supported Formats:**
- mp3
- mp4
- mpeg
- mpga
- m4a
- wav
- webm

**Recommended Format:** WebM (good compression, widely supported)

**File Size Limit:** 25 MB (OpenAI Whisper API limit)

**Base64 Encoding:**
- Ensure no line breaks in the base64 string
- Use standard base64 encoding (not URL-safe variant)

---

## Authentication

⚠️ **Phase 1 - No Authentication**

This endpoint currently has **no authentication** and is for personal development/testing only. Anyone with the URL can use it.

**Future phases will include:**
- Firebase Authentication
- API key validation
- Rate limiting
- Usage quotas per user

---

## Rate Limits

Currently no rate limits enforced. OpenAI Whisper API has its own rate limits based on your OpenAI account tier.

---

## Error Handling Best Practices

1. **Always check HTTP status code** before parsing response
2. **Handle network timeouts** - transcription can take several seconds
3. **Validate audio before sending** - check file size and format
4. **Implement retry logic** for 500 errors with exponential backoff
5. **Log errors with request IDs** for debugging

**Example Error Handling (Kotlin):**
```kotlin
override fun onResponse(call: Call, response: Response) {
    when (response.code) {
        200 -> {
            // Success
            val text = JSONObject(response.body?.string()).getString("text")
            handleSuccess(text)
        }
        400 -> {
            // Bad request - check your input
            val error = JSONObject(response.body?.string()).getString("error")
            handleBadRequest(error)
        }
        405 -> {
            // Wrong method - should be POST
            handleMethodError()
        }
        500 -> {
            // Server error - retry with backoff
            handleServerError()
        }
        else -> {
            // Unexpected error
            handleUnexpectedError(response.code)
        }
    }
}
```

---

## Testing

### Test with Postman

1. **Create a new POST request**
   - URL: `https://us-central1-whispertype-1de9f.cloudfunctions.net/transcribeAudio`

2. **Set Headers**
   - `Content-Type: application/json`

3. **Set Body** (raw JSON)
   ```json
   {
     "audioBase64": "YOUR_BASE64_AUDIO_HERE"
   }
   ```

4. **Send** and check response

### Generate Test Audio Base64

**macOS/Linux:**
```bash
# Create a test audio file or use existing
base64 -i test_audio.webm
```

**Windows:**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("test_audio.webm"))
```

---

## Troubleshooting

### "Missing or invalid audioBase64 field"
- Ensure request body contains `audioBase64` field
- Check that it's a string, not an object
- Verify JSON is properly formatted

### "Invalid base64 audio data"
- Base64 string may be corrupted
- Ensure no extra characters or line breaks
- Re-encode the audio file

### "Server configuration error"
- API key issue (contact admin)
- Should not occur in production

### "Failed to transcribe audio"
- Audio format may not be supported
- File may be corrupted
- OpenAI API may be down
- Check Firebase logs for details

### Request timeout
- Large audio files take longer to process
- Increase timeout to 30-60 seconds
- Consider splitting long audio files

---

## Support

**Firebase Console:**
https://console.firebase.google.com/project/whispertype-1de9f/overview

**View Logs:**
```bash
firebase functions:log
```

**Monitor Function:**
```bash
firebase functions:list
```

---

## Changelog

### v1.0.0 - Phase 1 (Current)
- Initial release
- `/transcribeAudio` endpoint
- OpenAI Whisper integration
- Basic error handling
- No authentication (development only)
