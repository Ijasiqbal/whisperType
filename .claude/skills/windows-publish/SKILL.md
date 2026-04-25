---
name: windows-publish
description: Use when releasing a new version of the Vozcribe Windows app. Triggers: "release windows", "publish windows", "ship windows", "bump windows version"
---

# Publish Vozcribe Windows

## Overview

Bump version, build the installer via InnoSetup, upload to GitHub Releases, and update Firestore so the in-app update check reflects the new version.

## Prerequisites

- Inno Setup 6 installed at `C:\Program Files (x86)\Inno Setup 6\ISCC.exe`
- `gh` CLI authenticated
- `dotnet` SDK 8 available
- `node` with `firebase-admin` installed in `scripts/` (`cd scripts && npm install firebase-admin`)
- `gcloud auth application-default login` run at least once

## Release Steps

### 0. Pick the next version

Check the current published version on GitHub:

```bash
gh release list --repo Ijasiqbal/Vozcribe-release --limit 5
```

The new version must be strictly greater (e.g. `1.0.5` → `1.0.6`).

### 1. Bump version

Update version in both files:

**`Vozcribe-Windows/installer.iss`** — replace `AppVersion`:
```
AppVersion=<VERSION>
```

**`Vozcribe-Windows/Vozcribe/Vozcribe.csproj`** — add or update `<Version>`:
```xml
<Version><VERSION></Version>
```

### 2. Build (dotnet publish)

```powershell
cd C:\Users\ijasi\Documents\whisperType\Vozcribe-Windows\Vozcribe
dotnet publish -c Release -r win-x64 --self-contained false -o publish-output
```

### 3. Compile installer (InnoSetup)

```powershell
& "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" `
  C:\Users\ijasi\Documents\whisperType\Vozcribe-Windows\installer.iss
```

Output: `Vozcribe-Windows/installer-output/VozcribeSetup.exe`

### 4. Upload to GitHub Release

```bash
gh release create v<VERSION>-windows \
  "Vozcribe-Windows/installer-output/VozcribeSetup.exe" \
  --repo Ijasiqbal/Vozcribe-release \
  --title "Vozcribe Windows <VERSION>" \
  --notes "Windows release <VERSION>"
```

To replace an existing release asset:

```bash
gh release delete-asset v<VERSION>-windows VozcribeSetup.exe \
  --repo Ijasiqbal/Vozcribe-release --yes
gh release upload v<VERSION>-windows \
  "Vozcribe-Windows/installer-output/VozcribeSetup.exe" \
  --repo Ijasiqbal/Vozcribe-release
```

### 5. Update Firestore latest version

```bash
cd C:\Users\ijasi\Documents\whisperType
node scripts/update-firestore-version.js windows <VERSION>
```

Expected output: `✓ config/windowsApp.latestVersion = <VERSION>`

### 6. Verify

- Download the installer from the GitHub release and run it on a clean machine (or VM)
- Launch Vozcribe → open tray → click **Check for Update** → should say "You're up to date"
- Test sign-in, recording, and transcription

## Key Files

| File | Purpose |
|------|---------|
| `Vozcribe-Windows/installer.iss` | InnoSetup config — version lives here |
| `Vozcribe-Windows/Vozcribe/Vozcribe.csproj` | .NET project — version lives here |
| `Vozcribe-Windows/installer-output/VozcribeSetup.exe` | Built installer output |
| `scripts/update-firestore-version.js` | Updates Firestore latestVersion |
| Releases: `Ijasiqbal/Vozcribe-release` | GitHub releases for both platforms |

## Common Mistakes

- **Version mismatch between `.iss` and `.csproj`** — keep both in sync or the in-app version check will report the wrong version.
- **Forgetting `dotnet publish` before ISCC** — ISCC packages whatever is in `publish-output/`; stale binaries = old build shipped.
- **Skipping the Firestore update** — users won't see the update prompt until `latestVersion` is updated.
- **Not testing on a fresh machine** — runtime dependencies may be missing; use `--self-contained true` if targeting machines without .NET installed.
