import Cocoa
import SwiftUI

// MARK: - Overlay Window Controller

final class OverlayWindowController {

    private var window: NSPanel?

    func showOverlay() {
        if window == nil {
            createWindow()
        }
        window?.orderFront(nil)
    }

    func hideOverlay() {
        window?.orderOut(nil)
    }

    private func createWindow() {
        let hostingView = NSHostingView(rootView: RecordingOverlayView().padding(2))
        let fittingSize = hostingView.fittingSize
        let width = max(fittingSize.width, 250)
        let height = max(fittingSize.height, 70)
        hostingView.frame = NSRect(x: 0, y: 0, width: width, height: height)

        let panel = NSPanel(
            contentRect: NSRect(x: 0, y: 0, width: width, height: height),
            styleMask: [.nonactivatingPanel, .borderless],
            backing: .buffered,
            defer: false
        )

        panel.contentView = hostingView
        panel.isFloatingPanel = true
        panel.level = .floating
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.hasShadow = true
        panel.hidesOnDeactivate = false
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]

        // Position near top center of screen
        if let screen = NSScreen.main {
            let screenFrame = screen.visibleFrame
            let x = screenFrame.midX - (width / 2)
            let y = screenFrame.maxY - 80
            panel.setFrameOrigin(NSPoint(x: x, y: y))
        }

        window = panel
    }
}

// MARK: - SwiftUI Overlay View

struct RecordingOverlayView: View {

    @StateObject private var service = TranscriptionService.shared
    @ObservedObject private var audioRecorder = TranscriptionService.shared.audioRecorder  // Observe audioRecorder directly for amplitude updates
    @AppStorage(Constants.selectedModelKey) private var selectedModelRaw = TranscriptionModel.groqTurbo.rawValue
    @State private var showModelPicker = false

    private var currentModel: TranscriptionModel {
        TranscriptionModel(rawValue: selectedModelRaw) ?? .groqTurbo
    }

    var body: some View {
        HStack(spacing: 10) {
            // Left side: Mic icon or Copy button
            if case .inserted = service.state {
                // Show copy button as fallback
                Button(action: {
                    service.copyToClipboard()
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "doc.on.doc")
                            .font(.system(size: 12, weight: .semibold))
                        Text("Copy")
                            .font(.system(size: 12, weight: .semibold))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 6)
                    .background(
                        Capsule()
                            .fill(Color.green.opacity(0.6))
                    )
                }
                .buttonStyle(.plain)
            } else if case .success = service.state {
                // No text field — show copy button
                Button(action: {
                    service.copyToClipboard()
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "doc.on.doc")
                            .font(.system(size: 12, weight: .semibold))
                        Text("Copy")
                            .font(.system(size: 12, weight: .semibold))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 6)
                    .background(
                        Capsule()
                            .fill(Color.green.opacity(0.6))
                    )
                }
                .buttonStyle(.plain)
            } else {
                // Mic icon with pulse — tinted to model color
                ZStack {
                    Circle()
                        .fill(currentModel.color.opacity(0.25))
                        .frame(width: 36, height: 36)

                    Image(systemName: micIconName)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white)
                }
            }

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(statusText)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.white)
                        .lineLimit(1)

                    // Clickable model badge (hide in final states)
                    if !isInFinalState {
                        modelBadge
                    }
                }

                if case .recording = service.state {
                    // Amplitude bars — tinted to model color
                    HStack(spacing: 2) {
                        ForEach(0..<8, id: \.self) { i in
                            AmplitudeBar(
                                amplitude: audioRecorder.currentAmplitude,
                                index: i,
                                color: currentModel.color
                            )
                        }
                    }
                    .frame(height: 20)
                }

                if case .processing = service.state {
                    ProgressView()
                        .controlSize(.small)
                        .tint(.white)
                }

                // Show transcribed text preview
                if case .success(let text) = service.state {
                    Text(text)
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.8))
                        .lineLimit(2)
                        .frame(maxWidth: 200, alignment: .leading)
                } else if case .inserted(let text) = service.state {
                    Text(text)
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.8))
                        .lineLimit(2)
                        .frame(maxWidth: 200, alignment: .leading)
                }
            }

            Spacer()

            // Right side: Duration or Close button
            if case .recording = service.state {
                Text(formattedDuration)
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundColor(.white.opacity(0.8))
            } else {
                // Close button (always visible except when recording)
                Button(action: {
                    service.dismiss()
                }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(.white.opacity(0.6))
                        .frame(width: 20, height: 20)
                        .background(
                            Circle()
                                .fill(.white.opacity(0.1))
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 28)
                .fill(.ultraThinMaterial)
                .environment(\.colorScheme, .dark)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 28)
                .stroke(overlayBorderColor, lineWidth: 1)
        )
        .animation(.easeInOut(duration: 0.2), value: service.state)
    }

    // MARK: - Model Badge

    private var modelBadge: some View {
        Menu {
            ForEach(TranscriptionModel.allCases) { model in
                Button {
                    selectedModelRaw = model.rawValue
                } label: {
                    HStack {
                        Text("\(model.shortName) — \(model.creditLabel)")
                        if model.rawValue == selectedModelRaw {
                            Image(systemName: "checkmark")
                        }
                    }
                }
            }
        } label: {
            Text(currentModel.shortName)
                .font(.system(size: 9, weight: .semibold))
                .foregroundColor(.white)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(
                    Capsule()
                        .fill(currentModel.color.opacity(0.6))
                )
        }
        .menuStyle(.borderlessButton)
        .fixedSize()
    }

    private var micIconName: String {
        switch service.state {
        case .recording: return "mic.fill"
        case .processing: return "waveform"
        case .inserted: return "checkmark.circle.fill"
        case .success: return "checkmark.circle.fill"
        case .error: return "mic.slash"
        default: return "mic"
        }
    }

    private var statusText: String {
        switch service.state {
        case .recording: return "Listening..."
        case .processing: return "Transcribing..."
        case .inserted: return "Text inserted!"
        case .success: return "Copy your text"
        case .error(let msg): return msg
        default: return "Ready"
        }
    }

    private var isInFinalState: Bool {
        if case .success = service.state { return true }
        if case .inserted = service.state { return true }
        if case .error = service.state { return true }
        return false
    }

    private var overlayBorderColor: Color {
        switch service.state {
        case .inserted: return .green.opacity(0.5)
        case .success: return .green.opacity(0.5)
        case .error: return .red.opacity(0.5)
        default: return currentModel.color.opacity(0.3)
        }
    }

    private var formattedDuration: String {
        let seconds = Int(audioRecorder.recordingDuration)
        return String(format: "%d:%02d", seconds / 60, seconds % 60)
    }
}

// MARK: - Amplitude Bar

struct AmplitudeBar: View {
    let amplitude: Float
    let index: Int
    var color: Color = .white

    var body: some View {
        // Apply sqrt curve so quiet sounds are still visible
        let boosted = sqrt(CGFloat(amplitude))
        let barAmplitude = max(
            CGFloat(Constants.minAmplitudeBarScale),
            min(boosted * randomScale, 1.0)
        )

        RoundedRectangle(cornerRadius: 1.5)
            .fill(color.opacity(0.85))
            .frame(width: 3, height: barAmplitude * 20)
            .animation(.interactiveSpring(response: 0.12, dampingFraction: 0.6), value: amplitude)
    }

    private var randomScale: CGFloat {
        let offsets: [CGFloat] = [0.7, 1.0, 0.85, 1.15, 0.9, 1.05, 0.75, 0.95]
        return offsets[index % offsets.count]
    }
}

// MARK: - Model Switch Toast Overlay

final class ModelSwitchOverlayController {

    static let shared = ModelSwitchOverlayController()

    private var window: NSPanel?
    private var hideTask: DispatchWorkItem?

    func show(model: TranscriptionModel) {
        hideTask?.cancel()

        if window == nil {
            createWindow()
        }

        // Update the content
        if let hostingView = window?.contentView as? NSHostingView<ModelSwitchToastView> {
            hostingView.rootView = ModelSwitchToastView(model: model)
        } else {
            let view = NSHostingView(rootView: ModelSwitchToastView(model: model))
            view.frame = NSRect(x: 0, y: 0, width: 180, height: 40)
            window?.contentView = view
        }

        window?.alphaValue = 0
        window?.orderFront(nil)

        // Fade in
        NSAnimationContext.runAnimationGroup { context in
            context.duration = 0.15
            window?.animator().alphaValue = 1
        }

        // Auto-hide after 1.5s
        let task = DispatchWorkItem { [weak self] in
            NSAnimationContext.runAnimationGroup({ context in
                context.duration = 0.3
                self?.window?.animator().alphaValue = 0
            }, completionHandler: {
                self?.window?.orderOut(nil)
            })
        }
        hideTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5, execute: task)
    }

    private func createWindow() {
        let panel = NSPanel(
            contentRect: NSRect(x: 0, y: 0, width: 180, height: 40),
            styleMask: [.nonactivatingPanel, .hudWindow],
            backing: .buffered,
            defer: false
        )

        panel.isFloatingPanel = true
        panel.level = .floating
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.hasShadow = true
        panel.hidesOnDeactivate = false
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]

        // Position near top center, slightly below where the recording overlay would be
        if let screen = NSScreen.main {
            let screenFrame = screen.visibleFrame
            let x = screenFrame.midX - 90
            let y = screenFrame.maxY - 130
            panel.setFrameOrigin(NSPoint(x: x, y: y))
        }

        window = panel
    }
}

struct ModelSwitchToastView: View {
    let model: TranscriptionModel

    var body: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(model.color)
                .frame(width: 10, height: 10)

            Text(model.shortName)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.white)

            Text("(\(model.creditLabel))")
                .font(.system(size: 11))
                .foregroundColor(.white.opacity(0.7))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(
            Capsule()
                .fill(.ultraThinMaterial)
                .environment(\.colorScheme, .dark)
        )
        .overlay(
            Capsule()
                .stroke(model.color.opacity(0.4), lineWidth: 1)
        )
    }
}
