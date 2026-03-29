using System.IO;
using Vozcribe.Models;
using Vozcribe.Services;

namespace Vozcribe.Tests.Services;

public class PendingTranscriptionServiceTests : IDisposable
{
    private readonly string _tempDir;
    private readonly PendingTranscriptionService _service;

    public PendingTranscriptionServiceTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"vozcribe_pending_test_{Guid.NewGuid()}");
        _service = new PendingTranscriptionService(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    [Fact]
    public void Save_CreatesAudioAndMetadataFiles()
    {
        var audio = new byte[] { 1, 2, 3, 4 };
        _service.Save(audio, "auto", 5000, "test error");

        var items = _service.LoadAll();
        Assert.Single(items);
        Assert.Equal("auto", items[0].TierCode);
        Assert.Equal(5000, items[0].DurationMs);
        Assert.Equal("test error", items[0].Error);
    }

    [Fact]
    public void LoadAudioData_ReturnsCorrectBytes()
    {
        var audio = new byte[] { 10, 20, 30, 40, 50 };
        _service.Save(audio, "premium", 3000, "error");

        var items = _service.LoadAll();
        var loaded = _service.LoadAudioData(items[0]);
        Assert.Equal(audio, loaded);
    }

    [Fact]
    public void Delete_RemovesFiles()
    {
        var audio = new byte[] { 1, 2, 3 };
        _service.Save(audio, "auto", 1000, "err");

        var items = _service.LoadAll();
        _service.Delete(items[0]);

        Assert.Empty(_service.LoadAll());
    }

    [Fact]
    public void Save_PurgesOldestWhenOverLimit()
    {
        for (int i = 0; i < Constants.MaxPendingTranscriptions + 5; i++)
        {
            _service.Save(new byte[] { (byte)i }, "auto", 1000, "err");
        }

        var items = _service.LoadAll();
        Assert.Equal(Constants.MaxPendingTranscriptions, items.Count);
    }
}
