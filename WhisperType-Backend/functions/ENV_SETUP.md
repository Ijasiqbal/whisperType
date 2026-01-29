# Environment Setup Guide

Complete guide for setting up environment variables and secrets for the WhisperType backend.

## Required Secrets

| Secret | Required | Description |
|--------|----------|-------------|
| `OPENAI_API_KEY` | Yes | OpenAI API key for Whisper transcription |
| `GROQ_API_KEY` | Yes | Groq API key for fast transcription |
| `GOOGLE_PLAY_KEY` | Yes | Google Play service account JSON for subscription verification |

## Local Development Setup

### Option 1: Using .env File (Recommended for Development)

Create a `.env` file in the `functions` directory:

```bash
# functions/.env

# OpenAI API Key (required for /transcribeAudio endpoint)
OPENAI_API_KEY=sk-proj-your-openai-api-key-here

# Groq API Key (required for /transcribeAudioGroq endpoint)
GROQ_API_KEY=gsk_your-groq-api-key-here

# Google Play Service Account JSON (required for subscription verification)
# Use a single-line JSON string
GOOGLE_PLAY_KEY={"type":"service_account","project_id":"...","private_key":"..."}
```

**Note:** The `.env` file is gitignored and will not be committed.

### Option 2: Shell Environment Variables

```bash
export OPENAI_API_KEY="sk-proj-your-openai-api-key-here"
export GROQ_API_KEY="gsk_your-groq-api-key-here"
export GOOGLE_PLAY_KEY='{"type":"service_account",...}'
```

### Running with Local Secrets

```bash
cd functions
npm run serve
```

The Firebase emulator will automatically load variables from `.env`.

## Production Deployment

### Setting Firebase Secrets

Use Firebase Functions secrets for production deployment:

```bash
# Set OpenAI API key
firebase functions:secrets:set OPENAI_API_KEY
# Enter your API key when prompted

# Set Groq API key
firebase functions:secrets:set GROQ_API_KEY
# Enter your API key when prompted

# Set Google Play service account
firebase functions:secrets:set GOOGLE_PLAY_KEY
# Paste your service account JSON when prompted
```

### Verifying Secrets

```bash
# List all secrets
firebase functions:secrets:list

# Access a secret value (requires appropriate permissions)
firebase functions:secrets:access OPENAI_API_KEY
```

### Updating Secrets

```bash
# Update an existing secret
firebase functions:secrets:set OPENAI_API_KEY
# Enter the new value when prompted

# Deploy to apply changes
firebase deploy --only functions
```

### Deleting Secrets

```bash
firebase functions:secrets:destroy OPENAI_API_KEY
```

## Obtaining API Keys

### OpenAI API Key

1. Go to [OpenAI Platform](https://platform.openai.com/api-keys)
2. Sign in or create an account
3. Click "Create new secret key"
4. Copy the key (you won't be able to see it again)
5. Add billing information to enable API access

**Note:** OpenAI charges per audio minute transcribed. Monitor usage in the [OpenAI Dashboard](https://platform.openai.com/usage).

### Groq API Key

1. Go to [Groq Console](https://console.groq.com/keys)
2. Sign in or create an account
3. Create a new API key
4. Copy the key

**Note:** Groq has generous free tier limits. Check [Groq pricing](https://groq.com/pricing/) for details.

### Google Play Service Account

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project (or create one linked to your Play Console)
3. Navigate to **IAM & Admin** > **Service Accounts**
4. Click **Create Service Account**
5. Give it a name like `play-subscription-verifier`
6. Grant the role: `Pub/Sub Admin` (or create a custom role with subscription read access)
7. Click **Done**, then click on the service account
8. Go to **Keys** > **Add Key** > **Create new key** > **JSON**
9. Download the JSON file

#### Link to Play Console

1. Go to [Google Play Console](https://play.google.com/console/)
2. Navigate to **Setup** > **API access**
3. Link your Google Cloud project
4. Grant access to your service account
5. Give it **View app information and download bulk reports** and **Manage orders and subscriptions** permissions

## Firebase Remote Config

The following parameters can be configured via Firebase Remote Config:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `free_tier_credits` | 1000 | Credits for free trial users |
| `pro_tier_credits` | 10000 | Monthly credits for Pro users |
| `seconds_per_credit` | 6 | Seconds of audio per credit |
| `trial_duration_months` | 3 | Free trial duration in months |
| `pro_product_id` | whispertype_pro_monthly | Google Play subscription product ID |
| `pro_plan_enabled` | true | Whether Pro subscriptions are enabled |

### Setting Remote Config Values

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Navigate to **Remote Config**
4. Add parameters with the names above
5. Publish changes

## Troubleshooting

### "Server configuration error"

- Check that all required secrets are set
- Verify secret values are correct (no extra whitespace)
- Redeploy functions after setting secrets

### "Invalid API key"

- Verify the API key is correctly copied
- Check that billing is enabled (OpenAI)
- Ensure the key hasn't been revoked

### "Google Play verification failed"

- Verify service account JSON is valid
- Check that the service account has proper permissions in Play Console
- Ensure the package name matches your app

### Emulator Issues

If secrets aren't loading in the emulator:

```bash
# Clear emulator data and restart
rm -rf .firebase/
npm run serve
```

## Security Best Practices

1. **Never commit secrets** to version control
2. **Rotate keys regularly** - at least every 90 days
3. **Use minimal permissions** for service accounts
4. **Monitor API usage** for unexpected activity
5. **Enable alerts** for billing thresholds

## Environment Variable Reference

```typescript
// Accessing secrets in Cloud Functions
const openaiKey = process.env.OPENAI_API_KEY;
const groqKey = process.env.GROQ_API_KEY;
const googlePlayKey = process.env.GOOGLE_PLAY_KEY;

// Parsing Google Play service account
const serviceAccount = JSON.parse(googlePlayKey);
```
