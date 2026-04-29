using System.Text.Json;
using Vozcribe.Models;

namespace Vozcribe.Tests.Models;

public class VersionStatusTests
{
    [Fact]
    public void Default_StatusIsOk()
    {
        var status = new VersionStatus();
        Assert.Equal("ok", status.Status);
        Assert.False(status.IsBlocked);
        Assert.False(status.IsUpdateAvailable);
    }

    [Fact]
    public void Deserialize_BlockedResponse_FromBackend()
    {
        var json =
            """
            {
                "status": "blocked",
                "message": "This version is no longer supported.",
                "downloadUrl": "https://vozcribe.com/windows"
            }
            """;

        var status = JsonSerializer.Deserialize<VersionStatus>(json);

        Assert.NotNull(status);
        Assert.True(status.IsBlocked);
        Assert.False(status.IsUpdateAvailable);
        Assert.Equal("This version is no longer supported.", status.Message);
        Assert.Equal("https://vozcribe.com/windows", status.DownloadUrl);
    }

    [Fact]
    public void Deserialize_UpdateAvailableResponse_FromBackend()
    {
        var json =
            """
            {
                "status": "update_available",
                "latestVersion": "1.3.0",
                "downloadUrl": "https://vozcribe.com/windows",
                "message": "Version 1.3.0 is available."
            }
            """;

        var status = JsonSerializer.Deserialize<VersionStatus>(json);

        Assert.NotNull(status);
        Assert.True(status.IsUpdateAvailable);
        Assert.False(status.IsBlocked);
        Assert.Equal("1.3.0", status.LatestVersion);
    }

    [Fact]
    public void Deserialize_OkResponse_FromBackend()
    {
        var json = """{"status":"ok"}""";

        var status = JsonSerializer.Deserialize<VersionStatus>(json);

        Assert.NotNull(status);
        Assert.False(status.IsBlocked);
        Assert.False(status.IsUpdateAvailable);
        Assert.Null(status.LatestVersion);
        Assert.Null(status.DownloadUrl);
    }

    [Fact]
    public void Deserialize_UnknownStatus_TreatedAsNotBlockedNotUpdateAvailable()
    {
        var json = """{"status":"some_future_status"}""";

        var status = JsonSerializer.Deserialize<VersionStatus>(json);

        Assert.NotNull(status);
        Assert.False(status.IsBlocked);
        Assert.False(status.IsUpdateAvailable);
    }
}
