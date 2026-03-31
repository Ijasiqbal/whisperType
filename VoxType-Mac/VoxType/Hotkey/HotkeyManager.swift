import Cocoa
import Combine

final class HotkeyManager: ObservableObject {

    @Published var isRecording = false
    @Published var selectedHotkey: HotkeyOption {
        didSet {
            UserDefaults.standard.set(selectedHotkey.rawValue, forKey: Constants.selectedHotkeyKey)
        }
    }

    /// Called when the hotkey toggles recording ON
    var onRecordingStart: (() -> Void)?
    /// Called when the hotkey toggles recording OFF
    var onRecordingStop: (() -> Void)?
    /// Called when Shift+Up/Right is pressed to cycle model forward
    var onModelCycleForward: (() -> Void)?
    /// Called when Shift+Down/Left is pressed to cycle model backward
    var onModelCycleBackward: (() -> Void)?

    // -- CGEvent backend state --
    fileprivate var eventTap: CFMachPort?
    private var runLoopSource: CFRunLoopSource?
    private var modifierFlagsActive = false
    private let allMonitoredFlags: CGEventFlags = [.maskControl, .maskAlternate, .maskCommand, .maskShift]

    // -- NSEvent backend state --
    private var globalMonitor: Any?
    private var localMonitor: Any?

    // MARK: - Init

    init() {
        let saved = UserDefaults.standard.string(forKey: Constants.selectedHotkeyKey) ?? ""
        selectedHotkey = HotkeyOption(rawValue: saved) ?? .ctrlSpace
    }

    // MARK: - Public

    /// Start listening for hotkey events. Returns true if successful.
    @discardableResult
    func start() -> Bool {
        if selectedHotkey.usesNSEventMonitor {
            return startNSEventMonitor()
        } else {
            return startCGEventTap()
        }
    }

    /// Stop all hotkey listening.
    func stop() {
        stopNSEventMonitor()
        stopCGEventTap()
        isRecording = false
        modifierFlagsActive = false
        print("[HotkeyManager] All hotkey listeners stopped")
    }

    /// Change the active hotkey. Restarts the listener with the appropriate backend.
    func changeHotkey(to option: HotkeyOption) {
        let wasRunning = (eventTap != nil || globalMonitor != nil)
        stop()
        selectedHotkey = option
        if wasRunning { start() }
        print("[HotkeyManager] Hotkey changed to \(option.displayName)")
    }

    // MARK: - Permission

    static var isAccessibilityGranted: Bool {
        AXIsProcessTrusted()
    }

    static func promptAccessibility() {
        let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue(): true] as CFDictionary
        _ = AXIsProcessTrustedWithOptions(options)
    }

    // MARK: - Toggle Logic

    private func toggle() {
        isRecording.toggle()
        if isRecording {
            onRecordingStart?()
        } else {
            onRecordingStop?()
        }
    }

    // =========================================================================
    // MARK: - NSEvent Backend (no Accessibility required)
    // =========================================================================

    private func startNSEventMonitor() -> Bool {
        guard let keyCode = selectedHotkey.nsEventKeyCode,
              let modifiers = selectedHotkey.nsEventModifierFlags else {
            print("[HotkeyManager] NSEvent hotkey misconfigured")
            return false
        }

        // Global monitor — catches events when other apps are focused
        globalMonitor = NSEvent.addGlobalMonitorForEvents(matching: .keyDown) { [weak self] event in
            self?.handleNSEvent(event, expectedKeyCode: keyCode, expectedModifiers: modifiers)
        }

        // Local monitor — catches events when our own app is focused
        localMonitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { [weak self] event in
            self?.handleNSEvent(event, expectedKeyCode: keyCode, expectedModifiers: modifiers)
            return event // pass through
        }

        print("[HotkeyManager] NSEvent monitor started for \(selectedHotkey.displayName)")
        return true
    }

    private func stopNSEventMonitor() {
        if let monitor = globalMonitor {
            NSEvent.removeMonitor(monitor)
            globalMonitor = nil
        }
        if let monitor = localMonitor {
            NSEvent.removeMonitor(monitor)
            localMonitor = nil
        }
    }

    private func handleNSEvent(_ event: NSEvent, expectedKeyCode: UInt16, expectedModifiers: NSEvent.ModifierFlags) {
        let flags = event.modifierFlags.intersection(.deviceIndependentFlagsMask)

        // Check for model cycling: Shift + Arrow keys
        let isShiftOnly = flags.contains(.shift)
            && !flags.contains(.command)
            && !flags.contains(.control)
            && !flags.contains(.option)

        if isShiftOnly {
            if event.keyCode == 0x7E || event.keyCode == 0x7C { // Up or Right
                DispatchQueue.main.async { [weak self] in self?.onModelCycleForward?() }
                return
            } else if event.keyCode == 0x7D || event.keyCode == 0x7B { // Down or Left
                DispatchQueue.main.async { [weak self] in self?.onModelCycleBackward?() }
                return
            }
        }

        // Check for our hotkey
        let hasRequiredModifiers = flags.contains(expectedModifiers)
        // Make sure no extra modifiers are pressed (e.g., Ctrl+Shift+D should not trigger Ctrl+D)
        let extraModifiers: NSEvent.ModifierFlags = [.command, .option, .shift, .control]
        let activeExtra = flags.intersection(extraModifiers).subtracting(expectedModifiers)
        let noExtraModifiers = activeExtra.isEmpty

        if event.keyCode == expectedKeyCode && hasRequiredModifiers && noExtraModifiers {
            DispatchQueue.main.async { [weak self] in self?.toggle() }
        }
    }

    // =========================================================================
    // MARK: - CGEvent Backend (Accessibility required)
    // =========================================================================

    private func startCGEventTap() -> Bool {
        guard HotkeyManager.isAccessibilityGranted else {
            print("[HotkeyManager] CGEvent tap requires Accessibility permission")
            return false
        }

        let eventMask: CGEventMask =
            (1 << CGEventType.flagsChanged.rawValue) |
            (1 << CGEventType.keyDown.rawValue) |
            (1 << CGEventType.keyUp.rawValue)

        let userInfo = Unmanaged.passUnretained(self).toOpaque()

        guard let tap = CGEvent.tapCreate(
            tap: .cgSessionEventTap,
            place: .headInsertEventTap,
            options: .defaultTap,
            eventsOfInterest: eventMask,
            callback: hotkeyEventCallback,
            userInfo: userInfo
        ) else {
            print("[HotkeyManager] Failed to create event tap")
            return false
        }

        eventTap = tap
        runLoopSource = CFMachPortCreateRunLoopSource(kCFAllocatorDefault, tap, 0)

        if let source = runLoopSource {
            CFRunLoopAddSource(CFRunLoopGetMain(), source, .commonModes)
        }

        CGEvent.tapEnable(tap: tap, enable: true)
        print("[HotkeyManager] CGEvent tap started for \(selectedHotkey.displayName)")
        return true
    }

    private func stopCGEventTap() {
        if let tap = eventTap {
            CGEvent.tapEnable(tap: tap, enable: false)
        }
        if let source = runLoopSource {
            CFRunLoopRemoveSource(CFRunLoopGetMain(), source, .commonModes)
        }
        eventTap = nil
        runLoopSource = nil
    }

    // MARK: - CGEvent Handlers

    fileprivate func handleFlagsChanged(_ flags: CGEventFlags) {
        let hotkey = selectedHotkey
        guard hotkey.isModifierOnly, let required = hotkey.cgEventRequiredFlags else { return }

        let activeModifiers = flags.intersection(allMonitoredFlags)
        let isMatch = activeModifiers == required

        if isMatch && !modifierFlagsActive {
            modifierFlagsActive = true
            toggle()
        } else if !isMatch && modifierFlagsActive {
            modifierFlagsActive = false
        }
    }

    fileprivate func handleKeyDown(_ keyCode: UInt16, flags: CGEventFlags) {
        // Shift+Arrow for model cycling
        let isShiftOnly = flags.contains(.maskShift)
            && !flags.contains(.maskCommand)
            && !flags.contains(.maskControl)
            && !flags.contains(.maskAlternate)

        if isShiftOnly {
            if keyCode == 0x7E || keyCode == 0x7C {
                onModelCycleForward?()
            } else if keyCode == 0x7D || keyCode == 0x7B {
                onModelCycleBackward?()
            }
        }
    }

    deinit {
        stop()
    }
}

// MARK: - CGEvent C Callback

private func hotkeyEventCallback(
    proxy: CGEventTapProxy,
    type: CGEventType,
    event: CGEvent,
    userInfo: UnsafeMutableRawPointer?
) -> Unmanaged<CGEvent>? {
    if type == .tapDisabledByTimeout || type == .tapDisabledByUserInput {
        if let userInfo {
            let manager = Unmanaged<HotkeyManager>.fromOpaque(userInfo).takeUnretainedValue()
            if let tap = manager.eventTap {
                CGEvent.tapEnable(tap: tap, enable: true)
            }
        }
        return Unmanaged.passRetained(event)
    }

    guard let userInfo else {
        return Unmanaged.passRetained(event)
    }

    let manager = Unmanaged<HotkeyManager>.fromOpaque(userInfo).takeUnretainedValue()

    if type == .flagsChanged {
        let flags = event.flags
        DispatchQueue.main.async {
            manager.handleFlagsChanged(flags)
        }
    } else if type == .keyDown {
        let keyCode = UInt16(event.getIntegerValueField(.keyboardEventKeycode))
        let flags = event.flags
        DispatchQueue.main.async {
            manager.handleKeyDown(keyCode, flags: flags)
        }
    }

    return Unmanaged.passRetained(event)
}
