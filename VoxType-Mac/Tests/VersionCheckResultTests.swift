import XCTest
@testable import Vozcribe

/// These tests document the expected backend response shape for /checkMacVersion
/// and verify our client interprets each documented status correctly. The actual
/// JSON parsing happens inside VoxTypeAPIClient.checkVersion(); these tests
/// exercise the public VersionCheckResult struct used downstream.
final class VersionCheckResultTests: XCTestCase {

    func testOkStatus_doesNotTriggerBlockOrUpdate() {
        let result = VoxTypeAPIClient.VersionCheckResult(
            status: .ok,
            latestVersion: nil,
            downloadUrl: nil,
            message: nil
        )

        if case .blocked = result.status { XCTFail("should not be blocked") }
        if case .updateAvailable = result.status { XCTFail("should not be update") }
    }

    func testBlockedStatus_carriesDownloadUrlAndMessage() {
        let result = VoxTypeAPIClient.VersionCheckResult(
            status: .blocked,
            latestVersion: nil,
            downloadUrl: "https://vozcribe.com/mac",
            message: "This version is no longer supported."
        )

        guard case .blocked = result.status else {
            return XCTFail("expected blocked status")
        }
        XCTAssertEqual(result.downloadUrl, "https://vozcribe.com/mac")
        XCTAssertEqual(result.message, "This version is no longer supported.")
    }

    func testUpdateAvailableStatus_carriesLatestVersion() {
        let result = VoxTypeAPIClient.VersionCheckResult(
            status: .updateAvailable,
            latestVersion: "1.3.0",
            downloadUrl: "https://vozcribe.com/mac",
            message: "Version 1.3.0 is available."
        )

        guard case .updateAvailable = result.status else {
            return XCTFail("expected update_available status")
        }
        XCTAssertEqual(result.latestVersion, "1.3.0")
    }
}
