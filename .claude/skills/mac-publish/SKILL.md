---
name: mac-publish
description: Use when releasing a new version of the Vozcribe macOS app via Homebrew. Triggers: "release mac", "publish mac", "update homebrew", "bump mac version"
---

# Publish Vozcribe Mac via Homebrew

## Overview

Build, ad-hoc sign, package, and release Vozcribe macOS app so users can install/upgrade via `brew tap ijasiqbal/vozcribe && brew install --cask vozcribe`.

## Prerequisites

- Xcode with VoxType project buildable
- `gh` CLI authenticated
- `create-dmg` installed (`brew install create-dmg`)
- Homebrew tap `Ijasiqbal/homebrew-vozcribe` push access

## Release Steps

### 0. Pick the next version

**Always check the current cask version first** — the Xcode project's `MARKETING_VERSION` can drift out of sync with the Homebrew cask, and shipping a lower number than what's already published is a downgrade (Homebrew won't update existing installs).

```bash
grep '^  version' "$(brew --repository ijasiqbal/vozcribe)/Casks/vozcribe.rb"
```

The new version must be **strictly greater** than what's printed. Pick the next increment (e.g. `1.13` → `1.14`) unless there's a reason to jump.

### 1. Build the release

```bash
cd /Users/ijas/Documents/whisperType/VoxType-Mac
bash build-release.sh <VERSION>    # e.g. bash build-release.sh 1.14
```

The script produces the `.app` in `build/Vozcribe.xcarchive/Products/Applications/Vozcribe.app`.

### 2. Ad-hoc sign the app

**Critical step.** The build output is signed with an Apple Development certificate, which doesn't work on other users' Macs ("The application can't be opened" error). Ad-hoc signing makes it work everywhere:

```bash
codesign --force --deep --sign - \
  build/Vozcribe.xcarchive/Products/Applications/Vozcribe.app
```

Verify it says `Signature=adhoc`:

```bash
codesign -dvv build/Vozcribe.xcarchive/Products/Applications/Vozcribe.app 2>&1 | grep Signature
```

### 3. Create ZIP from the ad-hoc signed app

The Homebrew cask expects a ZIP containing `Vozcribe.app` directly (not a DMG inside a ZIP). GitHub Releases also doesn't accept `.dmg` uploads — only `.zip`.

```bash
(cd /Users/ijas/Documents/whisperType/VoxType-Mac/build/Vozcribe.xcarchive/Products/Applications && \
  zip -r /tmp/Vozcribe-<VERSION>.zip Vozcribe.app)
shasum -a 256 /tmp/Vozcribe-<VERSION>.zip
```

Save the sha256 hash for step 5.

### 4. Create or update GitHub release

For a new release:

```bash
gh release create v<VERSION> \
  /tmp/Vozcribe-<VERSION>.zip \
  --repo Ijasiqbal/Vozcribe-release \
  --title "Vozcribe <VERSION>" \
  --notes "<release notes>"
```

To update an existing release (replace the zip):

```bash
gh release delete-asset v<VERSION> Vozcribe-<VERSION>.zip \
  --repo Ijasiqbal/Vozcribe-release --yes
gh release upload v<VERSION> /tmp/Vozcribe-<VERSION>.zip \
  --repo Ijasiqbal/Vozcribe-release
```

### 5. Update Homebrew cask

```bash
CASK_PATH="$(brew --repository ijasiqbal/vozcribe)/Casks/vozcribe.rb"
# Show current values so you know exactly what to replace
grep -E '^  (version|sha256)' "$CASK_PATH"
```

Update `version` and `sha256` in the cask file. **Do not remove the `preflight` block** — it strips the quarantine flag so Gatekeeper doesn't block the unsigned app:

```ruby
preflight do
  system_command "/usr/bin/xattr",
    args: ["-cr", "#{staged_path}/Vozcribe.app"]
end
```

Then push:

```bash
cd "$(brew --repository ijasiqbal/vozcribe)"
git add Casks/vozcribe.rb
git commit -m "bump vozcribe to <VERSION>"
git push
```

### 6. Verify end-to-end

```bash
brew uninstall --cask vozcribe
brew untap ijasiqbal/vozcribe
brew tap ijasiqbal/vozcribe
brew install --cask vozcribe
```

Check no quarantine flag:

```bash
xattr /Applications/Vozcribe.app
# Should show com.apple.provenance only, NOT com.apple.quarantine
```

Test as fresh user:

```bash
bash /Users/ijas/Documents/whisperType/VoxType-Mac/reset-to-fresh-user.sh
open /Applications/Vozcribe.app
```

Test core flows: sign-in, recording, transcription.

## Key Files

| File | Purpose |
|------|---------|
| `VoxType-Mac/build-release.sh` | Archive, sign, package script |
| `VoxType-Mac/ExportOptions.plist` | Xcode export config |
| `VoxType-Mac/reset-to-fresh-user.sh` | Reset app to fresh state for testing |
| Cask: `Ijasiqbal/homebrew-vozcribe` | Homebrew tap repo (Casks/vozcribe.rb) |
| Releases: `Ijasiqbal/Vozcribe-release` | GitHub releases with ZIPs |

## How the Homebrew install works (for context)

1. `brew install` downloads the zip — Homebrew adds `com.apple.quarantine` flag
2. Homebrew unzips to a staging directory
3. **`preflight` block runs** — `xattr -cr` removes the quarantine flag
4. Homebrew moves the clean `.app` to `/Applications`
5. User opens it — no Gatekeeper warning

**This workaround bypasses Gatekeeper without needing a $99/yr Apple Developer ID certificate.** Homebrew is deprecating this path — unsigned cask support ends Sept 2026. Plan to get a Developer ID before then.

## Common Mistakes

- **Skipping ad-hoc signing** — the build uses an Apple Development cert that only works on your machine. Other users get "can't be opened" error. Always run `codesign --force --deep --sign -` before zipping.
- **Zipping the DMG instead of the .app** — the cask expects `Vozcribe.app` at the root of the zip, not a DMG file inside a zip. GitHub Releases also doesn't accept `.dmg` uploads.
- **Removing the preflight block** — this is what strips quarantine. Without it, users get Gatekeeper warnings/blocks.
- **Forgetting `--cask` flag** — use `brew install --cask vozcribe`, not `brew install vozcribe`.
- **Forgetting to push the cask** — local `brew install` works but nobody else gets the update.
- **Not testing as fresh user** — run `reset-to-fresh-user.sh` before verifying.
- **SHA mismatch** — if you update the zip on GitHub but forget to update the SHA in the cask, install will fail.
- **Publishing a version ≤ the current cask version** — the Xcode project's `MARKETING_VERSION` drifts independently of the cask; always run the check in Step 0 before building. Shipping `1.0` when the cask is at `1.13` is a silent no-op for existing users and a downgrade for new ones.
