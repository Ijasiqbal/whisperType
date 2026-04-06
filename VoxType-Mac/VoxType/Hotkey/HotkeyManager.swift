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
    /// Called when Shift+Up is pressed to cycle model forward
    var onModelCycleForward: (() -> Void)?
    /// Called when Shift+Down is pressed to cycle model backward
    var onModelCycleBackward: (() -> Void)?

    fileprivate var eventTap: CFMachPort?
    private var runLoopSource: CFRunLoopSource?

    /// Tracks whether modifier-only hotkey flags are currently held (to detect press/release edges)
    private var modifierFlagsActive = false

    /// Timestamp of last Fn key press for double-tap detection
    private var lastFnTapTime: TimeInterval = 0
    /// Maximum interval between taps to count as double-tap (seconds)
    private let doubleTapInterval: TimeInterval = 0.4

    // All modifier flags we monitor
    private let allMonitoredFlags: CGEventFlags = [.maskControl, .maskAlternate, .maskCommand, .maskShift]

    // MARK: - Init

    init() {
        let saved = UserDefaults.standard.string(forKey: Constants.selectedHotkeyKey) ?? ""
        selectedHotkey = HotkeyOption(rawValue: saved) ?? .ctrlOption
    }

    // MARK: - Public

    func start() -> Bool {
        guard checkAccessibilityPermission() else {
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
            debugLog("[HotkeyManager] Failed to create event tap. Check Accessibility permissions.")
            return false
        }

        eventTap = tap
        runLoopSource = CFMachPortCreateRunLoopSource(kCFAllocatorDefault, tap, 0)

        if let source = runLoopSource {
            CFRunLoopAddSource(CFRunLoopGetMain(), source, .commonModes)
        }

        CGEvent.tapEnable(tap: tap, enable: true)
        debugLog("[HotkeyManager] Global hotkey registered (\(selectedHotkey.displayName), toggle mode)")
        return true
    }

    func stop() {
        if let tap = eventTap {
            CGEvent.tapEnable(tap: tap, enable: false)
        }
        if let source = runLoopSource {
            CFRunLoopRemoveSource(CFRunLoopGetMain(), source, .commonModes)
        }
        eventTap = nil
        runLoopSource = nil
        isRecording = false
        modifierFlagsActive = false
        debugLog("[HotkeyManager] Global hotkey unregistered")
    }

    /// Change the active hotkey. Restarts the event tap if running.
    func changeHotkey(to option: HotkeyOption) {
        let wasRunning = eventTap != nil
        if wasRunning { stop() }
        selectedHotkey = option
        modifierFlagsActive = false
        if wasRunning { _ = start() }
        debugLog("[HotkeyManager] Hotkey changed to \(option.displayName)")
    }

    // MARK: - Permission

    func checkAccessibilityPermission() -> Bool {
        let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue(): true] as CFDictionary
        return AXIsProcessTrustedWithOptions(options)
    }

    static var isAccessibilityGranted: Bool {
        AXIsProcessTrusted()
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

    // MARK: - Event Handling

    fileprivate func handleFlagsChanged(keyCode: UInt16, flags: CGEventFlags) {
        let hotkey = selectedHotkey

        // Key-based modifier hotkey (e.g. Right Option, Fn — modifier keys detected by keycode)
        if let targetKey = hotkey.keyCode, !hotkey.isModifierOnly {
            guard keyCode == targetKey else { return }

            if hotkey.isDoubleTap {
                // Fn/Globe fires flagsChanged with maskSecondaryFn on Apple Silicon (not keyDown)
                // Only react on press edge (flag becoming active), ignore release
                guard flags.contains(.maskSecondaryFn) else { return }
                let now = ProcessInfo.processInfo.systemUptime
                if now - lastFnTapTime < doubleTapInterval {
                    lastFnTapTime = 0
                    toggle()
                } else {
                    lastFnTapTime = now
                }
            } else {
                // Right Option: edge-detect press/release via maskAlternate
                let isPressed = flags.contains(.maskAlternate)
                if isPressed && !modifierFlagsActive {
                    modifierFlagsActive = true
                    toggle()
                } else if !isPressed && modifierFlagsActive {
                    modifierFlagsActive = false
                }
            }
            return
        }

        // Combo modifier hotkey (e.g. Ctrl+Option, Ctrl+Shift)
        guard hotkey.isModifierOnly, let required = hotkey.requiredFlags else { return }

        let activeModifiers = flags.intersection(allMonitoredFlags)
        let isMatch = activeModifiers == required

        if isMatch && !modifierFlagsActive {
            // Modifier combo just pressed — toggle on key-down edge
            modifierFlagsActive = true
            toggle()
        } else if !isMatch && modifierFlagsActive {
            // Modifiers released — just track the release, don't toggle again
            modifierFlagsActive = false
        }
    }

    fileprivate func handleKeyDown(_ keyCode: UInt16, flags: CGEventFlags) {
        // Shift+Up/Down for model cycling
        let isShiftOnly = flags.contains(.maskShift)
            && !flags.contains(.maskCommand)
            && !flags.contains(.maskControl)
            && !flags.contains(.maskAlternate)

        if isShiftOnly {
            if keyCode == 0x7E || keyCode == 0x7C { // Up arrow or Right arrow
                onModelCycleForward?()
                return
            } else if keyCode == 0x7D || keyCode == 0x7B { // Down arrow or Left arrow
                onModelCycleBackward?()
                return
            }
        }

        // Normal hotkey handling
        let hotkey = selectedHotkey

        guard !hotkey.isModifierOnly, let targetKey = hotkey.keyCode else { return }

        // doubleTapFn is handled in handleFlagsChanged (Fn fires flagsChanged on Apple Silicon)
        if keyCode == targetKey && !hotkey.isDoubleTap {
            toggle()
        }
    }

    deinit {
        stop()
    }
}

// MARK: - C Callback

private func hotkeyEventCallback(
    proxy: CGEventTapProxy,
    type: CGEventType,
    event: CGEvent,
    userInfo: UnsafeMutableRawPointer?
) -> Unmanaged<CGEvent>? {
    // If the tap is disabled by the system, re-enable it
    if type == .tapDisabledByTimeout || type == .tapDisabledByUserInput {
        if let userInfo {
            let manager = Unmanaged<HotkeyManager>.fromOpaque(userInfo).takeUnretainedValue()
            if let tap = manager.eventTap {
                CGEvent.tapEnable(tap: tap, enable: true)
            }
        }
        return Unmanaged.passUnretained(event)
    }

    guard let userInfo else {
        return Unmanaged.passUnretained(event)
    }

    let manager = Unmanaged<HotkeyManager>.fromOpaque(userInfo).takeUnretainedValue()

    if type == .flagsChanged {
        let keyCode = UInt16(event.getIntegerValueField(.keyboardEventKeycode))
        let flags = event.flags
        DispatchQueue.main.async {
            manager.handleFlagsChanged(keyCode: keyCode, flags: flags)
        }
    } else if type == .keyDown {
        let keyCode = UInt16(event.getIntegerValueField(.keyboardEventKeycode))
        let flags = event.flags
        DispatchQueue.main.async {
            manager.handleKeyDown(keyCode, flags: flags)
        }
    }

    // Pass the event through (don't consume it)
    return Unmanaged.passUnretained(event)
}
