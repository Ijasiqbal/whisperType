using System.Text.Json.Serialization;

namespace Vozcribe.Models;

public class AppSettings
{
    [JsonPropertyName("hotkeyId")]
    public string HotkeyId { get; set; } = "ctrl_shift";

    [JsonPropertyName("customHotkeyModifiers")]
    public int? CustomHotkeyModifiers { get; set; }

    [JsonPropertyName("customHotkeyKey")]
    public int? CustomHotkeyKey { get; set; }

    [JsonPropertyName("modelTierCode")]
    public string ModelTierCode { get; set; } = "auto";

    [JsonPropertyName("region")]
    public string Region { get; set; } = "us-central1";

    [JsonPropertyName("insertionMode")]
    public string InsertionMode { get; set; } = "where_started";

    [JsonPropertyName("launchAtStartup")]
    public bool LaunchAtStartup { get; set; }

    [JsonPropertyName("overlayX")]
    public double? OverlayX { get; set; }

    [JsonPropertyName("overlayY")]
    public double? OverlayY { get; set; }

    [JsonPropertyName("hasCompletedOnboarding")]
    public bool HasCompletedOnboarding { get; set; }
}
