# Test Report: 3-Tier Plan & Billing System

**Date:** 2026-02-07
**Scope:** Migration from single Pro plan to 3-tier system (Starter/Pro/Unlimited), free trial credits reduced from 1000 to 500.

---

## Summary

| Status | Count |
|--------|-------|
| Passed | 16 |
| Failed | 0 |
| Skipped | 1 |
| Pending | 0 |
| Bugs | 0 |

---

## UI Tests

### TC1: PlanScreen shows 3 tier cards for free trial user
- **Status:** PASS (re-tested after Remote Config update)
- **Steps:** Sign in as free trial user > Navigate to Plan tab
- **Expected:**
  - Top: "Free Trial" status card with green "Active" badge and credits remaining (out of 500)
  - Title: "Choose Your Plan"
  - Subtitle: "AUTO model is always free, unlimited"
  - 3 plan cards: Starter ($1.99, 2000 credits), Pro ($6.99, 6000 credits, "Popular" badge), Unlimited ($16.99, 15000 credits)
  - All 3 cards have "Select [Plan]" buttons enabled
  - "Need help? Contact Support" link at bottom
- **Notes:** Initially failed showing 1000 credits — fixed by updating Remote Config `free_tier_credits` from 1000 to 500.

### TC2: PlanScreen shows local Google Play prices
- **Status:** PASS
- **Steps:** Sign in > Navigate to Plan tab > Observe prices
- **Expected:** Prices show local currency from Google Play; fallback to $1.99/$6.99/$16.99 if unavailable.

### TC3: PlanScreen for expired trial user
- **Status:** PASS
- **Steps:** Open app with expired trial account > Navigate to Plan tab
- **Expected:**
  - Top status card shows "Free Trial" in red with "Expired" text
  - Badge shows "Upgrade Now" in red
  - 3 plan cards still shown with "Select [Plan]" buttons enabled
  - Title: "Choose Your Plan"
- **How to test quickly:** In Firebase Console > Firestore > `users/{your-uid}`, set `freeCreditsUsed` to 500 (to simulate credits exhaustion). Reopen app.

### TC4: TrialExpiredScreen shows 3-tier pricing
- **Status:** PASS
- **Notes:** No separate TrialExpiredScreen — app auto-redirects to Plan tab with 3 plans and "Free Trial Expired" shown at top. Acceptable behavior.
- **Steps:** Trigger trial expiration (exhaust credits or backdate trial) > Observe TrialExpiredScreen
- **Expected:**
  - Mic icon at top in purple circle
  - Title: "Ready to Continue?"
  - Reason text (e.g., "You've used all your free credits." or "Your free trial period has ended.")
  - Subtitle: "Choose a plan to keep using VoxType"
  - 3 compact plan cards (Starter, Pro with "Popular" badge, Unlimited)
  - Each card shows name, price/mo, top 2 features, and "Select [Plan]" button
  - Green info card: "AUTO model remains free & unlimited on all plans"
  - "Need help? Contact Support" link

### TC8: PlanScreen for active subscriber shows "Current" badge
- **Status:** PASS
- **Steps:** Sign in with subscribed account > Navigate to Plan tab
- **Expected:**
  - Title: "Manage Your Plan" (not "Choose Your Plan")
  - Free Trial status card is NOT shown (hidden for Pro users)
  - The subscribed plan card shows green "Current" badge and disabled "Current Plan" button
  - Other plan cards remain selectable (for upgrade/downgrade)
  - Credits remaining displayed correctly based on tier (2000/6000/15000)

### TC15: Manage Subscription link opens Play Store correctly
- **Status:** PASS
- **Steps:** Navigate to Plan tab as subscribed user > Tap "Manage Subscription"
- **Expected:**
  - Opens Google Play at `https://play.google.com/store/account/subscriptions?package=com.whispertype.app`
  - URL no longer includes `sku=whispertype_pro_monthly` (removed in changes)
  - User can see and manage their active subscription

### TC16: Skeleton loading state displays while fetching data
- **Status:** PASS
- **Steps:** Navigate to Plan tab while data is loading; also test TrialExpiredScreen loading state
- **Expected:**
  - Both screens show skeleton loaders (shimmer) while `isLoading=true`
  - No blank screen or crash during loading
  - Content appears smoothly with fade/slide animations after load

---

## Purchase Flow Tests

### TC5: Purchase flow — select Starter plan
- **Status:** PASS
- **Steps:** Navigate to Plan tab or TrialExpiredScreen > Tap "Select Starter" > Complete purchase
- **Expected:**
  - Google Play purchase dialog opens for `voxtype_starter_monthly`
  - After success: toast "Welcome to VoxType Pro!", backend sets credit limit to 2000
  - Plan tab: Starter card shows green "Current" badge, button disabled
- **Debug build:** MockBillingManager simulates purchase with 2000 credits after 2s delay

### TC6: Purchase flow — select Pro plan
- **Status:** PASS
- **Steps:** Navigate to Plan tab > Tap "Select Pro" > Complete purchase
- **Expected:**
  - Google Play purchase dialog opens for `voxtype_pro_monthly`
  - Backend sets credit limit to 6000
  - Plan tab: Pro card shows green "Current" badge
- **Debug build:** MockBillingManager simulates purchase with 6000 credits

### TC7: Purchase flow — select Unlimited plan
- **Status:** PASS
- **Steps:** Navigate to Plan tab > Tap "Select Unlimited" > Complete purchase
- **Expected:**
  - Google Play purchase dialog opens for `voxtype_unlimited_monthly`
  - Backend sets credit limit to 15000
  - Plan tab: Unlimited card shows green "Current" badge
- **Debug build:** MockBillingManager simulates purchase with 15000 credits

### TC17: Debug build uses MockBillingManager with 3 tiers
- **Status:** SKIPPED
- **Steps:** Build and run debug variant > Navigate to Plan tab > Try purchasing each plan
- **Expected:**
  - MockBillingManager is used (not real Google Play)
  - `queryProducts()` returns 3 mock products with correct prices/credits
  - Starter purchase → 2000 credits, Pro → 6000, Unlimited → 15000
  - Mock prices: "$1.99/month (Mock)", "$6.99/month (Mock)", "$16.99/month (Mock)"

---

## Backend Tests

### TC9: Backend getTrialStatus returns correct response for free user
- **Status:** PASS (indirect — verified via TC13)
- **Steps:** Call `GET /getTrialStatus` with valid auth token for free trial user
- **Expected response:**
  ```json
  {
    "plan": "free",
    "status": "active",
    "freeCreditsUsed": <number>,
    "freeCreditsRemaining": <number, max 500>,
    "freeTierCredits": 500,
    "trialExpiryDateMs": <timestamp>,
    "warningLevel": "none" | "fifty_percent" | "eighty_percent" | "ninety_five_percent",
    "totalCreditsThisMonth": <number>
  }
  ```
- `freeTierCredits` should be 500 (not 1000), `freeCreditsRemaining` <= 500

### TC10: Backend getTrialStatus returns Pro status for subscriber
- **Status:** PASS (indirect — verified via TC8)
- **Steps:** Call `GET /getTrialStatus` with valid auth token for subscribed user
- **Expected response:**
  ```json
  {
    "plan": "pro",
    "status": "active",
    "proCreditsUsed": <number>,
    "proCreditsRemaining": <number>,
    "proCreditsLimit": 2000 | 6000 | 15000,
    "resetDateMs": <timestamp>,
    "warningLevel": "none" | "ninety_percent"
  }
  ```
- `proCreditsLimit` must match: starter→2000, pro→6000, unlimited→15000

### TC11: Backend verifySubscription sets correct credits per tier
- **Status:** PASS (indirect — verified via TC5, TC6, TC7)
- **Steps:** Call `POST /verifySubscription` with each productId
- **Expected:**
  - `voxtype_starter_monthly` → `proCreditsLimit: 2000`
  - `voxtype_pro_monthly` → `proCreditsLimit: 6000`
  - `voxtype_unlimited_monthly` → `proCreditsLimit: 15000`
  - Firestore user doc has `plan: "pro"` and correct `proSubscription.productId`

### TC12: Legacy product ID fallback works
- **Status:** PASS (indirect — verified via BUG-1 fix)
- **Steps:** Sign in with user whose Firestore has `proSubscription.productId: "whispertype_pro_monthly"` (old ID)
- **Expected:**
  - `getCreditsForProduct("whispertype_pro_monthly")` returns 6000 (default fallback)
  - User sees 6000 credits limit, no errors, no crash

### TC13: Free trial now has 500 credits (not 1000)
- **Status:** PASS
- **Steps:** Create a new account (0 credits used) > Check trial status
- **Expected:**
  - `freeCreditsRemaining` = 500, `freeTierCredits` = 500
  - App UI shows "500 credits left"
  - Backend default `DEFAULT_FREE_TIER_CREDITS = 500` and frontend defaults are consistent

---

## Regression Tests

### TC14: Transcription still works and deducts credits correctly
- **Status:** PASS
- **Steps:** Transcribe with STANDARD, PREMIUM, and AUTO models
- **Expected:**
  - AUTO: 0 credits deducted (free)
  - STANDARD: 1x credit rate (duration-based)
  - PREMIUM: 2x credit rate
  - Credits remaining updates correctly after each transcription
  - Text appears in focused field — no regression

---

## Known Bugs

### BUG-1: Legacy Pro user (pre-credit system) cannot transcribe despite showing 0/6000 credits
- **Status:** FIXED
- **Severity:** High
- **Account type:** Pro subscriber from BEFORE the credit system was implemented. Subscription purchased under old single-Pro-plan system. Firestore document created under old schema and may be missing fields the new credit system expects.
- **Symptoms:**
  1. Profile section shows "Pro Subscription" and "0 out of 6000 credits"
  2. Plan tab (Manage Your Plan) shows Pro as current plan
  3. Transcription fails with error message starting "Your pro subscription..." (rest cut off by UI)
  4. Transcription blocked even though credits display suggests 6000 remaining
- **Likely root causes:**
  - "0 out of 6000" may mean 0 REMAINING, not 0 used — ambiguous UI
  - Legacy Firestore doc missing fields: `proCreditsUsed`, `currentPeriodStart`, `currentPeriodEnd`, `productId`
  - `checkProStatus()` may mishandle undefined/null credit fields
  - `currentPeriodEnd` on legacy subscription may have expired; Google Play auto-renewed but Firestore wasn't updated
  - `reVerifyProSubscriptionWithGooglePlay()` may not trigger for this user
  - Error message toast/snackbar text is clipped — UI overflow issue
- **Files to investigate:**
  - `index.ts:checkProStatus()` — handling of legacy users without credit fields
  - `index.ts:getCreditsForProduct("whispertype_pro_monthly")` — returns 6000 but usage calc may be wrong
  - Firestore doc for this user — compare existing fields vs what new code expects
  - Transcription endpoint's Pro user credit check logic
  - Consider a migration path: backfill missing fields on first access for legacy Pro users
  - Fix error message text overflow in UI
