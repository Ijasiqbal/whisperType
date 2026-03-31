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
    case ctrlSpace = "ctrl_space"
    case ctrlD = "ctrl_d"
    case ctrlOption = "ctrl_option"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .ctrlSpace: return "Ctrl + Space"
        case .ctrlD: return "Ctrl + D"
        case .ctrlOption: return "Ctrl + Option"
        }
    }

    var shortLabel: String {
        switch self {
        case .ctrlSpace: return "⌃Space"
        case .ctrlD: return "⌃D"
        case .ctrlOption: return "⌃⌥"
        }
    }

    var subtitle: String {
        switch self {
        case .ctrlSpace: return "Recommended — natural and quick"
        case .ctrlD: return "D for Dictate"
        case .ctrlOption: return "Two keys side by side — requires extra permission"
        }
    }

    /// Whether this hotkey requires Accessibility permission
    var requiresAccessibility: Bool {
        switch self {
        case .ctrlSpace, .ctrlD: return false
        case .ctrlOption: return true
        }
    }

    /// Whether this hotkey uses NSEvent monitor (no Accessibility) or CGEvent tap (Accessibility)
    var usesNSEventMonitor: Bool {
        !requiresAccessibility
    }

    // -- NSEvent detection properties --

    /// The key code for NSEvent-based hotkeys
    var nsEventKeyCode: UInt16? {
        switch self {
        case .ctrlSpace: return 0x31  // Space bar
        case .ctrlD: return 0x02     // D key
        case .ctrlOption: return nil  // Uses CGEvent, not NSEvent
        }
    }

    /// The modifier flags for NSEvent-based hotkeys
    var nsEventModifierFlags: NSEvent.ModifierFlags? {
        switch self {
        case .ctrlSpace, .ctrlD: return .control
        case .ctrlOption: return nil
        }
    }

    // -- CGEvent detection properties --

    /// The modifier flags for CGEvent-based hotkeys (modifier-only combos)
    var cgEventRequiredFlags: CGEventFlags? {
        switch self {
        case .ctrlOption: return [.maskControl, .maskAlternate]
        case .ctrlSpace, .ctrlD: return nil
        }
    }

    /// Whether this hotkey is modifier-only (detected via flagsChanged in CGEvent)
    var isModifierOnly: Bool {
        cgEventRequiredFlags != nil
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
