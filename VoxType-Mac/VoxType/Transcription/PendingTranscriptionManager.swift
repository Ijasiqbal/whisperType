import Foundation

/// Manages locally saved audio recordings that failed transcription.
/// Audio files are stored in Application Support, metadata in a JSON file.
@MainActor
final class PendingTranscriptionManager: ObservableObject {

    static let shared = PendingTranscriptionManager()

    @Published var entries: [PendingTranscription] = []

    private static let maxEntries = 50
    private let fileManager = FileManager.default

    private init() {
        entries = loadEntries()
    }

    // MARK: - Data Model

    struct PendingTranscription: Identifiable, Codable, Equatable {
        let id: String
        let timestamp: Date
        let durationMs: Int
        let failedModel: String
        let errorMessage: String
        let audioFileName: String
        let audioFormat: String
        var status: Status = .pending
        var transcribedText: String?

        enum Status: String, Codable {
            case pending
            case completed
        }
    }

    // MARK: - Storage Paths

    private var storageDir: URL {
        let appSupport = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let dir = appSupport.appendingPathComponent("VoxType/PendingTranscriptions", isDirectory: true)
        try? fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private var metadataURL: URL {
        storageDir.appendingPathComponent("metadata.json")
    }

    private var audioDir: URL {
        let dir = storageDir.appendingPathComponent("audio", isDirectory: true)
        try? fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    // MARK: - Public API

    /// Save a failed audio recording for later retry.
    @discardableResult
    func save(audioData: Data, audioFormat: String, durationMs: Int, failedModel: TranscriptionModel, errorMessage: String) -> PendingTranscription {
        let id = UUID().uuidString
        let fileName = "audio_\(id).\(audioFormat)"
        let audioFileURL = audioDir.appendingPathComponent(fileName)

        try? audioData.write(to: audioFileURL)

        let entry = PendingTranscription(
            id: id,
            timestamp: Date(),
            durationMs: durationMs,
            failedModel: failedModel.shortName,
            errorMessage: errorMessage,
            audioFileName: fileName,
            audioFormat: audioFormat
        )

        entries.insert(entry, at: 0)
        // Evict oldest entries beyond cap
        while entries.count > Self.maxEntries {
            let removed = entries.removeLast()
            let audioURL = audioDir.appendingPathComponent(removed.audioFileName)
            try? fileManager.removeItem(at: audioURL)
        }
        saveEntries()

        print("[PendingTxn] Saved: \(id) (\(audioData.count) bytes)")
        return entry
    }

    /// Load audio data for a pending transcription.
    func loadAudioData(for entry: PendingTranscription) -> Data? {
        let url = audioDir.appendingPathComponent(entry.audioFileName)
        return try? Data(contentsOf: url)
    }

    /// Mark an entry as completed with transcribed text.
    func markCompleted(id: String, text: String) {
        if let index = entries.firstIndex(where: { $0.id == id }) {
            entries[index].status = .completed
            entries[index].transcribedText = text
            saveEntries()
            print("[PendingTxn] Completed: \(id)")
        }
    }

    /// Delete an entry and its audio file.
    func delete(id: String) {
        if let index = entries.firstIndex(where: { $0.id == id }) {
            let entry = entries[index]
            let audioURL = audioDir.appendingPathComponent(entry.audioFileName)
            try? fileManager.removeItem(at: audioURL)
            entries.remove(at: index)
            saveEntries()
            print("[PendingTxn] Deleted: \(id)")
        }
    }

    /// Number of pending (not yet completed) entries.
    var pendingCount: Int {
        entries.filter { $0.status == .pending }.count
    }

    // MARK: - Retry

    /// Retry a pending transcription with optional model override.
    func retry(entry: PendingTranscription, withModel model: TranscriptionModel? = nil) async {
        guard let audioData = loadAudioData(for: entry) else {
            print("[PendingTxn] Audio file not found for: \(entry.id)")
            return
        }

        let targetModel = model ?? TranscriptionModel.current

        do {
            let result = try await VoxTypeAPIClient.shared.transcribe(
                audioData: audioData,
                format: entry.audioFormat,
                model: targetModel,
                audioDurationMs: entry.durationMs
            )

            // Update usage from retry result
            UsageManager.shared.updateFromTranscriptionResult(result)

            let text = result.text.trimmingCharacters(in: .whitespacesAndNewlines)
            if !text.isEmpty {
                markCompleted(id: entry.id, text: text)
            }
        } catch {
            print("[PendingTxn] Retry failed for \(entry.id): \(error)")
        }
    }

    /// Retry all pending entries.
    func retryAll() async {
        let pendingEntries = entries.filter { $0.status == .pending }
        for entry in pendingEntries {
            await retry(entry: entry)
        }
    }

    // MARK: - Persistence

    private func loadEntries() -> [PendingTranscription] {
        guard let data = try? Data(contentsOf: metadataURL) else { return [] }
        return (try? JSONDecoder().decode([PendingTranscription].self, from: data)) ?? []
    }

    private func saveEntries() {
        let data = try? JSONEncoder().encode(entries)
        try? data?.write(to: metadataURL)
    }
}
