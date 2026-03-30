using System.Text.Json.Serialization;

namespace Vozcribe.Models;

public class PendingTranscription
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = Guid.NewGuid().ToString();

    [JsonPropertyName("timestamp")]
    public long Timestamp { get; set; }

    [JsonPropertyName("tierCode")]
    public string TierCode { get; set; } = "auto";

    [JsonPropertyName("durationMs")]
    public int DurationMs { get; set; }

    [JsonPropertyName("error")]
    public string Error { get; set; } = "";

    [JsonIgnore]
    public string AudioFilePath => $"{Id}.opus";
}
