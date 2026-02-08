import { UserListItem, UserDetail, UsageLogEntry, UserStats } from "./user";
import { AnalyticsData } from "./analytics";

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
}

export interface ListUsersResponse {
  users: UserListItem[];
  nextPageToken: string | null;
  totalCount: number;
}

export interface GetUserDetailsResponse {
  user: UserDetail;
  recentUsage: UsageLogEntry[];
  stats: UserStats;
}

export interface AdjustCreditsRequest {
  uid: string;
  adjustment: number;
  reason: string;
}

export interface AdjustCreditsResponse {
  success: boolean;
  newCreditsUsed: number;
  creditsRemaining: number;
}

export interface UpdateUserPlanRequest {
  uid: string;
  plan: "free" | "pro";
  resetCredits?: boolean;
  extendTrialDays?: number;
  proCreditsLimit?: number;
}

export interface UpdateUserPlanResponse {
  success: boolean;
  user: UserDetail;
}

export interface GetAnalyticsResponse extends AnalyticsData {}

export interface SetAdminClaimRequest {
  uid: string;
  isAdmin: boolean;
}

export interface SetAdminClaimResponse {
  success: boolean;
}
