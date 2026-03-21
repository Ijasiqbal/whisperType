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
  const [grantDuration, setGrantDuration] = useState("1");
  const [customMonths, setCustomMonths] = useState("");
  const [loading, setLoading] = useState(false);

  const isUpgradingToPro = plan === "pro" && currentPlan !== "pro";

  const getGrantMonths = (): number | undefined => {
    if (!isUpgradingToPro) return undefined;
    if (grantDuration === "custom") {
      const parsed = parseInt(customMonths, 10);
      return parsed > 0 ? parsed : undefined;
    }
    return parseInt(grantDuration, 10);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (isUpgradingToPro) {
      const months = getGrantMonths();
      if (!months || months <= 0) {
        toast.error("Please select a valid grant duration");
        return;
      }
    }

    setLoading(true);
    try {
      await updateUserPlan({
        uid,
        plan,
        resetCredits,
        extendTrialDays: extendTrialDays ? parseInt(extendTrialDays, 10) : undefined,
        grantDurationMonths: getGrantMonths(),
      });

      const months = getGrantMonths();
      const durationText = months ? ` for ${months} month${months > 1 ? "s" : ""}` : "";
      toast.success(`User plan updated to ${plan}${durationText}`);
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

          {isUpgradingToPro && (
            <div className="space-y-2">
              <Label>Grant Duration</Label>
              <Select value={grantDuration} onValueChange={setGrantDuration}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="1">1 Month</SelectItem>
                  <SelectItem value="2">2 Months</SelectItem>
                  <SelectItem value="3">3 Months</SelectItem>
                  <SelectItem value="6">6 Months</SelectItem>
                  <SelectItem value="12">12 Months</SelectItem>
                  <SelectItem value="custom">Custom</SelectItem>
                </SelectContent>
              </Select>
              {grantDuration === "custom" && (
                <Input
                  type="number"
                  min="1"
                  placeholder="Number of months"
                  value={customMonths}
                  onChange={(e) => setCustomMonths(e.target.value)}
                />
              )}
              <p className="text-sm text-muted-foreground">
                After this period, the user must subscribe via Google Play to
                continue pro access.
              </p>
            </div>
          )}

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
