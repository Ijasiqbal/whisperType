using System.Text.Json.Serialization;

namespace Vozcribe.Models;

public class VersionStatus
{
    [JsonPropertyName("status")]
    public string Status { get; set; } = "ok";

    [JsonPropertyName("message")]
    public string? Message { get; set; }

    [JsonPropertyName("downloadUrl")]
    public string? DownloadUrl { get; set; }

    [JsonPropertyName("latestVersion")]
    public string? LatestVersion { get; set; }

    public bool IsBlocked => Status == "blocked";
    public bool IsUpdateAvailable => Status == "update_available";
}
