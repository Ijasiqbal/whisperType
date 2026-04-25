import Foundation

enum Constants {

    // MARK: - API

    static let firebaseProjectID = "whispertype-1de9f"
    static let regions = ["us-central1", "asia-south1", "europe-west1"]

    static func baseURL(for region: String) -> String {
        "https://\(region)-\(firebaseProjectID).cloudfunctions.net"
    }

    // MARK: - Endpoints

    static let transcribeAutoPath = "/transcribeAuto"
    static let transcribePremiumPath = "/transcribePremium"
    static let transcribeStandardPath = "/transcribeStandard"
    static let trialStatusPath = "/getTrialStatus"
    static let subscriptionStatusPath = "/getSubscriptionStatus"
    static let healthPath = "/health"
    static let versionCheckPath = "/checkMacVersion"

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
    static let minSavingsPercent = 10  // Skip trimming if silence savings < 10%

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

    // MARK: - Model Tier Codes

    static let modelAuto = "auto"
    static let modelStandard = "standard_v2"
    static let modelPremium = "premium"

    // MARK: - UserDefaults Keys

    static let selectedModelKey = "selectedModel"
    static let selectedRegionKey = "selectedRegion"
    static let launchAtLoginKey = "launchAtLogin"
    static let hasCompletedOnboardingKey = "hasCompletedOnboarding"
    static let selectedHotkeyKey = "selectedHotkey"

    // MARK: - REST Auth Token Storage Keys

    static let restFirebaseIDToken = "vozcribe_firebase_id_token"
    static let restFirebaseRefreshToken = "vozcribe_firebase_refresh_token"
    static let restFirebaseTokenExpiry = "vozcribe_firebase_token_expiry"
    static let restUserEmail = "vozcribe_user_email"
    static let restUserName = "vozcribe_user_name"
    static let restUserUID = "vozcribe_user_uid"

    static let firebaseTokenLifetime: TimeInterval = 3600
}
