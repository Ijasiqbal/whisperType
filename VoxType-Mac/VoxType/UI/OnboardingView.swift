import AVFoundation
import SwiftUI

struct OnboardingView: View {

    @EnvironmentObject var auth: AuthManager
    @State private var currentStep = 0
    @State private var microphoneGranted = PermissionChecker.isMicrophoneGranted
    @State private var accessibilityGranted = HotkeyManager.isAccessibilityGranted
    @State private var selectedHotkey: HotkeyOption = .ctrlSpace
    var onComplete: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Group {
                switch currentStep {
                case 0: welcomeStep
                case 1: signInStep
                case 2: microphoneStep
                case 3: hotkeyStep
                case 4: readyStep
                default: EmptyView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            HStack {
                if currentStep > 0 {
                    Button("Back") {
                        withAnimation { currentStep -= 1 }
                    }
                    .controlSize(.large)
                }

                Spacer()

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
                    .disabled(nextDisabled)
                } else {
                    Button("Get Started") {
                        UserDefaults.standard.set(selectedHotkey.rawValue, forKey: Constants.selectedHotkeyKey)
                        UserDefaults.standard.set(true, forKey: Constants.hasCompletedOnboardingKey)
                        UserDefaults.standard.synchronize()
                        print("[Vozcribe] Onboarding completed — hotkey: \(selectedHotkey.displayName)")

                        UserDefaults.standard.removeObject(forKey: "NSWindow Frame onboarding")
                        UserDefaults.standard.synchronize()

                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                            if let window = NSApplication.shared.windows.first(where: { $0.title == "Welcome to Vozcribe" }) {
                                window.close()
                            }
                            onComplete()
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                }
            }
            .padding(24)
        }
        .frame(width: 520, height: 460)
    }

    private var nextDisabled: Bool {
        switch currentStep {
        case 1: return !auth.isSignedIn
        case 2: return !microphoneGranted
        case 3: return selectedHotkey.requiresAccessibility && !accessibilityGranted
        default: return false
        }
    }

    // MARK: - Step 0: Welcome

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

    // MARK: - Step 1: Sign In

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

    // MARK: - Step 2: Microphone

    private var microphoneStep: some View {
        VStack(spacing: 16) {
            Image(systemName: "mic.fill")
                .font(.system(size: 48))
                .foregroundColor(.accentColor)

            Text("Microphone Access")
                .font(.title)
                .fontWeight(.bold)

            Text("Vozcribe needs microphone access to record your voice.")
                .multilineTextAlignment(.center)
                .foregroundColor(.secondary)

            if microphoneGranted {
                Label("Microphone access granted", systemImage: "checkmark.circle.fill")
                    .foregroundColor(.green)
                    .padding(.top, 8)
            } else {
                Button("Grant Microphone Access") {
                    Task {
                        let granted = await PermissionChecker.requestMicrophone()
                        await MainActor.run {
                            microphoneGranted = granted
                        }
                    }
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .padding(.top, 8)

                if AVCaptureDevice.authorizationStatus(for: .audio) == .denied {
                    Text("Microphone was denied. Go to **System Settings > Privacy & Security > Microphone** and enable Vozcribe.")
                        .font(.caption)
                        .foregroundColor(.orange)
                        .multilineTextAlignment(.center)
                }
            }
        }
        .padding(40)
        .onAppear {
            microphoneGranted = PermissionChecker.isMicrophoneGranted
        }
    }

    // MARK: - Step 3: Hotkey Selection

    private var hotkeyStep: some View {
        VStack(spacing: 16) {
            Image(systemName: "keyboard")
                .font(.system(size: 48))
                .foregroundColor(.accentColor)

            Text("Choose Your Hotkey")
                .font(.title)
                .fontWeight(.bold)

            Text("Press this key combo to start and stop recording.")
                .foregroundColor(.secondary)

            VStack(spacing: 10) {
                ForEach(HotkeyOption.allCases) { option in
                    hotkeyCard(option)
                }
            }
            .padding(.top, 4)

            if selectedHotkey.requiresAccessibility {
                VStack(spacing: 8) {
                    Divider()
                        .padding(.vertical, 4)

                    if accessibilityGranted {
                        Label("Accessibility permission granted", systemImage: "checkmark.circle.fill")
                            .foregroundColor(.green)
                            .font(.system(size: 13))
                    } else {
                        VStack(spacing: 6) {
                            Text("Accessibility lets Vozcribe detect modifier-only key combos. Your keystrokes are never recorded or stored.")
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)

                            Button("Grant Accessibility Access") {
                                PermissionChecker.openAccessibilitySettings()
                            }
                            .controlSize(.regular)
                        }
                    }
                }
            }
        }
        .padding(32)
        .onAppear {
            startAccessibilityPolling()
        }
    }

    private func hotkeyCard(_ option: HotkeyOption) -> some View {
        let isSelected = selectedHotkey == option

        return Button {
            withAnimation(.easeInOut(duration: 0.15)) {
                selectedHotkey = option
            }
            if option.requiresAccessibility {
                startAccessibilityPolling()
            }
        } label: {
            HStack(spacing: 12) {
                Text(option.shortLabel)
                    .font(.system(size: 15, weight: .bold, design: .monospaced))
                    .foregroundColor(isSelected ? .white : .primary)
                    .frame(width: 60)
                    .padding(.vertical, 6)
                    .background(
                        RoundedRectangle(cornerRadius: 6)
                            .fill(isSelected ? Color.accentColor : Color.secondary.opacity(0.15))
                    )

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(option.displayName)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.primary)

                        if option == .ctrlSpace {
                            Text("Recommended")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Capsule().fill(Color.green))
                        }

                        if option.requiresAccessibility {
                            Text("Requires permission")
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(.orange)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Capsule().strokeBorder(Color.orange.opacity(0.5), lineWidth: 1))
                        }
                    }

                    Text(option.subtitle)
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                }

                Spacer()

                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.accentColor)
                }
            }
            .padding(10)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(isSelected ? Color.accentColor.opacity(0.08) : Color.clear)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(isSelected ? Color.accentColor : Color.secondary.opacity(0.2), lineWidth: isSelected ? 2 : 1)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Step 4: Ready

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

    private func startAccessibilityPolling() {
        Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { timer in
            let newStatus = HotkeyManager.isAccessibilityGranted
            if newStatus != accessibilityGranted {
                accessibilityGranted = newStatus
            }
            if currentStep != 3 || accessibilityGranted {
                timer.invalidate()
            }
        }
    }
}
