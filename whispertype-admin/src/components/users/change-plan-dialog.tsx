"use client";

import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { updateUserPlan } from "@/lib/api/admin-api";
import { toast } from "sonner";

interface ChangePlanDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  uid: string;
  currentPlan: "free" | "pro";
  onSuccess: () => void;
}

export function ChangePlanDialog({
  open,
  onOpenChange,
  uid,
  currentPlan,
  onSuccess,
}: ChangePlanDialogProps) {
  const [plan, setPlan] = useState<"free" | "pro">(currentPlan);
  const [resetCredits, setResetCredits] = useState(false);
  const [extendTrialDays, setExtendTrialDays] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    setLoading(true);
    try {
      await updateUserPlan({
        uid,
        plan,
        resetCredits,
        extendTrialDays: extendTrialDays ? parseInt(extendTrialDays, 10) : undefined,
      });

      toast.success(`User plan updated to ${plan}`);
      onSuccess();
      onOpenChange(false);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to update plan");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Change Plan</DialogTitle>
          <DialogDescription>
            Change this user&apos;s subscription plan and optionally reset their
            credits.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="plan">Plan</Label>
            <Select value={plan} onValueChange={(v) => setPlan(v as "free" | "pro")}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="free">Free</SelectItem>
                <SelectItem value="pro">Pro</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="flex items-center space-x-2">
            <input
              type="checkbox"
              id="resetCredits"
              checked={resetCredits}
              onChange={(e) => setResetCredits(e.target.checked)}
              className="h-4 w-4 rounded border-gray-300"
            />
            <Label htmlFor="resetCredits" className="text-sm font-normal">
              Reset credits to 0
            </Label>
          </div>

          {plan === "free" && (
            <div className="space-y-2">
              <Label htmlFor="extendTrial">Extend Trial (days)</Label>
              <Input
                id="extendTrial"
                type="number"
                min="0"
                placeholder="e.g., 30"
                value={extendTrialDays}
                onChange={(e) => setExtendTrialDays(e.target.value)}
              />
              <p className="text-sm text-muted-foreground">
                Leave empty to keep current trial expiry
              </p>
            </div>
          )}

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={loading}>
              {loading ? "Updating..." : "Update Plan"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
