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

    // Retained audio data for retry after error
    var lastFailedAudioData: Data?
    var lastFailedAudioFormat: String = "wav"
    var lastFailedDurationMs: Int = 0
    var lastFailedModel: TranscriptionModel?

    /// Whether the current error state has retryable audio data
    var hasRetryableAudio: Bool {
        lastFailedAudioData != nil && !(lastFailedAudioData?.isEmpty ?? true)
    }

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

        let meta = audioResult.metadata
        let durationMs = meta.silenceTrimmingApplied ? meta.speechDurationMs : meta.originalDurationMs
        let model = selectedModel

        print("[Transcription] Audio: \(audioResult.data.count) bytes (\(audioResult.format)), speech: \(meta.speechDurationMs)ms of \(meta.originalDurationMs)ms, segments: \(meta.speechSegmentCount), trimmed: \(meta.silenceTrimmingApplied)")

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
                lastFailedAudioData = nil // Not retryable
                clearErrorAfterDelay()
                return
            }

            lastTranscription = text

            // Clear failed audio on success
            lastFailedAudioData = nil
            lastFailedModel = nil

            NSLog("[VOXDEBUG] Transcription complete: \(text.prefix(50))...")

            // Always attempt to paste (harmless if no text field)
            // AND always show overlay with copy button as backup
            textInsertion.insertText(text)
            state = .inserted(text)
            NSLog("[VOXDEBUG] Text inserted + overlay shown with copy button")

        } catch {
            // Retain audio data for retry
            lastFailedAudioData = audioData
            lastFailedAudioFormat = format
            lastFailedDurationMs = durationMs
            lastFailedModel = model

            state = .error(error.localizedDescription)
            lastError = error.localizedDescription
            // Do NOT auto-dismiss - let user retry or save
            print("[Transcription] Error (retryable): \(error)")
        }
    }

    // MARK: - Retry & Save

    /// Retry the last failed transcription with an optional different model.
    func retryWithModel(_ model: TranscriptionModel? = nil) {
        guard let audioData = lastFailedAudioData, !audioData.isEmpty else {
            print("[Transcription] No audio data to retry")
            return
        }

        let targetModel = model ?? lastFailedModel ?? selectedModel
        let format = lastFailedAudioFormat
        let durationMs = lastFailedDurationMs

        state = .processing

        Task {
            await transcribeAndInsert(audioData: audioData, format: format, durationMs: durationMs, model: targetModel)
        }
    }

    /// Save the last failed audio to local storage for later retry.
    func saveForLater() {
        guard let audioData = lastFailedAudioData, !audioData.isEmpty else {
            print("[Transcription] No audio data to save")
            return
        }

        let model = lastFailedModel ?? selectedModel
        let errorMsg = lastError ?? "Unknown error"

        PendingTranscriptionManager.shared.save(
            audioData: audioData,
            audioFormat: lastFailedAudioFormat,
            durationMs: lastFailedDurationMs,
            failedModel: model,
            errorMessage: errorMsg
        )

        // Clear retained audio
        lastFailedAudioData = nil
        lastFailedModel = nil

        // Brief "Saved" confirmation then dismiss
        state = .inserted("Saved for later")
        Task {
            try? await Task.sleep(nanoseconds: UInt64(Constants.successMessageDelayMs) * 1_000_000)
            if case .inserted("Saved for later") = state {
                state = .idle
            }
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
        else if case .error = state {
            // Clear retained audio data to free memory
            lastFailedAudioData = nil
            lastFailedModel = nil
            state = .idle
        }
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
