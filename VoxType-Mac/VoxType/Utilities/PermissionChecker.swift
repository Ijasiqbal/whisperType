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

    static func promptAccessibility() {
        let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue(): true] as CFDictionary
        _ = AXIsProcessTrustedWithOptions(options)
    }

    static func openAccessibilitySettings() {
        NSWorkspace.shared.open(
            URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility")!
        )
    }

    // MARK: - Check All

    static func checkAllPermissions() -> (microphone: Bool, accessibility: Bool) {
        (isMicrophoneGranted, isAccessibilityGranted)
    }
}
