export interface AnalyticsSummary {
  totalUsers: number;
  activeUsersThisPeriod: number;
  freeUsers: number;
  proUsers: number;
  totalCreditsUsed: number;
  totalTranscriptions: number;
  successRate: number;
}

export interface ChartDataPoint {
  date: string;
  count: number;
}

export interface CreditsChartDataPoint {
  date: string;
  free: number;
  pro: number;
}

export interface TierDataPoint {
  tier: string;
  count: number;
}

export interface PlanDataPoint {
  plan: string;
  count: number;
}

export interface AnalyticsCharts {
  userGrowth: ChartDataPoint[];
  creditsUsage: CreditsChartDataPoint[];
  transcriptionsByTier: TierDataPoint[];
  planDistribution: PlanDataPoint[];
}

export interface AnalyticsData {
  summary: AnalyticsSummary;
  charts: AnalyticsCharts;
}

export type AnalyticsPeriod = "7d" | "30d" | "90d" | "all";
