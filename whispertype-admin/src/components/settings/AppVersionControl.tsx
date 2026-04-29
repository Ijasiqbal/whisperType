"use client";

import { useEffect, useState } from "react";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { db } from "@/lib/firebase/config";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { toast } from "sonner";
import { X, Plus, Apple, Monitor } from "lucide-react";

interface VersionConfig {
  minVersion: string;
  latestVersion: string;
  blockedVersions: string[];
  downloadUrl: string;
  blockedMessage: string;
}

const DEFAULTS: VersionConfig = {
  minVersion: "",
  latestVersion: "",
  blockedVersions: [],
  downloadUrl: "",
  blockedMessage: "",
};

function VersionCard({
  platform,
  firestoreDoc,
  icon: Icon,
  defaultDownloadUrl,
}: {
  platform: string;
  firestoreDoc: string;
  icon: React.ElementType;
  defaultDownloadUrl: string;
}) {
  const [config, setConfig] = useState<VersionConfig>(DEFAULTS);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [newBlockedVersion, setNewBlockedVersion] = useState("");

  useEffect(() => {
    async function load() {
      try {
        const snap = await getDoc(doc(db, "config", firestoreDoc));
        if (snap.exists()) {
          const data = snap.data();
          setConfig({
            minVersion: data.minVersion ?? "",
            latestVersion: data.latestVersion ?? "",
            blockedVersions: data.blockedVersions ?? [],
            downloadUrl: data.downloadUrl ?? "",
            blockedMessage: data.blockedMessage ?? "",
          });
        }
      } catch {
        toast.error(`Failed to load ${platform} config`);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [firestoreDoc, platform]);

  async function handleSave() {
    setSaving(true);
    try {
      await setDoc(
        doc(db, "config", firestoreDoc),
        {
          minVersion: config.minVersion.trim(),
          latestVersion: config.latestVersion.trim(),
          blockedVersions: config.blockedVersions,
          downloadUrl: config.downloadUrl.trim() || defaultDownloadUrl,
          blockedMessage: config.blockedMessage.trim(),
        },
        { merge: true }
      );
      toast.success(`${platform} config saved`);
    } catch {
      toast.error(`Failed to save ${platform} config`);
    } finally {
      setSaving(false);
    }
  }

  function addBlockedVersion() {
    const v = newBlockedVersion.trim();
    if (!v) return;
    if (!/^\d+(\.\d+)*$/.test(v)) {
      toast.error("Invalid version format — use e.g. 1.2.3");
      return;
    }
    if (config.blockedVersions.includes(v)) {
      toast.error("Already in the blocked list");
      return;
    }
    setConfig((c) => ({ ...c, blockedVersions: [...c.blockedVersions, v] }));
    setNewBlockedVersion("");
  }

  function removeBlockedVersion(v: string) {
    setConfig((c) => ({
      ...c,
      blockedVersions: c.blockedVersions.filter((b) => b !== v),
    }));
  }

  if (loading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Icon className="h-5 w-5" />
            {platform}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">Loading…</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Icon className="h-5 w-5" />
          {platform}
        </CardTitle>
        <CardDescription>
          Firestore: <code className="text-xs">config/{firestoreDoc}</code>
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        {/* Min version — hard block */}
        <div className="space-y-1.5">
          <Label htmlFor={`${firestoreDoc}-min`}>
            Min version{" "}
            <span className="text-muted-foreground text-xs font-normal">
              (block everything below this — hard block)
            </span>
          </Label>
          <Input
            id={`${firestoreDoc}-min`}
            placeholder="e.g. 1.16.0"
            value={config.minVersion}
            onChange={(e) =>
              setConfig((c) => ({ ...c, minVersion: e.target.value }))
            }
          />
        </div>

        {/* Latest version — soft nudge */}
        <div className="space-y-1.5">
          <Label htmlFor={`${firestoreDoc}-latest`}>
            Latest version{" "}
            <span className="text-muted-foreground text-xs font-normal">
              (soft update nudge — once per version)
            </span>
          </Label>
          <Input
            id={`${firestoreDoc}-latest`}
            placeholder="e.g. 1.17.0"
            value={config.latestVersion}
            onChange={(e) =>
              setConfig((c) => ({ ...c, latestVersion: e.target.value }))
            }
          />
        </div>

        {/* Blocked versions list */}
        <div className="space-y-2">
          <Label>
            Blocked versions{" "}
            <span className="text-muted-foreground text-xs font-normal">
              (exact version hard block)
            </span>
          </Label>
          <div className="flex flex-wrap gap-2 min-h-8">
            {config.blockedVersions.length === 0 && (
              <p className="text-xs text-muted-foreground self-center">
                None blocked
              </p>
            )}
            {config.blockedVersions.map((v) => (
              <Badge key={v} variant="secondary" className="gap-1 pr-1">
                {v}
                <button
                  type="button"
                  onClick={() => removeBlockedVersion(v)}
                  className="ml-0.5 rounded hover:bg-muted"
                  aria-label={`Remove ${v}`}
                >
                  <X className="h-3 w-3" />
                </button>
              </Badge>
            ))}
          </div>
          <div className="flex gap-2">
            <Input
              placeholder="e.g. 1.14.2"
              value={newBlockedVersion}
              onChange={(e) => setNewBlockedVersion(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  addBlockedVersion();
                }
              }}
            />
            <Button
              type="button"
              variant="outline"
              size="icon"
              onClick={addBlockedVersion}
            >
              <Plus className="h-4 w-4" />
            </Button>
          </div>
        </div>

        {/* Download URL */}
        <div className="space-y-1.5">
          <Label htmlFor={`${firestoreDoc}-url`}>
            Download URL{" "}
            <span className="text-muted-foreground text-xs font-normal">
              (shown in blocked / soft-update dialogs)
            </span>
          </Label>
          <Input
            id={`${firestoreDoc}-url`}
            placeholder={defaultDownloadUrl}
            value={config.downloadUrl}
            onChange={(e) =>
              setConfig((c) => ({ ...c, downloadUrl: e.target.value }))
            }
          />
        </div>

        {/* Blocked message */}
        <div className="space-y-1.5">
          <Label htmlFor={`${firestoreDoc}-msg`}>
            Blocked message{" "}
            <span className="text-muted-foreground text-xs font-normal">
              (shown in hard-block dialog — leave empty for default)
            </span>
          </Label>
          <Input
            id={`${firestoreDoc}-msg`}
            placeholder="This version is no longer supported. Please update."
            value={config.blockedMessage}
            onChange={(e) =>
              setConfig((c) => ({ ...c, blockedMessage: e.target.value }))
            }
          />
        </div>

        <Button onClick={handleSave} disabled={saving} className="w-full">
          {saving ? "Saving…" : `Save ${platform} Config`}
        </Button>
      </CardContent>
    </Card>
  );
}

export function AppVersionControl() {
  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold">App Version Control</h2>
        <p className="text-sm text-muted-foreground">
          Changes take effect immediately — no deploy needed. Users see the
          result on their next app launch.
        </p>
      </div>
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <VersionCard
          platform="Mac"
          firestoreDoc="macApp"
          icon={Apple}
          defaultDownloadUrl="https://vozcribe.com/mac"
        />
        <VersionCard
          platform="Windows"
          firestoreDoc="windowsApp"
          icon={Monitor}
          defaultDownloadUrl="https://vozcribe.com/windows"
        />
      </div>
    </div>
  );
}
