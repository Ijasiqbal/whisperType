import AppKit
import Combine
import Foundation

@MainActor
final class TranscriptionService: ObservableObject {

    static let shared = TranscriptionService()

    @Published var state: RecordingState = .idle
    @Published var lastTranscription: String?
    @Published var lastError: String?
    @Published var isShowingRecordingWarning = false
    @Published var warningSecondsLeft = 60

    @Published var audioRecorder = AudioRecorder()  // MUST be @Published so amplitude updates trigger view refresh
    private let apiClient = VoxTypeAPIClient.shared
    private let textInsertion = TextInsertionService.shared
    private let usageManager = UsageManager.shared

    private var recordingLimitTask: Task<Void, Never>?

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
        return TranscriptionModel(rawValue: raw) ?? .auto
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
                    debugLog("[Transcription] Auth token pre-fetched")
                } catch {
                    debugLog("[Transcription] Auth warmup failed (non-critical): \(error.localizedDescription)")
                }
            }

            // Warmup API endpoints in background
            Task {
                await VoxTypeAPIClient.shared.warmAllEndpoints()
            }

            scheduleRecordingLimit()
            debugLog("[Transcription] Recording started")
        } catch {
            state = .error(error.localizedDescription)
            lastError = error.localizedDescription
            debugLog("[Transcription] Failed to start recording: \(error)")
        }
    }

    func stopRecordingAndTranscribe() {
        guard case .recording = state else { return }

        cancelRecordingLimit()
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

        debugLog("[Transcription] Audio: \(audioResult.data.count) bytes (\(audioResult.format)), speech: \(meta.speechDurationMs)ms of \(meta.originalDurationMs)ms, segments: \(meta.speechSegmentCount), trimmed: \(meta.silenceTrimmingApplied)")

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

            debugLog("[VOXDEBUG] Transcription complete: \(text.prefix(50))...")

            // Paste and confirm via cursor shift detection
            // Returns true if cursor advanced (paste confirmed), false if unconfirmed
            let pasteConfirmed = await textInsertion.insertText(text)

            if pasteConfirmed {
                state = .inserted(text)  // auto-closes in 1.5s
                debugLog("[VOXDEBUG] Paste confirmed — auto-closing overlay")
            } else {
                state = .success(text)   // shows copy button for 6s
                debugLog("[VOXDEBUG] Paste unconfirmed — showing copy button")
            }

        } catch {
            // Retain audio data for retry
            lastFailedAudioData = audioData
            lastFailedAudioFormat = format
            lastFailedDurationMs = durationMs
            lastFailedModel = model

            state = .error(error.localizedDescription)
            lastError = error.localizedDescription
            // Do NOT auto-dismiss - let user retry or save
            debugLog("[Transcription] Error (retryable): \(error)")
        }
    }

    // MARK: - Retry & Save

    /// Retry the last failed transcription with an optional different model.
    func retryWithModel(_ model: TranscriptionModel? = nil) {
        guard let audioData = lastFailedAudioData, !audioData.isEmpty else {
            debugLog("[Transcription] No audio data to retry")
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
            debugLog("[Transcription] No audio data to save")
            return
        }

        let model = lastFailedModel ?? selectedModel
        let errorMsg = lastError ?? "Unknown error"

        let saved = PendingTranscriptionManager.shared.save(
            audioData: audioData,
            audioFormat: lastFailedAudioFormat,
            durationMs: lastFailedDurationMs,
            failedModel: model,
            errorMessage: errorMsg
        )

        guard saved != nil else {
            state = .error("Could not save recording. Check available disk space.")
            return
        }

        // Clear retained audio only after confirmed write
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

    /// Cancel an in-progress recording without transcribing
    func cancelRecording() {
        guard case .recording = state else { return }
        cancelRecordingLimit()
        _ = audioRecorder.stopRecording() // discard audio
        state = .idle
        debugLog("[Transcription] Recording cancelled by user")
    }

    /// Extend the current recording by another 5 minutes, resetting the warning.
    func extendRecording() {
        isShowingRecordingWarning = false
        scheduleRecordingLimit()
        debugLog("[Transcription] Recording extended by 5 minutes")
    }

    // MARK: - Recording Limit

    private func scheduleRecordingLimit() {
        recordingLimitTask?.cancel()
        recordingLimitTask = nil
        recordingLimitTask = Task {
            // Wait 5 minutes before warning
            try? await Task.sleep(nanoseconds: 5 * 60 * 1_000_000_000)
            guard !Task.isCancelled else { return }

            isShowingRecordingWarning = true
            warningSecondsLeft = 60

            for i in stride(from: 59, through: 0, by: -1) {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard !Task.isCancelled else { return }
                warningSecondsLeft = i
            }

            guard !Task.isCancelled else { return }
            isShowingRecordingWarning = false
            stopRecordingAndTranscribe()
            debugLog("[Transcription] Recording auto-stopped after limit reached")
        }
    }

    private func cancelRecordingLimit() {
        recordingLimitTask?.cancel()
        recordingLimitTask = nil
        isShowingRecordingWarning = false
    }

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
            debugLog("[Transcription] Copied to clipboard: \(text.prefix(50))...")
        }
    }
}
