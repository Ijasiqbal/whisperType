# Environment Variables for Local Development

## Required Variables

### OPENAI_API_KEY
Your OpenAI API key for accessing the Whisper API.

## Setup Instructions

### For Local Testing with Firebase Emulator

Create a `.env` file in the `functions` directory:

```bash
OPENAI_API_KEY=sk-proj-your-actual-openai-api-key-here
```

**Note:** The `.env` file is already gitignored, so your API key won't be committed to version control.

### For Production Deployment

Set the environment variable using Firebase CLI:

```bash
firebase functions:config:set openai.api_key="sk-proj-your-actual-openai-api-key-here"
```

Then update your code to read from config in production:
```typescript
const apiKey = process.env.OPENAI_API_KEY || functions.config().openai.api_key;
```

**Or** use Firebase Functions v2 secrets (recommended for production):

```bash
firebase functions:secrets:set OPENAI_API_KEY
```

This will prompt you to enter your API key securely.

## Getting an OpenAI API Key

1. Go to https://platform.openai.com/api-keys
2. Sign in or create an account
3. Click "Create new secret key"
4. Copy the key (you won't be able to see it again)
5. Add it to your environment as described above
