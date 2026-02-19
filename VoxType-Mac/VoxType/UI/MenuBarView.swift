import SwiftUI

struct MenuBarView: View {

    @EnvironmentObject var transcription: TranscriptionService
    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var usage: UsageManager
    @AppStorage(Constants.selectedModelKey) private var selectedModelRaw = TranscriptionModel.groqTurbo.rawValue

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header
            HStack {
                Image(systemName: "mic.fill")
                    .foregroundColor(.accentColor)
                Text("Wozcribe")
                    .font(.headline)
                Spacer()
            }
            .padding(.bottom, 4)

            Divider()

            // Status
            if auth.isSignedIn {
                statusSection
            } else {
                signInPrompt
            }

            Divider()

            // Model picker
            modelPickerSection

            Divider()

            // Hotkey hint
            hotkeyHint

            Divider()

            // Last transcription
            if let last = transcription.lastTranscription {
                lastTranscriptionSection(last)
                Divider()
            }

            // Actions
            actionButtons
        }
        .padding(12)
        .frame(width: 280)
    }

    // MARK: - Sections

    private var statusSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("Plan:")
                    .foregroundColor(.secondary)
                Text(usage.planDisplayText)
                    .fontWeight(.medium)
            }
            .font(.system(size: 12))

            HStack {
                Text("Credits:")
                    .foregroundColor(.secondary)
                Text(usage.creditsDisplayText)
                    .fontWeight(.medium)
                    .foregroundColor(usage.isLow ? .orange : .primary)
            }
            .font(.system(size: 12))

            if let email = auth.userEmail {
                Text(email)
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
            }
        }
    }

    private var modelPickerSection: some View {
        ModelPickerMenuSection(selectedModelRaw: $selectedModelRaw)
    }

    private var signInPrompt: some View {
        VStack(spacing: 8) {
            Text("Sign in to start transcribing")
                .font(.system(size: 12))
                .foregroundColor(.secondary)

            Button("Sign in with Google") {
                auth.signInWithGoogle()
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.small)

            Button("Sign in with Apple") {
                auth.signInWithApple()
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 4)
    }

    private var hotkeyHint: some View {
        HStack(spacing: 6) {
            stateIndicator

            VStack(alignment: .leading, spacing: 2) {
                Text(stateText)
                    .font(.system(size: 12, weight: .medium))

                Text("Press \(hotkeyLabel) to toggle recording")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 2)
    }

    @ViewBuilder
    private var stateIndicator: some View {
        switch transcription.state {
        case .idle:
            Circle()
                .fill(.green)
                .frame(width: 8, height: 8)
        case .recording:
            Circle()
                .fill(.red)
                .frame(width: 8, height: 8)
        case .processing:
            ProgressView()
                .controlSize(.mini)
        case .inserted, .success:
            Circle()
                .fill(.green)
                .frame(width: 8, height: 8)
        case .error:
            Circle()
                .fill(.orange)
                .frame(width: 8, height: 8)
        }
    }

    private var stateText: String {
        switch transcription.state {
        case .idle: return "Ready"
        case .recording: return "Recording..."
        case .processing: return "Transcribing..."
        case .inserted, .success: return "Done"
        case .error(let msg): return msg
        }
    }

    private var hotkeyLabel: String {
        let saved = UserDefaults.standard.string(forKey: Constants.selectedHotkeyKey) ?? ""
        let option = HotkeyOption(rawValue: saved) ?? .ctrlOption
        return option.shortLabel
    }

    private func lastTranscriptionSection(_ text: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("Last transcription:")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
                Spacer()
                Button {
                    transcription.copyToClipboard()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "doc.on.doc")
                            .font(.system(size: 10))
                        Text("Copy")
                            .font(.system(size: 10, weight: .medium))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(
                        Capsule()
                            .fill(Color.accentColor)
                    )
                }
                .buttonStyle(.plain)
            }
            Text(text)
                .font(.system(size: 12))
                .lineLimit(3)
                .truncationMode(.tail)
        }
    }

    private var actionButtons: some View {
        VStack(spacing: 4) {
            Button {
                openSettings()
            } label: {
                Label("Settings...", systemImage: "gear")
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
            .padding(.vertical, 2)

            if auth.isSignedIn {
                Button {
                    auth.signOut()
                } label: {
                    Label("Sign Out", systemImage: "arrow.right.square")
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
                .padding(.vertical, 2)
            }

            Divider()

            Button {
                NSApplication.shared.terminate(nil)
            } label: {
                Label("Quit Wozcribe", systemImage: "xmark.circle")
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
            .padding(.vertical, 2)
            .keyboardShortcut("q")
        }
    }

    // MARK: - Helpers

    private func openSettings() {
        // First, activate the app to ensure it comes to front
        NSApplication.shared.activate(ignoringOtherApps: true)

        // Small delay to ensure app is activated before opening Settings
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            // Open Settings window using standard macOS action
            NSApp.sendAction(Selector(("showSettingsWindow:")), to: nil, from: nil)
        }
    }
}

// MARK: - Model Picker Section (extracted for type-checker performance)

struct ModelPickerMenuSection: View {
    @Binding var selectedModelRaw: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Model")
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(.secondary)

            ForEach(TranscriptionModel.allCases) { model in
                modelRow(model)
            }
        }
    }

    private func modelRow(_ model: TranscriptionModel) -> some View {
        let isSelected = model.rawValue == selectedModelRaw

        return Button {
            selectedModelRaw = model.rawValue
        } label: {
            HStack(spacing: 8) {
                Circle()
                    .fill(model.color)
                    .frame(width: 8, height: 8)

                Text(model.shortName)
                    .font(.system(size: 12, weight: isSelected ? .semibold : .regular))

                Spacer()

                Text(model.creditLabel)
                    .font(.system(size: 10))
                    .foregroundColor(.secondary)

                if isSelected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(model.color)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, 3)
            .padding(.horizontal, 6)
            .background(
                RoundedRectangle(cornerRadius: 5)
                    .fill(isSelected ? model.color.opacity(0.12) : Color.clear)
            )
        }
        .buttonStyle(.plain)
    }
}
