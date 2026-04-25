import SwiftUI

struct MenuBarView: View {

    @EnvironmentObject var transcription: TranscriptionService
    @EnvironmentObject var auth: AuthManager
    @EnvironmentObject var usage: UsageManager
    @ObservedObject var pendingManager = PendingTranscriptionManager.shared
    @AppStorage(Constants.selectedModelKey) private var selectedModelRaw = TranscriptionModel.auto.rawValue
    @State private var showingReportIssue = false
    @State private var updateAlert: UpdateAlert?

    private struct UpdateAlert: Identifiable {
        let id = UUID()
        let title: String
        let message: String
        let downloadUrl: String?
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header
            HStack {
                Image(systemName: "mic.fill")
                    .foregroundColor(.accentColor)
                Text("Vozcribe")
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

            Group {
                Divider()

                // Last transcription
                if let last = transcription.lastTranscription {
                    lastTranscriptionSection(last)
                    Divider()
                }

                // Pending transcriptions
                if !pendingManager.entries.isEmpty {
                    pendingSection
                    Divider()
                }

                // Actions
                actionButtons
            }
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

    private var pendingSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Image(systemName: "exclamationmark.circle")
                    .font(.system(size: 11))
                    .foregroundColor(.orange)
                Text("Pending (\(pendingManager.pendingCount))")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.secondary)
                Spacer()
                if pendingManager.entries.contains(where: { $0.status == .pending }) {
                    Button {
                        Task { await pendingManager.retryAll() }
                    } label: {
                        Text("Retry All")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Capsule().fill(Color.accentColor))
                    }
                    .buttonStyle(.plain)
                }
            }

            ForEach(pendingManager.entries.prefix(5)) { entry in
                PendingEntryRow(entry: entry, manager: pendingManager)
            }

            if pendingManager.entries.count > 5 {
                Text("+\(pendingManager.entries.count - 5) more")
                    .font(.system(size: 10))
                    .foregroundColor(.secondary)
            }
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
                checkForUpdate()
            } label: {
                Label("Check for Update", systemImage: "arrow.down.circle")
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
            .padding(.vertical, 2)

            Button {
                showingReportIssue = true
            } label: {
                Label("Report an Issue", systemImage: "exclamationmark.bubble")
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
            .padding(.vertical, 2)
            .sheet(isPresented: $showingReportIssue) {
                ReportIssueView()
                    .environmentObject(auth)
            }

            Divider()

            Button {
                NSApplication.shared.terminate(nil)
            } label: {
                Label("Quit Vozcribe", systemImage: "xmark.circle")
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
            .padding(.vertical, 2)
            .keyboardShortcut("q")
        }
        .alert(item: $updateAlert) { alert in
            if let url = alert.downloadUrl {
                return Alert(
                    title: Text(alert.title),
                    message: Text(alert.message),
                    primaryButton: .default(Text("Download"), action: { openURL(url) }),
                    secondaryButton: .cancel()
                )
            } else {
                return Alert(title: Text(alert.title), message: Text(alert.message), dismissButton: .default(Text("OK")))
            }
        }
    }

    // MARK: - Helpers

    private func openSettings() {
        NSApplication.shared.activate(ignoringOtherApps: true)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            NSApp.sendAction(Selector(("showSettingsWindow:")), to: nil, from: nil)
        }
    }

    private func openURL(_ urlString: String) {
        guard let url = URL(string: urlString) else { return }
        NSWorkspace.shared.open(url)
    }

    private func checkForUpdate() {
        Task {
            let result = await VoxTypeAPIClient.shared.checkVersion()
            let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0"
            await MainActor.run {
                switch result.status {
                case .updateAvailable:
                    updateAlert = UpdateAlert(
                        title: "Update Available",
                        message: result.message ?? "Version \(result.latestVersion ?? "") is available.",
                        downloadUrl: result.downloadUrl ?? "https://vozcribe.com/mac"
                    )
                case .blocked:
                    updateAlert = UpdateAlert(
                        title: "Update Required",
                        message: result.message ?? "This version is no longer supported.",
                        downloadUrl: result.downloadUrl ?? "https://vozcribe.com/mac"
                    )
                case .ok:
                    updateAlert = UpdateAlert(
                        title: "You're up to date",
                        message: "Vozcribe v\(appVersion) is the latest version.",
                        downloadUrl: nil
                    )
                }
            }
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
        .contentShape(Rectangle())
    }
}

// MARK: - Pending Entry Row

struct PendingEntryRow: View {
    let entry: PendingTranscriptionManager.PendingTranscription
    @ObservedObject var manager: PendingTranscriptionManager
    @State private var isRetrying = false

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a"
        return formatter
    }()

    private var timeText: String {
        Self.timeFormatter.string(from: entry.timestamp)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Image(systemName: entry.status == .completed ? "checkmark.circle.fill" : "mic.fill")
                    .font(.system(size: 10))
                    .foregroundColor(entry.status == .completed ? .green : .orange)

                Text(timeText)
                    .font(.system(size: 11, weight: .medium))

                Text("\(entry.durationMs / 1000)s")
                    .font(.system(size: 10))
                    .foregroundColor(.secondary)

                Spacer()

                // Delete button (only for completed entries; pending entries have delete in pill row)
                if entry.status == .completed {
                    Button {
                        manager.delete(id: entry.id)
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                    }
                    .buttonStyle(.plain)
                }
            }

            if entry.status == .completed, let text = entry.transcribedText {
                // Show transcribed text with copy button
                Text(text)
                    .font(.system(size: 11))
                    .lineLimit(2)
                    .truncationMode(.tail)
                    .foregroundColor(.primary)

                Button {
                    let pasteboard = NSPasteboard.general
                    pasteboard.clearContents()
                    pasteboard.setString(text, forType: .string)
                } label: {
                    HStack(spacing: 3) {
                        Image(systemName: "doc.on.doc")
                            .font(.system(size: 9))
                        Text("Copy")
                            .font(.system(size: 10, weight: .medium))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Capsule().fill(Color.accentColor))
                }
                .buttonStyle(.plain)
            } else {
                // Show error and action pills
                Text(entry.errorMessage)
                    .font(.system(size: 10))
                    .foregroundColor(.red)
                    .lineLimit(1)
                    .truncationMode(.tail)

                if isRetrying {
                    ProgressView()
                        .controlSize(.mini)
                } else {
                    HStack(spacing: 4) {
                        // Retry pill (filled)
                        Button {
                            isRetrying = true
                            Task {
                                await manager.retry(entry: entry)
                                isRetrying = false
                            }
                        } label: {
                            HStack(spacing: 3) {
                                Image(systemName: "arrow.clockwise")
                                    .font(.system(size: 9))
                                Text("Retry")
                                    .font(.system(size: 10, weight: .medium))
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(Color.accentColor))
                        }
                        .buttonStyle(.plain)

                        // Model picker pill (outlined)
                        Menu {
                            ForEach(TranscriptionModel.allCases) { model in
                                Button {
                                    isRetrying = true
                                    Task {
                                        await manager.retry(entry: entry, withModel: model)
                                        isRetrying = false
                                    }
                                } label: {
                                    Text("\(model.shortName) (\(model.creditLabel))")
                                }
                            }
                        } label: {
                            HStack(spacing: 2) {
                                Text(entry.failedModel)
                                    .font(.system(size: 10, weight: .medium))
                                Image(systemName: "chevron.down")
                                    .font(.system(size: 6, weight: .bold))
                            }
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(
                                Capsule()
                                    .strokeBorder(Color.secondary.opacity(0.4), lineWidth: 0.5)
                            )
                        }
                        .menuStyle(.borderlessButton)
                        .fixedSize()

                        // Delete pill (ghost)
                        Button {
                            manager.delete(id: entry.id)
                        } label: {
                            HStack(spacing: 3) {
                                Image(systemName: "trash")
                                    .font(.system(size: 9))
                                Text("Delete")
                                    .font(.system(size: 10, weight: .medium))
                            }
                            .foregroundColor(.secondary.opacity(0.7))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(
                                Capsule()
                                    .fill(Color.primary.opacity(0.05))
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(.vertical, 4)
        .padding(.horizontal, 6)
        .background(
            RoundedRectangle(cornerRadius: 6)
                .fill(Color.primary.opacity(0.04))
        )
    }
}
