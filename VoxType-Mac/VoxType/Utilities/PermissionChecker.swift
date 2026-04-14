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

    /// Registers the app in the Accessibility list, then opens System Settings.
    /// If the app is already trusted, opens System Settings immediately (no prompt needed).
    static func promptAndOpenAccessibilitySettings() {
        let alreadyTrusted = promptAccessibility()
        if alreadyTrusted {
            openAccessibilitySettings()
        } else {
            // Delay so the system accessibility prompt fully appears before Settings opens on top
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                openAccessibilitySettings()
            }
        }
    }

    // MARK: - Check All

    static func checkAllPermissions() -> (microphone: Bool, accessibility: Bool) {
        (isMicrophoneGranted, isAccessibilityGranted)
    }
}
