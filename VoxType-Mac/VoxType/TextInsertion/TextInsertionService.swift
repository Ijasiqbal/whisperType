import Cocoa

final class TextInsertionService {

    static let shared = TextInsertionService()

    /// The PID of the app that was frontmost when recording started.
    private(set) var frontmostPIDAtRecordingStart: pid_t?

    private init() {}

    /// Call this when recording starts to snapshot which app the user is in.
    /// Must be called BEFORE showing the overlay.
    func captureFrontmostApp() {
        frontmostPIDAtRecordingStart = NSWorkspace.shared.frontmostApplication?.processIdentifier
        let name = NSWorkspace.shared.frontmostApplication?.localizedName ?? "nil"
        NSLog("[VOXDEBUG] Captured frontmost app: \(name) PID: \(frontmostPIDAtRecordingStart ?? -1)")
    }

    /// Checks if a text input element is focused in the captured frontmost app.
    func isTextFieldFocused() -> Bool {
        // First try the captured PID, then fall back to current frontmost
        let pid = frontmostPIDAtRecordingStart
            ?? NSWorkspace.shared.frontmostApplication?.processIdentifier

        guard let pid else {
            NSLog("[VOXDEBUG] No PID available")
            return false
        }

        NSLog("[VOXDEBUG] Checking AX for PID: \(pid)")

        // CRITICAL: Re-activate the captured app to restore focus
        if let app = NSRunningApplication(processIdentifier: pid) {
            app.activate(options: [])
            NSLog("[VOXDEBUG] Reactivated app PID: \(pid)")
            // Give it a moment to activate (non-blocking)
            usleep(50000) // 0.05 seconds = 50,000 microseconds (runs on background thread)
        }

        let appElement = AXUIElementCreateApplication(pid)
        var focusedElement: CFTypeRef?
        let result = AXUIElementCopyAttributeValue(
            appElement,
            kAXFocusedUIElementAttribute as CFString,
            &focusedElement
        )

        guard result == .success else {
            NSLog("[VOXDEBUG] AX query failed with code: \(result.rawValue)")
            // -25212 = kAXErrorNoValue (no focused element)
            // This likely means no text field is focused
            return false
        }

        guard let focusedElement = focusedElement,
              CFGetTypeID(focusedElement) == AXUIElementGetTypeID() else {
            NSLog("[VOXDEBUG] Focused element is not an AXUIElement")
            return false
        }

        let element = focusedElement as! AXUIElement

        // Get role
        var role: CFTypeRef?
        AXUIElementCopyAttributeValue(element, kAXRoleAttribute as CFString, &role)
        let roleStr = role as? String ?? "unknown"

        // Get subrole
        var subrole: CFTypeRef?
        AXUIElementCopyAttributeValue(element, kAXSubroleAttribute as CFString, &subrole)
        let subroleStr = subrole as? String ?? ""

        NSLog("[VOXDEBUG] Focused element — role: \(roleStr), subrole: \(subroleStr)")

        // Known text input roles
        let textRoles: Set<String> = [
            "AXTextField",
            "AXTextArea",
            "AXComboBox",
            "AXSearchField",
            "AXWebArea",       // Browser content areas, Electron apps
        ]
        if textRoles.contains(roleStr) {
            NSLog("[VOXDEBUG] Matched text role: \(roleStr)")
            return true
        }

        // Known text subroles
        if subroleStr == "AXSearchTextField" || subroleStr == "AXSecureTextField" {
            NSLog("[VOXDEBUG] Matched text subrole: \(subroleStr)")
            return true
        }

        // Fallback: check if the element has a settable AXValue (editable)
        var settable: DarwinBoolean = false
        let settableResult = AXUIElementIsAttributeSettable(element, kAXValueAttribute as CFString, &settable)
        if settableResult == .success && settable.boolValue {
            NSLog("[VOXDEBUG] Element has settable AXValue — treating as text field")
            return true
        }

        NSLog("[VOXDEBUG] Not a text field")
        return false
    }

    /// Inserts text into the currently focused text field.
    func insertText(_ text: String) {
        NSLog("[VOXDEBUG] insertText called with text: \(text.prefix(50))...")
        let pasteboard = NSPasteboard.general

        // Save current clipboard contents
        let previousString = pasteboard.string(forType: .string)
        let previousChangeCount = pasteboard.changeCount

        // Set transcribed text
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)

        // Small delay to ensure clipboard is set before paste
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
            NSLog("[VOXDEBUG] Simulating Cmd+V paste")
            self.simulatePaste()

            // Restore previous clipboard after paste completes
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                // Only restore if clipboard hasn't been changed by something else
                if pasteboard.changeCount == previousChangeCount + 1 {
                    pasteboard.clearContents()
                    if let prev = previousString {
                        pasteboard.setString(prev, forType: .string)
                    }
                    NSLog("[VOXDEBUG] Clipboard restored")
                }
            }
        }
    }

    // MARK: - Private

    private func simulatePaste() {
        let source = CGEventSource(stateID: .hidSystemState)

        // Virtual key code for 'V' is 0x09
        let vKeyCode: CGKeyCode = 0x09

        guard let keyDown = CGEvent(keyboardEventSource: source, virtualKey: vKeyCode, keyDown: true),
              let keyUp = CGEvent(keyboardEventSource: source, virtualKey: vKeyCode, keyDown: false) else {
            print("[TextInsertion] Failed to create keyboard events")
            return
        }

        keyDown.flags = .maskCommand
        keyUp.flags = .maskCommand

        keyDown.post(tap: .cghidEventTap)
        keyUp.post(tap: .cghidEventTap)
    }
}
