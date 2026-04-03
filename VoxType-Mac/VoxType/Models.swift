import CoreGraphics
import Foundation
import SwiftUI

// MARK: - Transcription

struct TranscriptionRequest: Encodable {
    let audioBase64: String
    let audioFormat: String
    let model: String
    let audioDurationMs: Int
}

struct TwoStageRequest: Encodable {
    let audioBase64: String
    let audioFormat: String
    let audioDurationMs: Int
    let llmModel: String?
    let tier: String?
}

struct TranscriptionResult: Decodable {
    let text: String
    let creditsUsed: Int?
    let totalCreditsThisMonth: Int?
    let plan: String?
    let trialStatus: TrialStatus?
    let proStatus: ProStatus?
    let subscriptionStatus: SubscriptionStatus?
}

// MARK: - Trial Status

struct TrialStatus: Decodable {
    let status: String
    let freeCreditsUsed: Int?
    let freeCreditsRemaining: Int?
    let freeTierCredits: Int?
    let trialExpiryDateMs: Int64?
    let warningLevel: String?
}

// MARK: - Pro Status

struct ProStatus: Decodable {
    let isActive: Bool?
    let proCreditsUsed: Int?
    let proCreditsRemaining: Int?
    let proCreditsLimit: Int?
    let currentPeriodEndMs: Int64?
}

// MARK: - Subscription Status

struct SubscriptionStatus: Decodable {
    let status: String?
    let creditsRemaining: Int?
    let creditsLimit: Int?
    let resetDateMs: Int64?
    let warningLevel: String?
}

// MARK: - User Plan

enum UserPlan: String, Codable {
    case free
    case pro
}

// MARK: - Transcription Model

enum TranscriptionModel: String, CaseIterable, Identifiable {
    case auto = "auto"
    case standard = "standard_v2"
    case premium = "premium"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .auto: return "Auto (Free)"
        case .standard: return "Standard (1x credits)"
        case .premium: return "Premium (2x credits)"
        }
    }

    var endpoint: String {
        switch self {
        case .auto:
            return Constants.transcribeAutoPath
        case .standard:
            return Constants.transcribeStandardPath
        case .premium:
            return Constants.transcribePremiumPath
        }
    }

    /// Whether this model uses the standard pipeline
    var isTwoStage: Bool {
        self == .standard
    }

    /// LLM tier for standard pipeline cleanup
    var llmModel: String? {
        switch self {
        case .standard: return "standard_v2"
        default: return nil
        }
    }

    /// Billing tier sent to the backend
    var tier: String {
        switch self {
        case .auto: return "AUTO"
        case .standard: return "STANDARD"
        case .premium: return "PREMIUM"
        }
    }

    var isFree: Bool {
        self == .auto
    }

    var shortName: String {
        switch self {
        case .auto: return "Auto"
        case .standard: return "Standard"
        case .premium: return "Premium"
        }
    }

    var creditLabel: String {
        switch self {
        case .auto: return "Free"
        case .standard: return "1x"
        case .premium: return "2x"
        }
    }

    var color: Color {
        switch self {
        case .auto: return .green
        case .standard: return .blue
        case .premium: return .orange
        }
    }

    /// Get the current model from UserDefaults
    static var current: TranscriptionModel {
        let raw = UserDefaults.standard.string(forKey: Constants.selectedModelKey) ?? ""
        return TranscriptionModel(rawValue: raw) ?? .auto
    }

    /// Save this model as the current selection
    func saveAsSelected() {
        UserDefaults.standard.set(self.rawValue, forKey: Constants.selectedModelKey)
    }

    /// Cycle to the next model in the list
    func next() -> TranscriptionModel {
        let all = TranscriptionModel.allCases
        guard let idx = all.firstIndex(of: self) else { return self }
        let nextIdx = all.index(after: idx)
        return nextIdx < all.endIndex ? all[nextIdx] : all[all.startIndex]
    }

    /// Cycle to the previous model in the list
    func previous() -> TranscriptionModel {
        let all = TranscriptionModel.allCases
        guard let idx = all.firstIndex(of: self) else { return self }
        if idx == all.startIndex {
            return all[all.index(before: all.endIndex)]
        }
        return all[all.index(before: idx)]
    }
}

// MARK: - Hotkey Options

enum HotkeyOption: String, CaseIterable, Identifiable {
    case ctrlOption = "ctrl_option"
    case rightOption = "right_option"
    case ctrlShift = "ctrl_shift"
    case doubleTapFn = "double_tap_fn"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .rightOption: return "Right Option"
        case .ctrlOption: return "Ctrl + Option"
        case .ctrlShift: return "Ctrl + Shift"
        case .doubleTapFn: return "Double-tap Fn"
        }
    }

    var shortLabel: String {
        switch self {
        case .rightOption: return "Right ⌥"
        case .ctrlOption: return "⌃⌥"
        case .ctrlShift: return "⌃⇧"
        case .doubleTapFn: return "Fn Fn"
        }
    }

    /// The modifier flags that constitute this hotkey (for modifier-only hotkeys)
    var requiredFlags: CGEventFlags? {
        switch self {
        case .ctrlOption: return [.maskControl, .maskAlternate]
        case .ctrlShift: return [.maskControl, .maskShift]
        case .rightOption, .doubleTapFn: return nil  // Handled via keyCode, not flags
        }
    }

    /// The key code for key-based hotkeys (non-modifier-only)
    var keyCode: UInt16? {
        switch self {
        case .rightOption: return 0x3D // Right Option
        case .doubleTapFn: return 0x3F // Fn key
        default: return nil
        }
    }

    /// Whether this hotkey is modifier-only (detected via flagsChanged)
    var isModifierOnly: Bool {
        requiredFlags != nil && keyCode == nil
    }

    /// Whether this hotkey requires double-tap detection
    var isDoubleTap: Bool {
        self == .doubleTapFn
    }
}

// MARK: - App State

enum RecordingState: Equatable {
    case idle
    case recording
    case processing
    case inserted(String) // Text was inserted into a focused text field
    case success(String)  // No text field detected, showing copy button
    case error(String)
}

// MARK: - API Error

enum VoxTypeError: LocalizedError {
    case notAuthenticated
    case noAudioData
    case audioTooShort
    case transcriptionEmpty
    case quotaExceeded(String)
    case serverError(Int, String)
    case networkError(Error)

    var errorDescription: String? {
        switch self {
        case .notAuthenticated:
            return "Not signed in. Please sign in to continue."
        case .noAudioData:
            return "No audio recorded."
        case .audioTooShort:
            return "Recording too short. Try recording a bit longer."
        case .transcriptionEmpty:
            return "No speech detected."
        case .quotaExceeded(let msg):
            return "Credits exhausted: \(msg)"
        case .serverError(let code, let msg):
            return "Server error (\(code)): \(msg)"
        case .networkError(let error):
            return "Network error: \(error.localizedDescription)"
        }
    }
}
