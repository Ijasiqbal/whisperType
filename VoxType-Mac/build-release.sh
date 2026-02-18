#!/bin/bash
#
# VoxType macOS — Build, Sign, Package, Notarize
#
# Prerequisites:
#   1. "Developer ID Application" certificate installed in Keychain
#   2. Notarytool credentials stored:
#      xcrun notarytool store-credentials "notary" \
#        --apple-id YOUR_APPLE_ID \
#        --team-id 32AWBC2NDR \
#        --password APP_SPECIFIC_PASSWORD
#
# Usage:
#   ./build-release.sh              # Build with current version from Xcode
#   ./build-release.sh 1.0.1        # Override marketing version
#   ./build-release.sh 1.0.1 2      # Override marketing version + build number

set -euo pipefail

# --- Configuration ---
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
SCHEME="VoxType"
PROJECT="$PROJECT_DIR/VoxType.xcodeproj"
EXPORT_OPTIONS="$PROJECT_DIR/ExportOptions.plist"
BUILD_DIR="$PROJECT_DIR/build"
ARCHIVE_PATH="$BUILD_DIR/VoxType.xcarchive"
EXPORT_PATH="$BUILD_DIR/export"
DMG_DIR="$BUILD_DIR/dmg"
NOTARY_PROFILE="notary"

# Version from args or Xcode project
VERSION="${1:-}"
BUILD_NUMBER="${2:-}"

# --- Colors ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log()  { echo -e "${BLUE}[VoxType]${NC} $1"; }
ok()   { echo -e "${GREEN}[✓]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
err()  { echo -e "${RED}[✗]${NC} $1"; exit 1; }

# --- Preflight Checks ---
log "Running preflight checks..."

# Check for Developer ID certificate
if ! security find-identity -v -p codesigning | grep -q "Developer ID Application"; then
    warn "No 'Developer ID Application' certificate found. DMG will be unsigned."
    warn "To sign, create one at: https://developer.apple.com/account/resources/certificates"
    HAS_DEV_ID=false
else
    ok "Developer ID Application certificate found"
    HAS_DEV_ID=true
fi

# Check for notarytool credentials
if ! xcrun notarytool history --keychain-profile "$NOTARY_PROFILE" > /dev/null 2>&1; then
    warn "Notarytool credentials not found. Skipping notarization."
    warn "To set up, run:"
    warn "  xcrun notarytool store-credentials \"notary\" \\"
    warn "    --apple-id YOUR_APPLE_ID \\"
    warn "    --team-id 32AWBC2NDR \\"
    warn "    --password APP_SPECIFIC_PASSWORD"
    SKIP_NOTARIZE=true
else
    ok "Notarytool credentials found"
    SKIP_NOTARIZE=false
fi

# Check ExportOptions.plist
if [ ! -f "$EXPORT_OPTIONS" ]; then
    err "ExportOptions.plist not found at $EXPORT_OPTIONS"
fi
ok "ExportOptions.plist found"

# --- Clean Previous Build ---
log "Cleaning previous build artifacts..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# --- Build Version Overrides ---
VERSION_ARGS=""
if [ -n "$VERSION" ]; then
    VERSION_ARGS="MARKETING_VERSION=$VERSION"
    log "Using marketing version: $VERSION"
fi
if [ -n "$BUILD_NUMBER" ]; then
    VERSION_ARGS="$VERSION_ARGS CURRENT_PROJECT_VERSION=$BUILD_NUMBER"
    log "Using build number: $BUILD_NUMBER"
fi

# Check for create-dmg
if ! command -v create-dmg &> /dev/null; then
    warn "create-dmg not found. Install with: brew install create-dmg"
    warn "Falling back to plain DMG (no styled background)"
    HAS_CREATE_DMG=false
else
    ok "create-dmg found"
    HAS_CREATE_DMG=true
fi

# --- Step 1: Archive ---
log "Step 1/5: Archiving..."
if [ "$HAS_DEV_ID" = true ]; then
    SIGN_ARGS="CODE_SIGN_IDENTITY=Developer ID Application DEVELOPMENT_TEAM=32AWBC2NDR CODE_SIGN_STYLE=Manual"
else
    SIGN_ARGS=""
fi
xcodebuild archive \
    -project "$PROJECT" \
    -scheme "$SCHEME" \
    -configuration Release \
    -archivePath "$ARCHIVE_PATH" \
    $VERSION_ARGS \
    $SIGN_ARGS \
    -quiet

if [ ! -d "$ARCHIVE_PATH" ]; then
    err "Archive failed — no .xcarchive produced"
fi
ok "Archive created"

# --- Step 2: Export ---
if [ "$HAS_DEV_ID" = true ]; then
    log "Step 2/5: Exporting with Developer ID signing..."
    xcodebuild -exportArchive \
        -archivePath "$ARCHIVE_PATH" \
        -exportOptionsPlist "$EXPORT_OPTIONS" \
        -exportPath "$EXPORT_PATH" \
        -quiet
    APP_PATH="$EXPORT_PATH/VoxType.app"
    if [ ! -d "$APP_PATH" ]; then
        err "Export failed — no .app produced"
    fi
    ok "App exported and signed"

    log "Verifying code signature..."
    codesign --verify --deep --strict "$APP_PATH" 2>&1 || err "Code signature verification failed"
    ok "Code signature valid"
else
    log "Step 2/5: Skipping export (no Developer ID). Using build output directly."
    APP_PATH="$ARCHIVE_PATH/Products/Applications/VoxType.app"
    if [ ! -d "$APP_PATH" ]; then
        # Fallback to derived data path
        APP_PATH="$(find "$ARCHIVE_PATH" -name "VoxType.app" -type d | head -1)"
    fi
    if [ ! -d "$APP_PATH" ]; then
        err "No .app found in archive"
    fi
    ok "App built (unsigned)"
fi

# --- Step 3: Create DMG ---
log "Step 3/5: Creating DMG..."

# Get version for filename
if [ -n "$VERSION" ]; then
    DMG_VERSION="$VERSION"
else
    DMG_VERSION=$(/usr/libexec/PlistBuddy -c "Print :ApplicationProperties:CFBundleShortVersionString" "$ARCHIVE_PATH/Info.plist" 2>/dev/null || echo "1.0")
fi
DMG_NAME="VoxType-${DMG_VERSION}.dmg"
DMG_PATH="$BUILD_DIR/$DMG_NAME"

# Background image for DMG (generate if missing)
BG_IMAGE="$PROJECT_DIR/dmg-background.png"
if [ "$HAS_CREATE_DMG" = true ] && [ -f "$BG_IMAGE" ]; then
    create-dmg \
        --volname "VoxType" \
        --background "$BG_IMAGE" \
        --window-pos 200 120 \
        --window-size 660 400 \
        --icon-size 100 \
        --icon "VoxType.app" 170 180 \
        --app-drop-link 490 180 \
        --no-internet-enable \
        "$DMG_PATH" \
        "$APP_PATH"
else
    warn "Using plain DMG layout"
    mkdir -p "$DMG_DIR"
    cp -R "$APP_PATH" "$DMG_DIR/"
    ln -s /Applications "$DMG_DIR/Applications"
    hdiutil create -volname "VoxType" -srcfolder "$DMG_DIR" -ov -format UDZO -imagekey zlib-level=9 "$DMG_PATH"
fi

if [ ! -f "$DMG_PATH" ]; then
    err "DMG creation failed"
fi

# Sign the DMG (skip if no Developer ID)
if security find-identity -v -p codesigning | grep -q "Developer ID Application"; then
    codesign --sign "Developer ID Application" "$DMG_PATH"
    ok "DMG created and signed: $DMG_NAME"
else
    warn "DMG created (unsigned): $DMG_NAME"
fi

# --- Step 4: Notarize ---
if [ "$HAS_DEV_ID" = false ] || [ "$SKIP_NOTARIZE" = true ]; then
    warn "Skipping notarization (${HAS_DEV_ID:+no credentials}${HAS_DEV_ID:- no Developer ID})"
    log "DMG is at: $DMG_PATH"
    log "Note: Users will need to right-click → Open on first launch."
else
    log "Step 4/5: Submitting for notarization (this may take a few minutes)..."
    if xcrun notarytool submit "$DMG_PATH" --keychain-profile "$NOTARY_PROFILE" --wait; then
        ok "Notarization approved"

        # --- Step 5: Staple ---
        log "Step 5/5: Stapling notarization ticket..."
        xcrun stapler staple "$DMG_PATH"
        ok "Ticket stapled"
    else
        err "Notarization failed. Check logs with: xcrun notarytool log <submission-id> --keychain-profile \"$NOTARY_PROFILE\""
    fi
fi

# --- Done ---
echo ""
echo -e "${GREEN}════════════════════════════════════════${NC}"
echo -e "${GREEN}  VoxType $DMG_VERSION — Ready for distribution${NC}"
echo -e "${GREEN}════════════════════════════════════════${NC}"
echo ""
echo "  DMG: $DMG_PATH"
echo "  Size: $(du -h "$DMG_PATH" | cut -f1)"
echo ""
echo "  Upload this DMG to your website for download."
echo ""

# Verify the final product
log "Final verification..."
spctl --assess --type open --context context:primary-signature "$DMG_PATH" 2>&1 && ok "Gatekeeper assessment: PASSED" || warn "Gatekeeper assessment: run after notarization completes"
