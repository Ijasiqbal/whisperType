"use client";

import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { Search, X } from "lucide-react";

type PlanFilter = "free" | "starter" | "pro" | "unlimited" | "all";
type SortBy = "default" | "credits_used";

interface UserSearchProps {
  search: string;
  onSearchChange: (value: string) => void;
  planFilter: PlanFilter;
  onPlanFilterChange: (value: PlanFilter) => void;
  sortBy: SortBy;
  onSortByChange: (value: SortBy) => void;
}

export function UserSearch({
  search,
  onSearchChange,
  planFilter,
  onPlanFilterChange,
  sortBy,
  onSortByChange,
}: UserSearchProps) {
  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
      <div className="relative flex-1">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder="Search by email or UID..."
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          className="pl-9"
        />
        {search && (
          <Button
            variant="ghost"
            size="icon"
            className="absolute right-1 top-1/2 h-7 w-7 -translate-y-1/2"
            onClick={() => onSearchChange("")}
          >
            <X className="h-4 w-4" />
          </Button>
        )}
      </div>
      <Select value={planFilter} onValueChange={onPlanFilterChange}>
        <SelectTrigger className="w-full sm:w-36">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">All Plans</SelectItem>
          <SelectItem value="free">Free</SelectItem>
          <SelectItem value="starter">Starter</SelectItem>
          <SelectItem value="pro">Pro</SelectItem>
          <SelectItem value="unlimited">Unlimited</SelectItem>
        </SelectContent>
      </Select>
      <Select value={sortBy} onValueChange={onSortByChange}>
        <SelectTrigger className="w-full sm:w-40">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="default">Default</SelectItem>
          <SelectItem value="credits_used">Credits Used</SelectItem>
        </SelectContent>
      </Select>
    </div>
  );
}
