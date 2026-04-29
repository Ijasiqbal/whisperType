"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { getUserDetails, deleteUser } from "@/lib/api/admin-api";
import { useSuspendUser } from "@/hooks/use-suspend-user";
import { GetUserDetailsResponse } from "@/types/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { AdjustCreditsDialog } from "@/components/users/adjust-credits-dialog";
import { ChangePlanDialog } from "@/components/users/change-plan-dialog";
import {
  ArrowLeft,
  Calendar,
  Zap,
  Crown,
  RefreshCw,
  ShieldBan,
  ShieldCheck,
  Trash2,
  Smartphone,
  Monitor,
} from "lucide-react";
import { format, formatDistanceToNow } from "date-fns";
import { Suspense } from "react";

function UserDetailContent({ uid }: { uid: string | null }) {
  const router = useRouter();
  const [data, setData] = useState<GetUserDetailsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [adjustCreditsOpen, setAdjustCreditsOpen] = useState(false);
  const [changePlanOpen, setChangePlanOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleteReason, setDeleteReason] = useState("");
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const fetchUser = useCallback(async () => {
    if (!uid) return;
    setLoading(true);
    setError(null);

    try {
      const result = await getUserDetails(uid);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to fetch user");
    } finally {
      setLoading(false);
    }
  }, [uid]);

  const handleSuspend = useSuspendUser(fetchUser);

  const handleDelete = useCallback(async () => {
    if (!uid || !deleteReason.trim()) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await deleteUser(uid, deleteReason.trim());
      router.push("/users");
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : "Failed to delete user");
    } finally {
      setDeleting(false);
    }
  }, [uid, deleteReason, router]);

  useEffect(() => {
    fetchUser();
  }, [fetchUser]);

  const getInitials = (name: string | null, email: string | null) => {
    if (name) {
      return name
        .split(" ")
        .map((n) => n[0])
        .join("")
        .toUpperCase()
        .slice(0, 2);
    }
    if (email) {
      return email.slice(0, 2).toUpperCase();
    }
    return "??";
  };

  if (!uid) {
    return (
      <div className="space-y-6">
        <Button variant="ghost" onClick={() => router.back()}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back
        </Button>
        <div className="rounded-md bg-destructive/10 p-4 text-sm text-destructive">
          No user ID provided
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center gap-4">
          <Skeleton className="h-10 w-10" />
          <Skeleton className="h-8 w-48" />
        </div>
        <div className="grid gap-6 md:grid-cols-2">
          <Skeleton className="h-64" />
          <Skeleton className="h-64" />
        </div>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="space-y-6">
        <Button variant="ghost" onClick={() => router.back()}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back
        </Button>
        <div className="rounded-md bg-destructive/10 p-4 text-sm text-destructive">
          {error || "User not found"}
        </div>
      </div>
    );
  }

  const { user, recentUsage, stats } = data;
  const isPaidPlan = user.plan !== "free";
  const isUnlimitedPlan = user.plan === "unlimited";
  const creditsRemaining = isUnlimitedPlan
    ? Infinity
    : isPaidPlan
      ? (user.proSubscription?.proCreditsLimit ?? 10000) -
        (user.proSubscription?.proCreditsUsed ?? 0)
      : user.freeTierCredits - user.freeCreditsUsed;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="icon" onClick={() => router.back()}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div className="flex items-center gap-3">
            <Avatar className="h-12 w-12">
              <AvatarImage src={user.photoURL || undefined} />
              <AvatarFallback>
                {getInitials(user.displayName, user.email)}
              </AvatarFallback>
            </Avatar>
            <div>
              <h1 className="text-2xl font-bold">
                {user.displayName || "Anonymous User"}
              </h1>
              <p className="text-muted-foreground">{user.email || user.uid}</p>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" onClick={fetchUser}>
            <RefreshCw className="h-4 w-4" />
          </Button>
          <Button
            variant="destructive"
            size="sm"
            onClick={() => setDeleteDialogOpen(true)}
          >
            <Trash2 className="h-4 w-4 mr-1" />
            Delete User
          </Button>
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>User Information</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-muted-foreground">Status</span>
              {user.suspended ? (
                <Badge variant="destructive">Suspended</Badge>
              ) : (
                <Badge variant="outline">Active</Badge>
              )}
            </div>
            <div className="flex items-center justify-between">
              <span className="text-muted-foreground">Plan</span>
              <Badge variant={isPaidPlan ? "default" : "secondary"}>
                {isPaidPlan && <Crown className="h-3 w-3 mr-1" />}
                {user.plan}
              </Badge>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-muted-foreground">Email Verified</span>
              <span>{user.emailVerified ? "Yes" : "No"}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-muted-foreground">Created</span>
              <span className="flex items-center gap-1">
                <Calendar className="h-4 w-4" />
                {format(new Date(user.createdAt), "MMM d, yyyy")}
              </span>
            </div>
            {user.lastSignInTime && (
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Last Sign In</span>
                <span>{format(new Date(user.lastSignInTime), "MMM d, yyyy")}</span>
              </div>
            )}
            <div className="flex items-center justify-between">
              <span className="text-muted-foreground">UID</span>
              <code className="text-xs bg-muted px-2 py-1 rounded">
                {user.uid.slice(0, 16)}...
              </code>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Zap className="h-5 w-5" />
              Credits
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-muted-foreground">Credits Used</span>
              <span className="font-medium">
                {isPaidPlan
                  ? user.proSubscription?.proCreditsUsed?.toLocaleString() ?? 0
                  : user.freeCreditsUsed.toLocaleString()}
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-muted-foreground">Credits Remaining</span>
              <span className="font-medium text-green-600">
                {isUnlimitedPlan ? "Unlimited" : creditsRemaining.toLocaleString()}
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-muted-foreground">Credit Limit</span>
              <span>
                {isUnlimitedPlan
                  ? "Unlimited"
                  : isPaidPlan
                    ? (user.proSubscription?.proCreditsLimit ?? 10000).toLocaleString()
                    : user.freeTierCredits.toLocaleString()}
              </span>
            </div>
            {user.plan === "free" && (
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Trial Expires</span>
                <span>
                  {format(new Date(user.trialExpiryDate), "MMM d, yyyy")}
                </span>
              </div>
            )}
            {isPaidPlan && user.proSubscription && (
              <>
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Subscription Status</span>
                  <Badge
                    variant={
                      user.proSubscription.status === "active"
                        ? "default"
                        : "destructive"
                    }
                  >
                    {user.proSubscription.status}
                  </Badge>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Billing Period Ends</span>
                  <span>
                    {format(
                      new Date(user.proSubscription.currentPeriodEnd),
                      "MMM d, yyyy"
                    )}
                  </span>
                </div>
              </>
            )}

            <div className="flex gap-2 pt-2">
              <Button
                variant="outline"
                className="flex-1"
                onClick={() => setAdjustCreditsOpen(true)}
              >
                Adjust Credits
              </Button>
              <Button
                variant="outline"
                className="flex-1"
                onClick={() => setChangePlanOpen(true)}
              >
                Change Plan
              </Button>
              {user.suspended ? (
                <Button
                  variant="outline"
                  className="flex-1"
                  onClick={() => handleSuspend(user.uid, false)}
                >
                  <ShieldCheck className="h-4 w-4 mr-1" />
                  Unsuspend
                </Button>
              ) : (
                <Button
                  variant="destructive"
                  className="flex-1"
                  onClick={() => handleSuspend(user.uid, true)}
                >
                  <ShieldBan className="h-4 w-4 mr-1" />
                  Suspend
                </Button>
              )}
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Active Platforms</CardTitle>
        </CardHeader>
        <CardContent>
          {Object.keys(user.platforms ?? {}).length === 0 ? (
            <p className="text-muted-foreground text-center py-4 text-sm">
              No platform activity recorded yet
            </p>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              {Object.entries(user.platforms).map(([platform, info]) => (
                <div
                  key={platform}
                  className="flex items-start gap-3 p-4 bg-muted/50 rounded-lg"
                >
                  {platform === "android" ? (
                    <Smartphone className="h-5 w-5 mt-0.5 text-green-500 shrink-0" />
                  ) : (
                    <Monitor className="h-5 w-5 mt-0.5 text-blue-500 shrink-0" />
                  )}
                  <div className="min-w-0">
                    <p className="font-medium capitalize">{platform}</p>
                    <p className="text-sm text-muted-foreground">
                      v{info.appVersion}
                    </p>
                    {info.osVersion && (
                      <p className="text-xs text-muted-foreground">
                        {info.osVersion}
                      </p>
                    )}
                    {info.lastSeen > 0 && (
                      <p className="text-xs text-muted-foreground mt-0.5">
                        Last seen{" "}
                        {formatDistanceToNow(new Date(info.lastSeen), {
                          addSuffix: true,
                        })}
                      </p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Usage Statistics</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 md:grid-cols-4">
            <div className="text-center p-4 bg-muted/50 rounded-lg">
              <p className="text-2xl font-bold">
                {stats.totalTranscriptions.toLocaleString()}
              </p>
              <p className="text-sm text-muted-foreground">Total Transcriptions</p>
            </div>
            <div className="text-center p-4 bg-muted/50 rounded-lg">
              <p className="text-2xl font-bold">
                {stats.successfulTranscriptions.toLocaleString()}
              </p>
              <p className="text-sm text-muted-foreground">Successful</p>
            </div>
            <div className="text-center p-4 bg-muted/50 rounded-lg">
              <p className="text-2xl font-bold">
                {stats.totalCreditsUsed.toLocaleString()}
              </p>
              <p className="text-sm text-muted-foreground">Total Credits</p>
            </div>
            <div className="text-center p-4 bg-muted/50 rounded-lg">
              <p className="text-2xl font-bold">
                {stats.creditsThisMonth.toLocaleString()}
              </p>
              <p className="text-sm text-muted-foreground">This Month</p>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Recent Usage</CardTitle>
        </CardHeader>
        <CardContent>
          {recentUsage.length === 0 ? (
            <p className="text-muted-foreground text-center py-8">
              No usage history
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Credits</TableHead>
                  <TableHead>Source</TableHead>
                  <TableHead>Model Tier</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {recentUsage.map((entry) => (
                  <TableRow key={entry.id}>
                    <TableCell>
                      {format(new Date(entry.timestamp), "MMM d, yyyy h:mm a")}
                    </TableCell>
                    <TableCell>{entry.creditsUsed}</TableCell>
                    <TableCell>
                      <Badge variant="outline">{entry.source}</Badge>
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant={
                          entry.modelTier === "PREMIUM"
                            ? "default"
                            : entry.modelTier === "STANDARD"
                            ? "secondary"
                            : "outline"
                        }
                      >
                        {entry.modelTier}
                      </Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <AdjustCreditsDialog
        open={adjustCreditsOpen}
        onOpenChange={setAdjustCreditsOpen}
        uid={user.uid}
        currentCreditsUsed={
          isPaidPlan
            ? user.proSubscription?.proCreditsUsed ?? 0
            : user.freeCreditsUsed
        }
        onSuccess={fetchUser}
      />
      <ChangePlanDialog
        open={changePlanOpen}
        onOpenChange={setChangePlanOpen}
        uid={user.uid}
        currentPlan={user.plan}
        onSuccess={fetchUser}
      />

      <Dialog
        open={deleteDialogOpen}
        onOpenChange={(open) => {
          setDeleteDialogOpen(open);
          if (!open) { setDeleteReason(""); setDeleteError(null); }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete User</DialogTitle>
            <DialogDescription>
              This will permanently delete the Firebase Auth record and all
              Firestore data for:
              <br />
              <strong>{user.displayName || "Anonymous"}</strong>
              {user.email && (
                <span className="text-muted-foreground"> ({user.email})</span>
              )}
              <br />
              This cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <label className="text-sm font-medium">Reason</label>
            <Input
              placeholder="Why is this account being deleted?"
              value={deleteReason}
              onChange={(e) => setDeleteReason(e.target.value)}
            />
          </div>
          {deleteError && (
            <p className="text-sm text-destructive">{deleteError}</p>
          )}
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setDeleteDialogOpen(false)}
              disabled={deleting}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={handleDelete}
              disabled={!deleteReason.trim() || deleting}
            >
              {deleting ? "Deleting…" : "Delete permanently"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function UserDetailLoader() {
  const searchParams = useSearchParams();
  const uid = searchParams.get("uid");
  return <UserDetailContent uid={uid} />;
}

export default function UserDetailPage() {
  return (
    <Suspense fallback={
      <div className="space-y-6">
        <Skeleton className="h-10 w-48" />
        <div className="grid gap-6 md:grid-cols-2">
          <Skeleton className="h-64" />
          <Skeleton className="h-64" />
        </div>
      </div>
    }>
      <UserDetailLoader />
    </Suspense>
  );
}
