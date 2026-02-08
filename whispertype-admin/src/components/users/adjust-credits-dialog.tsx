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
import { adjustCredits } from "@/lib/api/admin-api";
import { toast } from "sonner";

interface AdjustCreditsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  uid: string;
  currentCreditsUsed: number;
  onSuccess: () => void;
}

export function AdjustCreditsDialog({
  open,
  onOpenChange,
  uid,
  currentCreditsUsed,
  onSuccess,
}: AdjustCreditsDialogProps) {
  const [adjustment, setAdjustment] = useState("");
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const adjustmentValue = parseInt(adjustment, 10);
    if (isNaN(adjustmentValue) || adjustmentValue === 0) {
      toast.error("Please enter a valid adjustment value");
      return;
    }

    if (!reason.trim()) {
      toast.error("Please provide a reason for this adjustment");
      return;
    }

    setLoading(true);
    try {
      const result = await adjustCredits({
        uid,
        adjustment: adjustmentValue,
        reason: reason.trim(),
      });

      toast.success(
        `Credits adjusted. New balance: ${result.creditsRemaining} remaining`
      );
      onSuccess();
      onOpenChange(false);
      setAdjustment("");
      setReason("");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to adjust credits");
    } finally {
      setLoading(false);
    }
  };

  const adjustmentValue = parseInt(adjustment, 10) || 0;
  const newCreditsUsed = currentCreditsUsed - adjustmentValue;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Adjust Credits</DialogTitle>
          <DialogDescription>
            Add or remove credits from this user&apos;s account. Positive values
            add credits, negative values remove credits.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="adjustment">Adjustment Amount</Label>
            <Input
              id="adjustment"
              type="number"
              placeholder="e.g., 100 or -50"
              value={adjustment}
              onChange={(e) => setAdjustment(e.target.value)}
            />
            <p className="text-sm text-muted-foreground">
              Current: {currentCreditsUsed} used → New:{" "}
              {newCreditsUsed < 0 ? 0 : newCreditsUsed} used
              {adjustmentValue > 0 && (
                <span className="text-green-600"> (+{adjustmentValue} credits)</span>
              )}
              {adjustmentValue < 0 && (
                <span className="text-red-600"> ({adjustmentValue} credits)</span>
              )}
            </p>
          </div>

          <div className="space-y-2">
            <Label htmlFor="reason">Reason</Label>
            <Input
              id="reason"
              placeholder="e.g., Customer support refund"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={loading}>
              {loading ? "Adjusting..." : "Adjust Credits"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
