"use client";

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from "recharts";
import { CreditsChartDataPoint } from "@/types/analytics";

interface CreditsUsageChartProps {
  data: CreditsChartDataPoint[];
  loading: boolean;
}

export function CreditsUsageChart({ data, loading }: CreditsUsageChartProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Credits Usage</CardTitle>
        <CardDescription>Credits consumed by plan type</CardDescription>
      </CardHeader>
      <CardContent>
        {loading ? (
          <Skeleton className="h-[300px] w-full" />
        ) : (
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={data}>
              <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
              <XAxis
                dataKey="date"
                className="text-xs"
                tickLine={false}
                axisLine={false}
              />
              <YAxis
                className="text-xs"
                tickLine={false}
                axisLine={false}
                tickFormatter={(value) => value.toLocaleString()}
              />
              <Tooltip
                content={({ active, payload, label }) => {
                  if (active && payload && payload.length) {
                    return (
                      <div className="rounded-lg border bg-background p-2 shadow-sm">
                        <p className="text-sm font-medium">{label}</p>
                        {payload.map((entry) => (
                          <p
                            key={entry.name}
                            className="text-sm text-muted-foreground"
                          >
                            {entry.name}: {Number(entry.value).toLocaleString()}
                          </p>
                        ))}
                      </div>
                    );
                  }
                  return null;
                }}
              />
              <Legend />
              <Bar dataKey="free" fill="hsl(var(--muted-foreground))" name="Free" />
              <Bar dataKey="pro" fill="hsl(var(--primary))" name="Pro" />
            </BarChart>
          </ResponsiveContainer>
        )}
      </CardContent>
    </Card>
  );
}
