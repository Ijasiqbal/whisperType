#!/bin/bash

# Bump version script for Android project
# Usage: ./bump-version.sh [patch|minor|major]

BUILD_FILE="app/build.gradle.kts"

# Check if build file exists
if [ ! -f "$BUILD_FILE" ]; then
    echo "❌ Error: $BUILD_FILE not found!"
    exit 1
fi

# Extract current version
CURRENT_CODE=$(grep -E 'versionCode = [0-9]+' "$BUILD_FILE" | grep -oE '[0-9]+')
CURRENT_NAME=$(grep -E 'versionName = "' "$BUILD_FILE" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')

if [ -z "$CURRENT_CODE" ] || [ -z "$CURRENT_NAME" ]; then
    echo "❌ Error: Could not parse current version from $BUILD_FILE"
    exit 1
fi

# Parse version components
IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_NAME"

# Bump version based on argument
case "${1:-patch}" in
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        ;;
    patch)
        PATCH=$((PATCH + 1))
        ;;
    *)
        echo "Usage: $0 [patch|minor|major]"
        echo "  patch  - Bump patch version (1.0.2 → 1.0.3)"
        echo "  minor  - Bump minor version (1.0.2 → 1.1.0)"
        echo "  major  - Bump major version (1.0.2 → 2.0.0)"
        exit 1
        ;;
esac

NEW_CODE=$((CURRENT_CODE + 1))
NEW_NAME="${MAJOR}.${MINOR}.${PATCH}"

echo "📦 Bumping version..."
echo "   Version Code: $CURRENT_CODE → $NEW_CODE"
echo "   Version Name: $CURRENT_NAME → $NEW_NAME"

# Update build.gradle.kts
sed -i '' "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" "$BUILD_FILE"
sed -i '' "s/versionName = \"$CURRENT_NAME\"/versionName = \"$NEW_NAME\"/" "$BUILD_FILE"

# Verify changes
NEW_CODE_CHECK=$(grep -E 'versionCode = [0-9]+' "$BUILD_FILE" | grep -oE '[0-9]+')
NEW_NAME_CHECK=$(grep -E 'versionName = "' "$BUILD_FILE" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')

if [ "$NEW_CODE_CHECK" = "$NEW_CODE" ] && [ "$NEW_NAME_CHECK" = "$NEW_NAME" ]; then
    echo "✅ Version updated successfully!"
    echo ""
    echo "   Current version: $NEW_NAME (code: $NEW_CODE)"
else
    echo "❌ Error: Version update failed!"
    exit 1
fi
