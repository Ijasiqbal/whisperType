using System.Text.Json.Serialization;

namespace Vozcribe.Models;

public class TranscriptionResult
{
    [JsonPropertyName("text")]
    public string Text { get; set; } = "";

    [JsonPropertyName("creditsUsed")]
    public int CreditsUsed { get; set; }

    [JsonPropertyName("totalCreditsThisMonth")]
    public int TotalCreditsThisMonth { get; set; }

    [JsonPropertyName("plan")]
    public string? Plan { get; set; }

    [JsonPropertyName("trialStatus")]
    public TrialStatus? TrialStatus { get; set; }

    [JsonPropertyName("proStatus")]
    public ProStatus? ProStatus { get; set; }
}

public class ProStatus
{
    [JsonPropertyName("isActive")]
    public bool IsActive { get; set; }

    [JsonPropertyName("proCreditsUsed")]
    public int ProCreditsUsed { get; set; }

    [JsonPropertyName("proCreditsRemaining")]
    public int ProCreditsRemaining { get; set; }

    [JsonPropertyName("proCreditsLimit")]
    public int ProCreditsLimit { get; set; }

    [JsonPropertyName("currentPeriodEndMs")]
    public long CurrentPeriodEndMs { get; set; }
}
