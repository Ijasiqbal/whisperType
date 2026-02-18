# VoxType for macOS — Implementation Plan

## Context

VoxType (WhisperType) is an Android voice-to-text app that uses an Accessibility Service + floating overlay to let users transcribe speech into any text field via a volume button shortcut. This approach is unique on mobile — no competitor does it.

The goal is to build a **native macOS equivalent** that lives in the menu bar, uses a global hotkey (hold-to-record, release-to-transcribe), and inserts text into the currently focused field. It reuses the **existing Firebase backend** with no backend changes for core transcription.

**Decisions made:**
- Swift + SwiftUI (native macOS)
- Cloud transcription via existing Firebase backend
- Global hotkey trigger (default: Ctrl+Option)
- Shared Firebase Auth + subscription plans with Android

---

## Project Structure

```
VoxType-Mac/
├── VoxType.xcodeproj
├── VoxType/
│   ├── VoxTypeApp.swift              # App entry point (MenuBarExtra)
│   ├── Info.plist                     # Permissions, LSUIElement=true (no dock icon)
│   ├── Constants.swift                # API URLs, audio config, timeouts
│   ├── Assets.xcassets                # Menu bar icons, app icon
│   │
│   ├── Auth/
│   │   ├── AuthManager.swift          # Firebase Auth (Google/Apple sign-in)
│   │   └── KeychainHelper.swift       # Secure token storage
│   │
│   ├── Audio/
│   │   ├── AudioRecorder.swift        # AVAudioEngine recording (16kHz mono)
│   │   └── AudioConverter.swift       # Convert to WAV/M4A for API
│   │
│   ├── API/
│   │   ├── VoxTypeAPIClient.swift     # Backend communication (mirrors Android)
│   │   └── RegionSelector.swift       # Auto-select closest region
│   │
│   ├── Hotkey/
│   │   ├── HotkeyManager.swift        # Global hotkey registration (CGEvent tap)
│   │   └── HotkeyRecorder.swift       # UI for custom hotkey capture
│   │
│   ├── TextInsertion/
│   │   └── TextInsertionService.swift  # Clipboard + Cmd+V simulation
│   │
│   ├── Transcription/
│   │   └── TranscriptionService.swift  # Orchestrates record → upload → insert
│   │
│   ├── Usage/
│   │   └── UsageManager.swift          # Credit tracking, plan status
│   │
│   ├── UI/
│   │   ├── MenuBarView.swift           # Menu bar dropdown content
│   │   ├── RecordingOverlay.swift      # Floating recording indicator
│   │   ├── SettingsView.swift          # Preferences window
│   │   ├── OnboardingView.swift        # First-launch setup
│   │   └── LoginView.swift             # Firebase auth UI
│   │
│   └── Utilities/
│       ├── LaunchAtLogin.swift         # SMAppService for auto-start
│       └── PermissionChecker.swift     # Mic + Accessibility permission checks
│
├── GoogleService-Info.plist            # Firebase config
└── Package.swift / SPM dependencies
```

---

## Core Architecture

### 1. App Lifecycle — Menu Bar App (no dock icon)

```swift
@main
struct VoxTypeApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        MenuBarExtra("VoxType", systemImage: "mic.fill") {
            MenuBarView()
        }
        Settings {
            SettingsView()
        }
    }
}
```

- `Info.plist`: Set `LSUIElement = true` (hides from Dock)
- Uses SwiftUI `MenuBarExtra` (macOS 13+)
- `AppDelegate` handles setup: Firebase init, hotkey registration, permission checks

**Minimum macOS version: 13.0 (Ventura)** — required for MenuBarExtra and modern SwiftUI features.

---

### 2. Global Hotkey — Hold to Record, Release to Transcribe

**Approach: CGEvent tap** (most reliable for modifier-only hotkeys)

```swift
// HotkeyManager.swift
class HotkeyManager {
    private var eventTap: CFMachPort?

    func start() {
        // Requires Accessibility permission
        let mask: CGEventMask = (1 << CGEventType.flagsChanged.rawValue)
        eventTap = CGEvent.tapCreate(
            tap: .cgSessionEventTap,
            place: .headInsertEventTap,
            options: .defaultTap,
            eventsOfInterest: mask,
            callback: hotkeyCallback,
            userInfo: pointer
        )
        // Add to run loop
    }
}
```

**Default hotkey:** Ctrl+Option (both pressed simultaneously)
- **Key down** → start recording, show overlay
- **Key up** → stop recording, send to API, insert text

**Requires:** Accessibility permission (System Settings > Privacy & Security > Accessibility). App must prompt user on first launch.

**Alternative hotkeys users can configure:** Fn key, Globe key, Cmd+Shift, or any custom combo.

---

### 3. Audio Recording — AVAudioEngine

```swift
// AudioRecorder.swift
class AudioRecorder {
    private let engine = AVAudioEngine()
    private var audioBuffer = Data()

    func startRecording() {
        let inputNode = engine.inputNode
        let format = AVAudioFormat(
            commonFormat: .pcmFormatInt16,
            sampleRate: 16000,      // Whisper standard
            channels: 1,            // Mono
            interleaved: true
        )!

        inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
            // Append PCM data to audioBuffer
        }
        try engine.start()
    }

    func stopRecording() -> Data {
        engine.stop()
        engine.inputNode.removeTap(onBus: 0)
        return createWAVFile(from: audioBuffer)  // Wrap PCM in WAV header
    }
}
```

**Audio format sent to API:** WAV (simplest, no encoding library needed)
- 16kHz, mono, 16-bit PCM wrapped in WAV container
- `audioFormat: "wav"` in API request
- WAV is larger than Opus but avoids needing a native Opus encoder dependency
- For a typical 5-10 second recording, WAV is ~80-160KB — acceptable over WiFi/broadband

**Future optimization:** Add Opus encoding via `libopus` Swift wrapper if bandwidth becomes an issue.

---

### 4. API Client — Mirrors Android WhisperApiClient

```swift
// VoxTypeAPIClient.swift
class VoxTypeAPIClient {
    static let shared = VoxTypeAPIClient()

    private let session = URLSession.shared
    private let regions = ["us-central1", "asia-south1", "europe-west1"]

    func transcribe(audioData: Data, format: String = "wav", model: String = "whisper-large-v3-turbo") async throws -> TranscriptionResult {
        let base64Audio = audioData.base64EncodedString()
        let region = RegionSelector.bestRegion()
        let url = URL(string: "https://\(region)-whispertype-1de9f.cloudfunctions.net/transcribeAudioGroq")!

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(try await AuthManager.shared.getIDToken())", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 60

        let body: [String: Any] = [
            "audioBase64": base64Audio,
            "audioFormat": format,
            "model": model,
            "audioDurationMs": audioDurationMs
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        // Retry logic: 3 attempts, exponential backoff (1s, 2s, 4s)
        let (data, response) = try await session.data(for: request)
        return try JSONDecoder().decode(TranscriptionResult.self, from: data)
    }
}
```

**Region selection:** Based on system timezone (same logic as Android).

---

### 5. Text Insertion — Clipboard + Simulated Paste

```swift
// TextInsertionService.swift
class TextInsertionService {
    func insertText(_ text: String) {
        // 1. Save current clipboard
        let pasteboard = NSPasteboard.general
        let previousContents = pasteboard.string(forType: .string)

        // 2. Set transcribed text to clipboard
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)

        // 3. Simulate Cmd+V
        let source = CGEventSource(stateID: .hidSystemState)
        let keyDown = CGEvent(keyboardEventSource: source, virtualKey: 0x09, keyDown: true)  // V key
        keyDown?.flags = .maskCommand
        let keyUp = CGEvent(keyboardEventSource: source, virtualKey: 0x09, keyDown: false)
        keyUp?.flags = .maskCommand

        keyDown?.post(tap: .cghidEventTap)
        keyUp?.post(tap: .cghidEventTap)

        // 4. Restore previous clipboard after brief delay
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            if let prev = previousContents {
                pasteboard.clearContents()
                pasteboard.setString(prev, forType: .string)
            }
        }
    }
}
```

**Requires:** Accessibility permission (same as hotkey — one permission covers both).

---

### 6. Firebase Auth — Google + Apple Sign-In

Using **Firebase iOS SDK via SPM** (works on macOS via Catalyst support):

```swift
// AuthManager.swift
class AuthManager: ObservableObject {
    @Published var isSignedIn = false
    @Published var user: User?

    func signInWithGoogle() async throws {
        // Use ASWebAuthenticationSession for OAuth flow
        // Exchange credential with Firebase Auth
    }

    func signInWithApple() async throws {
        // Use ASAuthorizationAppleIDProvider
        // Exchange credential with Firebase Auth
    }

    func getIDToken() async throws -> String {
        guard let user = Auth.auth().currentUser else { throw AuthError.notSignedIn }
        return try await user.getIDToken()
    }
}
```

**Cross-platform subscription:** User signs in with same Google/Apple account on Mac and Android. Firebase Auth gives the same UID, so their subscription status and credits are shared automatically. No backend changes needed for auth.

---

## UI Components

### Menu Bar Dropdown
```
┌─────────────────────────┐
│  VoxType                │
│  ─────────────────────  │
│  ⏺ Hold Ctrl+Option    │
│     to record           │
│  ─────────────────────  │
│  Credits: 450 / 500     │
│  Plan: Free Trial       │
│  ─────────────────────  │
│  ⚙ Settings...          │
│  ⟳ Sign Out             │
│  ✕ Quit VoxType         │
└─────────────────────────┘
```

### Recording Overlay (floating window)
- Small pill-shaped overlay near menu bar
- Shows: mic icon + waveform animation + duration
- Appears on hotkey press, disappears after transcription
- `NSPanel` with `.floating` level, non-activating (doesn't steal focus)

### Settings Window
- **General**: Launch at login, hotkey configuration
- **Transcription**: Model selection (Groq Turbo / Groq Standard / OpenAI Premium)
- **Account**: Plan info, credits, sign out
- Standard macOS `Settings` scene via SwiftUI

### Onboarding (first launch)
1. Welcome screen
2. Sign in (Google / Apple)
3. Grant Microphone permission
4. Grant Accessibility permission (required for hotkey + text insertion)
5. Set preferred hotkey
6. Done — ready to use

---

## Dependencies (via Swift Package Manager)

| Package | Purpose |
|---------|---------|
| `firebase-ios-sdk` | Auth + Firestore (works on macOS) |
| `KeyboardShortcuts` (sindresorhus) | Optional: hotkey recording UI helper |

Minimal dependencies. Audio, networking, and text insertion all use native Apple frameworks.

---

## Backend Changes Required

### Minimal — only for Mac App Store billing (if needed later)

The existing backend works as-is for transcription and auth. The only change needed eventually:

1. **Add `POST /verifyAppleSubscription`** endpoint in `index.ts` — verifies App Store receipts using Apple's StoreKit Server API
2. **Add `platform` field** to `proSubscription` in Firestore schema
3. **Update `checkProStatus`** to re-verify with Apple or Google based on platform

**For MVP: skip billing.** Use existing free trial credits. Add Mac billing later once the app is validated.

---

## Implementation Phases

### Phase 1: Project Scaffold + Core Recording (Day 1-2)
- Create Xcode project, configure as menu bar app (`LSUIElement`)
- Add Firebase SDK via SPM, configure `GoogleService-Info.plist`
- Implement `AudioRecorder.swift` (AVAudioEngine, 16kHz mono WAV)
- Implement `Constants.swift` (mirror Android constants)
- Basic menu bar UI with status display

### Phase 2: Hotkey + Text Insertion (Day 2-3)
- Implement `HotkeyManager.swift` (CGEvent tap for Ctrl+Option)
- Implement `TextInsertionService.swift` (clipboard + Cmd+V simulation)
- Permission checking and prompting for Accessibility
- Recording overlay window

### Phase 3: API Client + Auth (Day 3-4)
- Implement `AuthManager.swift` (Firebase Google/Apple sign-in)
- Implement `VoxTypeAPIClient.swift` (transcribeAudioGroq endpoint)
- Implement `RegionSelector.swift` (timezone-based)
- Implement `UsageManager.swift` (credit tracking from API responses)
- Wire up full pipeline: hotkey → record → transcribe → insert

### Phase 4: UI Polish + Settings (Day 4-5)
- Settings window (hotkey config, model selection, account info)
- Onboarding flow
- Launch at login (SMAppService)
- Error handling, notification for failures
- Menu bar icon states (idle, recording, processing)

### Phase 5: Distribution (Day 5-6)
- Code signing with Developer ID
- Notarization via `notarytool`
- Create DMG for direct distribution
- (Later) Mac App Store submission if desired

---

## Key Technical Notes

1. **macOS 13+ minimum** — required for `MenuBarExtra` and modern SwiftUI
2. **Accessibility permission** — required once, covers both hotkey capture and text insertion
3. **Microphone permission** — standard macOS prompt on first use
4. **No Opus encoding for MVP** — WAV is simpler and sufficient over broadband. Add Opus later if needed.
5. **Firebase iOS SDK works on macOS** — via SPM, officially supported for macOS targets
6. **No backend changes for MVP** — transcription and auth work as-is. Only billing needs future work.
7. **Sandbox incompatible** — CGEvent tap requires non-sandboxed app, so direct distribution (DMG) is easier than Mac App Store initially

---

## Verification / Testing

1. **Build**: `xcodebuild -scheme VoxType -configuration Debug build`
2. **Hotkey**: Press Ctrl+Option → verify recording starts, release → verify it stops
3. **Audio**: Check recorded WAV is 16kHz mono 16-bit, plays correctly
4. **API**: Verify transcription response with valid Firebase auth token
5. **Text insertion**: Open TextEdit, trigger transcription, verify text appears
6. **Auth**: Sign in with Google → verify same UID as Android app
7. **Credits**: Verify credit deduction matches Android behavior
8. **Menu bar**: Verify icon states change (idle → recording → processing → idle)
