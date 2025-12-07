//
//  HistoryView.swift
//  FloatNative
//
//  Watch history grouped by date
//

import SwiftUI

struct HistoryView: View {
    @StateObject private var viewModel = HistoryViewModel()

    var body: some View {
        ZStack {
            // Background
            Color.adaptiveBackground
                .ignoresSafeArea()

            if viewModel.groupedHistory.isEmpty && !viewModel.isLoading {
                emptyState
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(viewModel.groupedHistory.keys.sorted(by: >), id: \.self) { date in
                            Section {
                                LazyVStack(spacing: 8) {
                                    ForEach(viewModel.groupedHistory[date] ?? []) { item in
                                        NavigationLink(value: item.post) {
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
        .globalMenu()
        .task {
            await viewModel.loadHistory()
        }
        .navigationDestination(for: BlogPost.self) { post in
            let _ = print("🔍 HistoryView: Navigating to BlogPost: \(post.title)")
            #if os(tvOS)
            let _ = print("🔍 HistoryView: Using VideoPlayerTvosView")
            return VideoPlayerTvosView(post: post)
            #else
            let _ = print("🔍 HistoryView: Using VideoPlayerView")
            return VideoPlayerView(post: post)
            #endif
        }
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

    private let api = FloatplaneAPI.shared

    func loadHistory() async {
        isLoading = true

        do {
            // Fetch watch history from server
            let watchHistory = try await api.getWatchHistory(offset: 0)

            guard !watchHistory.isEmpty else {
                historyItems = []
                groupedHistory = [:]
                isLoading = false
                return
            }

            // Convert server response to HistoryItems
            var items: [HistoryItem] = []
            for historyResponse in watchHistory {
                // Convert WatchHistoryBlogPost to BlogPost format
                let blogPost = BlogPost(
                    id: historyResponse.blogPost.id,
                    guid: historyResponse.blogPost.guid,
                    title: historyResponse.blogPost.title,
                    text: historyResponse.blogPost.text,
                    type: .blogpost,
                    channel: .typeChannelModel(historyResponse.blogPost.channel),
                    tags: historyResponse.blogPost.tags,
                    attachmentOrder: historyResponse.blogPost.attachmentOrder,
                    metadata: historyResponse.blogPost.metadata,
                    releaseDate: historyResponse.blogPost.releaseDate,
                    likes: historyResponse.blogPost.likes,
                    dislikes: historyResponse.blogPost.dislikes,
                    score: historyResponse.blogPost.score,
                    comments: historyResponse.blogPost.comments,
                    creator: BlogPostModelV3Creator(
                        id: historyResponse.blogPost.creator.id,
                        owner: BlogPostModelV3CreatorOwner(
                            id: historyResponse.blogPost.creator.owner,
                            username: historyResponse.blogPost.creator.owner
                        ),
                        title: historyResponse.blogPost.creator.title,
                        urlname: historyResponse.blogPost.creator.urlname,
                        description: historyResponse.blogPost.creator.description,
                        about: historyResponse.blogPost.creator.about,
                        category: CreatorModelV3Category(
                            id: historyResponse.blogPost.creator.category,
                            title: historyResponse.blogPost.creator.category
                        ),
                        cover: historyResponse.blogPost.creator.cover,
                        icon: historyResponse.blogPost.creator.icon,
                        liveStream: historyResponse.blogPost.creator.liveStream,
                        subscriptionPlans: historyResponse.blogPost.creator.subscriptionPlans ?? [],
                        discoverable: historyResponse.blogPost.creator.discoverable,
                        subscriberCountDisplay: historyResponse.blogPost.creator.subscriberCountDisplay,
                        incomeDisplay: historyResponse.blogPost.creator.incomeDisplay,
                        defaultChannel: historyResponse.blogPost.creator.defaultChannel,
                        channels: nil
                    ),
                    wasReleasedSilently: historyResponse.blogPost.wasReleasedSilently,
                    thumbnail: historyResponse.blogPost.thumbnail,
                    isAccessible: historyResponse.blogPost.isAccessible,
                    videoAttachments: historyResponse.blogPost.videoAttachments, // Already strings
                    audioAttachments: historyResponse.blogPost.audioAttachments, // Already strings
                    pictureAttachments: historyResponse.blogPost.pictureAttachments, // Already strings
                    galleryAttachments: historyResponse.blogPost.galleryAttachments ?? []
                )

                items.append(HistoryItem(
                    id: historyResponse.contentId,
                    post: blogPost,
                    watchedAt: historyResponse.updatedAt,
                    progress: Double(historyResponse.progress) / 100.0 // Convert 0-100 to 0.0-1.0
                ))
            }

            historyItems = items
            groupHistory()

        } catch {
            historyItems = []
            groupedHistory = [:]
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
