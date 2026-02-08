"use client";

import { useState, useEffect, useCallback } from "react";
import { UserListItem } from "@/types/user";
import { listUsers } from "@/lib/api/admin-api";

interface UseUsersOptions {
  initialLimit?: number;
}

export function useUsers(options: UseUsersOptions = {}) {
  const { initialLimit = 20 } = options;

  const [users, setUsers] = useState<UserListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [planFilter, setPlanFilter] = useState<"free" | "pro" | "all">("all");
  const [pageToken, setPageToken] = useState<string | null>(null);
  const [nextPageToken, setNextPageToken] = useState<string | null>(null);
  const [totalCount, setTotalCount] = useState(0);
  const [limit] = useState(initialLimit);

  const fetchUsers = useCallback(
    async (token?: string | null) => {
      setLoading(true);
      setError(null);

      try {
        const result = await listUsers({
          limit,
          pageToken: token ?? undefined,
          search: search || undefined,
          plan: planFilter,
        });
        setUsers(result.users);
        setNextPageToken(result.nextPageToken);
        setTotalCount(result.totalCount);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to fetch users");
        setUsers([]);
      } finally {
        setLoading(false);
      }
    },
    [limit, search, planFilter]
  );

  useEffect(() => {
    setPageToken(null);
    fetchUsers(null);
  }, [fetchUsers]);

  const goToNextPage = () => {
    if (nextPageToken) {
      setPageToken(nextPageToken);
      fetchUsers(nextPageToken);
    }
  };

  const goToPreviousPage = () => {
    // For simplicity, we'll just reset to the first page
    // A more complex implementation would track page history
    setPageToken(null);
    fetchUsers(null);
  };

  const refresh = () => {
    fetchUsers(pageToken);
  };

  return {
    users,
    loading,
    error,
    search,
    setSearch,
    planFilter,
    setPlanFilter,
    totalCount,
    hasNextPage: !!nextPageToken,
    hasPreviousPage: !!pageToken,
    goToNextPage,
    goToPreviousPage,
    refresh,
  };
}
