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
        model: TranscriptionModel = .auto,
        audioDurationMs: Int
    ) async throws -> TranscriptionResult {
        let region = RegionSelector.bestRegion()
        let urlString = Constants.baseURL(for: region) + model.endpoint
        guard let url = URL(string: urlString) else {
            throw VoxTypeError.serverError(0, "Invalid URL")
        }

        let token = try await AuthManager.shared.getIDToken()

        let bodyData: Data
        if model.usesEnhancedPipeline {
            let body = EnhancedTranscriptionRequest(
                audioBase64: audioData.base64EncodedString(),
                audioFormat: format,
                audioDurationMs: audioDurationMs,
                processingVariant: model.processingVariant,
                tier: model.tier
            )
            bodyData = try JSONEncoder().encode(body)
        } else {
            let body = TranscriptionRequest(
                audioBase64: audioData.base64EncodedString(),
                audioFormat: format,
                model: model.rawValue,
                audioDurationMs: audioDurationMs
            )
            bodyData = try JSONEncoder().encode(body)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = Constants.apiReadTimeout
        request.httpBody = bodyData

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
            let message = Self.extractMessage(from: data) ?? "Unknown error"
            throw VoxTypeError.serverError(httpResponse.statusCode, message)
        }

        return try JSONDecoder().decode(TrialStatus.self, from: data)
    }

    // MARK: - Version Check

    struct VersionCheckResult {
        enum Status { case ok, updateAvailable, blocked }
        let status: Status
        let latestVersion: String?
        let downloadUrl: String?
        let message: String?
    }

    func checkVersion() async -> VersionCheckResult {
        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0"
        let region = RegionSelector.bestRegion()
        let urlString = Constants.baseURL(for: region) + Constants.versionCheckPath + "?version=\(appVersion)"
        guard let url = URL(string: urlString) else {
            return VersionCheckResult(status: .ok, latestVersion: nil, downloadUrl: nil, message: nil)
        }

        do {
            var request = URLRequest(url: url)
            request.httpMethod = "GET"
            request.timeoutInterval = 8

            let (data, _) = try await session.data(for: request)
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let statusStr = json["status"] as? String else {
                return VersionCheckResult(status: .ok, latestVersion: nil, downloadUrl: nil, message: nil)
            }

            let latestVersion = json["latestVersion"] as? String
            let downloadUrl = json["downloadUrl"] as? String
            let message = json["message"] as? String

            switch statusStr {
            case "update_available":
                return VersionCheckResult(status: .updateAvailable, latestVersion: latestVersion, downloadUrl: downloadUrl, message: message)
            case "blocked":
                return VersionCheckResult(status: .blocked, latestVersion: nil, downloadUrl: downloadUrl, message: message)
            default:
                return VersionCheckResult(status: .ok, latestVersion: nil, downloadUrl: nil, message: nil)
            }
        } catch {
            return VersionCheckResult(status: .ok, latestVersion: nil, downloadUrl: nil, message: nil)
        }
    }

    // MARK: - Issue Reporting

    func submitIssue(
        userId: String,
        userEmail: String?,
        category: String,
        description: String
    ) async throws {
        let urlString = "https://firestore.googleapis.com/v1/projects/\(Constants.firebaseProjectID)/databases/(default)/documents/issues"
        guard let url = URL(string: urlString) else {
            throw VoxTypeError.serverError(0, "Invalid URL")
        }

        let token = try await AuthManager.shared.getIDToken()
        let osVersion = ProcessInfo.processInfo.operatingSystemVersionString
        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
        let now = ISO8601DateFormatter().string(from: Date())

        func str(_ v: String) -> [String: Any] { ["stringValue": v] }

        let body: [String: Any] = [
            "fields": [
                "userId":      str(userId),
                "userEmail":   str(userEmail ?? "unknown"),
                "category":    str(category),
                "description": str(description),
                "osVersion":   str(osVersion),
                "appVersion":  str(appVersion),
                "platform":    str("mac"),
                "createdAt":   ["timestampValue": now],
                "status":      str("open")
            ]
        ]

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        request.timeoutInterval = 30

        let (_, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            throw VoxTypeError.serverError(code, "Failed to submit issue")
        }
    }

    // MARK: - Warmup

    /// Warm up ALL transcription endpoints simultaneously.
    /// Each Firebase Function (Gen 2) runs in a separate Cloud Run instance,
    /// so warming the health endpoint does NOT warm transcription functions.
    /// Call this when recording starts so the function is warm when recording stops.
    func warmAllEndpoints() async {
        let region = RegionSelector.bestRegion()
        async let autoTier: Void = warmEndpoint(Constants.baseURL(for: region) + Constants.transcribeAutoPath)
        async let premiumTier: Void = warmEndpoint(Constants.baseURL(for: region) + Constants.transcribePremiumPath)
        async let standardTier: Void = warmEndpoint(Constants.baseURL(for: region) + Constants.transcribeStandardPath)
        _ = await (autoTier, premiumTier, standardTier)
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
                    let message = Self.extractMessage(from: data) ?? "Quota exceeded"
                    throw VoxTypeError.quotaExceeded(message)
                }

                guard httpResponse.statusCode == 200 else {
                    let message = Self.extractMessage(from: data) ?? "Unknown error"
                    throw VoxTypeError.serverError(httpResponse.statusCode, message)
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

    /// Extract "message" field from a JSON error response body.
    private static func extractMessage(from data: Data) -> String? {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let msg = json["message"] as? String else {
            return nil
        }
        return msg
    }
}
