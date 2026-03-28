using Vozcribe.Models;

namespace Vozcribe.Tests.Models;

public class ModelTierTests
{
    [Fact]
    public void Auto_HasCorrectProperties()
    {
        var tier = ModelTier.Auto;
        Assert.Equal("auto", tier.TierCode);
        Assert.Equal("Auto (Free)", tier.DisplayName);
        Assert.Equal("/transcribeAuto", tier.Endpoint);
        Assert.True(tier.IsFree);
        Assert.False(tier.IsTwoStage);
    }

    [Fact]
    public void Standard_HasCorrectProperties()
    {
        var tier = ModelTier.Standard;
        Assert.Equal("standard", tier.TierCode);
        Assert.Equal("Standard (1x)", tier.DisplayName);
        Assert.Equal("/transcribeStandard", tier.Endpoint);
        Assert.False(tier.IsFree);
        Assert.True(tier.IsTwoStage);
    }

    [Fact]
    public void Premium_HasCorrectProperties()
    {
        var tier = ModelTier.Premium;
        Assert.Equal("premium", tier.TierCode);
        Assert.Equal("Premium (2x)", tier.DisplayName);
        Assert.Equal("/transcribePremium", tier.Endpoint);
        Assert.False(tier.IsFree);
        Assert.False(tier.IsTwoStage);
    }

    [Fact]
    public void All_ReturnsThreeTiers()
    {
        Assert.Equal(3, ModelTier.All.Length);
    }

    [Fact]
    public void Next_CyclesCorrectly()
    {
        Assert.Equal(ModelTier.Standard, ModelTier.Auto.Next());
        Assert.Equal(ModelTier.Premium, ModelTier.Standard.Next());
        Assert.Equal(ModelTier.Auto, ModelTier.Premium.Next());
    }

    [Fact]
    public void Previous_CyclesCorrectly()
    {
        Assert.Equal(ModelTier.Premium, ModelTier.Auto.Previous());
        Assert.Equal(ModelTier.Auto, ModelTier.Standard.Previous());
        Assert.Equal(ModelTier.Standard, ModelTier.Premium.Previous());
    }
}
