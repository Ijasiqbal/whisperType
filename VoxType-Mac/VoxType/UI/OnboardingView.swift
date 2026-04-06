import AVFoundation
import SwiftUI

struct OnboardingView: View {

    @EnvironmentObject var auth: AuthManager
    @SceneStorage("onboarding_step") private var currentStep = 0
    @State private var microphoneGranted = PermissionChecker.isMicrophoneGranted
    @State private var accessibilityGranted = HotkeyManager.isAccessibilityGranted
    @State private var selectedHotkey: HotkeyOption = {
        let raw = UserDefaults.standard.string(forKey: Constants.selectedHotkeyKey) ?? ""
        return HotkeyOption(rawValue: raw) ?? .ctrlOption
    }()
    var onComplete: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            // Step content
            Group {
                switch currentStep {
                case 0: welcomeStep
                case 1: signInStep
                case 2: permissionsStep
                case 3: hotkeyStep
                case 4: readyStep
                default: EmptyView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            // Navigation
            HStack {
                if currentStep > 0 {
                    Button("Back") {
                        withAnimation { currentStep -= 1 }
                    }
                    .controlSize(.large)
                }

                Spacer()

                // Step indicators
                HStack(spacing: 6) {
                    ForEach(0..<5, id: \.self) { step in
                        Circle()
                            .fill(step == currentStep ? Color.accentColor : Color.secondary.opacity(0.3))
                            .frame(width: 8, height: 8)
                    }
                }

                Spacer()

                if currentStep < 4 {
                    Button("Next") {
                        withAnimation { currentStep += 1 }
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .disabled(currentStep == 1 && !auth.isSignedIn)
                } else {
                    Button("Get Started") {
                        UserDefaults.standard.set(true, forKey: Constants.hasCompletedOnboardingKey)
                        // Force UserDefaults to sync to disk immediately
                        UserDefaults.standard.synchronize()
                        onComplete()
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                }
            }
            .padding(24)
        }
        .frame(width: 500, height: 480)
    }

    // MARK: - Steps

    private var welcomeStep: some View {
        VStack(spacing: 16) {
            Image(systemName: "mic.badge.xmark")
                .font(.system(size: 60))
                .foregroundColor(.accentColor)

            Text("Welcome to Vozcribe")
                .font(.largeTitle)
                .fontWeight(.bold)

            Text("Voice-to-text that works everywhere.\nPress a hotkey, speak, and your words appear.")
                .multilineTextAlignment(.center)
                .foregroundColor(.secondary)
                .font(.body)
        }
        .padding(40)
    }

    private var signInStep: some View {
        VStack(spacing: 16) {
            Image(systemName: "person.circle")
                .font(.system(size: 48))
                .foregroundColor(.accentColor)

            Text("Sign In")
                .font(.title)
                .fontWeight(.bold)

            Text("Sign in to sync your subscription and credits with your Android device.")
                .multilineTextAlignment(.center)
                .foregroundColor(.secondary)

            if auth.isSignedIn {
                Label("Signed in as \(auth.userEmail ?? "user")", systemImage: "checkmark.circle.fill")
                    .foregroundColor(.green)
                    .padding(.top, 8)
            } else {
                VStack(spacing: 10) {
                    Button {
                        auth.signInWithGoogle()
                    } label: {
                        Label("Sign in with Google", systemImage: "globe")
                            .frame(width: 220)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                }
                .padding(.top, 8)

                if auth.isLoading {
                    ProgressView()
                        .padding(.top, 8)
                }
            }
        }
        .padding(40)
    }

    private var permissionsStep: some View {
        VStack(spacing: 16) {
            Image(systemName: "lock.shield")
                .font(.system(size: 48))
                .foregroundColor(.accentColor)

            Text("Permissions")
                .font(.title)
                .fontWeight(.bold)

            Text("Vozcribe needs two permissions to work:")
                .foregroundColor(.secondary)

            VStack(alignment: .leading, spacing: 12) {
                permissionRow(
                    icon: "mic.fill",
                    title: "Microphone",
                    description: "To record your voice",
                    isGranted: microphoneGranted
                )

                permissionRow(
                    icon: "hand.raised.fill",
                    title: "Accessibility",
                    description: "For global hotkey & text insertion",
                    isGranted: accessibilityGranted
                )
            }
            .padding(20)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(.quaternary)
            )

            HStack(spacing: 12) {
                if !microphoneGranted {
                    Button("Grant Microphone Access") {
                        let status = AVCaptureDevice.authorizationStatus(for: .audio)
                        if status == .notDetermined {
                            AVCaptureDevice.requestAccess(for: .audio) { granted in
                                DispatchQueue.main.async {
                                    microphoneGranted = granted
                                    if !granted {
                                        if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone") {
                                            NSWorkspace.shared.open(url)
                                        }
                                    }
                                }
                            }
                        } else if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone") {
                            NSWorkspace.shared.open(url)
                        }
                    }
                    .controlSize(.large)
                }

                if !accessibilityGranted {
                    Button("Open Accessibility Settings") {
                        if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility") {
                            NSWorkspace.shared.open(url)
                        }
                    }
                    .controlSize(.large)
                }
            }

            if AVCaptureDevice.authorizationStatus(for: .audio) == .denied {
                Text("Microphone was denied. Go to **System Settings > Privacy & Security > Microphone** and enable Vozcribe.")
                    .font(.caption)
                    .foregroundColor(.orange)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(40)
        .onAppear {
            // Start polling for permission changes
            Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { timer in
                let newMicStatus = PermissionChecker.isMicrophoneGranted
                let newAccessStatus = HotkeyManager.isAccessibilityGranted

                if newMicStatus != microphoneGranted {
                    microphoneGranted = newMicStatus
                }
                if newAccessStatus != accessibilityGranted {
                    accessibilityGranted = newAccessStatus
                }

                // Stop timer if we move away from permissions step
                if currentStep != 2 {
                    timer.invalidate()
                }
            }
        }
    }

    private var hotkeyStep: some View {
        VStack(spacing: 16) {
            Image(systemName: "keyboard")
                .font(.system(size: 48))
                .foregroundColor(.accentColor)

            Text("Choose Your Hotkey")
                .font(.title)
                .fontWeight(.bold)

            Text("Pick the shortcut you'll press to start and stop recording.")
                .multilineTextAlignment(.center)
                .foregroundColor(.secondary)

            ScrollView {
                VStack(spacing: 8) {
                    ForEach(HotkeyOption.allCases) { option in
                        Button {
                            selectedHotkey = option
                            UserDefaults.standard.set(option.rawValue, forKey: Constants.selectedHotkeyKey)
                        } label: {
                            HStack {
                                Text(option.shortLabel)
                                    .font(.title3)
                                    .frame(width: 50)
                                Text(option.displayName)
                                    .fontWeight(.medium)
                                Spacer()
                                if selectedHotkey == option {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundColor(.accentColor)
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(selectedHotkey == option ? Color.accentColor.opacity(0.15) : Color.clear)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(selectedHotkey == option ? Color.accentColor : Color.secondary.opacity(0.2), lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .frame(maxHeight: 240)
        }
        .padding(.horizontal, 40)
        .padding(.top, 24)
        .padding(.bottom, 8)
    }

    private var readyStep: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.circle")
                .font(.system(size: 60))
                .foregroundColor(.green)

            Text("You're All Set!")
                .font(.title)
                .fontWeight(.bold)

            VStack(alignment: .leading, spacing: 8) {
                Label("Press **\(selectedHotkey.displayName)** to start recording", systemImage: "keyboard")
                Label("Press again to stop and transcribe", systemImage: "text.cursor")
                Label("Click the menu bar icon for settings", systemImage: "menubar.arrow.up.rectangle")
            }
            .font(.body)
            .padding(20)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(.quaternary)
            )
        }
        .padding(40)
    }

    // MARK: - Helpers

    private func permissionRow(icon: String, title: String, description: String, isGranted: Bool) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.title3)
                .frame(width: 24)
                .foregroundColor(.accentColor)

            VStack(alignment: .leading) {
                Text(title)
                    .fontWeight(.medium)
                Text(description)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()

            if isGranted {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundColor(.green)
            } else {
                Image(systemName: "exclamationmark.circle")
                    .foregroundColor(.orange)
            }
        }
    }
}
