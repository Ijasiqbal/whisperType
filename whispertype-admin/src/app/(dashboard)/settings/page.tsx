"use client";

import { useState } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Construction, Trash2 } from "lucide-react";
import { deleteAnonymousUsers } from "@/lib/api/admin-api";
import { toast } from "sonner";

const CONFIRM_TEXT = "DELETE ANONYMOUS USERS";

export default function SettingsPage() {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [confirmText, setConfirmText] = useState("");
  const [loading, setLoading] = useState(false);

  const handleDelete = async (e: React.FormEvent) => {
    e.preventDefault();

    if (confirmText !== CONFIRM_TEXT) {
      toast.error("Please type the confirmation text exactly");
      return;
    }

    if (!reason.trim()) {
      toast.error("Please provide a reason");
      return;
    }

    setLoading(true);
    try {
      const result = await deleteAnonymousUsers(reason.trim());
      const msg = result.failedCount > 0
        ? `Deleted ${result.deletedCount} account(s). ${result.failedCount} failed — check audit logs.`
        : `Deleted ${result.deletedCount} anonymous account(s)`;
      result.failedCount > 0 ? toast.warning(msg) : toast.success(msg);
      setDialogOpen(false);
      setReason("");
      setConfirmText("");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to delete anonymous users");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Settings</h1>
        <p className="text-muted-foreground">
          Configure admin dashboard settings
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Construction className="h-5 w-5" />
            Coming Soon
            <Badge variant="secondary">Phase 2</Badge>
          </CardTitle>
          <CardDescription>
            Settings management will be available in a future update
          </CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground">
            Planned features include:
          </p>
          <ul className="mt-2 list-disc list-inside text-sm text-muted-foreground space-y-1">
            <li>Remote Config editor (credit limits, trial duration)</li>
            <li>Admin user management (grant/revoke admin access)</li>
            <li>API key configuration</li>
            <li>Alert and notification settings</li>
          </ul>
        </CardContent>
      </Card>

      <Card className="border-red-200 dark:border-red-900">
        <CardHeader>
          <CardTitle className="text-red-600 dark:text-red-400">
            Danger Zone
          </CardTitle>
          <CardDescription>
            Irreversible actions that affect multiple users
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center justify-between rounded-lg border border-red-200 dark:border-red-900 p-4">
            <div>
              <p className="font-medium">Delete all anonymous accounts</p>
              <p className="text-sm text-muted-foreground">
                Permanently delete all anonymous (guest) user accounts and their
                associated data including usage logs and transcription requests.
              </p>
            </div>
            <Button
              variant="destructive"
              onClick={() => setDialogOpen(true)}
            >
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </Button>
          </div>
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="text-red-600">Delete All Anonymous Users</DialogTitle>
            <DialogDescription>
              This will permanently delete all anonymous (guest) accounts and their
              Firestore data (user docs, usage logs, transcription requests). This
              action cannot be undone.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleDelete} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="reason">Reason</Label>
              <Input
                id="reason"
                placeholder="e.g., Quarterly cleanup of inactive guest accounts"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="confirm">
                Type <span className="font-mono font-bold">{CONFIRM_TEXT}</span> to confirm
              </Label>
              <Input
                id="confirm"
                placeholder={CONFIRM_TEXT}
                value={confirmText}
                onChange={(e) => setConfirmText(e.target.value)}
              />
            </div>

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setDialogOpen(false)}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                variant="destructive"
                disabled={loading || confirmText !== CONFIRM_TEXT}
              >
                {loading ? "Deleting..." : "Delete All Anonymous Users"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
