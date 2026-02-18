#!/bin/bash
# Reset VoxType Mac to simulate fresh user experience

set -e

echo "🔄 Resetting VoxType to fresh user state..."

# 1. Quit the app if running
echo "1️⃣  Quitting VoxType..."
killall VoxType 2>/dev/null || echo "   (VoxType was not running)"

# 2. Clear UserDefaults (both sandboxed and non-sandboxed locations)
echo "2️⃣  Clearing app settings..."
defaults delete com.voxtype.VoxType 2>/dev/null || echo "   (No non-sandboxed settings found)"

# Clear sandboxed preferences
SANDBOXED_PREFS="$HOME/Library/Containers/com.voxtype.VoxType/Data/Library/Preferences"
if [ -d "$SANDBOXED_PREFS" ]; then
    rm -f "$SANDBOXED_PREFS/com.voxtype.VoxType.plist" 2>/dev/null || true
    echo "   ✓ Cleared sandboxed preferences"
fi

# 3. Clear Application Support data (both sandboxed and non-sandboxed)
APP_SUPPORT="$HOME/Library/Application Support/VoxType"
if [ -d "$APP_SUPPORT" ]; then
    echo "3️⃣  Removing app data from Application Support..."
    rm -rf "$APP_SUPPORT"
else
    echo "3️⃣  (No non-sandboxed Application Support data found)"
fi

# Clear sandboxed Application Support
SANDBOXED_APP_SUPPORT="$HOME/Library/Containers/com.voxtype.VoxType/Data/Library/Application Support"
if [ -d "$SANDBOXED_APP_SUPPORT" ]; then
    echo "   Removing sandboxed app data..."
    rm -rf "$SANDBOXED_APP_SUPPORT"/*
    echo "   ✓ Cleared sandboxed Application Support"
fi

# 4. Clear caches (if exists)
CACHE_DIR="$HOME/Library/Caches/com.voxtype.VoxType"
if [ -d "$CACHE_DIR" ]; then
    echo "4️⃣  Clearing caches..."
    rm -rf "$CACHE_DIR"
else
    echo "4️⃣  (No caches found)"
fi

echo ""
echo "✅ App reset complete!"
echo ""
echo "📋 Manual steps still needed:"
echo "   1. Reset Microphone permission:"
echo "      System Settings → Privacy & Security → Microphone → Remove VoxType"
echo ""
echo "   2. Reset Accessibility permission:"
echo "      System Settings → Privacy & Security → Accessibility → Remove VoxType"
echo ""
echo "   You can open these settings directly with:"
echo "      open 'x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone'"
echo "      open 'x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility'"
echo ""
