using Vozcribe.Services;
using Vozcribe.Models;

namespace Vozcribe.Tests.Services;

public class OpusEncoderTests
{
    [Fact]
    public void Encode_ValidSamples_ReturnsNonEmptyBytes()
    {
        var samples = new short[Constants.SampleRate];
        for (int i = 0; i < samples.Length; i++)
            samples[i] = (short)(16000 * Math.Sin(2 * Math.PI * 440 * i / Constants.SampleRate));

        var opus = OpusEncoderService.Encode(samples, Constants.SampleRate);
        Assert.NotNull(opus);
        Assert.True(opus.Length > 0);
        Assert.True(opus.Length < samples.Length * 2);
    }

    [Fact]
    public void Encode_ShortSamples_StillWorks()
    {
        var samples = new short[320];
        for (int i = 0; i < samples.Length; i++)
            samples[i] = (short)(8000 * Math.Sin(2 * Math.PI * 440 * i / Constants.SampleRate));

        var opus = OpusEncoderService.Encode(samples, Constants.SampleRate);
        Assert.NotNull(opus);
        Assert.True(opus.Length > 0);
    }
}
