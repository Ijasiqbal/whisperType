import XCTest
@testable import Vozcribe

final class SoftUpdateNudgePolicyTests: XCTestCase {

    private func updateAvailable(
        latestVersion: String? = "1.3.0"
    ) -> VoxTypeAPIClient.VersionCheckResult {
        VoxTypeAPIClient.VersionCheckResult(
            status: .updateAvailable,
            latestVersion: latestVersion,
            downloadUrl: "https://vozcribe.com/mac",
            message: "Version is available."
        )
    }

    private func ok() -> VoxTypeAPIClient.VersionCheckResult {
        VoxTypeAPIClient.VersionCheckResult(
            status: .ok,
            latestVersion: nil,
            downloadUrl: nil,
            message: nil
        )
    }

    private func blocked() -> VoxTypeAPIClient.VersionCheckResult {
        VoxTypeAPIClient.VersionCheckResult(
            status: .blocked,
            latestVersion: "1.3.0",
            downloadUrl: "https://vozcribe.com/mac",
            message: "Blocked."
        )
    }

    func testShouldShow_whenUpdateAvailable_andOnboarded_andNotShownYet() {
        XCTAssertTrue(SoftUpdateNudgePolicy.shouldShow(
            result: updateAvailable(),
            hasCompletedOnboarding: true,
            lastShownVersion: nil
        ))
    }

    func testShouldShow_whenStatusOk_returnsFalse() {
        XCTAssertFalse(SoftUpdateNudgePolicy.shouldShow(
            result: ok(),
            hasCompletedOnboarding: true,
            lastShownVersion: nil
        ))
    }

    func testShouldShow_whenBlocked_returnsFalse() {
        XCTAssertFalse(SoftUpdateNudgePolicy.shouldShow(
            result: blocked(),
            hasCompletedOnboarding: true,
            lastShownVersion: nil
        ))
    }

    func testShouldShow_whenOnboardingIncomplete_returnsFalse() {
        XCTAssertFalse(SoftUpdateNudgePolicy.shouldShow(
            result: updateAvailable(),
            hasCompletedOnboarding: false,
            lastShownVersion: nil
        ))
    }

    func testShouldShow_whenAlreadyShownForThisVersion_returnsFalse() {
        XCTAssertFalse(SoftUpdateNudgePolicy.shouldShow(
            result: updateAvailable(latestVersion: "1.3.0"),
            hasCompletedOnboarding: true,
            lastShownVersion: "1.3.0"
        ))
    }

    func testShouldShow_whenShownVersionDiffers_returnsTrue() {
        // User dismissed nudge for 1.2.0; new release 1.3.0 should re-prompt.
        XCTAssertTrue(SoftUpdateNudgePolicy.shouldShow(
            result: updateAvailable(latestVersion: "1.3.0"),
            hasCompletedOnboarding: true,
            lastShownVersion: "1.2.0"
        ))
    }

    func testShouldShow_whenLatestVersionMissing_returnsFalse() {
        XCTAssertFalse(SoftUpdateNudgePolicy.shouldShow(
            result: updateAvailable(latestVersion: nil),
            hasCompletedOnboarding: true,
            lastShownVersion: nil
        ))
    }

    func testShouldShow_whenLatestVersionEmpty_returnsFalse() {
        XCTAssertFalse(SoftUpdateNudgePolicy.shouldShow(
            result: updateAvailable(latestVersion: ""),
            hasCompletedOnboarding: true,
            lastShownVersion: nil
        ))
    }
}
