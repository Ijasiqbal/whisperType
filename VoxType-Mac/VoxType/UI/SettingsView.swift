import AVFoundation
import ServiceManagement
import SwiftUI

struct SettingsView: View {

    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var usage: UsageManager

    @AppStorage(Constants.selectedModelKey) private var selectedModel = TranscriptionModel.auto.rawValue
    @AppStorage(Constants.launchAtLoginKey) private var launchAtLogin = false
    @EnvironmentObject var hotkeyManager: HotkeyManager
    @AppStorage(Constants.selectedHotkeyKey) private var selectedHotkeyRaw = HotkeyOption.ctrlOption.rawValue
    @State private var accessibilityGranted = PermissionChecker.isAccessibilityGranted
    @State private var accessibilityPollingTimer: Timer?

    var body: some View {
        TabView {
            generalTab
                .tabItem {
                    Label("General", systemImage: "gear")
                }

            accountTab
                .tabItem {
                    Label("Account", systemImage: "person.circle")
                }
        }
        .frame(width: 450, height: 420)
        .onAppear {
            activateSettingsWindow()
            startAccessibilityPolling()
        }
        .onDisappear {
            accessibilityPollingTimer?.invalidate()
            accessibilityPollingTimer = nil
        }
    }

    // MARK: - Accessibility Polling

    private func startAccessibilityPolling() {
        accessibilityPollingTimer?.invalidate()
        let t = Timer(timeInterval: 1.0, repeats: true) { [self] timer in
            let granted = PermissionChecker.isAccessibilityGranted
            if granted != accessibilityGranted {
                accessibilityGranted = granted
            }
            if granted {
                timer.invalidate()
                accessibilityPollingTimer = nil
            }
        }
        RunLoop.main.add(t, forMode: .common)
        accessibilityPollingTimer = t
    }

    // MARK: - Window Activation

    private func activateSettingsWindow() {
        // Activate the app to bring it to front
        NSApplication.shared.activate(ignoringOtherApps: true)

        // Find and bring the Settings window to front
        DispatchQueue.main.async {
            // Loop through all windows to find the Settings window
            for window in NSApplication.shared.windows {
                // Settings window typically has "Settings" in title or is a preferences window
                if window.title.contains("Settings") ||
                   window.title.contains("Preferences") ||
                   window.title.contains("Vozcribe") {
                    window.makeKeyAndOrderFront(nil)
                    window.orderFrontRegardless()
                    break
                }
            }
        }
    }

    // MARK: - General Tab

    private var generalTab: some View {
        Form {
            Section("Startup") {
                Toggle("Launch Vozcribe at login", isOn: $launchAtLogin)
                    .onChange(of: launchAtLogin) { newValue in
                        setLaunchAtLogin(newValue)
                    }
            }

            Section("Hotkey") {
                Picker("Record shortcut:", selection: $selectedHotkeyRaw) {
                    ForEach(HotkeyOption.allCases) { option in
                        Text(option.displayName).tag(option.rawValue)
                    }
                }
                .onChange(of: selectedHotkeyRaw) { newValue in
                    if let option = HotkeyOption(rawValue: newValue) {
                        hotkeyManager.changeHotkey(to: option)
                    }
                }
                Text("Press once to start recording, press again to stop and transcribe.")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Section("Permissions") {
                HStack {
                    Text("Accessibility")
                    Spacer()
                    if accessibilityGranted {
                        Label("Granted", systemImage: "checkmark.circle.fill")
                            .foregroundColor(.green)
                            .font(.system(size: 12))
                    } else {
                        Button("Grant Access") {
                            PermissionChecker.promptAndOpenAccessibilitySettings()
                        }
                        .controlSize(.small)
                    }
                }

                HStack {
                    Text("Microphone")
                    Spacer()
                    switch AVCaptureDevice.authorizationStatus(for: .audio) {
                    case .authorized:
                        Label("Granted", systemImage: "checkmark.circle.fill")
                            .foregroundColor(.green)
                            .font(.system(size: 12))
                    case .notDetermined:
                        Button("Grant Access") {
                            Task { await PermissionChecker.requestMicrophone() }
                        }
                        .controlSize(.small)
                    case .denied, .restricted:
                        Button("Open Settings") {
                            NSWorkspace.shared.open(URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone")!)
                        }
                        .controlSize(.small)
                    @unknown default:
                        EmptyView()
                    }
                }
            }

            Section("Model") {
                Picker("Transcription model:", selection: $selectedModel) {
                    ForEach(TranscriptionModel.allCases) { model in
                        Text(model.displayName).tag(model.rawValue)
                    }
                }
                .pickerStyle(.radioGroup)

                Text("Auto is fastest and free. Standard uses enhanced processing for better accuracy (1x credits). Premium is highest quality (2x credits).")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .formStyle(.grouped)
        .padding()
    }

    // MARK: - Account Tab

    private var accountTab: some View {
        Form {
            if auth.isSignedIn {
                Section("Profile") {
                    if let name = auth.userName {
                        LabeledContent("Name", value: name)
                    }
                    if let email = auth.userEmail {
                        LabeledContent("Email", value: email)
                    }
                }

                Section("Subscription") {
                    LabeledContent("Plan", value: usage.planDisplayText)
                    LabeledContent("Credits", value: usage.creditsDisplayText)

                    Button("Refresh") {
                        Task { await usage.refreshStatus() }
                    }
                    .controlSize(.small)
                }

                Section {
                    Button("Sign Out", role: .destructive) {
                        auth.signOut()
                    }
                }
            } else {
                Section {
                    VStack(spacing: 12) {
                        Text("Sign in to sync your subscription and credits across devices.")
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)

                        Button("Sign in with Google") {
                            auth.signInWithGoogle()
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 20)
                }
            }
        }
        .formStyle(.grouped)
        .padding()
    }

    // MARK: - Helpers

    private func setLaunchAtLogin(_ enabled: Bool) {
        do {
            if enabled {
                try SMAppService.mainApp.register()
            } else {
                try SMAppService.mainApp.unregister()
            }
        } catch {
            debugLog("[Settings] Launch at login error: \(error)")
        }
    }
}
