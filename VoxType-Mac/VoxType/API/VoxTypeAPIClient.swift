import Foundation

final class VoxTypeAPIClient {

    static let shared = VoxTypeAPIClient()

    private let session: URLSession

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = Constants.apiReadTimeout
        config.timeoutIntervalForResource = Constants.apiReadTimeout
        session = URLSession(configuration: config)
    }

    // MARK: - Transcription

    func transcribe(
        audioData: Data,
        format: String = "wav",
        model: TranscriptionModel = .groqTurbo,
        audioDurationMs: Int
    ) async throws -> TranscriptionResult {
        let region = RegionSelector.bestRegion()
        let urlString = Constants.baseURL(for: region) + model.endpoint
        guard let url = URL(string: urlString) else {
            throw VoxTypeError.serverError(0, "Invalid URL")
        }

        let token = try await AuthManager.shared.getIDToken()

        let body = TranscriptionRequest(
            audioBase64: audioData.base64EncodedString(),
            audioFormat: format,
            model: model.rawValue,
            audioDurationMs: audioDurationMs
        )

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = Constants.apiReadTimeout
        request.httpBody = try JSONEncoder().encode(body)

        return try await performWithRetry(request: request)
    }

    // MARK: - Trial Status

    func getTrialStatus() async throws -> TrialStatus {
        let region = RegionSelector.bestRegion()
        let urlString = Constants.baseURL(for: region) + Constants.trialStatusPath
        guard let url = URL(string: urlString) else {
            throw VoxTypeError.serverError(0, "Invalid URL")
        }

        let token = try await AuthManager.shared.getIDToken()

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.timeoutInterval = Constants.apiReadTimeout

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw VoxTypeError.serverError(0, "Invalid response")
        }

        guard httpResponse.statusCode == 200 else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw VoxTypeError.serverError(httpResponse.statusCode, body)
        }

        return try JSONDecoder().decode(TrialStatus.self, from: data)
    }

    // MARK: - Warmup

    /// Warm up ALL transcription endpoints simultaneously.
    /// Each Firebase Function (Gen 2) runs in a separate Cloud Run instance,
    /// so warming the health endpoint does NOT warm transcription functions.
    /// Call this when recording starts so the function is warm when recording stops.
    func warmAllEndpoints() async {
        let region = RegionSelector.bestRegion()
        async let groq: Void = warmEndpoint(Constants.baseURL(for: region) + Constants.transcribeGroqPath)
        async let openAI: Void = warmEndpoint(Constants.baseURL(for: region) + Constants.transcribeOpenAIPath)
        _ = await (groq, openAI)
    }

    private func warmEndpoint(_ urlString: String) async {
        guard let url = URL(string: urlString) else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 10
        _ = try? await session.data(for: request)
    }

    // MARK: - Retry Logic

    private func performWithRetry(request: URLRequest) async throws -> TranscriptionResult {
        var lastError: Error?
        var backoffMs = Constants.initialBackoffMs

        for attempt in 0..<Constants.maxRetries {
            do {
                let (data, response) = try await session.data(for: request)

                guard let httpResponse = response as? HTTPURLResponse else {
                    throw VoxTypeError.serverError(0, "Invalid response")
                }

                // Check for retryable status codes
                if Constants.retryableStatusCodes.contains(httpResponse.statusCode) {
                    lastError = VoxTypeError.serverError(httpResponse.statusCode, "Retryable error")
                    if attempt < Constants.maxRetries - 1 {
                        try await Task.sleep(nanoseconds: UInt64(backoffMs) * 1_000_000)
                        backoffMs = min(backoffMs * Constants.backoffMultiplier, Constants.maxBackoffMs)
                        continue
                    }
                }

                // Handle error responses
                if httpResponse.statusCode == 401 {
                    throw VoxTypeError.notAuthenticated
                }

                if httpResponse.statusCode == 403 {
                    let body = String(data: data, encoding: .utf8) ?? "Quota exceeded"
                    throw VoxTypeError.quotaExceeded(body)
                }

                guard httpResponse.statusCode == 200 else {
                    let body = String(data: data, encoding: .utf8) ?? "Unknown error"
                    throw VoxTypeError.serverError(httpResponse.statusCode, body)
                }

                return try JSONDecoder().decode(TranscriptionResult.self, from: data)

            } catch let error as VoxTypeError {
                throw error
            } catch {
                lastError = error
                if attempt < Constants.maxRetries - 1 {
                    try await Task.sleep(nanoseconds: UInt64(backoffMs) * 1_000_000)
                    backoffMs = min(backoffMs * Constants.backoffMultiplier, Constants.maxBackoffMs)
                } else {
                    throw VoxTypeError.networkError(error)
                }
            }
        }

        throw lastError.map { VoxTypeError.networkError($0) } ?? VoxTypeError.serverError(0, "Max retries exceeded")
    }
}
