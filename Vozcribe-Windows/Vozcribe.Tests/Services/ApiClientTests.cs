using Vozcribe.Models;
using Vozcribe.Services;

namespace Vozcribe.Tests.Services;

public class ApiClientTests
{
    [Fact]
    public void BuildBaseUrl_UsesRegion()
    {
        var url = ApiClient.BuildBaseUrl("us-central1");
        Assert.Equal("https://us-central1-whispertype-1de9f.cloudfunctions.net", url);
    }

    [Fact]
    public void BuildBaseUrl_AsiaSouth()
    {
        var url = ApiClient.BuildBaseUrl("asia-south1");
        Assert.Equal("https://asia-south1-whispertype-1de9f.cloudfunctions.net", url);
    }

    [Fact]
    public void BuildTranscriptionRequest_SingleStage_HasCorrectFields()
    {
        var body = ApiClient.BuildTranscriptionBody(
            audioBase64: "dGVzdA==",
            audioFormat: "opus",
            tierCode: "auto",
            durationMs: 5000,
            isTwoStage: false,
            llmTierCode: null,
            requestTier: null);

        Assert.Equal("dGVzdA==", body["audioBase64"]?.ToString());
        Assert.Equal("opus", body["audioFormat"]?.ToString());
        Assert.Equal("auto", body["model"]?.ToString());
        Assert.Equal("5000", body["audioDurationMs"]?.ToString());
        Assert.False(body.ContainsKey("llmModel"));
        Assert.False(body.ContainsKey("tier"));
    }

    [Fact]
    public void BuildTranscriptionRequest_TwoStage_HasLlmFields()
    {
        var body = ApiClient.BuildTranscriptionBody(
            audioBase64: "dGVzdA==",
            audioFormat: "opus",
            tierCode: "standard",
            durationMs: 5000,
            isTwoStage: true,
            llmTierCode: "standard_v2",
            requestTier: "STANDARD");

        Assert.Equal("standard", body["model"]?.ToString());
        Assert.Equal("standard_v2", body["llmModel"]?.ToString());
        Assert.Equal("STANDARD", body["tier"]?.ToString());
    }
}
