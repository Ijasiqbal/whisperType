using System.Windows.Media;

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
    public SolidColorBrush ColorBrush { get; }
    public SolidColorBrush GlowBrush { get; }
    public SolidColorBrush TintBrush { get; }
    public string CreditLabel { get; }

    private ModelTier(string tierCode, string displayName, string shortName,
        string endpoint, bool isFree, bool isTwoStage,
        SolidColorBrush colorBrush, SolidColorBrush glowBrush, SolidColorBrush tintBrush,
        string creditLabel,
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
        ColorBrush = colorBrush;
        GlowBrush = glowBrush;
        TintBrush = tintBrush;
        CreditLabel = creditLabel;
    }

    private static SolidColorBrush MakeBrush(byte a, byte r, byte g, byte b)
    {
        var br = new SolidColorBrush(Color.FromArgb(a, r, g, b));
        br.Freeze();
        return br;
    }

    private static SolidColorBrush Brush(byte r, byte g, byte b) => MakeBrush(0xFF, r, g, b);
    private static SolidColorBrush Glow(byte r, byte g, byte b) => MakeBrush(0x55, r, g, b);
    private static SolidColorBrush Tint(byte r, byte g, byte b) => MakeBrush(0x1F, r, g, b);

    public static readonly ModelTier Auto = new(
        "auto", "Auto (Free)", "Auto",
        "/transcribeAuto", isFree: true, isTwoStage: false,
        colorBrush: Brush(0x4C, 0xAF, 0x50),
        glowBrush: Glow(0x4C, 0xAF, 0x50),
        tintBrush: Tint(0x4C, 0xAF, 0x50),
        creditLabel: "Free");

    public static readonly ModelTier Standard = new(
        "standard", "Standard (1x)", "Standard",
        "/transcribeStandard", isFree: false, isTwoStage: true,
        colorBrush: Brush(0x5E, 0x9B, 0xFF),
        glowBrush: Glow(0x5E, 0x9B, 0xFF),
        tintBrush: Tint(0x5E, 0x9B, 0xFF),
        creditLabel: "1×",
        llmTierCode: "standard_v2", requestTier: "STANDARD");

    public static readonly ModelTier Premium = new(
        "premium", "Premium (2x)", "Premium",
        "/transcribePremium", isFree: false, isTwoStage: false,
        colorBrush: Brush(0xFF, 0xB5, 0x47),
        glowBrush: Glow(0xFF, 0xB5, 0x47),
        tintBrush: Tint(0xFF, 0xB5, 0x47),
        creditLabel: "2×",
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
