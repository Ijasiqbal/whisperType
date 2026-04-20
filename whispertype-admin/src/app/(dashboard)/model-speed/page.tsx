"use client";

import { useCallback, useEffect, useState } from "react";
import {
  getModelSpeedStats,
  GetModelSpeedStatsResponse,
  ModelSpeedStat,
} from "@/lib/api/admin-api";
import { AnalyticsPeriod } from "@/types/analytics";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { RefreshCw } from "lucide-react";

function formatMs(ms: number | null | undefined) {
  if (ms == null) return "—";
  if (ms >= 1000) return `${(ms / 1000).toFixed(2)}s`;
  return `${Math.round(ms)}ms`;
}

function formatRtf(rtf: number | null | undefined) {
  if (rtf == null) return "—";
  return `${rtf.toFixed(2)}×`;
}

function formatPercent(n: number) {
  return `${(n * 100).toFixed(1)}%`;
}

export default function ModelSpeedPage() {
  const [period, setPeriod] = useState<AnalyticsPeriod>("30d");
  const [data, setData] = useState<GetModelSpeedStatsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchStats = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await getModelSpeedStats(period);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to fetch stats");
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [period]);

  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  const models: ModelSpeedStat[] = data?.models ?? [];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Model Speed</h1>
          <p className="text-muted-foreground">
            Per-model latency and real-time factor across transcription tiers.
            Admin-only: actual model and provider names are shown here and
            nowhere else.
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
          <Button variant="outline" size="icon" onClick={fetchStats}>
            <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
          </Button>
        </div>
      </div>

      {error && (
        <div className="rounded-md bg-destructive/10 p-4 text-sm text-destructive">
          {error}
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Per-model performance</CardTitle>
          <CardDescription>
            {data ?
              `${data.totalSamples.toLocaleString()} samples in ${period}` :
              "Loading…"}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Model</TableHead>
                <TableHead>Provider</TableHead>
                <TableHead className="text-right">Samples</TableHead>
                <TableHead className="text-right">Success</TableHead>
                <TableHead className="text-right">p50 latency</TableHead>
                <TableHead className="text-right">p95 latency</TableHead>
                <TableHead className="text-right">p50 RTF</TableHead>
                <TableHead className="text-right">p95 RTF</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {models.length === 0 && !loading && (
                <TableRow>
                  <TableCell colSpan={8} className="text-center text-muted-foreground">
                    No samples yet.
                  </TableCell>
                </TableRow>
              )}
              {models.map((m) => (
                <TableRow key={`${m.provider}-${m.modelResolved}`}>
                  <TableCell className="font-mono text-xs">
                    {m.modelResolved}
                  </TableCell>
                  <TableCell>{m.provider}</TableCell>
                  <TableCell className="text-right">
                    {m.count.toLocaleString()}
                  </TableCell>
                  <TableCell className="text-right">
                    {formatPercent(m.successRate)}
                  </TableCell>
                  <TableCell className="text-right">
                    {formatMs(m.durationMs?.p50)}
                  </TableCell>
                  <TableCell className="text-right">
                    {formatMs(m.durationMs?.p95)}
                  </TableCell>
                  <TableCell className="text-right">
                    {formatRtf(m.rtf?.p50)}
                  </TableCell>
                  <TableCell className="text-right">
                    {formatRtf(m.rtf?.p95)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Hour-of-day breakdown (UTC)</CardTitle>
          <CardDescription>
            Sample counts per hour for each model. Useful for spotting
            slowdowns at specific times.
          </CardDescription>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Model</TableHead>
                {Array.from({ length: 24 }, (_, h) => (
                  <TableHead key={h} className="text-right">
                    {h}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {models.map((m) => (
                <TableRow key={`hrs-${m.provider}-${m.modelResolved}`}>
                  <TableCell className="font-mono text-xs whitespace-nowrap">
                    {m.modelResolved}
                  </TableCell>
                  {Array.from({ length: 24 }, (_, h) => (
                    <TableCell key={h} className="text-right tabular-nums">
                      {m.hourBuckets[h] ?? 0}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}
