"use client";

import { useState, useEffect, useCallback } from "react";
import { AnalyticsData, AnalyticsPeriod } from "@/types/analytics";
import { getAnalytics } from "@/lib/api/admin-api";

export function useAnalytics(initialPeriod: AnalyticsPeriod = "30d") {
  const [data, setData] = useState<AnalyticsData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [period, setPeriod] = useState<AnalyticsPeriod>(initialPeriod);

  const fetchAnalytics = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const result = await getAnalytics(period);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to fetch analytics");
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [period]);

  useEffect(() => {
    fetchAnalytics();
  }, [fetchAnalytics]);

  return {
    data,
    loading,
    error,
    period,
    setPeriod,
    refresh: fetchAnalytics,
  };
}
