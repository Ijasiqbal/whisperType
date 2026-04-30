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
        .sheet(item: $updateAlert) { alert in
            UpdateAvailableSheet(
                title: alert.title,
                message: alert.message,
                downloadUrl: alert.downloadUrl,
                onDismiss: { updateAlert = nil },
                onOpenSite: { url in openURL(url) }
            )
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

// MARK: - Update Available Sheet

struct UpdateAvailableSheet: View {

    let title: String
    let message: String
    let downloadUrl: String?
    let onDismiss: () -> Void
    let onOpenSite: (String) -> Void

    @State private var copied = false
    @State private var pulse = false

    private let command = "brew upgrade --cask vozcribe"

    // Palette
    private let bg = Color(red: 0.058, green: 0.058, blue: 0.066)
    private let card = Color(red: 0.086, green: 0.086, blue: 0.094)
    private let hairline = Color.white.opacity(0.08)
    private let textPrimary = Color.white.opacity(0.94)
    private let textMuted = Color.white.opacity(0.55)
    private let textDim = Color.white.opacity(0.38)
    private let accent = Color(red: 0.486, green: 0.890, blue: 0.545) // soft terminal green

    var body: some View {
        VStack(spacing: 0) {
            header
            Divider().background(hairline)
            content
            Divider().background(hairline)
            footer
        }
        .frame(width: 380)
        .background(bg)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(hairline, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.5), radius: 30, y: 12)
    }

    // MARK: Header

    private var header: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(accent)
                .frame(width: 6, height: 6)
                .shadow(color: accent.opacity(0.7), radius: 4)
            Text("VOZCRIBE / UPDATE")
                .font(.system(size: 10, weight: .semibold, design: .monospaced))
                .tracking(2)
                .foregroundColor(textDim)
            Spacer()
            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(textDim)
                    .frame(width: 22, height: 22)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 14)
    }

    // MARK: Content

    private var content: some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 6) {
                Text(title)
                    .font(.system(.title2, design: .serif).weight(.medium))
                    .foregroundColor(textPrimary)
                Text(message)
                    .font(.system(size: 12.5))
                    .foregroundColor(textMuted)
                    .fixedSize(horizontal: false, vertical: true)
                    .lineSpacing(2)
            }

            terminalCard

            HStack(spacing: 6) {
                Image(systemName: "info.circle")
                    .font(.system(size: 10))
                Text("Paste in Terminal to install the latest build.")
                    .font(.system(size: 11))
            }
            .foregroundColor(textDim)
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 22)
    }

    private var terminalCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Faux title bar
            HStack(spacing: 6) {
                Circle().fill(Color.white.opacity(0.18)).frame(width: 6, height: 6)
                Circle().fill(Color.white.opacity(0.12)).frame(width: 6, height: 6)
                Circle().fill(Color.white.opacity(0.08)).frame(width: 6, height: 6)
                Spacer()
                Text("zsh")
                    .font(.system(size: 9.5, weight: .medium, design: .monospaced))
                    .tracking(1)
                    .foregroundColor(textDim)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color.white.opacity(0.03))

            // Command row
            HStack(spacing: 10) {
                Text("$")
                    .font(.system(size: 12.5, weight: .semibold, design: .monospaced))
                    .foregroundColor(accent.opacity(0.85))
                Text(command)
                    .font(.system(size: 12.5, design: .monospaced))
                    .foregroundColor(textPrimary)
                Spacer(minLength: 8)
                copyButton
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 14)
        }
        .background(card)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .strokeBorder(pulse ? accent.opacity(0.45) : hairline, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private var copyButton: some View {
        Button(action: copyCommand) {
            HStack(spacing: 5) {
                Image(systemName: copied ? "checkmark" : "doc.on.doc")
                    .font(.system(size: 10, weight: .semibold))
                Text(copied ? "Copied" : "Copy")
                    .font(.system(size: 10.5, weight: .semibold, design: .monospaced))
                    .tracking(0.5)
            }
            .foregroundColor(copied ? accent : textPrimary)
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background(
                RoundedRectangle(cornerRadius: 5)
                    .fill(copied ? accent.opacity(0.12) : Color.white.opacity(0.06))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 5)
                    .strokeBorder(copied ? accent.opacity(0.4) : Color.white.opacity(0.1), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .animation(.spring(response: 0.3, dampingFraction: 0.7), value: copied)
    }

    // MARK: Footer

    private var footer: some View {
        HStack(spacing: 12) {
            if let url = downloadUrl {
                Button(action: { onOpenSite(url) }) {
                    HStack(spacing: 4) {
                        Text("Open install page")
                        Image(systemName: "arrow.up.right")
                            .font(.system(size: 9, weight: .semibold))
                    }
                    .font(.system(size: 11.5))
                    .foregroundColor(textMuted)
                }
                .buttonStyle(.plain)
            }
            Spacer()
            Button(action: onDismiss) {
                Text(copied ? "Done" : "Close")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(.black)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 7)
                    .background(
                        RoundedRectangle(cornerRadius: 6)
                            .fill(copied ? accent : Color.white.opacity(0.92))
                    )
            }
            .buttonStyle(.plain)
            .keyboardShortcut(.defaultAction)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 14)
    }

    // MARK: Actions

    private func copyCommand() {
        let pb = NSPasteboard.general
        pb.clearContents()
        pb.setString(command, forType: .string)

        withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
            copied = true
            pulse = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            withAnimation(.easeOut(duration: 0.6)) { pulse = false }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            withAnimation(.easeOut(duration: 0.3)) { copied = false }
        }
    }
}
