# Wozcribe Pricing Strategy

## Free Tier

| Feature | Value |
|---------|-------|
| One-time credits | **500 credits** (~50 min STANDARD) |
| AUTO model | **Unlimited** (always free) |
| Trial period | 3 months |

---

## Pro Plans - India

| Plan | Price | Credits/month | After Google Cut (15%) | Margin |
|------|-------|---------------|------------------------|--------|
| **Starter** | ₹89/mo | 2,000 | ₹75.65 | ~60% |
| **Pro** | ₹249/mo | 6,000 | ₹211.65 | ~57% |
| **Unlimited** | ₹799/mo | 15,000 | ₹679.15 | ~67% |

---

## Pro Plans - International

| Plan | Price | Credits/month | After Google Cut (15%) | Margin |
|------|-------|---------------|------------------------|--------|
| **Starter** | $1.99/mo | 2,000 | $1.69 | ~79% |
| **Pro** | $6.99/mo | 6,000 | $5.94 | ~82% |
| **Unlimited** | $16.99/mo | 15,000 | $14.44 | ~81% |

---

## Quick Reference

| | Free | Starter | Pro | Unlimited |
|---|------|---------|-----|-----------|
| **India** | ₹0 | ₹89 | ₹249 | ₹799 |
| **International** | $0 | $1.99 | $6.99 | $16.99 |
| **Credits** | 500 (once) | 2,000/mo | 6,000/mo | 15,000/mo |

---

## Credit Usage by Model

| Model | Multiplier | Starter (2K) | Pro (6K) | Unlimited (15K) |
|-------|------------|--------------|----------|-----------------|
| AUTO (Groq Turbo) | 0x | Unlimited | Unlimited | Unlimited |
| STANDARD (Groq Large) | 1x | ~200 min | ~600 min | ~1500 min |
| PREMIUM (OpenAI) | 2x | ~100 min | ~300 min | ~750 min |

---

## API Costs (Reference)

| Model | Cost/minute | Provider |
|-------|-------------|----------|
| AUTO | ~₹0.05 | Groq whisper-large-v3-turbo |
| STANDARD | ~₹0.15 | Groq whisper-large-v3 |
| PREMIUM | ~₹0.25 | OpenAI gpt-4o-mini-transcribe |

---

## Google Play Product IDs

Google Play handles regional pricing (INR/USD) automatically via country-specific price overrides.

| Plan | Product ID |
|------|------------|
| Wozcribe Starter | `wozcribe_starter_monthly` |
| Wozcribe Pro | `wozcribe_pro_monthly` |
| Wozcribe Unlimited | `wozcribe_unlimited_monthly` |

---

## Notes

- Google Play takes 15% cut (for developers earning < $1M/year)
- 1 credit = 6 seconds of audio
- AUTO tier is always free (0 credits charged)
- Margins calculated assuming 100% STANDARD usage (actual margins will be higher due to AUTO usage)
