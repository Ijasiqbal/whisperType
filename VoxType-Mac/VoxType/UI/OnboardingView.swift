import AVFoundation
import SwiftUI

struct OnboardingView: View {

    @EnvironmentObject var auth: AuthManager
    @State private var currentStep = 0
    @State private var microphoneGranted = PermissionChecker.isMicrophoneGranted
    @State private var accessibilityGranted = HotkeyManager.isAccessibilityGranted
    var onComplete: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            // Step content
            Group {
                switch currentStep {
                case 0: welcomeStep
                case 1: signInStep
                case 2: permissionsStep
                case 3: readyStep
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
                    ForEach(0..<4, id: \.self) { step in
                        Circle()
                            .fill(step == currentStep ? Color.accentColor : Color.secondary.opacity(0.3))
                            .frame(width: 8, height: 8)
                    }
                }

                Spacer()

                if currentStep < 3 {
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
        .frame(width: 500, height: 400)
    }

    // MARK: - Steps

    private var welcomeStep: some View {
        VStack(spacing: 16) {
            Image(systemName: "mic.badge.xmark")
                .font(.system(size: 60))
                .foregroundColor(.accentColor)

            Text("Welcome to VoxType")
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
                        auth.signInWithApple()
                    } label: {
                        Label("Sign in with Apple", systemImage: "apple.logo")
                            .frame(width: 220)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .tint(.black)

                    Button {
                        auth.signInWithGoogle()
                    } label: {
                        Label("Sign in with Google", systemImage: "globe")
                            .frame(width: 220)
                    }
                    .buttonStyle(.bordered)
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

            Text("VoxType needs two permissions to work:")
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
                        Task {
                            let granted = await PermissionChecker.requestMicrophone()
                            print("[VoxType] Microphone permission: \(granted)")
                            // Update state on main thread
                            await MainActor.run {
                                microphoneGranted = granted
                            }
                        }
                    }
                    .controlSize(.large)
                }

                if !accessibilityGranted {
                    Button("Open Accessibility Settings") {
                        NSWorkspace.shared.open(URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility")!)
                    }
                    .controlSize(.large)
                }
            }

            if AVCaptureDevice.authorizationStatus(for: .audio) == .denied {
                Text("Microphone was denied. Go to **System Settings > Privacy & Security > Microphone** and enable VoxType.")
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

    private var readyStep: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.circle")
                .font(.system(size: 60))
                .foregroundColor(.green)

            Text("You're All Set!")
                .font(.title)
                .fontWeight(.bold)

            VStack(alignment: .leading, spacing: 8) {
                Label("Press **Ctrl+Option** to start recording", systemImage: "keyboard")
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
