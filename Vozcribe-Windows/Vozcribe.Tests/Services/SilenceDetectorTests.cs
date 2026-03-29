using Vozcribe.Services;
using Vozcribe.Models;

namespace Vozcribe.Tests.Services;

public class SilenceDetectorTests
{
    [Fact]
    public void CalculateRmsDb_SilentSamples_ReturnsBelowThreshold()
    {
        var samples = new short[1600];
        for (int i = 0; i < samples.Length; i++)
            samples[i] = 1;

        var db = SilenceDetector.CalculateRmsDb(samples);
        Assert.True(db < Constants.SilenceThresholdDb);
    }

    [Fact]
    public void CalculateRmsDb_LoudSamples_ReturnsAboveThreshold()
    {
        var samples = new short[1600];
        for (int i = 0; i < samples.Length; i++)
            samples[i] = (short)(16000 * Math.Sin(2 * Math.PI * 440 * i / 16000));

        var db = SilenceDetector.CalculateRmsDb(samples);
        Assert.True(db > Constants.SilenceThresholdDb);
    }

    [Fact]
    public void DetectSpeechSegments_AllSilence_ReturnsEmpty()
    {
        var samples = new short[32000];
        var segments = SilenceDetector.DetectSpeechSegments(samples, Constants.SampleRate);
        Assert.Empty(segments);
    }

    [Fact]
    public void DetectSpeechSegments_AllSpeech_ReturnsSingleSegment()
    {
        var samples = new short[16000];
        for (int i = 0; i < samples.Length; i++)
            samples[i] = (short)(16000 * Math.Sin(2 * Math.PI * 440 * i / 16000));

        var segments = SilenceDetector.DetectSpeechSegments(samples, Constants.SampleRate);
        Assert.Single(segments);
    }

    [Fact]
    public void TrimAudio_WhenSavingsAboveThreshold_ReturnsTrimmed()
    {
        var samples = new short[32000];
        for (int i = 8000; i < 16000; i++)
            samples[i] = (short)(16000 * Math.Sin(2 * Math.PI * 440 * i / 16000));

        var result = SilenceDetector.TrimAudio(samples, Constants.SampleRate);
        Assert.True(result.WasTrimmed);
        Assert.True(result.Samples.Length < samples.Length);
        Assert.True(result.Samples.Length > 0);
    }

    [Fact]
    public void TrimAudio_WhenAllSpeech_ReturnsOriginal()
    {
        var samples = new short[16000];
        for (int i = 0; i < samples.Length; i++)
            samples[i] = (short)(16000 * Math.Sin(2 * Math.PI * 440 * i / 16000));

        var result = SilenceDetector.TrimAudio(samples, Constants.SampleRate);
        Assert.False(result.WasTrimmed);
        Assert.Equal(samples.Length, result.Samples.Length);
    }
}
