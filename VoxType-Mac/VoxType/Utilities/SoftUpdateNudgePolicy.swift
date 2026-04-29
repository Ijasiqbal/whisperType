import Foundation

enum SoftUpdateNudgePolicy {
    static func shouldShow(
        result: VoxTypeAPIClient.VersionCheckResult,
        hasCompletedOnboarding: Bool,
        lastShownVersion: String?
    ) -> Bool {
        guard case .updateAvailable = result.status else { return false }
        guard hasCompletedOnboarding else { return false }
        guard let latestVersion = result.latestVersion,
              !latestVersion.isEmpty else { return false }
        if lastShownVersion == latestVersion { return false }
        return true
    }
}
