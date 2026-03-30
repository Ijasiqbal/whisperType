namespace Vozcribe.Models;

public class ModelTier
{
    public string TierCode { get; }
    public string DisplayName { get; }
    public string ShortName { get; }
    public string Endpoint { get; }
    public bool IsFree { get; }
    public bool IsTwoStage { get; }
    public string? LlmTierCode { get; }
    public string? RequestTier { get; }

    private ModelTier(string tierCode, string displayName, string shortName,
        string endpoint, bool isFree, bool isTwoStage,
        string? llmTierCode = null, string? requestTier = null)
    {
        TierCode = tierCode;
        DisplayName = displayName;
        ShortName = shortName;
        Endpoint = endpoint;
        IsFree = isFree;
        IsTwoStage = isTwoStage;
        LlmTierCode = llmTierCode;
        RequestTier = requestTier;
    }

    public static readonly ModelTier Auto = new(
        "auto", "Auto (Free)", "Auto",
        "/transcribeAuto", isFree: true, isTwoStage: false);

    public static readonly ModelTier Standard = new(
        "standard", "Standard (1x)", "Standard",
        "/transcribeStandard", isFree: false, isTwoStage: true,
        llmTierCode: "standard_v2", requestTier: "STANDARD");

    public static readonly ModelTier Premium = new(
        "premium", "Premium (2x)", "Premium",
        "/transcribePremium", isFree: false, isTwoStage: false,
        requestTier: "PREMIUM");

    public static readonly ModelTier[] All = [Auto, Standard, Premium];

    public ModelTier Next()
    {
        int idx = Array.IndexOf(All, this);
        return All[(idx + 1) % All.Length];
    }

    public ModelTier Previous()
    {
        int idx = Array.IndexOf(All, this);
        return All[(idx - 1 + All.Length) % All.Length];
    }

    public static ModelTier FromTierCode(string code) =>
        All.FirstOrDefault(t => t.TierCode == code) ?? Auto;
}
