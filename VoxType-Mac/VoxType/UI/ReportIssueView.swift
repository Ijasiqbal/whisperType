import SwiftUI

private enum IssueCategory: String, CaseIterable, Identifiable {
    case bug = "bug"
    case featureRequest = "feature_request"
    case transcriptionIssue = "transcription_issue"
    case other = "other"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .bug: return "Bug"
        case .featureRequest: return "Feature Request"
        case .transcriptionIssue: return "Transcription Issue"
        case .other: return "Other"
        }
    }
}

struct ReportIssueView: View {
    @EnvironmentObject var auth: AuthManager
    @Environment(\.dismiss) private var dismiss

    @State private var selectedCategory: IssueCategory = .bug
    @State private var description = ""
    @State private var isSubmitting = false
    @State private var errorMessage: String?
    @State private var submitted = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header
            HStack {
                Text("Report an Issue")
                    .font(.headline)
                Spacer()
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                        .font(.system(size: 16))
                }
                .buttonStyle(.plain)
            }
            .padding()

            Divider()

            if submitted {
                VStack(spacing: 12) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 40))
                        .foregroundColor(.green)
                    Text("Issue reported. Thank you!")
                        .font(.headline)
                    Text("We'll look into it.")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    Button("Done") { dismiss() }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.regular)
                }
                .frame(maxWidth: .infinity)
                .padding(32)
            } else {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Describe the problem and we'll look into it")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)

                    // Category
                    VStack(alignment: .leading, spacing: 6) {
                        Text("CATEGORY")
                            .font(.system(size: 10, weight: .medium, design: .monospaced))
                            .foregroundColor(.secondary)

                        Picker("Category", selection: $selectedCategory) {
                            ForEach(IssueCategory.allCases) { cat in
                                Text(cat.label).tag(cat)
                            }
                        }
                        .pickerStyle(.menu)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    // Description
                    VStack(alignment: .leading, spacing: 6) {
                        Text("DESCRIPTION")
                            .font(.system(size: 10, weight: .medium, design: .monospaced))
                            .foregroundColor(.secondary)

                        ZStack(alignment: .topLeading) {
                            TextEditor(text: $description)
                                .font(.system(size: 12))
                                .frame(height: 100)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 6)
                                        .stroke(Color.secondary.opacity(0.3), lineWidth: 1)
                                )

                            if description.isEmpty {
                                Text("Describe the issue you're experiencing...")
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary.opacity(0.5))
                                    .padding(.horizontal, 5)
                                    .padding(.vertical, 8)
                                    .allowsHitTesting(false)
                            }
                        }
                    }

                    Text("Device info will be attached automatically")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)

                    if let error = errorMessage {
                        Text(error)
                            .font(.system(size: 12))
                            .foregroundColor(.red)
                    }

                    // Buttons
                    HStack {
                        Spacer()
                        Button("Cancel") { dismiss() }
                            .buttonStyle(.plain)
                            .foregroundColor(.secondary)
                            .padding(.trailing, 8)

                        Button {
                            submitIssue()
                        } label: {
                            if isSubmitting {
                                HStack(spacing: 6) {
                                    ProgressView().controlSize(.mini)
                                    Text("Submitting...")
                                }
                            } else {
                                Text("Submit")
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.regular)
                        .disabled(isSubmitting || description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
                .padding()
            }
        }
        .frame(width: 340)
    }

    private func submitIssue() {
        let trimmed = description.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        isSubmitting = true
        errorMessage = nil

        Task {
            do {
                let uid = auth.userUID ?? "unknown"
                try await VoxTypeAPIClient.shared.submitIssue(
                    userId: uid,
                    userEmail: auth.userEmail,
                    category: selectedCategory.rawValue,
                    description: trimmed
                )
                await MainActor.run {
                    submitted = true
                    isSubmitting = false
                }
            } catch {
                await MainActor.run {
                    errorMessage = "Failed to submit. Please try again."
                    isSubmitting = false
                }
            }
        }
    }
}
