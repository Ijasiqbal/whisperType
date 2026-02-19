"use client";

import { useAnalytics } from "@/hooks/use-analytics";
import { StatsCards } from "@/components/dashboard/stats-cards";
import { UserGrowthChart } from "@/components/dashboard/user-growth-chart";
import { CreditsUsageChart } from "@/components/dashboard/credits-usage-chart";
import { PlanDistributionChart } from "@/components/dashboard/plan-distribution-chart";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { RefreshCw } from "lucide-react";
import { AnalyticsPeriod } from "@/types/analytics";

export default function DashboardPage() {
  const { data, loading, error, period, setPeriod, refresh } = useAnalytics();

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
          <p className="text-muted-foreground">
            Overview of your Wozcribe application
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Select
            value={period}
            onValueChange={(value) => setPeriod(value as AnalyticsPeriod)}
          >
            <SelectTrigger className="w-32">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="7d">Last 7 days</SelectItem>
              <SelectItem value="30d">Last 30 days</SelectItem>
              <SelectItem value="90d">Last 90 days</SelectItem>
              <SelectItem value="all">All time</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" size="icon" onClick={refresh}>
            <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
          </Button>
        </div>
      </div>

      {error && (
        <div className="rounded-md bg-destructive/10 p-4 text-sm text-destructive">
          {error}
        </div>
      )}

      <StatsCards data={data?.summary ?? null} loading={loading} />

      <div className="grid gap-6 md:grid-cols-2">
        <UserGrowthChart data={data?.charts.userGrowth ?? []} loading={loading} />
        <CreditsUsageChart
          data={data?.charts.creditsUsage ?? []}
          loading={loading}
        />
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <PlanDistributionChart
          data={data?.charts.planDistribution ?? []}
          loading={loading}
        />
      </div>
    </div>
  );
}
