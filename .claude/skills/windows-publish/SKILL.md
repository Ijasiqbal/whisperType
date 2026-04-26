---
name: windows-publish
description: Use when releasing a new version of the Vozcribe Windows app. Triggers: "release windows", "publish windows", "ship windows", "bump windows version"
---

# Publish Vozcribe Windows

## Overview

Bump version, build the installer via InnoSetup, upload to GitHub Releases, and update Firestore so the in-app update check reflects the new version.

## Prerequisites

- **Inno Setup 6** — `ISCC.exe` reachable at one of:
  - `C:\Program Files (x86)\Inno Setup 6\ISCC.exe` (system install)
  - `$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe` (per-user install)
- **`gh` CLI** — installed and authenticated. Install via `winget install --id GitHub.cli` (admin shell) or MSI from https://cli.github.com. Auth via `gh auth login`.
- **`dotnet` SDK 8** available on PATH.
- **`node` with `firebase-admin`** installed in `scripts/` (`cd scripts && npm install firebase-admin`).
- **gcloud Application Default Credentials** — `gcloud auth application-default login` (this is **not** the same as `gcloud init` or `gcloud auth login` — those don't write ADC).

## Step 0a. Verify prerequisites

Run these checks first; fix anything that fails before proceeding.

```powershell
# Reload PATH in case something was just installed in this terminal session
$env:PATH = [System.Environment]::GetEnvironmentVariable("PATH","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("PATH","User")

gh --version                                   # gh CLI present
gh auth status                                 # gh authenticated
dotnet --version                               # .NET SDK present
gcloud auth application-default print-access-token  # ADC creds exist (any token = OK)

# Locate ISCC.exe
$iscc = @(
  "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
  "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $iscc) { throw "Inno Setup not found. Install from https://jrsoftware.org/isdl.php" }
"ISCC: $iscc"
```

If `gh` was just installed, the new PATH won't be visible until the terminal is restarted **or** the reload line above is run.

## Release Steps

### 0. Pick the next version

List existing **Windows** releases (the repo also hosts Mac releases — filter by the `-windows` suffix):

```bash
gh release list --repo Ijasiqbal/Vozcribe-release --limit 20 | grep -- "-windows"
```

- If at least one Windows release exists, the new version must be strictly greater (e.g. `1.0.5` → `1.0.6`).
- If **no** Windows release exists yet, start with the version currently in `installer.iss` / `.csproj` (do not invent a higher one).

### 1. Bump version

Update version in both files (must match exactly):

**`Vozcribe-Windows/installer.iss`** — replace `AppVersion`:
```
AppVersion=<VERSION>
```

**`Vozcribe-Windows/Vozcribe/Vozcribe.csproj`** — update `<Version>`, `<AssemblyVersion>`, `<FileVersion>`:
```xml
<Version><VERSION></Version>
<AssemblyVersion><VERSION>.0</AssemblyVersion>
<FileVersion><VERSION>.0</FileVersion>
```

### 2. Build (dotnet publish)

⚠️ **Always rebuild — never trust an existing `installer-output/VozcribeSetup.exe`**. ISCC packages whatever is in `publish-output/` at compile time, so a stale `publish-output/` ships stale binaries.

```powershell
cd C:\Users\ijasi\Documents\whisperType\Vozcribe-Windows\Vozcribe
dotnet publish -c Release -r win-x64 --self-contained false -o publish-output
```

### 3. Compile installer (InnoSetup)

Use the `$iscc` path discovered in Step 0a:

```powershell
& $iscc "C:\Users\ijasi\Documents\whisperType\Vozcribe-Windows\installer.iss"
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

If this fails with "Could not load the default credentials", run `gcloud auth application-default login` (not `gcloud init`).

### 6. Website (no action needed)

`website/windows.html` reads `latestVersion` from the `checkWindowsVersion` Cloud Function on page load and constructs the GitHub asset URL automatically. As long as Step 5 succeeds, the site self-updates — no manual edit required.

(Caveat: this assumes Firestore `config/windowsApp.minVersion` stays at `0.0.0`. If that's ever raised, the website fetcher would need a backend tweak so `latestVersion` is returned in the "blocked" branch too.)

### 7. Commit version bumps

```bash
git add Vozcribe-Windows/installer.iss Vozcribe-Windows/Vozcribe/Vozcribe.csproj
git commit -m "bump: windows v<VERSION>"
```

(Stage any other in-flight changes separately; this commit should only contain version bumps.)

### 8a. Automated sanity checks (Claude — always run these)

These prove Steps 4 and 5 actually landed server-side. **Do not skip — run them every release before handing off to the user.** If either fails, the release is broken even if local steps appeared to succeed.

```bash
# Confirm the GitHub release + asset exist
gh release view v<VERSION>-windows --repo Ijasiqbal/Vozcribe-release \
  --json tagName,assets

# Confirm Firestore latestVersion was updated (Cloud Function reads from Firestore)
curl -s "https://us-central1-whispertype-1de9f.cloudfunctions.net/checkWindowsVersion?version=0.0.0"
# Expect: "latestVersion":"<VERSION>"
```

Report both results to the user before declaring the release done.

### 8b. Manual end-user testing (user — Claude cannot do these)

Hand these off; Claude has no browser, no clean VM, no microphone.

- Open https://vozcribe.com/windows → the "Version X.Y.Z · VozcribeSetup.exe · 64-bit" label should show the new version (hard-refresh if cached). Click **Download** → confirm the URL contains `v<VERSION>-windows/VozcribeSetup.exe`.
- Run the installer on a clean machine (or VM).
- Launch Vozcribe → tray → **Check for Update** → should say "You're up to date".
- Test sign-in, recording, and transcription.

## Key Files

| File | Purpose |
|------|---------|
| `Vozcribe-Windows/installer.iss` | InnoSetup config — version lives here |
| `Vozcribe-Windows/Vozcribe/Vozcribe.csproj` | .NET project — version lives here |
| `Vozcribe-Windows/Vozcribe/publish-output/` | Built binaries that ISCC packages |
| `Vozcribe-Windows/installer-output/VozcribeSetup.exe` | Final installer (regenerated each release) |
| `scripts/update-firestore-version.js` | Updates Firestore latestVersion |
| Releases: `Ijasiqbal/Vozcribe-release` | GitHub releases for both platforms (Mac uses `vX.Y`, Windows uses `vX.Y.Z-windows`) |

## Common Mistakes

- **Version mismatch between `.iss` and `.csproj`** — keep both in sync or the in-app version check will report the wrong version.
- **Trusting an existing `VozcribeSetup.exe`** — always rebuild; the artifact may be days old and predate your fixes.
- **Forgetting `dotnet publish` before ISCC** — ISCC packages whatever is in `publish-output/`; stale binaries = old build shipped.
- **Skipping the Firestore update** — users won't see the update prompt until `latestVersion` is updated.
- **`gcloud init` ≠ `gcloud auth application-default login`** — only the latter writes the ADC file the Firestore script needs (`%APPDATA%\gcloud\application_default_credentials.json`).
- **Stale PATH after fresh install** — newly-installed CLIs (`gh`, `dotnet`, `gcloud`) won't be on PATH in an existing terminal. Restart the terminal or run the PATH-reload one-liner from Step 0a.
- **Not testing on a fresh machine** — runtime dependencies may be missing; use `--self-contained true` if targeting machines without .NET installed.
