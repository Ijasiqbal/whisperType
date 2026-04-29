using Vozcribe.Models;
using Vozcribe.Services;

namespace Vozcribe.Tests.Services;

public class SoftUpdateNudgePolicyTests
{
    private static VersionStatus UpdateAvailable(
        string latestVersion = "1.3.0") =>
        new()
        {
            Status = "update_available",
            LatestVersion = latestVersion,
            DownloadUrl = "https://vozcribe.com/windows",
            Message = "Version is available.",
        };

    [Fact]
    public void ShouldShow_WhenUpdateAvailable_AndOnboarded_AndNotShownYet_ReturnsTrue()
    {
        var result = SoftUpdateNudgePolicy.ShouldShow(
            UpdateAvailable(),
            hasCompletedOnboarding: true,
            lastShownVersion: null);

        Assert.True(result);
    }

    [Fact]
    public void ShouldShow_WhenStatusOk_ReturnsFalse()
    {
        var status = new VersionStatus { Status = "ok" };

        var result = SoftUpdateNudgePolicy.ShouldShow(
            status,
            hasCompletedOnboarding: true,
            lastShownVersion: null);

        Assert.False(result);
    }

    [Fact]
    public void ShouldShow_WhenBlocked_ReturnsFalse()
    {
        var status = new VersionStatus
        {
            Status = "blocked",
            LatestVersion = "1.3.0",
        };

        var result = SoftUpdateNudgePolicy.ShouldShow(
            status,
            hasCompletedOnboarding: true,
            lastShownVersion: null);

        Assert.False(result);
    }

    [Fact]
    public void ShouldShow_WhenOnboardingIncomplete_ReturnsFalse()
    {
        var result = SoftUpdateNudgePolicy.ShouldShow(
            UpdateAvailable(),
            hasCompletedOnboarding: false,
            lastShownVersion: null);

        Assert.False(result);
    }

    [Fact]
    public void ShouldShow_WhenAlreadyShownForThisVersion_ReturnsFalse()
    {
        var result = SoftUpdateNudgePolicy.ShouldShow(
            UpdateAvailable("1.3.0"),
            hasCompletedOnboarding: true,
            lastShownVersion: "1.3.0");

        Assert.False(result);
    }

    [Fact]
    public void ShouldShow_WhenShownVersionDiffers_ReturnsTrue()
    {
        // User dismissed nudge for 1.2.0 previously; new release 1.3.0 should re-prompt.
        var result = SoftUpdateNudgePolicy.ShouldShow(
            UpdateAvailable("1.3.0"),
            hasCompletedOnboarding: true,
            lastShownVersion: "1.2.0");

        Assert.True(result);
    }

    [Fact]
    public void ShouldShow_WhenLatestVersionMissing_ReturnsFalse()
    {
        var status = new VersionStatus
        {
            Status = "update_available",
            LatestVersion = null,
        };

        var result = SoftUpdateNudgePolicy.ShouldShow(
            status,
            hasCompletedOnboarding: true,
            lastShownVersion: null);

        Assert.False(result);
    }

    [Fact]
    public void ShouldShow_WhenLatestVersionEmpty_ReturnsFalse()
    {
        var status = new VersionStatus
        {
            Status = "update_available",
            LatestVersion = "",
        };

        var result = SoftUpdateNudgePolicy.ShouldShow(
            status,
            hasCompletedOnboarding: true,
            lastShownVersion: null);

        Assert.False(result);
    }
}
