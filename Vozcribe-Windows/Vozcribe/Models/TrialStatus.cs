using System.Text.Json.Serialization;

namespace Vozcribe.Models;

public class TrialStatus
{
    [JsonPropertyName("plan")]
    public string Plan { get; set; } = "free";

    [JsonPropertyName("status")]
    public string Status { get; set; } = "";

    [JsonIgnore]
    public UserPlan UserPlan => Plan == "pro" ? UserPlan.Pro : UserPlan.Free;

    [JsonPropertyName("freeCreditsUsed")]
    public int FreeCreditsUsed { get; set; }

    [JsonPropertyName("freeCreditsRemaining")]
    public int FreeCreditsRemaining { get; set; }

    [JsonPropertyName("freeTierCredits")]
    public int FreeTierCredits { get; set; }

    [JsonPropertyName("trialExpiryDateMs")]
    public long TrialExpiryDateMs { get; set; }

    [JsonPropertyName("proCreditsUsed")]
    public int ProCreditsUsed { get; set; }

    [JsonPropertyName("proCreditsRemaining")]
    public int ProCreditsRemaining { get; set; }

    [JsonPropertyName("proCreditsLimit")]
    public int ProCreditsLimit { get; set; }

    [JsonPropertyName("warningLevel")]
    public string WarningLevel { get; set; } = "none";
}
