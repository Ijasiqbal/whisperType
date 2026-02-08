export interface UserListItem {
  uid: string;
  email: string | null;
  displayName: string | null;
  photoURL: string | null;
  plan: "free" | "pro";
  freeCreditsUsed: number;
  createdAt: number;
  lastSignInTime: string | null;
  disabled: boolean;
}

export interface ProSubscription {
  status: "active" | "cancelled" | "expired" | "pending";
  productId: string;
  proCreditsUsed: number;
  proCreditsLimit: number;
  currentPeriodStart: number;
  currentPeriodEnd: number;
}

export interface UserDetail {
  uid: string;
  email: string | null;
  displayName: string | null;
  photoURL: string | null;
  emailVerified: boolean;
  disabled: boolean;
  createdAt: number;
  lastSignInTime: string | null;
  plan: "free" | "pro";
  freeCreditsUsed: number;
  freeTierCredits: number;
  trialExpiryDate: number;
  freeTrialStart: number;
  proSubscription?: ProSubscription;
}

export interface UsageLogEntry {
  id: string;
  creditsUsed: number;
  timestamp: number;
  source: "free" | "pro" | "overage" | "recharge";
  modelTier: "AUTO" | "STANDARD" | "PREMIUM";
}

export interface UserStats {
  totalTranscriptions: number;
  successfulTranscriptions: number;
  totalCreditsUsed: number;
  creditsThisMonth: number;
}
