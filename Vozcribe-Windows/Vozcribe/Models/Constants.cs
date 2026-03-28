namespace Vozcribe.Models;

public static class Constants
{
    // API
    public const string FirebaseProjectId = "whispertype-1de9f";
    public const string BaseUrlPattern = "https://{0}-whispertype-1de9f.cloudfunctions.net";
    public static readonly string[] Regions = ["us-central1", "asia-south1", "europe-west1"];
    public static readonly string[] RegionDisplayNames = ["US Central", "Asia South", "Europe West"];

    // Endpoints
    public const string TrialStatusPath = "/getTrialStatus";
    public const string SubscriptionStatusPath = "/getSubscriptionStatus";
    public const string HealthPath = "/health";

    // Timeouts
    public static readonly TimeSpan ApiTimeout = TimeSpan.FromSeconds(60);
    public static readonly TimeSpan WarmupTimeout = TimeSpan.FromSeconds(10);

    // Retry
    public const int MaxRetries = 3;
    public const int InitialBackoffMs = 1000;
    public const int BackoffMultiplier = 2;
    public const int MaxBackoffMs = 8000;
    public static readonly int[] RetryableStatusCodes = [408, 502, 503, 504];

    // Audio
    public const int SampleRate = 16000;
    public const int Channels = 1;
    public const int BitsPerSample = 16;
    public const int MinAudioSizeBytes = 500;
    public const int ShortAudioThresholdBytes = 1000;
    public const int OpusBitrate = 32000;

    // Silence Detection
    public const float SilenceThresholdDb = -40f;
    public const int MinSilenceDurationMs = 500;
    public const int AudioBufferBeforeMs = 150;
    public const int AudioBufferAfterMs = 200;
    public const int MinSavingsPercent = 10;

    // Amplitude Visualization
    public const float MaxAmplitude = 3000f;
    public const float AmplitudeSmoothingFactor = 0.6f;

    // UI Timings
    public const int DirectInsertDismissMs = 2000;
    public const int ClipboardFallbackDismissMs = 6000;

    // Billing
    public const int DefaultFreeCredits = 500;
    public const int CreditsStarter = 2000;
    public const int CreditsPro = 6000;

    // Persistence
    public const int MaxPendingTranscriptions = 50;
    public const string AppDataFolder = "Vozcribe";
    public const string SettingsFileName = "settings.json";
    public const string PendingFolder = "pending";

    // Auth - loaded from appsettings.json at runtime
    public static string FirebaseApiKey { get; set; } = "";
    public static string GoogleClientId { get; set; } = "";
    public const string CredentialTarget = "Vozcribe_Auth";
}
