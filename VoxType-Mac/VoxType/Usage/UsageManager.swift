import Combine
import Foundation

final class UsageManager: ObservableObject {

    static let shared = UsageManager()

    @Published var plan: UserPlan = .free
    @Published var creditsUsed: Int = 0
    @Published var creditsRemaining: Int = 0
    @Published var creditsLimit: Int = 500
    @Published var warningLevel: String = "none"
    @Published var isLoading = false

    private init() {}

    // MARK: - Update from API Response

    func updateFromTranscriptionResult(_ result: TranscriptionResult) {
        DispatchQueue.main.async { [self] in
            if let p = result.plan {
                plan = UserPlan(rawValue: p) ?? .free
            }

            if plan == .pro, let pro = result.proStatus {
                creditsUsed = pro.proCreditsUsed ?? 0
                creditsRemaining = pro.proCreditsRemaining ?? 0
                creditsLimit = pro.proCreditsLimit ?? Constants.creditsPro
            } else if let trial = result.trialStatus {
                creditsUsed = trial.freeCreditsUsed ?? 0
                creditsRemaining = trial.freeCreditsRemaining ?? 0
                creditsLimit = trial.freeTierCredits ?? 500
                warningLevel = trial.warningLevel ?? "none"
            }
        }
    }

    func updateFromTrialStatus(_ status: TrialStatus) {
        DispatchQueue.main.async { [self] in
            creditsUsed = status.freeCreditsUsed ?? 0
            creditsRemaining = status.freeCreditsRemaining ?? 0
            creditsLimit = status.freeTierCredits ?? 500
            warningLevel = status.warningLevel ?? "none"

            if status.status == "active" {
                // Determine plan from context
            }
        }
    }

    // MARK: - Refresh

    func refreshStatus() async {
        DispatchQueue.main.async { self.isLoading = true }
        defer { DispatchQueue.main.async { self.isLoading = false } }

        do {
            let status = try await VoxTypeAPIClient.shared.getTrialStatus()
            updateFromTrialStatus(status)
        } catch {
            print("[UsageManager] Failed to refresh: \(error.localizedDescription)")
        }
    }

    // MARK: - Display

    var creditsDisplayText: String {
        "\(creditsRemaining) / \(creditsLimit)"
    }

    var planDisplayText: String {
        switch plan {
        case .free: return "Free Trial"
        case .pro: return "Pro"
        }
    }

    var isLow: Bool {
        warningLevel != "none"
    }
}
