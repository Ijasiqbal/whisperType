using System.Text.Json.Serialization;

namespace Vozcribe.Models;

public class TrialStatus
{
    [JsonPropertyName("status")]
    public string Status { get; set; } = "";

    [JsonPropertyName("freeCreditsUsed")]
    public int FreeCreditsUsed { get; set; }

    [JsonPropertyName("freeCreditsRemaining")]
    public int FreeCreditsRemaining { get; set; }

    [JsonPropertyName("freeTierCredits")]
    public int FreeTierCredits { get; set; }

    [JsonPropertyName("trialExpiryDateMs")]
    public long TrialExpiryDateMs { get; set; }

    [JsonPropertyName("warningLevel")]
    public string WarningLevel { get; set; } = "none";
}
