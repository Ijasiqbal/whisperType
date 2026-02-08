"use client";

import { useUsers } from "@/hooks/use-users";
import { UserSearch } from "@/components/users/user-search";
import { UserTable } from "@/components/users/user-table";
import { Button } from "@/components/ui/button";
import { RefreshCw } from "lucide-react";

export default function UsersPage() {
  const {
    users,
    loading,
    error,
    search,
    setSearch,
    planFilter,
    setPlanFilter,
    totalCount,
    hasNextPage,
    hasPreviousPage,
    goToNextPage,
    goToPreviousPage,
    refresh,
  } = useUsers();

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Users</h1>
          <p className="text-muted-foreground">
            Manage your application users
          </p>
        </div>
        <Button variant="outline" size="icon" onClick={refresh}>
          <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
        </Button>
      </div>

      {error && (
        <div className="rounded-md bg-destructive/10 p-4 text-sm text-destructive">
          {error}
        </div>
      )}

      <UserSearch
        search={search}
        onSearchChange={setSearch}
        planFilter={planFilter}
        onPlanFilterChange={setPlanFilter}
      />

      <UserTable
        users={users}
        loading={loading}
        totalCount={totalCount}
        hasNextPage={hasNextPage}
        hasPreviousPage={hasPreviousPage}
        onNextPage={goToNextPage}
        onPreviousPage={goToPreviousPage}
      />
    </div>
  );
}
