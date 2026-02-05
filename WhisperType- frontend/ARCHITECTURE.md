# VoxType Android Architecture

Detailed technical architecture of the VoxType Android app.

## Table of Contents

1. [System Overview](#system-overview)
2. [Core Services](#core-services)
3. [Audio Pipeline](#audio-pipeline)
4. [Transcription Flows](#transcription-flows)
5. [UI Architecture](#ui-architecture)
6. [Data Layer](#data-layer)
7. [Authentication](#authentication)
8. [Billing System](#billing-system)
9. [Remote Configuration](#remote-configuration)

---

## System Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              VoxType Architecture                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐     ┌─────────────────┐     ┌─────────────────────────┐   │
│  │   User App   │     │  VoxType A11y   │     │    OverlayService       │   │
│  │ (WhatsApp,   │────▶│    Service      │────▶│  (Floating Mic UI)      │   │
│  │  Notes, etc) │     │ (Key Detection) │     │                         │   │
│  └──────────────┘     └─────────────────┘     └───────────┬─────────────┘   │
│                                                           │                  │
│                              ┌─────────────────────────────┘                 │
│                              ▼                                               │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                         Audio Recording Layer                          │  │
│  │  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────────┐  │  │
│  │  │  AudioRecorder  │  │ RealtimeRmsRec.  │  │ ParallelOpusRecorder │  │  │
│  │  │  (MediaRecorder)│  │ (AudioRecord+RMS)│  │ (AudioRecord+Opus)   │  │  │
│  │  └─────────────────┘  └──────────────────┘  └──────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                              │                                               │
│                              ▼                                               │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                      SpeechRecognitionHelper                           │  │
│  │  - Orchestrates recording flow                                         │  │
│  │  - Selects transcription method based on ModelTier                     │  │
│  │  - Handles silence trimming                                            │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                              │                                               │
│                              ▼                                               │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                       WhisperApiClient                                 │  │
│  │  - HTTP client (OkHttp singleton)                                      │  │
│  │  - Multipart audio upload                                              │  │
│  │  - Auth token injection                                                │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                              │                                               │
│                              ▼                                               │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                     Firebase Cloud Functions                           │  │
│  │  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────────┐  │  │
│  │  │ transcribeAudio │  │transcribeAudioGrq│  │   getTrialStatus     │  │  │
│  │  │    (OpenAI)     │  │     (Groq)       │  │                      │  │  │
│  │  └─────────────────┘  └──────────────────┘  └──────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                              │                                               │
│                              ▼                                               │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │               WhisperTypeAccessibilityService.insertText()             │  │
│  │  - Finds focused editable node                                         │  │
│  │  - ACTION_SET_TEXT or clipboard fallback                               │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Core Services

### WhisperTypeAccessibilityService

**Location**: `service/WhisperTypeAccessibilityService.kt`

The heart of the app. This Android AccessibilityService:

1. **Detects Volume Shortcuts**
   - Intercepts `onKeyEvent()` for volume button presses
   - Supports three modes:
     - `DOUBLE_VOLUME_UP`: Double-press volume up within 350ms
     - `DOUBLE_VOLUME_DOWN`: Double-press volume down within 350ms
     - `BOTH_VOLUME_BUTTONS`: Press both within 300ms

2. **Manages Overlay Lifecycle**
   - Starts/stops `OverlayService` via intents
   - Provides `ACTION_TOGGLE` to show/hide floating UI

3. **Inserts Transcribed Text**
   - Traverses accessibility tree to find focused editable node
   - Primary: `ACTION_SET_TEXT` with Bundle argument
   - Fallback: Clipboard + `ACTION_PASTE`
   - Handles placeholder text detection for apps like WhatsApp

**Key Methods**:
```kotlin
fun findFocusedEditableNode(): AccessibilityNodeInfo?
fun insertText(text: String): Boolean
fun triggerOverlay()
```

**Singleton Pattern**:
```kotlin
companion object {
    var instance: WhisperTypeAccessibilityService? = null
    fun isRunning(): Boolean = isConnected
}
```

### OverlayService

**Location**: `service/OverlayService.kt`

Foreground service that displays the floating microphone button:

1. **Window Management**
   - Uses `WindowManager` with `TYPE_APPLICATION_OVERLAY` (API 26+)
   - Fallback to `TYPE_PHONE` for API 24-25
   - Draggable button with touch handling

2. **Recording States**
   ```
   IDLE → RECORDING → PROCESSING → IDLE
           │              │
           └──(cancel)────┘
   ```

3. **Visual Feedback**
   - Waveform visualization (`CircularWaveformView`)
   - Pulse animation during recording
   - State-specific colors and icons

**Actions**:
- `ACTION_SHOW`: Display overlay
- `ACTION_HIDE`: Remove overlay
- `ACTION_TOGGLE`: Toggle visibility

---

## Audio Pipeline

### Recording Options

The app offers multiple recording strategies optimized for different scenarios:

#### 1. AudioRecorder (Legacy)
**File**: `audio/AudioRecorder.kt`

- Uses `MediaRecorder` API
- Outputs AAC in M4A container
- Simple but no parallel processing

#### 2. RealtimeRmsRecorder
**File**: `audio/RealtimeRmsRecorder.kt`

- Uses `AudioRecord` for raw PCM capture
- Real-time RMS (Root Mean Square) calculation
- Identifies silence segments during recording
- Outputs WAV format

#### 3. ParallelOpusRecorder (Recommended)
**File**: `audio/ParallelOpusRecorder.kt`

- Uses `AudioRecord` with parallel processing
- Simultaneous RMS analysis AND Opus encoding
- 90% smaller file size vs WAV
- Requires Android 10+ (API 29)
- Outputs OGG/Opus format

```
┌─────────────────────────────────────────────────────────────────┐
│                    ParallelOpusRecorder Pipeline                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Microphone → AudioRecord (16kHz, Mono, 16-bit)                 │
│                    │                                             │
│                    ▼                                             │
│           ┌───────────────────┐                                  │
│           │  Concurrent Jobs  │                                  │
│           ├───────────────────┤                                  │
│           │ 1. RMS Analysis   │──▶ Silence Timestamps           │
│           │ 2. Opus Encoding  │──▶ OGG File                     │
│           └───────────────────┘                                  │
│                    │                                             │
│                    ▼                                             │
│           Audio ready for upload                                 │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Audio Constants

**File**: `Constants.kt`

```kotlin
// Sample rate for speech recognition
const val AUDIO_SAMPLE_RATE = 16000

// Bitrates
const val AUDIO_BIT_RATE_AAC = 64000   // AAC fallback
const val AUDIO_BIT_RATE_OPUS = 24000  // Opus (speech-optimized)

// Silence detection
const val SILENCE_THRESHOLD_DB = -40f
const val MIN_SILENCE_DURATION_MS = 500L
```

---

## Transcription Flows

**File**: `speech/TranscriptionFlow.kt`

Each flow represents a different transcription pipeline:

| Flow | Recorder | Encoding | Backend | Credit Multiplier |
|------|----------|----------|---------|-------------------|
| `FLOW_3` | MediaRecorder | AAC | Groq Turbo | 0x (Free) |
| `GROQ_WHISPER` | MediaRecorder | AAC | Groq Large | 1x |
| `FLOW_4` | MediaRecorder | AAC | OpenAI Mini | 1x |
| `ARAMUS_OPENAI` | RealtimeRms | WAV | OpenAI Mini | 1x |
| `PARALLEL_OPUS` | ParallelOpus | Opus | OpenAI Mini | 2x |

### Flow Selection Logic

```kotlin
fun fromModelTier(tier: ModelTier): TranscriptionFlow {
    return when (tier) {
        ModelTier.AUTO -> FLOW_3         // Free tier
        ModelTier.STANDARD -> GROQ_WHISPER
        ModelTier.PREMIUM -> PARALLEL_OPUS
    }
}
```

### SpeechRecognitionHelper

**File**: `speech/SpeechRecognitionHelper.kt`

Orchestrates the complete recording-to-transcription flow:

1. Determines which recorder to use based on selected flow
2. Starts recording with appropriate configuration
3. Collects RMS data for silence detection (if applicable)
4. Stops recording and prepares audio file
5. Calls `WhisperApiClient` to upload and transcribe
6. Returns transcript or error via callback

---

## UI Architecture

### MVVM Pattern

```
┌──────────────┐     ┌──────────────┐     ┌──────────────────┐
│    Screen    │────▶│  ViewModel   │────▶│   Repository     │
│  (Compose)   │◀────│              │◀────│                  │
└──────────────┘     └──────────────┘     └──────────────────┘
      │                     │                      │
      │                     │                      │
   UI State            Business Logic         Data Source
   (StateFlow)         (Coroutines)      (API, Firebase, DB)
```

### Screens

| Screen | ViewModel | Purpose |
|--------|-----------|---------|
| `LoginScreen` | `LoginViewModel` | Google Sign-In, guest login |
| `MainScreen` | `MainViewModel` | Permission setup, shortcuts |
| `PlanScreen` | `PlanViewModel` | Subscription plans |
| `ProfileScreen` | `ProfileViewModel` | User info, settings |

### State Management

ViewModels expose state via `StateFlow`:

```kotlin
class MainViewModel : ViewModel() {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun refreshUserStatus() {
        viewModelScope.launch {
            // Update state
        }
    }
}
```

Compose collects state:

```kotlin
val authState by mainViewModel.authState.collectAsStateWithLifecycle()
```

### Navigation

Bottom navigation with three tabs:

```kotlin
enum class BottomNavTab(val label: String) {
    HOME("Home"),
    PROFILE("Profile"),
    PLAN("Pricing")
}
```

Navigation state managed in `AppWithBottomNav` composable.

---

## Data Layer

### Repository Pattern

Repositories abstract data sources from ViewModels:

```
┌─────────────────────────────────────────────────────────────────┐
│                         Repositories                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────┐  │
│  │ AuthRepository  │    │ UserRepository  │    │ BillingRepo │  │
│  │                 │    │                 │    │             │  │
│  │ - signIn()      │    │ - getProfile()  │    │ - purchase()│  │
│  │ - signOut()     │    │ - getUsage()    │    │ - restore() │  │
│  │ - getUser()     │    │ - updateTier()  │    │             │  │
│  └────────┬────────┘    └────────┬────────┘    └──────┬──────┘  │
│           │                      │                     │         │
│           ▼                      ▼                     ▼         │
│     Firebase Auth          WhisperApiClient      Play Billing   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Dependency Injection (Hilt)

**File**: `di/RepositoryModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepository {
        return AuthRepositoryImpl()
    }
}
```

### Local Storage

**SharedPreferences** used for:
- Shortcut mode preference
- Foreground service toggle
- MIUI setup prompt state
- Selected transcription flow

```kotlin
object Constants {
    const val PREFS_NAME = "whispertype_prefs"
}
```

---

## Authentication

### Firebase Auth Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                     Authentication Flow                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. User taps "Sign in with Google"                             │
│                    │                                             │
│                    ▼                                             │
│  2. CredentialManager.getCredential() → Google Sign-In prompt   │
│                    │                                             │
│                    ▼                                             │
│  3. GoogleIdToken received                                       │
│                    │                                             │
│                    ▼                                             │
│  4. Firebase Auth signInWithCredential(GoogleAuthProvider)      │
│                    │                                             │
│                    ▼                                             │
│  5. FirebaseUser created → AuthState.Authenticated              │
│                    │                                             │
│                    ▼                                             │
│  6. ID Token cached for API calls                               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### AuthState

**File**: `auth/AuthState.kt`

```kotlin
sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
}
```

### Guest Login

Controlled via Firebase Remote Config:

```kotlin
// In BuildConfig (debug only)
buildConfigField("Boolean", "ENABLE_GUEST_LOGIN", "true")

// Remote Config can override
RemoteConfigManager.isGuestLoginEnabled
```

---

## Billing System

### Google Play Billing Integration

**Files**: `billing/BillingManager.kt`, `billing/IBillingManager.kt`

```
┌─────────────────────────────────────────────────────────────────┐
│                    Billing Flow                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. App startup → BillingManager.initialize()                   │
│                    │                                             │
│                    ▼                                             │
│  2. BillingClient.startConnection()                             │
│                    │                                             │
│                    ▼                                             │
│  3. queryProductDetailsAsync("whispertype_pro_monthly")         │
│                    │                                             │
│                    ▼                                             │
│  4. User taps "Upgrade"                                          │
│                    │                                             │
│                    ▼                                             │
│  5. launchBillingFlow() → Google Play purchase sheet            │
│                    │                                             │
│                    ▼                                             │
│  6. PurchasesUpdatedListener receives purchase                  │
│                    │                                             │
│                    ▼                                             │
│  7. Backend verification via verifySubscription()               │
│                    │                                             │
│                    ▼                                             │
│  8. acknowledgePurchase() → Complete                            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Product SKU

```kotlin
const val PRO_SUBSCRIPTION_ID = "whispertype_pro_monthly"
```

### Debug vs Release

Factory pattern selects implementation:

```kotlin
object BillingManagerFactory {
    fun create(context: Context): IBillingManager {
        return if (BuildConfig.DEBUG) {
            MockBillingManager()  // Simulates purchases
        } else {
            BillingManager(context)  // Real Play Billing
        }
    }
}
```

---

## Remote Configuration

### Firebase Remote Config

**File**: `config/RemoteConfigManager.kt`

Manages dynamic configuration without app updates:

| Key | Type | Purpose |
|-----|------|---------|
| `pro_price_display` | String | Price shown in UI |
| `pro_minutes_limit` | Int | Pro plan minutes |
| `force_update_version` | Int | Minimum version required |
| `guide_video_url` | String | Setup tutorial URL |
| `guest_login_enabled` | Boolean | Enable guest auth |

### Update Checking

```kotlin
object ForceUpdateChecker {
    enum class UpdateStatus {
        UP_TO_DATE,
        SOFT_UPDATE,
        FORCE_UPDATE
    }

    fun checkUpdateStatus(
        currentVersionCode: Int,
        config: UpdateConfig
    ): UpdateStatus
}
```

---

## Threading Model

### Coroutine Usage

All async work uses Kotlin Coroutines:

```kotlin
// ViewModel scope for UI-related work
viewModelScope.launch {
    val result = repository.fetchData()
    _state.value = result
}

// IO dispatcher for network/disk
withContext(Dispatchers.IO) {
    apiClient.uploadAudio(file)
}

// Default dispatcher for CPU-intensive work
withContext(Dispatchers.Default) {
    audioProcessor.encode(samples)
}
```

### Thread Safety

Volatile annotations for cross-thread state:

```kotlin
companion object {
    @Volatile
    private var isConnected: Boolean = false
}
```

---

## Error Handling

### API Errors

```kotlin
WhisperApiClient().transcribe(
    authToken = token,
    audioFile = file,
    onSuccess = { transcript ->
        // Handle success
    },
    onError = { errorMessage ->
        // Display error to user
        // Log for debugging
    }
)
```

### Graceful Degradation

- If Opus encoding unavailable → fallback to AAC
- If `ACTION_SET_TEXT` fails → clipboard + paste
- If network unavailable → queue for retry

---

## Memory Management

### WeakReferences

Used for Activity/Context references in services:

```kotlin
private var contextRef: WeakReference<Context>? = null
```

### Resource Cleanup

```kotlin
override fun onDestroy() {
    audioRecorder?.release()
    accessibilityButtonCallback?.let {
        controller?.unregisterAccessibilityButtonCallback(it)
    }
    super.onDestroy()
}
```

### OkHttp Singleton

Single client instance reused across app:

```kotlin
companion object {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}
```

---

## Security Considerations

1. **No Sensitive Data Logging**: Content from accessibility events not logged
2. **Secure Token Handling**: Firebase ID tokens not persisted long-term
3. **ProGuard Obfuscation**: Release builds use R8 minification
4. **Billing Verification**: Server-side purchase verification

---

## Device-Specific Handling

### MIUI (Xiaomi/Redmi/POCO)

**File**: `util/MiuiHelper.kt`

MIUI requires additional setup:
- AutoStart permission for reliable background service
- Custom battery optimization settings

```kotlin
object MiuiHelper {
    fun isMiuiDevice(): Boolean
    fun openAutoStartSettings(context: Context)
}
```

### API Level Adaptations

- API 24-25: `TYPE_PHONE` window type
- API 26+: `TYPE_APPLICATION_OVERLAY`, Accessibility Button
- API 29+: Opus encoding via `MediaCodec`
- API 33+: POST_NOTIFICATIONS permission

---

## Build Configuration

### Gradle Setup

**File**: `app/build.gradle.kts`

```kotlin
android {
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
        versionCode = 18
        versionName = "1.0.18"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
}
```

### Size Optimizations

- R8 code shrinking (40-60% reduction)
- Resource shrinking (unused drawables removed)
- English-only resources
- ABI splits for native libraries
- Metadata file exclusions
