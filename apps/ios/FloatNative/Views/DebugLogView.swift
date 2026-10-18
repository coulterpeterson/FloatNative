//
//  DebugLogView.swift
//  FloatNative
//
//  Surfaces the in-memory ring-buffer from `DebugLogManager`. Use this when a
//  user reports a bug — they can paste the contents of this screen straight
//  into a GitHub issue.
//

import SwiftUI

struct DebugLogView: View {
    @ObservedObject private var log = DebugLogManager.shared
    @State private var copyConfirmation = false

    var body: some View {
        ZStack {
            Color.adaptiveBackground.ignoresSafeArea()

            if log.entries.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "checkmark.seal")
                        .font(.system(size: 48))
                        .foregroundColor(Color.adaptiveSecondaryText)
                    Text("No diagnostic events yet.")
                        .foregroundColor(Color.adaptiveSecondaryText)
                }
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        ForEach(log.entries) { entry in
                            EntryCard(entry: entry)
                        }
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 12)
                }
            }
        }
        .navigationTitle("Debug Log")
        #if !os(tvOS)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button {
                    UIPasteboard.general.string = log.exportText()
                    copyConfirmation = true
                } label: {
                    Label("Copy", systemImage: "doc.on.doc")
                }
                .disabled(log.entries.isEmpty)

                Button(role: .destructive) {
                    log.clear()
                } label: {
                    Label("Clear", systemImage: "trash")
                }
                .disabled(log.entries.isEmpty)
            }
        }
        .alert("Copied", isPresented: $copyConfirmation) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Debug log copied to clipboard.")
        }
        #endif
    }
}

private struct EntryCard: View {
    let entry: DebugLogManager.Entry
    @State private var expanded = false

    private static let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f
    }()

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Text(entry.category.rawValue.uppercased())
                    .font(.caption2.weight(.semibold))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(badgeColor.opacity(0.2))
                    .foregroundColor(badgeColor)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
                Text(Self.timeFormatter.string(from: entry.timestamp))
                    .font(.caption2)
                    .foregroundColor(Color.adaptiveSecondaryText)
                Spacer()
                if entry.detail != nil {
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.caption)
                        .foregroundColor(Color.adaptiveSecondaryText)
                }
            }
            Text(entry.message)
                .font(.callout)
                .foregroundColor(Color.adaptiveText)
                .selectableText()
            if expanded, let detail = entry.detail {
                Text(detail)
                    .font(.system(.caption, design: .monospaced))
                    .foregroundColor(Color.adaptiveSecondaryText)
                    .selectableText()
                    .padding(.top, 4)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.adaptiveSecondaryBackground)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .contentShape(Rectangle())
        .onTapGesture {
            if entry.detail != nil {
                withAnimation(.easeInOut(duration: 0.15)) { expanded.toggle() }
            }
        }
    }

    private var badgeColor: Color {
        switch entry.category {
        case .api: return .orange
        case .decode: return .red
        case .auth: return .blue
        case .player: return .green
        case .other: return .gray
        }
    }
}

private extension View {
    /// `.textSelection(.enabled)` on iOS/iPadOS, no-op on tvOS where the
    /// modifier doesn't exist (no on-screen text-selection UI on Apple TV).
    @ViewBuilder
    func selectableText() -> some View {
        #if os(tvOS)
        self
        #else
        self.textSelection(.enabled)
        #endif
    }
}

#Preview {
    NavigationStack {
        DebugLogView()
    }
}
