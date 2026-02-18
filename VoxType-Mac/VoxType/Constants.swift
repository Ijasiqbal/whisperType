import Foundation

enum Constants {

    // MARK: - API

    static let firebaseProjectID = "whispertype-1de9f"
    static let regions = ["us-central1", "asia-south1", "europe-west1"]

    static func baseURL(for region: String) -> String {
        "https://\(region)-\(firebaseProjectID).cloudfunctions.net"
    }

    // MARK: - Endpoints

    static let transcribeGroqPath = "/transcribeAudioGroq"
    static let transcribeOpenAIPath = "/transcribeAudio"
    static let trialStatusPath = "/getTrialStatus"
    static let subscriptionStatusPath = "/getSubscriptionStatus"
    static let healthPath = "/health"

    // MARK: - Timeouts (seconds)

    static let apiConnectTimeout: TimeInterval = 30
    static let apiReadTimeout: TimeInterval = 60
    static let apiWriteTimeout: TimeInterval = 30

    // MARK: - Retry

    static let maxRetries = 3
    static let initialBackoffMs = 1000
    static let backoffMultiplier = 2
    static let maxBackoffMs = 8000
    static let retryableStatusCodes: Set<Int> = [408, 502, 503, 504]

    // MARK: - Audio

    static let audioSampleRate: Double = 16000
    static let audioChannels: UInt32 = 1
    static let audioBitDepth = 16
    static let minAudioSizeBytes = 500
    static let shortAudioThresholdBytes = 1000

    // MARK: - Silence Detection

    static let silenceThresholdDB: Float = -40
    static let minSilenceDurationMs = 500
    static let audioBufferBeforeMs = 150
    static let audioBufferAfterMs = 200

    // MARK: - UI Delays

    static let errorMessageDelayMs = 2000
    static let successMessageDelayMs = 1000

    // MARK: - Amplitude Visualization

    static let maxAmplitude: Float = 3000  // Typical speech RMS at 16kHz
    static let minAmplitudeBarScale: Float = 0.15
    static let maxAmplitudeBarScale: Float = 1.0
    static let amplitudeSmoothingFactor: Float = 0.6  // Responsive animation

    // MARK: - Billing (credits per plan)

    static let creditsStarter = 2000
    static let creditsPro = 6000
    static let creditsUnlimited = 15000

    // MARK: - Models

    static let modelGroqTurbo = "whisper-large-v3-turbo"
    static let modelGroqStandard = "whisper-large-v3"
    static let modelOpenAIMini = "gpt-4o-mini-transcribe"
    static let modelOpenAI = "gpt-4o-transcribe"

    // MARK: - UserDefaults Keys

    static let selectedModelKey = "selectedModel"
    static let selectedRegionKey = "selectedRegion"
    static let launchAtLoginKey = "launchAtLogin"
    static let hasCompletedOnboardingKey = "hasCompletedOnboarding"
    static let selectedHotkeyKey = "selectedHotkey"
}
