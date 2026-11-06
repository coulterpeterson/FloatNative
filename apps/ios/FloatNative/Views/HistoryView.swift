//
//  HistoryView.swift
//  FloatNative
//
//  Watch history grouped by date
//

import SwiftUI

struct HistoryView: View {
    @StateObject private var viewModel = HistoryViewModel()
    @State private var playerPost: BlogPost?

    var body: some View {
        ZStack {
            // Background
            Color.adaptiveBackground
                .ignoresSafeArea()

            if viewModel.loadFailed {
                loadFailedState
            } else if viewModel.groupedHistory.isEmpty && !viewModel.isLoading {
                emptyState
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(viewModel.groupedHistory.keys.sorted(by: >), id: \.self) { date in
                            Section {
                                LazyVGrid(columns: AdaptiveGrid.rowColumns, spacing: 8) {
                                    ForEach(viewModel.groupedHistory[date] ?? []) { item in
                                        PostPlayerLink(post: item.post, presented: $playerPost) {
                                            CompactHistoryCard(
                                                post: item.post,
                                                progress: item.progress,
                                                watchedAt: item.watchedAt
                                            )
                                            .padding(.horizontal)
                                        }
                                        .buttonStyle(PlainButtonStyle())
                                    }
                                }
                                .padding(.top, 8)
                                .padding(.bottom, 20)
                            } header: {
                                HStack {
                                    Text(formatSectionDate(date))
                                        .font(.headline)
                                        .foregroundColor(Color.adaptiveText)
                                        .padding(.horizontal)
                                        .padding(.vertical, 8)

                                    Spacer()
                                }
                                .background(Color.adaptiveBackground)
                            }
                        }
                    }
                }
                .refreshable {
                    await viewModel.loadHistory()
                }
                .columnsAvoidingFold()
            }

            // Loading state
            if viewModel.isLoading {
                ProgressView()
                    .tint(.floatplaneBlue)
                    .scaleEffect(1.5)
            }
        }
        #if os(tvOS)
        .navigationTitle("")
        #else
        .navigationTitle("Watch History")
        #endif
        .toolbarBackground(.visible, for: .navigationBar)
        .offlineFallback(hasContent: !viewModel.groupedHistory.isEmpty) {
            await viewModel.loadHistory()
        }
        .globalMenu()
        .task {
            await viewModel.loadHistory()
        }
        #if os(tvOS)
        .tvOSPlayerCover($playerPost)
        #else
        .navigationDestination(for: BlogPost.self) { post in
            VideoPlayerView(post: post)
        }
        #endif
    }

    private var loadFailedState: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 50))
                .foregroundStyle(Color.floatplaneGray)

            Text("Couldn't load your watch history")
                .font(.title3)
                .fontWeight(.medium)
                .foregroundStyle(Color.adaptiveText)
                .multilineTextAlignment(.center)

            Button("Retry") {
                Task {
                    await viewModel.loadHistory()
                }
            }
            .buttonStyle(LiquidGlassButtonStyle(tint: .floatplaneBlue))
        }
        .padding()
        .messageAvoidingFold()
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "clock.fill")
                .font(.system(size: 60))
                .foregroundStyle(Color.floatplaneGray)

            Text("No watch history")
                .font(.title3)
                .fontWeight(.medium)
                .foregroundStyle(Color.adaptiveText)

            Text("Videos you watch will appear here")
                .font(.subheadline)
                .foregroundStyle(Color.adaptiveSecondaryText)
                .multilineTextAlignment(.center)
        }
        .padding()
        .messageAvoidingFold()
    }

    private func formatSectionDate(_ date: Date) -> String {
        let calendar = Calendar.current

        if calendar.isDateInToday(date) {
            return "Today"
        } else if calendar.isDateInYesterday(date) {
            return "Yesterday"
        } else if calendar.isDate(date, equalTo: Date(), toGranularity: .weekOfYear) {
            return date.formatted(.dateTime.weekday(.wide))
        } else {
            return date.formatted(.dateTime.month(.wide).day())
        }
    }
}

// MARK: - History Item Model

struct HistoryItem: Identifiable {
    let id: String // Use postId as id
    let post: BlogPost
    let watchedAt: Date
    let progress: Double // 0.0 to 1.0
}

// MARK: - History View Model

@MainActor
class HistoryViewModel: ObservableObject {
    @Published var historyItems: [HistoryItem] = []
    @Published var groupedHistory: [Date: [HistoryItem]] = [:]
    @Published var isLoading = false
    /// So a failed load doesn't look like having watched nothing.
    @Published var loadFailed = false

    private let api = FloatplaneAPI.shared

    func loadHistory() async {
        isLoading = true
        loadFailed = false

        do {
            let watchHistory = try await api.getWatchHistory(offset: 0)

            historyItems = watchHistory.map { entry in
                HistoryItem(
                    id: entry.contentId,
                    post: entry.blogPost,
                    watchedAt: entry.updatedAt,
                    progress: Double(entry.progress) / 100.0 // Convert 0-100 to 0.0-1.0
                )
            }
            groupHistory()

        } catch {
            historyItems = []
            groupedHistory = [:]
            loadFailed = true
        }

        isLoading = false
    }

    private func groupHistory() {
        let calendar = Calendar.current

        let grouped = Dictionary(grouping: historyItems) { item in
            calendar.startOfDay(for: item.watchedAt)
        }

        groupedHistory = grouped
    }
}

#Preview {
    NavigationStack {
        HistoryView()
    }
}
