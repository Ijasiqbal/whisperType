import { useCallback } from "react";
import { suspendUser } from "@/lib/api/admin-api";

export function useSuspendUser(onSuccess: () => void) {
  const handleSuspend = useCallback(
    async (uid: string, suspend: boolean) => {
      const reason = suspend
        ? window.prompt("Reason for suspension (optional):")
        : undefined;
      if (suspend && reason === null) return;
      try {
        await suspendUser({
          uid,
          suspended: suspend,
          reason: reason || undefined,
        });
        onSuccess();
      } catch (err) {
        alert(err instanceof Error ? err.message : "Failed to update user");
      }
    },
    [onSuccess]
  );

  return handleSuspend;
}
