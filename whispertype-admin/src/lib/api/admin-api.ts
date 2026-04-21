import { auth, API_BASE_URL } from "@/lib/firebase/config";
import {
  ListUsersResponse,
  GetUserDetailsResponse,
  AdjustCreditsRequest,
  AdjustCreditsResponse,
  UpdateUserPlanRequest,
  UpdateUserPlanResponse,
  GetAnalyticsResponse,
  SetAdminClaimRequest,
  SetAdminClaimResponse,
  ListUnlimitedUsersResponse,
  DeleteAnonymousUsersResponse,
  DeleteUserResponse,
} from "@/types/api";
import { AnalyticsPeriod } from "@/types/analytics";

async function getAuthHeader(): Promise<string> {
  const user = auth?.currentUser;
  if (!user) {
    throw new Error("Not authenticated");
  }
  const token = await user.getIdToken();
  return `Bearer ${token}`;
}

async function apiRequest<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const authHeader = await getAuthHeader();

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      Authorization: authHeader,
      ...options.headers,
    },
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.error || `Request failed: ${response.status}`);
  }

  return response.json();
}

export async function listUsers(params: {
  limit?: number;
  pageToken?: string;
  search?: string;
  plan?: "free" | "starter" | "pro" | "unlimited" | "all";
}): Promise<ListUsersResponse> {
  const queryParams = new URLSearchParams();
  if (params.limit) queryParams.set("limit", params.limit.toString());
  if (params.pageToken) queryParams.set("pageToken", params.pageToken);
  if (params.search) queryParams.set("search", params.search);
  if (params.plan && params.plan !== "all") queryParams.set("plan", params.plan);

  const query = queryParams.toString();
  return apiRequest<ListUsersResponse>(
    `/adminListUsers${query ? `?${query}` : ""}`
  );
}

export async function getUserDetails(uid: string): Promise<GetUserDetailsResponse> {
  return apiRequest<GetUserDetailsResponse>(`/adminGetUserDetails?uid=${uid}`);
}

export async function adjustCredits(
  data: AdjustCreditsRequest
): Promise<AdjustCreditsResponse> {
  return apiRequest<AdjustCreditsResponse>("/adminAdjustCredits", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateUserPlan(
  data: UpdateUserPlanRequest
): Promise<UpdateUserPlanResponse> {
  return apiRequest<UpdateUserPlanResponse>("/adminUpdateUserPlan", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function getAnalytics(
  period: AnalyticsPeriod = "30d"
): Promise<GetAnalyticsResponse> {
  return apiRequest<GetAnalyticsResponse>(`/adminGetAnalytics?period=${period}`);
}

export interface ModelSpeedStat {
  modelResolved: string;
  provider: string;
  count: number;
  successRate: number;
  durationMs: { p50: number; p95: number } | null;
  rtf: { p50: number; p95: number } | null;
  hourBuckets: Record<number, number>;
}

export interface GetModelSpeedStatsResponse {
  period: AnalyticsPeriod;
  totalSamples: number;
  models: ModelSpeedStat[];
}

export async function getModelSpeedStats(
  period: AnalyticsPeriod = "30d"
): Promise<GetModelSpeedStatsResponse> {
  return apiRequest<GetModelSpeedStatsResponse>(
    `/adminGetModelSpeedStats?period=${period}`
  );
}

export async function warnUser(
  data: { uid: string; message?: string }
): Promise<{ success: boolean; warningCount: number }> {
  return apiRequest("/adminWarnUser", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function suspendUser(
  data: { uid: string; suspended: boolean; reason?: string }
): Promise<{ success: boolean; suspended: boolean }> {
  return apiRequest("/adminSuspendUser", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function listUnlimitedUsers(params?: {
  limit?: number;
}): Promise<ListUnlimitedUsersResponse> {
  const queryParams = new URLSearchParams();
  if (params?.limit) queryParams.set("limit", params.limit.toString());
  const query = queryParams.toString();
  return apiRequest(`/adminListUnlimitedUsers${query ? `?${query}` : ""}`);
}

export async function setAdminClaim(
  data: SetAdminClaimRequest
): Promise<SetAdminClaimResponse> {
  return apiRequest<SetAdminClaimResponse>("/adminSetAdminClaim", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function deleteUser(
  uid: string,
  reason: string
): Promise<DeleteUserResponse> {
  return apiRequest<DeleteUserResponse>("/adminDeleteUser", {
    method: "POST",
    body: JSON.stringify({ uid, reason }),
  });
}

export async function deleteAnonymousUsers(
  reason: string
): Promise<DeleteAnonymousUsersResponse> {
  return apiRequest<DeleteAnonymousUsersResponse>("/adminDeleteAnonymousUsers", {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}
