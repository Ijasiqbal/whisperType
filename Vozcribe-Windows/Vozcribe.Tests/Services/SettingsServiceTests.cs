using System.IO;
using Vozcribe.Models;
using Vozcribe.Services;

namespace Vozcribe.Tests.Services;

public class SettingsServiceTests : IDisposable
{
    private readonly string _tempDir;
    private readonly SettingsService _service;

    public SettingsServiceTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), $"vozcribe_test_{Guid.NewGuid()}");
        Directory.CreateDirectory(_tempDir);
        _service = new SettingsService(_tempDir);
    }

    public void Dispose()
    {
        if (Directory.Exists(_tempDir))
            Directory.Delete(_tempDir, true);
    }

    [Fact]
    public void Load_WhenNoFile_ReturnsDefaults()
    {
        var settings = _service.Settings;
        Assert.Equal("ctrl_shift", settings.HotkeyId);
        Assert.Equal("auto", settings.ModelTierCode);
        Assert.Equal("us-central1", settings.Region);
        Assert.Equal("where_started", settings.InsertionMode);
        Assert.False(settings.LaunchAtStartup);
        Assert.False(settings.HasCompletedOnboarding);
    }

    [Fact]
    public void Save_ThenLoad_RoundTrips()
    {
        _service.Settings.ModelTierCode = "premium";
        _service.Settings.Region = "asia-south1";
        _service.Save();

        var service2 = new SettingsService(_tempDir);
        Assert.Equal("premium", service2.Settings.ModelTierCode);
        Assert.Equal("asia-south1", service2.Settings.Region);
    }

    [Fact]
    public void Save_CreatesJsonFile()
    {
        _service.Save();
        var filePath = Path.Combine(_tempDir, Constants.SettingsFileName);
        Assert.True(File.Exists(filePath));
    }
}
