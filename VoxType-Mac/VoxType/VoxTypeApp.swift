import AVFoundation
import Combine
import FirebaseCore
import SwiftUI

@main
struct VoxTypeApp: App {

    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var transcriptionService = TranscriptionService.shared
    @StateObject private var authManager = AuthManager.shared
    @StateObject private var usageManager = UsageManager.shared
    @AppStorage(Constants.hasCompletedOnboardingKey) private var hasCompletedOnboarding = false

    init() {
        // Configure Firebase first
        guard let plistPath = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: plistPath) else {
            fatalError("GoogleService-Info.plist not found in bundle")
        }
        FirebaseApp.configure(options: options)

        // NOW wire up AuthManager — Firebase is ready
        AuthManager.shared.configure()
    }

    var body: some Scene {
        // Onboarding window for first-time users
        Window("Welcome to Vozcribe", id: "onboarding") {
            // Only show onboarding content if not completed
            if !hasCompletedOnboarding {
                OnboardingView {
                    // Mark onboarding as completed
                    hasCompletedOnboarding = true

                    // Force UserDefaults to sync to disk
                    UserDefaults.standard.synchronize()

                    debugLog("[Vozcribe] Onboarding completed and saved to UserDefaults")

                    // Clear the window restoration state to prevent re-opening
                    UserDefaults.standard.removeObject(forKey: "NSWindow Frame onboarding")
                    UserDefaults.standard.synchronize()

                    // Give a brief delay to ensure UserDefaults persists, then close window
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        if let window = NSApplication.shared.windows.first(where: { $0.title == "Welcome to Vozcribe" }) {
                            window.close()
                        }

                        // Complete app setup
                        appDelegate.completeOnboarding()
                    }
                }
                .environmentObject(authManager)
            } else {
                // If onboarding is complete, show empty view and close window
                EmptyView()
                    .onAppear {
                        // Close this window immediately if it somehow opened
                        if let window = NSApplication.shared.windows.first(where: { $0.title == "Welcome to Vozcribe" }) {
                            window.close()
                        }
                    }
            }
        }
        .windowStyle(.hiddenTitleBar)
        .windowResizability(.contentSize)
        .defaultPosition(.center)

        MenuBarExtra {
            MenuBarView()
                .environmentObject(transcriptionService)
                .environmentObject(authManager)
                .environmentObject(usageManager)
        } label: {
            MenuBarIcon(state: transcriptionService.state)
        }
        .menuBarExtraStyle(.window)

        Settings {
            SettingsView()
                .environmentObject(authManager)
                .environmentObject(usageManager)
                .environmentObject(appDelegate.hotkeyManager)
        }
        .commands {
            // Override the default Settings command to ensure window activation
            CommandGroup(replacing: .appSettings) {
                Button("Settings...") {
                    openSettingsWithActivation()
                }
                .keyboardShortcut(",", modifiers: .command)
            }
        }
    }

    // MARK: - Settings Activation

    private func openSettingsWithActivation() {
        // Activate the app to bring it to front
        NSApplication.shared.activate(ignoringOtherApps: true)

        // Open Settings window after ensuring app is active
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            NSApp.sendAction(Selector(("showSettingsWindow:")), to: nil, from: nil)
        }
    }
}

// MARK: - Menu Bar Icon

struct MenuBarIcon: View {
    let state: RecordingState

    var body: some View {
        switch state {
        case .idle:
            Image(systemName: "mic.fill")
        case .recording:
            Image(systemName: "mic.badge.plus")
                .symbolRenderingMode(.multicolor)
        case .processing:
            Image(systemName: "ellipsis.circle")
        case .inserted, .success:
            Image(systemName: "checkmark.circle.fill")
        case .error:
            Image(systemName: "mic.slash")
        }
    }
}

// MARK: - App Delegate

final class AppDelegate: NSObject, NSApplicationDelegate {

    let hotkeyManager = HotkeyManager()
    private var overlayWindow: OverlayWindowController?
    private var transcriptionStateObserver: AnyCancellable?
    private var wasProcessing = false

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        return false
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        // Firebase is configured in VozcribeApp.init()

        // Check version status before any other setup. If this build is
        // blocked, show a dialog and quit. Mirrors the Windows flow.
        Task { @MainActor in
            let result = await VoxTypeAPIClient.shared.checkVersion()
            if case .blocked = result.status {
                showVersionBlockedDialog(result)
                NSApp.terminate(nil)
                return
            }
            continueLaunch()

            let hasCompletedOnboarding = UserDefaults.standard
                .bool(forKey: Constants.hasCompletedOnboardingKey)
            let lastShown = UserDefaults.standard
                .string(forKey: Constants.lastSoftUpdateShownVersionKey)

            if SoftUpdateNudgePolicy.shouldShow(
                result: result,
                hasCompletedOnboarding: hasCompletedOnboarding,
                lastShownVersion: lastShown
            ) {
                showSoftUpdateNudge(result)
            }
        }
    }

    @MainActor
    private func showSoftUpdateNudge(_ result: VoxTypeAPIClient.VersionCheckResult) {
        guard let latestVersion = result.latestVersion else { return }

        // Mark as shown immediately so a dismiss/quit before responding still counts.
        UserDefaults.standard.set(latestVersion, forKey: Constants.lastSoftUpdateShownVersionKey)

        let alert = NSAlert()
        alert.messageText = "Update Available"
        alert.informativeText = result.message
            ?? "Version \(latestVersion) of Vozcribe is available."
        alert.alertStyle = .informational
        alert.addButton(withTitle: "Update Now")
        alert.addButton(withTitle: "Later")

        NSApp.activate(ignoringOtherApps: true)

        let response = alert.runModal()
        if response == .alertFirstButtonReturn {
            let urlString = result.downloadUrl ?? "https://vozcribe.com/mac"
            if let url = URL(string: urlString) {
                NSWorkspace.shared.open(url)
            }
        }
    }

    private func continueLaunch() {
        // Check if onboarding is needed
        let hasCompletedOnboarding = UserDefaults.standard.bool(forKey: Constants.hasCompletedOnboardingKey)
        debugLog("[Vozcribe] App launched - hasCompletedOnboarding: \(hasCompletedOnboarding)")

        if !hasCompletedOnboarding {
            // Show onboarding window for first-time users
            debugLog("[Vozcribe] Showing onboarding window")
            showOnboardingWindow()
            return  // Don't setup hotkeys yet, wait for onboarding completion
        }

        // Request microphone permission early so the system prompt appears
        // and Vozcribe shows up in System Settings > Privacy > Microphone
        requestMicrophonePermission()

        // Setup hotkey
        setupHotkey()

        // Setup transcription state observer for auto-hiding overlay after completion
        setupTranscriptionObserver()

        // Refresh usage status if signed in
        if AuthManager.shared.isSignedIn {
            Task {
                await UsageManager.shared.refreshStatus()
            }
        }

        debugLog("[Vozcribe] App launched")
    }

    @MainActor
    private func showVersionBlockedDialog(_ result: VoxTypeAPIClient.VersionCheckResult) {
        let alert = NSAlert()
        alert.messageText = "Update Required"
        alert.informativeText = result.message
            ?? "This version of Vozcribe is no longer supported. Please download the latest version to continue."
        alert.alertStyle = .warning
        alert.addButton(withTitle: "Download Update")
        alert.addButton(withTitle: "Quit")

        // Bring the app forward so the alert isn't hidden behind other windows.
        NSApp.activate(ignoringOtherApps: true)

        let response = alert.runModal()
        if response == .alertFirstButtonReturn {
            let urlString = result.downloadUrl ?? "https://vozcribe.com/mac"
            if let url = URL(string: urlString) {
                NSWorkspace.shared.open(url)
            }
        }
    }

    private func showOnboardingWindow() {
        // Activate app and open onboarding window
        DispatchQueue.main.async {
            NSApplication.shared.activate(ignoringOtherApps: true)

            // Open the onboarding window using its identifier
            if let onboardingWindow = NSApplication.shared.windows.first(where: {
                $0.identifier?.rawValue.contains("onboarding") == true
            }) {
                onboardingWindow.makeKeyAndOrderFront(nil)
                onboardingWindow.center()
            }
        }
    }

    func completeOnboarding() {
        // Called when onboarding is completed
        // Setup the app normally
        requestMicrophonePermission()
        setupHotkey()
        setupTranscriptionObserver()

        if AuthManager.shared.isSignedIn {
            Task {
                await UsageManager.shared.refreshStatus()
            }
        }

        debugLog("[Vozcribe] Onboarding completed, app fully initialized")
    }

    private func requestMicrophonePermission() {
        switch AVCaptureDevice.authorizationStatus(for: .audio) {
        case .notDetermined:
            debugLog("[Vozcribe] Requesting microphone permission...")
            AVCaptureDevice.requestAccess(for: .audio) { granted in
                debugLog("[Vozcribe] Microphone permission \(granted ? "granted" : "denied")")
            }
        case .denied, .restricted:
            debugLog("[Vozcribe] Microphone permission denied. Please enable in System Settings > Privacy & Security > Microphone")
        case .authorized:
            debugLog("[Vozcribe] Microphone permission already granted")
        @unknown default:
            break
        }
    }

    private func setupHotkey() {
        hotkeyManager.onRecordingStart = { [weak self] in
            // Capture frontmost app BEFORE async dispatch or overlay showing
            TextInsertionService.shared.captureFrontmostApp()

            DispatchQueue.main.async { [weak self] in
                let state = TranscriptionService.shared.state
                debugLog("[VOXDEBUG] onRecordingStart: state=\(state)")

                // If in success/inserted/error state, dismiss overlay instead of starting new recording
                if case .success = state {
                    TranscriptionService.shared.dismiss()
                    self?.hotkeyManager.isRecording = false
                    self?.hideOverlay()
                    return
                } else if case .inserted = state {
                    TranscriptionService.shared.dismiss()
                    self?.hotkeyManager.isRecording = false
                    self?.hideOverlay()
                    return
                } else if case .error = state {
                    TranscriptionService.shared.dismiss()
                    self?.hotkeyManager.isRecording = false
                    self?.hideOverlay()
                    return
                }

                // Otherwise, show overlay and start recording
                self?.showOverlay()
                TranscriptionService.shared.startRecording()
            }
        }

        hotkeyManager.onRecordingStop = { [weak self] in
            DispatchQueue.main.async { [weak self] in
                let state = TranscriptionService.shared.state
                debugLog("[VOXDEBUG] onRecordingStop: state=\(state)")

                // If in success/inserted/error state, dismiss overlay
                if case .success = state {
                    TranscriptionService.shared.dismiss()
                    self?.hotkeyManager.isRecording = false
                    self?.hideOverlay()
                    return
                } else if case .inserted = state {
                    TranscriptionService.shared.dismiss()
                    self?.hotkeyManager.isRecording = false
                    self?.hideOverlay()
                    return
                } else if case .error = state {
                    TranscriptionService.shared.dismiss()
                    self?.hotkeyManager.isRecording = false
                    self?.hideOverlay()
                    return
                }

                // Otherwise, stop recording and transcribe
                TranscriptionService.shared.stopRecordingAndTranscribe()
                // Overlay will dismiss automatically after transcription completes
                // (handled by transcription state observer)
            }
        }

        hotkeyManager.onModelCycleForward = {
            DispatchQueue.main.async {
                let current = TranscriptionModel.current
                let next = current.next()
                next.saveAsSelected()
                ModelSwitchOverlayController.shared.show(model: next)
                debugLog("[Vozcribe] Model switched to \(next.shortName)")
            }
        }

        hotkeyManager.onEscapePressed = { [weak self] in
            DispatchQueue.main.async {
                let state = TranscriptionService.shared.state
                debugLog("[VOXDEBUG] onEscapePressed: state=\(state)")
                switch state {
                case .recording:
                    TranscriptionService.shared.cancelRecording()
                    self?.hotkeyManager.isRecording = false
                case .idle:
                    break
                default:
                    TranscriptionService.shared.dismiss()
                    self?.hotkeyManager.isRecording = false
                }
            }
        }

        hotkeyManager.onModelCycleBackward = {
            DispatchQueue.main.async {
                let current = TranscriptionModel.current
                let prev = current.previous()
                prev.saveAsSelected()
                ModelSwitchOverlayController.shared.show(model: prev)
                debugLog("[Vozcribe] Model switched to \(prev.shortName)")
            }
        }

        // Sync hotkey from UserDefaults — user may have changed it during onboarding
        // after HotkeyManager was initialized at app launch.
        let saved = UserDefaults.standard.string(forKey: Constants.selectedHotkeyKey) ?? ""
        if let option = HotkeyOption(rawValue: saved) {
            hotkeyManager.selectedHotkey = option
        }

        let isGranted = HotkeyManager.isAccessibilityGranted

        if isGranted {
            hotkeyManager.start()
        } else {
            // Keep trying every 3 seconds until accessibility is granted
            Timer.scheduledTimer(withTimeInterval: 3.0, repeats: true) { [weak self] timer in
                if HotkeyManager.isAccessibilityGranted {
                    self?.hotkeyManager.start()
                    timer.invalidate()
                }
            }
        }
    }

    // MARK: - Transcription Observer

    private func setupTranscriptionObserver() {
        Task { @MainActor in
            transcriptionStateObserver = TranscriptionService.shared.$state
                .sink { [weak self] state in
                    // Track when we enter processing state
                    if case .processing = state {
                        self?.wasProcessing = true
                    }

                    // Text field was focused — auto-close quickly after confirmed insert
                    if case .inserted = state, self?.wasProcessing == true {
                        self?.wasProcessing = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                            if case .inserted = TranscriptionService.shared.state {
                                TranscriptionService.shared.dismiss()
                                self?.hideOverlay()
                                self?.hotkeyManager.isRecording = false
                            }
                        }
                    }

                    // No text field detected — keep overlay open with copy button for 6s
                    if case .success = state, self?.wasProcessing == true {
                        self?.wasProcessing = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 6.0) {
                            if case .success = TranscriptionService.shared.state {
                                TranscriptionService.shared.dismiss()
                                self?.hideOverlay()
                                self?.hotkeyManager.isRecording = false
                            }
                        }
                    }

                    // Also hide on error after showing error message
                    // But NOT if there's retryable audio — let user retry or save
                    if case .error(let msg) = state {
                        debugLog("[VOXDEBUG] Error state: \(msg), hasRetryableAudio=\(TranscriptionService.shared.hasRetryableAudio)")
                        self?.wasProcessing = false
                        if !TranscriptionService.shared.hasRetryableAudio {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                                // Only auto-hide if still in error state and no retryable audio
                                if case .error = TranscriptionService.shared.state,
                                   !TranscriptionService.shared.hasRetryableAudio {
                                    TranscriptionService.shared.dismiss()
                                    self?.hideOverlay()
                                    self?.hotkeyManager.isRecording = false
                                }
                            }
                        }
                    }

                    // When dismissed to idle, hide overlay
                    if case .idle = state {
                        self?.wasProcessing = false
                        self?.hideOverlay()
                        self?.hotkeyManager.isRecording = false
                    }
                }
        }
    }

    // MARK: - Overlay

    private func showOverlay() {
        if overlayWindow == nil {
            overlayWindow = OverlayWindowController()
        }
        overlayWindow?.showOverlay()
        wasProcessing = false
    }

    private func hideOverlay() {
        overlayWindow?.hideOverlay()
    }

    func application(_ application: NSApplication, open urls: [URL]) {
        for url in urls {
            AuthManager.shared.handleCallbackURL(url)
        }
    }
}
