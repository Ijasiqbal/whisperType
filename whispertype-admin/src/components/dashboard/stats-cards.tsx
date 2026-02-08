"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Users, Crown, Zap, CheckCircle } from "lucide-react";
import { AnalyticsSummary } from "@/types/analytics";

interface StatsCardsProps {
  data: AnalyticsSummary | null;
  loading: boolean;
}

export function StatsCards({ data, loading }: StatsCardsProps) {
  const stats = [
    {
      title: "Total Users",
      value: data?.totalUsers ?? 0,
      icon: Users,
      description: `${data?.activeUsersThisPeriod ?? 0} active this period`,
    },
    {
      title: "Pro Subscribers",
      value: data?.proUsers ?? 0,
      icon: Crown,
      description: `${data?.freeUsers ?? 0} free users`,
    },
    {
      title: "Credits Used",
      value: data?.totalCreditsUsed ?? 0,
      icon: Zap,
      description: "Total credits consumed",
    },
    {
      title: "Success Rate",
      value: `${((data?.successRate ?? 0) * 100).toFixed(1)}%`,
      icon: CheckCircle,
      description: `${data?.totalTranscriptions ?? 0} transcriptions`,
    },
  ];

  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
      {stats.map((stat) => (
        <Card key={stat.title}>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{stat.title}</CardTitle>
            <stat.icon className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            {loading ? (
              <>
                <Skeleton className="h-8 w-24 mb-1" />
                <Skeleton className="h-4 w-32" />
              </>
            ) : (
              <>
                <div className="text-2xl font-bold">
                  {typeof stat.value === "number"
                    ? stat.value.toLocaleString()
                    : stat.value}
                </div>
                <p className="text-xs text-muted-foreground">
                  {stat.description}
                </p>
              </>
            )}
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
