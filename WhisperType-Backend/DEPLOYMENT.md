# Deployment Guide

Complete guide for deploying the WhisperType backend to Firebase.

## Table of Contents

- [Prerequisites](#prerequisites)
- [First-Time Setup](#first-time-setup)
- [Deployment Process](#deployment-process)
- [Multi-Region Deployment](#multi-region-deployment)
- [Environment Configuration](#environment-configuration)
- [Rollback Procedures](#rollback-procedures)
- [Monitoring](#monitoring)
- [CI/CD Integration](#cicd-integration)

## Prerequisites

### Required Tools

```bash
# Node.js 24+
node --version  # Should be v24.x.x

# Firebase CLI
npm install -g firebase-tools
firebase --version  # Should be 13.x.x or higher

# Login to Firebase
firebase login
```

### Required Access

- Firebase project owner or editor role
- Access to set Firebase secrets
- Google Cloud project access (for viewing logs)

## First-Time Setup

### 1. Clone and Install

```bash
cd WhisperType-Backend/functions
npm install
```

### 2. Configure Firebase Project

```bash
# List available projects
firebase projects:list

# Select project
firebase use whispertype-1de9f

# Or set default project
firebase use --add
```

### 3. Set Required Secrets

```bash
# OpenAI API key (for premium transcription)
firebase functions:secrets:set OPENAI_API_KEY

# Groq API key (for fast/free transcription)
firebase functions:secrets:set GROQ_API_KEY

# Google Play service account (for subscription verification)
firebase functions:secrets:set GOOGLE_PLAY_KEY
```

### 4. Configure Remote Config

Set the following parameters in Firebase Console > Remote Config:

| Parameter | Value | Description |
|-----------|-------|-------------|
| `free_tier_credits` | 1000 | Free trial credits |
| `pro_tier_credits` | 10000 | Pro monthly credits |
| `seconds_per_credit` | 6 | Audio seconds per credit |
| `trial_duration_months` | 3 | Trial duration |
| `pro_product_id` | whispertype_pro_monthly | Google Play product ID |
| `pro_plan_enabled` | true | Enable Pro subscriptions |

## Deployment Process

### Standard Deployment

```bash
cd functions

# 1. Install dependencies
npm install

# 2. Run linter
npm run lint

# 3. Build TypeScript
npm run build

# 4. Deploy to Firebase
npm run deploy
```

### Quick Deploy (Skip Lint)

```bash
cd functions
npm run build && firebase deploy --only functions
```

### Deploy Specific Function

```bash
# Deploy single function
firebase deploy --only functions:transcribeAudioGroq

# Deploy multiple functions
firebase deploy --only functions:transcribeAudio,functions:transcribeAudioGroq
```

### Deploy with Hosting

```bash
# Deploy everything (functions + hosting)
firebase deploy

# Deploy only hosting
firebase deploy --only hosting
```

## Multi-Region Deployment

The backend is configured to deploy to multiple regions for low latency:

### Configured Regions

| Region | Location | Primary Users |
|--------|----------|---------------|
| `us-central1` | Iowa, USA | Americas |
| `asia-south1` | Mumbai, India | South Asia |
| `europe-west1` | Belgium | Europe, Africa |

### Region Configuration

In `index.ts`:

```typescript
const functionConfig = {
  region: ["us-central1", "asia-south1", "europe-west1"],
  memory: "512MiB" as const,
  maxInstances: 10,
};
```

### Accessing Region-Specific Endpoints

```
# US Central
https://us-central1-whispertype-1de9f.cloudfunctions.net/<function>

# Asia South
https://asia-south1-whispertype-1de9f.cloudfunctions.net/<function>

# Europe West
https://europe-west1-whispertype-1de9f.cloudfunctions.net/<function>
```

## Environment Configuration

### Development vs Production

| Environment | Base URL | Firestore | Auth |
|-------------|----------|-----------|------|
| Local | `localhost:5001` | Emulator | Emulator |
| Production | `cloudfunctions.net` | Production | Production |

### Local Development

```bash
# Start emulators
npm run serve

# With specific emulators
firebase emulators:start --only functions,firestore,auth
```

### Switching Environments

```bash
# Use production
firebase use whispertype-1de9f

# Use staging (if configured)
firebase use whispertype-staging

# Check current project
firebase use
```

## Rollback Procedures

### View Deployment History

```bash
# List recent deployments
firebase functions:list

# View function versions in Cloud Console
# https://console.cloud.google.com/functions
```

### Rollback to Previous Version

#### Option 1: Redeploy from Git

```bash
# Checkout previous version
git checkout <previous-commit-hash>

# Redeploy
cd functions
npm install
npm run deploy
```

#### Option 2: Cloud Console Rollback

1. Go to [Cloud Functions Console](https://console.cloud.google.com/functions)
2. Select the function
3. Click on "Revisions" tab
4. Select previous revision
5. Click "Deploy"

### Emergency Disable

To quickly disable a malfunctioning function:

```bash
# Delete specific function
firebase functions:delete transcribeAudioGroq

# Confirm deletion when prompted
```

## Monitoring

### View Logs

```bash
# All functions
firebase functions:log

# Specific function
firebase functions:log --only transcribeAudioGroq

# Stream logs in real-time
firebase functions:log --follow

# Filter by time
firebase functions:log --since 1h
firebase functions:log --since 30m
```

### Cloud Console Monitoring

- **Logs**: [Cloud Logging](https://console.cloud.google.com/logs)
- **Metrics**: [Cloud Monitoring](https://console.cloud.google.com/monitoring)
- **Errors**: [Error Reporting](https://console.cloud.google.com/errors)

### Key Metrics to Monitor

| Metric | Alert Threshold | Description |
|--------|-----------------|-------------|
| Error rate | > 5% | Function invocation errors |
| Latency p95 | > 10s | 95th percentile response time |
| Instance count | > 8 | Approaching max instances |
| Memory usage | > 450 MiB | Near memory limit |

### Setting Up Alerts

1. Go to [Cloud Monitoring](https://console.cloud.google.com/monitoring)
2. Create alerting policy
3. Select metric (e.g., `cloudfunctions.googleapis.com/function/execution_count`)
4. Set condition and notification channel

## CI/CD Integration

### GitHub Actions Example

```yaml
# .github/workflows/deploy.yml
name: Deploy to Firebase

on:
  push:
    branches: [main]
    paths:
      - 'WhisperType-Backend/**'

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '24'
          cache: 'npm'
          cache-dependency-path: WhisperType-Backend/functions/package-lock.json

      - name: Install dependencies
        working-directory: WhisperType-Backend/functions
        run: npm ci

      - name: Lint
        working-directory: WhisperType-Backend/functions
        run: npm run lint

      - name: Build
        working-directory: WhisperType-Backend/functions
        run: npm run build

      - name: Deploy to Firebase
        uses: w9jds/firebase-action@master
        with:
          args: deploy --only functions
        env:
          FIREBASE_TOKEN: ${{ secrets.FIREBASE_TOKEN }}
          PROJECT_PATH: WhisperType-Backend
```

### Getting Firebase CI Token

```bash
firebase login:ci
# Copy the token and add it as FIREBASE_TOKEN secret in GitHub
```

### Pre-Deployment Checklist

- [ ] All tests pass locally
- [ ] Linter reports no errors
- [ ] TypeScript builds successfully
- [ ] Environment secrets are set
- [ ] Remote Config is up to date
- [ ] No sensitive data in code
- [ ] Changelog updated

## Deployment Troubleshooting

### Common Issues

#### "Functions did not deploy properly"

```bash
# Check for build errors
npm run build

# View detailed logs
firebase deploy --only functions --debug
```

#### "Secret not found"

```bash
# Verify secret exists
firebase functions:secrets:list

# Re-set the secret
firebase functions:secrets:set OPENAI_API_KEY
```

#### "Quota exceeded"

- Check [Cloud Quotas](https://console.cloud.google.com/iam-admin/quotas)
- Request quota increase if needed
- Or reduce `maxInstances` in function config

#### "Memory limit exceeded"

```typescript
// Increase memory in function config
const functionConfig = {
  memory: "1GiB" as const,  // Increase from 512MiB
  // ...
};
```

### Deployment Validation

After deployment, verify each endpoint:

```bash
# Health check
curl https://us-central1-whispertype-1de9f.cloudfunctions.net/health

# Check function list
firebase functions:list
```

## Security Checklist

Before deploying to production:

- [ ] API keys stored as secrets (not in code)
- [ ] No debug/verbose logging of sensitive data
- [ ] Authentication required on all endpoints (except health)
- [ ] CORS configured appropriately
- [ ] Input validation on all endpoints
- [ ] Rate limiting considered

## Cost Management

### Estimated Costs

| Resource | Free Tier | Cost After |
|----------|-----------|------------|
| Cloud Functions | 2M invocations/month | $0.40/million |
| Firestore | 50K reads/day | $0.06/100K reads |
| Cloud Storage | 5GB | $0.026/GB/month |

### Cost Optimization

1. **Use appropriate memory**: Don't over-provision
2. **Set `maxInstances`**: Prevent runaway scaling
3. **Enable caching**: Remote Config cache reduces reads
4. **Monitor usage**: Set up billing alerts

### Setting Billing Alerts

1. Go to [Billing](https://console.cloud.google.com/billing)
2. Select your billing account
3. Click "Budgets & alerts"
4. Create budget with notification thresholds
