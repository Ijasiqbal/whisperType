"use client";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Construction } from "lucide-react";

export default function SettingsPage() {
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
    </div>
  );
}
