import AVFoundation
import ServiceManagement
import SwiftUI

struct SettingsView: View {

    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var usage: UsageManager

    @AppStorage(Constants.selectedModelKey) private var selectedModel = TranscriptionModel.groqTurbo.rawValue
    @AppStorage(Constants.launchAtLoginKey) private var launchAtLogin = false
    @AppStorage(Constants.selectedRegionKey) private var selectedRegion = ""
    @AppStorage(Constants.selectedHotkeyKey) private var selectedHotkeyRaw = HotkeyOption.ctrlOption.rawValue

    var body: some View {
        TabView {
            generalTab
                .tabItem {
                    Label("General", systemImage: "gear")
                }

            transcriptionTab
                .tabItem {
                    Label("Transcription", systemImage: "waveform")
                }

            accountTab
                .tabItem {
                    Label("Account", systemImage: "person.circle")
                }
        }
        .frame(width: 450, height: 300)
        .onAppear {
            // Ensure Settings window comes to front when opened
            activateSettingsWindow()
        }
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
                   window.title.contains("VoxType") {
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
                Toggle("Launch VoxType at login", isOn: $launchAtLogin)
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
                Text("Press once to start recording, press again to stop and transcribe.")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Section("Permissions") {
                HStack {
                    Text("Accessibility")
                    Spacer()
                    if HotkeyManager.isAccessibilityGranted {
                        Label("Granted", systemImage: "checkmark.circle.fill")
                            .foregroundColor(.green)
                            .font(.system(size: 12))
                    } else {
                        Button("Grant Access") {
                            NSWorkspace.shared.open(URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility")!)
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
        }
        .formStyle(.grouped)
        .padding()
    }

    // MARK: - Transcription Tab

    private var transcriptionTab: some View {
        Form {
            Section("Model") {
                Picker("Transcription model:", selection: $selectedModel) {
                    ForEach(TranscriptionModel.allCases) { model in
                        Text(model.displayName).tag(model.rawValue)
                    }
                }
                .pickerStyle(.radioGroup)

                Text("Groq Turbo is fastest and free. OpenAI models use 2x credits but may be more accurate.")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Section("Region") {
                Picker("Server region:", selection: $selectedRegion) {
                    Text("Auto (based on timezone)").tag("")
                    ForEach(RegionSelector.allRegions, id: \.id) { region in
                        Text(region.name).tag(region.id)
                    }
                }

                Text("Current: \(RegionSelector.bestRegion())")
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

                        Button("Sign in with Apple") {
                            auth.signInWithApple()
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
            print("[Settings] Launch at login error: \(error)")
        }
    }
}
