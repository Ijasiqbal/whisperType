# VoxType Android Development Setup

Complete guide for setting up the development environment and building the app.

## Prerequisites

### Required Software

| Software | Version | Purpose |
|----------|---------|---------|
| Android Studio | Hedgehog (2023.1.1)+ | IDE |
| JDK | 17 | Compilation |
| Android SDK | 35 | Target platform |
| Git | Latest | Version control |

### Firebase Project

You need a Firebase project with:
- Firebase Authentication (Google Sign-In enabled)
- Firebase Remote Config
- Cloud Firestore (for backend)
- Cloud Functions (for transcription API)

## Setup Steps

### 1. Clone Repository

```bash
git clone <repository-url>
cd whisperType
```

### 2. Open in Android Studio

1. Open Android Studio
2. Select "Open" and navigate to `WhisperType- frontend` folder
3. Wait for Gradle sync to complete

### 3. Configure Firebase

#### Get google-services.json

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select your project
3. Click gear icon → Project settings
4. Under "Your apps", select the Android app
5. Download `google-services.json`
6. Place it in `WhisperType- frontend/app/`

#### Configure SHA-1 for Google Sign-In

1. Get your debug SHA-1:
   ```bash
   cd "WhisperType- frontend"
   ./gradlew signingReport
   ```
2. Copy the SHA-1 from the `debug` variant
3. In Firebase Console → Project settings → Your apps
4. Add the SHA-1 fingerprint

### 4. Build Configuration

#### Debug Build

Debug builds have:
- Guest login enabled
- Mock billing (no real purchases)
- No minification (faster builds)

```bash
./gradlew assembleDebug
```

#### Release Build

Release builds have:
- Guest login disabled
- Real Google Play Billing
- R8 minification and obfuscation

```bash
./gradlew assembleRelease
```

### 5. Run the App

1. Connect an Android device (or start an emulator)
2. Ensure USB debugging is enabled
3. Click "Run" (green play button) in Android Studio

## Project Configuration

### Build Variants

| Variant | Billing | Guest Login | Minification |
|---------|---------|-------------|--------------|
| `debug` | Mock | Enabled | Off |
| `release` | Real | Disabled | On |

### Key Files

| File | Purpose |
|------|---------|
| `app/build.gradle.kts` | Build configuration, dependencies |
| `app/google-services.json` | Firebase config (not in git) |
| `app/proguard-rules.pro` | ProGuard/R8 rules |
| `app/src/main/AndroidManifest.xml` | App manifest, permissions |

### Dependencies

Main dependencies (see `build.gradle.kts` for versions):

```kotlin
// Core
implementation("androidx.core:core-ktx")
implementation("androidx.lifecycle:lifecycle-runtime-ktx")

// Compose
implementation(platform("androidx.compose:compose-bom"))
implementation("androidx.compose.material3:material3")

// Hilt DI
implementation("com.google.dagger:hilt-android")
ksp("com.google.dagger:hilt-compiler")

// Firebase
implementation(platform("com.google.firebase:firebase-bom"))
implementation("com.google.firebase:firebase-auth-ktx")
implementation("com.google.firebase:firebase-config-ktx")

// Networking
implementation("com.squareup.okhttp3:okhttp")

// Billing
implementation("com.android.billingclient:billing-ktx")
```

## Testing on Device

### Required Permissions

When testing, you'll need to grant:

1. **Accessibility Service**: Settings → Accessibility → VoxType
2. **Overlay Permission**: Granted via in-app prompt
3. **Microphone**: Granted via in-app prompt

### Testing Volume Shortcuts

1. Grant all permissions
2. Open any app with a text field (e.g., Notes)
3. Tap on the text field
4. Double-press volume up (or your selected shortcut)
5. Floating mic should appear

### Testing Transcription

1. Tap the floating mic
2. Speak clearly
3. Tap again to stop
4. Wait for processing
5. Text should appear in the field

## Debugging

### Logcat Filters

Useful tags for debugging:

```
WhisperTypeA11y    # Accessibility service logs
OverlayService     # Overlay service logs
AudioRecorder      # Audio recording logs
WhisperApiClient   # API communication logs
TranscriptionFlow  # Flow selection logs
```

### Common Issues

#### Accessibility Service Not Detecting

- Check if service is enabled in Settings
- Verify `FLAG_REQUEST_FILTER_KEY_EVENTS` is set
- Some devices require additional battery optimization exemptions

#### Overlay Not Appearing

- Verify overlay permission is granted
- Check for conflicting apps with overlay permissions
- On some devices, enable "Display pop-up windows" permission

#### Transcription Failing

- Check network connectivity
- Verify Firebase Auth token is valid
- Check backend logs for API errors
- Ensure audio file is not empty/corrupted

#### MIUI Devices (Xiaomi/Redmi/POCO)

Enable these additional settings:
1. AutoStart permission
2. "Display pop-up windows" permission
3. Battery saver exemption

## Build Optimizations

### APK Size Reduction

Current optimizations (enabled in release):

- R8 minification: ~40-60% code reduction
- Resource shrinking: Removes unused resources
- English-only: `resourceConfigurations += listOf("en")`
- ABI splits: Only arm64-v8a, armeabi-v7a, x86_64

### Build Speed

For faster debug builds:

```properties
# gradle.properties
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
```

## Signing for Release

### Create Keystore

```bash
keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias voxtype
```

### Configure Signing

In `app/build.gradle.kts`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("path/to/release-key.jks")
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = "voxtype"
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}

buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
    }
}
```

## Continuous Integration

### GitHub Actions Example

```yaml
name: Android Build

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build
        run: ./gradlew assembleDebug
```

## Useful Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Build release AAB (for Play Store)
./gradlew bundleRelease

# Run lint checks
./gradlew lint

# Clean build
./gradlew clean

# List signing info
./gradlew signingReport

# Check dependency updates
./gradlew dependencyUpdates
```

## Troubleshooting

### Gradle Sync Failed

1. File → Invalidate Caches and Restart
2. Delete `.gradle` and `build` folders
3. Sync again

### Missing google-services.json

Build will fail with:
```
File google-services.json is missing
```

Solution: Download from Firebase Console and place in `app/`

### KSP Errors

If Hilt KSP fails:
1. Clean project
2. Invalidate caches
3. Rebuild

### Compose Preview Not Working

1. Build the project first
2. Use `@Preview` annotation with default parameters
3. Check for missing resources
