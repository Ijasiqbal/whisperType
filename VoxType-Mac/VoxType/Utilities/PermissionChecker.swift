import AVFoundation
import Cocoa

enum PermissionChecker {

    // MARK: - Microphone

    static var isMicrophoneGranted: Bool {
        AVCaptureDevice.authorizationStatus(for: .audio) == .authorized
    }

    static func requestMicrophone() async -> Bool {
        await AVCaptureDevice.requestAccess(for: .audio)
    }

    // MARK: - Accessibility

    static var isAccessibilityGranted: Bool {
        AXIsProcessTrusted()
    }

    /// Triggers the system prompt to register the app in the Accessibility list.
    /// Returns true if the app is already trusted (prompt was not shown).
    @discardableResult
    static func promptAccessibility() -> Bool {
        let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue(): true] as CFDictionary
        return AXIsProcessTrustedWithOptions(options)
    }

    static func openAccessibilitySettings() {
        NSWorkspace.shared.open(
            URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility")!
        )
    }

    /// Clears any stale Accessibility TCC entry for this bundle.
    /// This does not grant permission; it lets macOS create a fresh record when the user approves again.
    @discardableResult
    static func resetAccessibilityPermission() -> Bool {
        let bundleID = Bundle.main.bundleIdentifier ?? "com.wozcribe.mac"
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/tccutil")
        process.arguments = ["reset", "Accessibility", bundleID]

        let errorPipe = Pipe()
        process.standardError = errorPipe

        do {
            try process.run()
            process.waitUntilExit()

            if process.terminationStatus == 0 {
                debugLog("[Permissions] Reset Accessibility permission record for \(bundleID)")
                return true
            }

            let errorData = errorPipe.fileHandleForReading.readDataToEndOfFile()
            let errorMessage = String(data: errorData, encoding: .utf8)?
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? "unknown error"
            debugLog("[Permissions] Failed to reset Accessibility permission record for \(bundleID): status \(process.terminationStatus), \(errorMessage)")
        } catch {
            debugLog("[Permissions] Failed to run tccutil reset for Accessibility: \(error.localizedDescription)")
        }

        return false
    }

    /// Repairs any stale Accessibility entry, registers the app in the list, then opens System Settings.
    /// If the app is already trusted, opens System Settings immediately without resetting a working grant.
    static func promptAndOpenAccessibilitySettings() {
        if isAccessibilityGranted {
            openAccessibilitySettings()
            return
        }

        resetAccessibilityPermission()
        let isTrustedAfterPrompt = promptAccessibility()

        if isTrustedAfterPrompt {
            openAccessibilitySettings()
            return
        }

        // Delay so the system accessibility prompt fully appears before Settings opens on top
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            openAccessibilitySettings()
        }
    }

    // MARK: - Check All

    static func checkAllPermissions() -> (microphone: Bool, accessibility: Bool) {
        (isMicrophoneGranted, isAccessibilityGranted)
    }
}
