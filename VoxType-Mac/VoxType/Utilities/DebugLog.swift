import Foundation
import os.log

private let logger = OSLog(subsystem: "com.wozcribe.mac", category: "app")

/// Logs to Apple's unified logging system (visible via `log stream`).
/// Works in both debug and release builds.
@inline(__always)
func debugLog(_ message: @autoclosure () -> String) {
    os_log("%{public}@", log: logger, type: .default, message())
}
