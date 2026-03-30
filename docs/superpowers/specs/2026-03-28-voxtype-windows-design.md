# Vozcribe Windows — Design Spec

## Overview

Windows desktop version of Vozcribe, a voice-to-text app that lives in the system tray. Users press a global hotkey to record audio, which is sent to the existing Firebase backend for transcription, and the result is inserted into the text field that was focused when recording started.

**Technology**: C# / WPF / .NET 8 (LTS)

## Core Principles

- **Speed**: Fast recording start, fast encoding (Opus), fast upload, minimal latency end-to-end.
- **Reliable text insertion**: Capture the exact text field reference on hotkey press. Insert there regardless of what the user does during recording. Direct insertion via UI Automation preferred over clipboard paste.
- **Minimal footprint**: System tray app, no main window. Lightweight overlay appears only during recording/transcription.
- **Feature parity+**: Same core features as the Mac app, with improvements where Windows APIs allow (direct text field insertion, exact element capture, smart overlay dismiss).

## Architecture

### Pattern

MVVM with service layer. ViewModels expose observable state to WPF views. Services are singleton instances managing business logic.

### Project Structure

```
Vozcribe-Windows/
├── Vozcribe.sln
├── Vozcribe/
│   ├── App.xaml                          # Entry point, single-instance Mutex
│   ├── App.xaml.cs
│   ├── Models/
│   │   ├── TranscriptionResult.cs        # API response model
│   │   ├── RecordingState.cs             # Enum: Idle, Recording, Processing, Success, Error
│   │   ├── ModelTier.cs                  # Enum: Auto, Standard, Premium
│   │   ├── InsertionMode.cs             # Enum: WhereStarted, WhereCurrent
│   │   └── PendingTranscription.cs       # Saved failed transcription metadata
│   ├── Services/
│   │   ├── AudioService.cs              # WASAPI capture + Opus encoding
│   │   ├── SilenceDetector.cs           # RMS analysis + speech segment extraction
│   │   ├── HotkeyService.cs            # Global keyboard hooks + registration
│   │   ├── TextInsertionService.cs      # UI Automation + clipboard fallback
│   │   ├── TranscriptionService.cs      # Orchestration: record → transcribe → insert
│   │   ├── ApiClient.cs                 # HTTP client to Firebase backend
│   │   ├── AuthService.cs              # Google OAuth → Firebase token
│   │   ├── PendingTranscriptionService.cs # Local save/retry for failures
│   │   ├── SettingsService.cs           # JSON settings persistence
│   │   └── StartupService.cs            # Registry-based launch at login
│   ├── ViewModels/
│   │   ├── TrayPopupViewModel.cs
│   │   ├── RecordingOverlayViewModel.cs
│   │   ├── SettingsViewModel.cs
│   │   └── OnboardingViewModel.cs
│   ├── Views/
│   │   ├── TrayPopup.xaml               # Rich system tray flyout
│   │   ├── RecordingOverlay.xaml         # Floating draggable overlay
│   │   ├── SettingsWindow.xaml           # Single-page settings
│   │   └── OnboardingWindow.xaml         # First-run wizard
│   ├── Converters/                       # WPF value converters
│   ├── Resources/
│   │   ├── Styles.xaml                  # Global styles + modern theme
│   │   └── Icons/                       # Tray icon, app icon assets
│   └── Utilities/
│       ├── PermissionHelper.cs          # Microphone permission check
│       └── Win32Interop.cs              # P/Invoke declarations
```

## Core Flow

### Recording & Transcription Pipeline

1. User presses global hotkey.
2. `TextInsertionService` captures `AutomationElement.FocusedElement` — the exact text field reference.
3. Recording overlay appears at saved position (default: bottom-right).
4. `AudioService` starts WASAPI capture (16kHz mono 16-bit PCM).
5. `ApiClient` fires endpoint warmup requests in parallel (cold start mitigation).
6. User presses hotkey again to stop.
7. `SilenceDetector` trims dead air from audio (speech segments only).
8. `AudioService` encodes to Opus via Concentus.
9. `ApiClient` sends base64-encoded audio to Firebase backend with auth token + model tier code.
10. Transcription result received.
11. **Text insertion** (based on user setting):
    - "Paste where I started" → use stored `AutomationElement` reference.
    - "Paste where I am now" → get current `AutomationElement.FocusedElement`.
12. **Insertion attempt**:
    - Try `ValuePattern.SetValue()` on the target element (direct insertion, no clipboard).
    - If element doesn't support `ValuePattern` → fall back to: set focus on element, save clipboard, copy text to clipboard, simulate Ctrl+V via `SendInput`, restore original clipboard.
13. **Overlay behavior**:
    - Direct insertion confirmed → 2-second green checkmark + text preview, then auto-dismiss.
    - Clipboard fallback used → show text for 6 seconds with copy button, then auto-dismiss.
    - No text field available → show transcribed text with copy button, stay until user dismisses.

### Model Switching

- Shift+Up / Shift+Down cycles through model tiers (Auto → Standard → Premium → Auto).
- Overlay briefly flashes the new model name.
- Can switch during any state (idle, recording, processing).
- Model tier codes sent to backend: `auto`, `standard`, `premium` (backend resolves to actual models).

### Failure Handling

- Transcription fails → overlay shows error + "Retry" and "Save for Later" buttons.
- "Save for Later" persists audio + metadata to `%AppData%/Vozcribe/pending/`.
- Pending transcriptions accessible from tray popup, individually retryable.
- Max 50 pending transcriptions stored (oldest purged).

## UI Components

### 1. System Tray Popup

Left-click on tray icon opens a custom WPF popup (not a native context menu).

**Contents** (top to bottom):
- Status indicator: colored dot + text (Idle / Recording / Processing)
- Current model: label + dropdown to switch
- Credits: visual progress bar + remaining count
- Hotkey reminder: shows current hotkey binding
- Pending transcriptions: count badge, click to expand list with retry buttons
- Settings button
- Quit button

Right-click on tray icon: minimal context menu (Settings, Quit).

### 2. Recording Overlay

Floating, always-on-top, draggable, pill-shaped transparent window.

**States:**
- **Recording**: Pulsing red indicator + amplitude visualization (8 bars) + duration timer + model label
- **Processing**: Spinner animation + "Transcribing..."
- **Success (direct insert)**: Green checkmark + truncated text preview → auto-dismiss 2s
- **Success (clipboard fallback)**: Text preview + copy button → auto-dismiss 6s
- **Error**: Error message + Retry / Save for Later buttons

**Behavior:**
- Position saved to settings, remembered between sessions.
- Draggable by clicking anywhere on the overlay.
- Default position: bottom-right, above the taskbar.

### 3. Settings Window

Single-page window with grouped sections:

**Shortcuts**
- Hotkey: dropdown with presets (Ctrl+Shift, Ctrl+Alt, Win+Shift, F5) + "Record custom shortcut" button
- Insertion behavior: radio buttons — "Paste where I started recording" (default) / "Paste in currently active field"

**Transcription**
- Model: dropdown (Auto, Standard, Premium)
- Region: dropdown (US Central, Asia South, Europe West)

**General**
- Launch at startup: toggle switch
- Reset overlay position: button

**Account**
- Email display
- Plan + credits remaining
- Sign out button

### 4. Onboarding Window

3-step wizard on first launch:

1. **Welcome + Sign In**: App intro + "Sign in with Google" button (opens system browser)
2. **Microphone Permission**: Request mic access, show status
3. **Setup Complete**: Pick a hotkey, brief usage instructions, "Start Using Vozcribe" button

No accessibility permission step needed — Windows UI Automation doesn't require explicit user permission.

## Technical Details

### Audio

- **Capture**: NAudio WASAPI loopback-free capture, 16kHz sample rate, mono, 16-bit PCM.
- **Silence Detection**: RMS dB calculation per chunk. Threshold: -40 dB. Minimum silence duration: 500ms. Merges speech segments closer than 500ms.
- **Trimming**: Extracts speech segments with 150ms pre-padding and 200ms post-padding. Applied when savings exceed 10%.
- **Encoding**: Concentus (pure .NET Opus encoder). Target bitrate: 32kbps. No native dependencies.
- **Output**: Base64-encoded Opus audio sent to backend with `audioFormat: "opus"`.

### Global Hotkeys

- **Low-level keyboard hook**: `SetWindowsHookEx(WH_KEYBOARD_LL)` for capturing all key events system-wide.
- **Predefined hotkeys**: Ctrl+Shift, Ctrl+Alt, Win+Shift, F5, F8.
- **Custom binding**: In settings, "Record custom shortcut" captures the next key combination pressed.
- **Model cycling**: Shift+Up and Shift+Down registered as separate hooks.
- **Conflict detection**: Warn user if chosen hotkey conflicts with known system shortcuts.

### Text Insertion

- **Capture on hotkey press**: `AutomationElement.FocusedElement` returns a reference to the exact focused UI element. Stored as a field on `TextInsertionService`.
- **Direct insertion**: Check if element supports `ValuePattern`. If yes, `ValuePattern.SetValue()` to insert text. Check `TextPattern` as well for richer text fields.
- **Clipboard fallback**: Save current clipboard → copy transcribed text to clipboard → `SetForegroundWindow` to bring target app to front → `SendInput` simulates Ctrl+V → wait 100ms → restore original clipboard.
- **Detection of success**: After `ValuePattern.SetValue()`, read back the value to confirm. For clipboard fallback, success is assumed.

### Authentication

- **Google OAuth 2.0**: Open system browser to Google consent screen. Local HTTP listener on `http://localhost:{random_port}` for redirect callback.
- **Token exchange**: Auth code → Firebase REST API `signInWithIdp` → Firebase ID token + refresh token.
- **Storage**: Tokens stored in Windows Credential Manager (`CredWrite` / `CredRead`). Not plain text files.
- **Auto-refresh**: Check token expiry before each API call. Refresh via Firebase token refresh endpoint if within 5 minutes of expiry.

### API Communication

- **Base URL**: Firebase Cloud Functions (same as Mac/Android).
- **Endpoints used**:
  - `POST /transcribeAuto` — Auto tier transcription
  - `POST /transcribeStandard` — Standard tier transcription
  - `POST /transcribePremium` — Premium tier transcription
  - `GET /getTrialStatus` — check credits
  - `GET /getSubscriptionStatus` — billing status
  - `GET /health` — endpoint warmup
- **Auth**: Bearer token (Firebase ID token) in Authorization header.
- **Retry**: Max 3 retries on 408/502/503/504. Exponential backoff: 1s → 2s → 4s.
- **Timeouts**: 30s connect, 60s response.
- **Opaque tier codes**: `auto`, `standard`, `premium`, `standard_v2` sent as model identifiers. Never send real model names.

### Persistence

- **Settings**: `%AppData%/Vozcribe/settings.json` — hotkey, model, region, insertion mode, overlay position, launch at startup.
- **Pending transcriptions**: `%AppData%/Vozcribe/pending/` — `{guid}.opus` audio files + `{guid}.json` metadata (timestamp, model, duration, error message).
- **Auth tokens**: Windows Credential Manager (encrypted by OS).
- **Max pending**: 50 entries. Oldest purged when limit reached.

### Startup & Lifecycle

- **Single instance**: Named Mutex (`Vozcribe_SingleInstance`). Second launch activates existing instance.
- **Launch at login**: Registry key `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` pointing to exe path.
- **Tray icon**: `System.Windows.Forms.NotifyIcon` (WPF doesn't have native tray support, this is the standard approach).
- **Exit**: Right-click tray → Quit, or from settings. Cleans up hooks and audio resources.

## Dependencies

| Package | Purpose |
|---------|---------|
| NAudio | WASAPI audio capture |
| Concentus | Opus encoding (pure .NET) |
| Hardcodet.NotifyIcon.Wpf | WPF system tray icon |
| System.Text.Json | JSON serialization |
| .NET 8 built-in HttpClient | API communication |
| .NET 8 built-in UIAutomationClient | Text field detection + insertion |

No Firebase SDK dependency — all Firebase interaction via REST APIs.

## V1 Exclusions

- No Apple sign-in (Google only)
- No auto-update mechanism
- No MSI installer (standalone executable)
- No multi-language UI (English only)
- No tray icon animations
