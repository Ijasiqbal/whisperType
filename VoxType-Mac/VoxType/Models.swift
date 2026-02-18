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
    case groqTurbo = "whisper-large-v3-turbo"
    case groqStandard = "whisper-large-v3"
    case openAIMini = "gpt-4o-mini-transcribe"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .groqTurbo: return "Groq Turbo (Free)"
        case .groqStandard: return "Groq Standard (1x credits)"
        case .openAIMini: return "OpenAI Mini (2x credits)"
        }
    }

    var endpoint: String {
        switch self {
        case .groqTurbo, .groqStandard:
            return Constants.transcribeGroqPath
        case .openAIMini:
            return Constants.transcribeOpenAIPath
        }
    }

    var isFree: Bool {
        self == .groqTurbo
    }

    var shortName: String {
        switch self {
        case .groqTurbo: return "Auto"
        case .groqStandard: return "Standard"
        case .openAIMini: return "Premium"
        }
    }

    var creditLabel: String {
        switch self {
        case .groqTurbo: return "Free"
        case .groqStandard: return "1x"
        case .openAIMini: return "2x"
        }
    }

    var color: Color {
        switch self {
        case .groqTurbo: return .green
        case .groqStandard: return .blue
        case .openAIMini: return .orange
        }
    }

    /// Get the current model from UserDefaults
    static var current: TranscriptionModel {
        let raw = UserDefaults.standard.string(forKey: Constants.selectedModelKey) ?? ""
        return TranscriptionModel(rawValue: raw) ?? .groqTurbo
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
    case cmdShift = "cmd_shift"
    case rightOption = "right_option"
    case fn = "fn"
    case f5 = "f5"
    case ctrlShift = "ctrl_shift"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .ctrlOption: return "Ctrl + Option"
        case .cmdShift: return "Cmd + Shift"
        case .rightOption: return "Right Option"
        case .fn: return "Fn (Globe)"
        case .f5: return "F5"
        case .ctrlShift: return "Ctrl + Shift"
        }
    }

    var shortLabel: String {
        switch self {
        case .ctrlOption: return "⌃⌥"
        case .cmdShift: return "⌘⇧"
        case .rightOption: return "Right ⌥"
        case .fn: return "🌐"
        case .f5: return "F5"
        case .ctrlShift: return "⌃⇧"
        }
    }

    /// The modifier flags that constitute this hotkey (for modifier-only hotkeys)
    var requiredFlags: CGEventFlags? {
        switch self {
        case .ctrlOption: return [.maskControl, .maskAlternate]
        case .cmdShift: return [.maskCommand, .maskShift]
        case .ctrlShift: return [.maskControl, .maskShift]
        case .rightOption, .fn, .f5: return nil  // Handled via keyCode, not flags
        }
    }

    /// The key code for key-based hotkeys (non-modifier-only)
    var keyCode: UInt16? {
        switch self {
        case .fn: return 0x3F         // Fn/Globe key
        case .f5: return 0x60         // F5
        case .rightOption: return 0x3D // Right Option
        default: return nil
        }
    }

    /// Whether this hotkey is modifier-only (detected via flagsChanged)
    var isModifierOnly: Bool {
        requiredFlags != nil && keyCode == nil
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
