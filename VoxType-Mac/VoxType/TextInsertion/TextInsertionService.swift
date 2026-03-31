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

    /// Inserts text and returns true if cursor shift confirms the paste landed.
    /// Returns false if confirmation failed (Electron, Terminal, etc.) — caller should show copy button.
    func insertText(_ text: String) async -> Bool {
        NSLog("[VOXDEBUG] insertText called with text: \(text.prefix(50))...")

        let pid = frontmostPIDAtRecordingStart
            ?? NSWorkspace.shared.frontmostApplication?.processIdentifier

        // Re-activate the captured app so it receives the paste
        if let pid, let app = NSRunningApplication(processIdentifier: pid) {
            app.activate(options: [])
            NSLog("[VOXDEBUG] Reactivated app PID: \(pid)")
            try? await Task.sleep(nanoseconds: 50_000_000) // 50ms
        }

        // Read cursor position BEFORE paste
        let cursorBefore = cursorPosition(pid: pid)
        NSLog("[VOXDEBUG] Cursor before paste: \(cursorBefore?.description ?? "nil")")

        // Set clipboard and paste
        let pasteboard = NSPasteboard.general
        let previousString = pasteboard.string(forType: .string)
        let previousChangeCount = pasteboard.changeCount

        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)

        try? await Task.sleep(nanoseconds: 50_000_000) // 50ms — ensure clipboard is set
        simulatePaste()

        // Wait for the target app to process the paste
        try? await Task.sleep(nanoseconds: 150_000_000) // 150ms

        // Read cursor position AFTER paste
        let cursorAfter = cursorPosition(pid: pid)
        NSLog("[VOXDEBUG] Cursor after paste: \(cursorAfter?.description ?? "nil")")

        // Restore previous clipboard
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            if pasteboard.changeCount == previousChangeCount + 1 {
                pasteboard.clearContents()
                if let prev = previousString {
                    pasteboard.setString(prev, forType: .string)
                }
                NSLog("[VOXDEBUG] Clipboard restored")
            }
        }

        // Confirm paste by checking if cursor advanced
        if let before = cursorBefore, let after = cursorAfter, after > before {
            NSLog("[VOXDEBUG] Cursor advanced \(before) → \(after) — paste confirmed")
            return true
        }

        NSLog("[VOXDEBUG] Cursor did not advance — paste unconfirmed (Electron/Terminal/etc.)")
        return false
    }

    // MARK: - Private

    /// Returns the cursor location (insertion point) in the focused element of the given app.
    private func cursorPosition(pid: pid_t?) -> Int? {
        guard let pid else { return nil }

        let appElement = AXUIElementCreateApplication(pid)
        var focusedRef: CFTypeRef?
        guard AXUIElementCopyAttributeValue(appElement, kAXFocusedUIElementAttribute as CFString, &focusedRef) == .success,
              let focusedRef,
              CFGetTypeID(focusedRef) == AXUIElementGetTypeID() else {
            return nil
        }

        let element = focusedRef as! AXUIElement
        var rangeRef: CFTypeRef?
        guard AXUIElementCopyAttributeValue(element, kAXSelectedTextRangeAttribute as CFString, &rangeRef) == .success,
              let rangeRef else {
            return nil
        }

        var range = CFRange()
        guard AXValueGetValue(rangeRef as! AXValue, .cfRange, &range) else {
            return nil
        }

        return range.location
    }

    private func simulatePaste() {
        let source = CGEventSource(stateID: .hidSystemState)
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
