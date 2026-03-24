"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { listUnlimitedUsers, warnUser } from "@/lib/api/admin-api";
import { useSuspendUser } from "@/hooks/use-suspend-user";
import { UnlimitedUserItem } from "@/types/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  RefreshCw,
  ShieldBan,
  ShieldCheck,
  ExternalLink,
  Infinity,
  AlertTriangle,
} from "lucide-react";
import { format } from "date-fns";

export default function UnlimitedMonitorPage() {
  const router = useRouter();
  const [users, setUsers] = useState<UnlimitedUserItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await listUnlimitedUsers({ limit: 100 });
      setUsers(result.users);
      setTotal(result.total);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to fetch");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const handleSuspend = useSuspendUser(fetchUsers);

  const handleWarn = async (uid: string) => {
    const message = window.prompt(
      "Warning message (leave empty for default):"
    );
    if (message === null) return;
    try {
      const result = await warnUser({
        uid,
        message: message || undefined,
      });
      alert(`Warning #${result.warningCount} sent.`);
      fetchUsers();
    } catch (err) {
      alert(err instanceof Error ? err.message : "Failed to warn user");
    }
  };

  const estimateCost = (credits: number) => {
    const minutes = (credits * 6) / 60;
    const cost = minutes * 0.25;
    return `₹${cost.toFixed(0)}`;
  };

  const estimateMinutes = (credits: number) => {
    return Math.round((credits * 6) / 60);
  };

  const getStatusBadge = (user: UnlimitedUserItem) => {
    if (user.suspended) {
      return <Badge variant="destructive">Suspended</Badge>;
    }
    if (user.warningCount >= 2) {
      return (
        <Badge variant="destructive">
          {user.warningCount} Warnings
        </Badge>
      );
    }
    if (user.warningCount === 1) {
      return (
        <Badge className="bg-amber-500 hover:bg-amber-600">
          1 Warning
        </Badge>
      );
    }
    if (user.proCreditsUsed > 10000) {
      return <Badge variant="destructive">High Usage</Badge>;
    }
    if (user.proCreditsUsed > 5000) {
      return (
        <Badge className="bg-amber-500 hover:bg-amber-600">Watch</Badge>
      );
    }
    return <Badge variant="outline">Normal</Badge>;
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Infinity className="h-6 w-6" />
            Unlimited Plan Monitor
          </h1>
          <p className="text-muted-foreground">
            {total} unlimited user{total !== 1 ? "s" : ""} — sorted by
            usage (highest first)
          </p>
        </div>
        <Button variant="outline" size="icon" onClick={fetchUsers}>
          <RefreshCw className="h-4 w-4" />
        </Button>
      </div>

      {error && (
        <div className="rounded-md bg-destructive/10 p-4 text-sm text-destructive">
          {error}
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Usage This Billing Cycle</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-3">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : users.length === 0 ? (
            <p className="text-muted-foreground text-center py-8">
              No unlimited plan users
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>User</TableHead>
                  <TableHead className="text-right">Credits Used</TableHead>
                  <TableHead className="text-right">Est. Minutes</TableHead>
                  <TableHead className="text-right">Est. Cost</TableHead>
                  <TableHead>Period Ends</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {users.map((user) => (
                  <TableRow
                    key={user.uid}
                    className={user.suspended ? "opacity-60" : undefined}
                  >
                    <TableCell>
                      <div>
                        <p className="font-medium">
                          {user.displayName || "Anonymous"}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {user.email || user.uid.slice(0, 12) + "..."}
                        </p>
                      </div>
                    </TableCell>
                    <TableCell className="text-right font-mono font-medium">
                      {user.proCreditsUsed.toLocaleString()}
                    </TableCell>
                    <TableCell className="text-right font-mono">
                      {estimateMinutes(user.proCreditsUsed).toLocaleString()}
                    </TableCell>
                    <TableCell className="text-right font-mono">
                      {estimateCost(user.proCreditsUsed)}
                    </TableCell>
                    <TableCell>
                      {user.currentPeriodEnd
                        ? format(new Date(user.currentPeriodEnd), "MMM d")
                        : "—"}
                    </TableCell>
                    <TableCell>{getStatusBadge(user)}</TableCell>
                    <TableCell className="text-right">
                      <div className="flex gap-1 justify-end">
                        <Button
                          variant="ghost"
                          size="icon"
                          title="View details"
                          onClick={() =>
                            router.push(`/users/detail?uid=${user.uid}`)
                          }
                        >
                          <ExternalLink className="h-4 w-4" />
                        </Button>
                        {!user.suspended && (
                          <Button
                            variant="ghost"
                            size="icon"
                            title={`Warn (${user.warningCount} so far)`}
                            onClick={() => handleWarn(user.uid)}
                          >
                            <AlertTriangle className="h-4 w-4 text-amber-500" />
                          </Button>
                        )}
                        {user.suspended ? (
                          <Button
                            variant="ghost"
                            size="icon"
                            title="Unsuspend"
                            onClick={() => handleSuspend(user.uid, false)}
                          >
                            <ShieldCheck className="h-4 w-4 text-green-600" />
                          </Button>
                        ) : (
                          <Button
                            variant="ghost"
                            size="icon"
                            title="Suspend & cancel subscription"
                            onClick={() => handleSuspend(user.uid, true)}
                          >
                            <ShieldBan className="h-4 w-4 text-destructive" />
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
