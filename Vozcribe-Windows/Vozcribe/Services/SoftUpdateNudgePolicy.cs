using Vozcribe.Models;

namespace Vozcribe.Services;

public static class SoftUpdateNudgePolicy
{
    public static bool ShouldShow(
        VersionStatus status,
        bool hasCompletedOnboarding,
        string? lastShownVersion)
    {
        if (!status.IsUpdateAvailable) return false;
        if (!hasCompletedOnboarding) return false;
        if (string.IsNullOrEmpty(status.LatestVersion)) return false;
        if (lastShownVersion == status.LatestVersion) return false;
        return true;
    }
}
