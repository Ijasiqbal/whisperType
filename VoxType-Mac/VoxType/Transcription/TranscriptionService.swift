import AppKit
import Combine
import Foundation

@MainActor
final class TranscriptionService: ObservableObject {

    static let shared = TranscriptionService()

    @Published var state: RecordingState = .idle
    @Published var lastTranscription: String?
    @Published var lastError: String?

    @Published var audioRecorder = AudioRecorder()  // MUST be @Published so amplitude updates trigger view refresh
    private let apiClient = VoxTypeAPIClient.shared
    private let textInsertion = TextInsertionService.shared
    private let usageManager = UsageManager.shared

    private var selectedModel: TranscriptionModel {
        let raw = UserDefaults.standard.string(forKey: Constants.selectedModelKey) ?? ""
        return TranscriptionModel(rawValue: raw) ?? .groqTurbo
    }

    private init() {}

    // MARK: - Recording Control

    func startRecording() {
        guard case .idle = state else {
            if case .success = state { state = .idle }
            if case .inserted = state { state = .idle }
            return
        }

        do {
            try audioRecorder.startRecording()
            state = .recording
            lastError = nil

            // Pre-fetch auth token so it's ready when recording stops
            Task {
                do {
                    _ = try await AuthManager.shared.getIDToken()
                    print("[Transcription] Auth token pre-fetched")
                } catch {
                    print("[Transcription] Auth warmup failed (non-critical): \(error.localizedDescription)")
                }
            }

            // Warmup API endpoints in background
            Task {
                await VoxTypeAPIClient.shared.warmAllEndpoints()
            }

            print("[Transcription] Recording started")
        } catch {
            state = .error(error.localizedDescription)
            lastError = error.localizedDescription
            print("[Transcription] Failed to start recording: \(error)")
        }
    }

    func stopRecordingAndTranscribe() {
        guard case .recording = state else { return }

        state = .processing

        guard let audioResult = audioRecorder.stopRecording() else {
            state = .error("Recording too short")
            lastError = "Recording too short. Try recording a bit longer."
            clearErrorAfterDelay()
            return
        }

        let durationMs = audioRecorder.audioDurationMs
        let model = selectedModel

        print("[Transcription] Audio recorded: \(audioResult.data.count) bytes (\(audioResult.format)), \(durationMs)ms")

        Task {
            await transcribeAndInsert(audioData: audioResult.data, format: audioResult.format, durationMs: durationMs, model: model)
        }
    }

    // MARK: - Transcription Pipeline

    private func transcribeAndInsert(audioData: Data, format: String, durationMs: Int, model: TranscriptionModel) async {
        do {
            let result = try await apiClient.transcribe(
                audioData: audioData,
                format: format,
                model: model,
                audioDurationMs: durationMs
            )

            // Update usage
            usageManager.updateFromTranscriptionResult(result)

            let text = result.text.trimmingCharacters(in: .whitespacesAndNewlines)

            guard !text.isEmpty else {
                state = .error("No speech detected")
                lastError = "No speech detected."
                clearErrorAfterDelay()
                return
            }

            lastTranscription = text

            NSLog("[VOXDEBUG] Transcription complete: \(text.prefix(50))...")

            // Always attempt to paste (harmless if no text field)
            // AND always show overlay with copy button as backup
            textInsertion.insertText(text)
            state = .inserted(text)
            NSLog("[VOXDEBUG] Text inserted + overlay shown with copy button")

        } catch let error as VoxTypeError {
            state = .error(error.localizedDescription ?? "Unknown error")
            lastError = error.localizedDescription
            clearErrorAfterDelay()
            print("[Transcription] Error: \(error)")

        } catch {
            state = .error(error.localizedDescription)
            lastError = error.localizedDescription
            clearErrorAfterDelay()
            print("[Transcription] Error: \(error)")
        }
    }

    // MARK: - Helpers

    private func clearErrorAfterDelay() {
        Task {
            try? await Task.sleep(nanoseconds: UInt64(Constants.errorMessageDelayMs) * 1_000_000)
            if case .error = state {
                state = .idle
            }
        }
    }

    // MARK: - Manual Control

    /// Manually dismiss the overlay (reset to idle)
    func dismiss() {
        if case .success = state { state = .idle }
        else if case .inserted = state { state = .idle }
        else if case .error = state { state = .idle }
    }

    /// Copy text to clipboard
    func copyToClipboard() {
        let text: String?
        if case .success(let t) = state { text = t }
        else if case .inserted(let t) = state { text = t }
        else { text = nil }

        if let text {
            let pasteboard = NSPasteboard.general
            pasteboard.clearContents()
            pasteboard.setString(text, forType: .string)
            print("[Transcription] Copied to clipboard: \(text.prefix(50))...")
        }
    }
}
