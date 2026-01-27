//
//  HomeFeedViewModel.swift
//  FloatNative
//
//  View model for home feed and subscriptions
//

import SwiftUI

@MainActor
class HomeFeedViewModel: ObservableObject {
    @Published var posts: [BlogPost] = []
    @Published var subscriptions: [Subscription] = []
    @Published var isLoading = false
    @Published var isLoadingMore = false
    @Published var errorMessage: String?
    @Published var filter: FeedFilter = .all

    private var creatorOffsets: [String: Int] = [:] // Track numeric offset per creator (legacy for single creator/channel)
    private var lastFetchAfter: Int = 0 // For single creator/channel feeds
    private var lastCursors: [FetchCursor] = [] // For multi-creator feed pagination
    private let api = FloatplaneAPI.shared
    private var seenPostIds: Set<String> = [] // Track seen post IDs to prevent duplicates
    private var lastRefreshTime: Date? // Track when the feed was last refreshed (in-memory only)

    func loadFeed(filter: FeedFilter = .all) async {
        self.filter = filter
        guard !isLoading else { return }

        isLoading = true
        errorMessage = nil
        seenPostIds.removeAll() // Clear seen posts on initial load

        do {
            // Get user subscriptions if not already loaded
            if subscriptions.isEmpty {
                subscriptions = try await api.getSubscriptions()
            }
            
            // Check for live creators whenever we load feed (if we have subscriptions)
            if !subscriptions.isEmpty && filter == .all {
                // Run in background task so we don't block feed loading
                Task {
                    await checkLiveCreators()
                }
            }

            // Load content based on filter
            switch filter {
            case .all:
                // Multi-creator feed using the list API endpoint
                let creatorIds = subscriptions.map { $0.creator }

                guard !creatorIds.isEmpty else {
                    errorMessage = "No subscriptions found. Subscribe to creators to see content."
                    isLoading = false
                    return
                }

                // Use the multi-creator feed API (fetches from all channels within creators)
                let response = try await api.getMultiCreatorFeed(
                    creatorIds: creatorIds,
                    limit: 20,
                    fetchAfter: nil
                )

                // Store posts and cursors
                posts = response.blogPosts
                lastCursors = response.lastElements
                seenPostIds = Set(posts.map { $0.id })

            case .creator(let creatorId, _, _):
                // Single creator feed
                let creatorPosts = try await api.getCreatorContent(
                    creatorId: creatorId,
                    limit: 20,
                    fetchAfter: 0
                )

                posts = creatorPosts
                creatorOffsets = [:]
                lastFetchAfter = creatorPosts.count
                seenPostIds = Set(posts.map { $0.id })

            case .channel(let channelId, _, _, let creatorId):
                // Single channel feed
                let channelPosts = try await api.getCreatorContent(
                    creatorId: creatorId,
                    channelId: channelId,
                    limit: 20,
                    fetchAfter: 0
                )

                posts = channelPosts
                creatorOffsets = [:]
                lastFetchAfter = channelPosts.count
                seenPostIds = Set(posts.map { $0.id })
            }

            isLoading = false
            lastRefreshTime = Date() // Update refresh time on successful load
        } catch {
            // Don't show cancellation errors (normal for pull-to-refresh)
            let isCancelled = Task.isCancelled ||
                              error is CancellationError ||
                              (error as NSError).domain == NSURLErrorDomain && (error as NSError).code == NSURLErrorCancelled

            if !isCancelled {
                errorMessage = "Failed to load feed: \(error.localizedDescription)"
            }
            isLoading = false
        }
    }

    func loadMore() async {
        guard !isLoadingMore else { return }

        isLoadingMore = true

        do {
            switch filter {
            case .all:
                let creatorIds = subscriptions.map { $0.creator }

                guard !creatorIds.isEmpty else {
                    isLoadingMore = false
                    return
                }

                // Check if there are more posts to fetch
                let hasMoreToFetch = lastCursors.contains { $0.moreFetchable == true }
                if !hasMoreToFetch && !lastCursors.isEmpty {
                    isLoadingMore = false
                    return
                }

                // Use the multi-creator feed API with cursors
                let response = try await api.getMultiCreatorFeed(
                    creatorIds: creatorIds,
                    limit: 20,
                    fetchAfter: lastCursors.isEmpty ? nil : lastCursors
                )

                // Filter out duplicates
                let uniquePosts = response.blogPosts.filter { !seenPostIds.contains($0.id) }

                // Add new posts and update tracking
                posts.append(contentsOf: uniquePosts)
                seenPostIds.formUnion(uniquePosts.map { $0.id })
                lastCursors = response.lastElements

            case .creator(let creatorId, _, _):
                let morePosts = try await api.getCreatorContent(
                    creatorId: creatorId,
                    limit: 20,
                    fetchAfter: lastFetchAfter
                )

                if !morePosts.isEmpty {
                    posts.append(contentsOf: morePosts)
                    lastFetchAfter += morePosts.count
                }

            case .channel(let channelId, _, _, let creatorId):
                let morePosts = try await api.getCreatorContent(
                    creatorId: creatorId,
                    channelId: channelId,
                    limit: 20,
                    fetchAfter: lastFetchAfter
                )

                if !morePosts.isEmpty {
                    posts.append(contentsOf: morePosts)
                    lastFetchAfter += morePosts.count
                }
            }

            isLoadingMore = false
        } catch {
            print("❌ Failed to load more: \(error)")
            isLoadingMore = false
        }
    }

    func refresh() async {
        // Don't clear posts - keep old content visible during refresh
        // Only reset state that affects fetching
        creatorOffsets = [:]
        lastFetchAfter = 0
        lastCursors = []
        seenPostIds.removeAll()

        await loadFeed(filter: filter)
    }

    func clearFilter() async {
        posts = []
        creatorOffsets = [:]
        lastFetchAfter = 0
        lastCursors = []
        seenPostIds.removeAll()
        await loadFeed(filter: .all)
    }

    // MARK: - Foreground Refresh

    /// Check if the feed should be refreshed based on time since last refresh
    /// Returns true if more than 5 minutes have passed or if never refreshed
    private func shouldRefreshOnForeground() -> Bool {
        // Don't refresh if already loading
        guard !isLoading else { return false }

        // If we've never refreshed, don't auto-refresh (initial load handles this)
        guard let lastRefresh = lastRefreshTime else { return false }

        // Check if more than 5 minutes (300 seconds) have passed
        let timeSinceRefresh = Date().timeIntervalSince(lastRefresh)
        return timeSinceRefresh > 300
    }

    /// Refresh the feed if enough time has passed since last refresh
    /// Called when app returns to foreground
    func refreshIfNeeded() async {
        if shouldRefreshOnForeground() {
            await refresh()
        } else {
            // Even if we don't refresh the full feed, check if anyone went live
            await checkLiveCreators()
        }
    }
    
    // MARK: - Live Stream Detection

    @Published var liveCreators: [Creator] = []

    func checkLiveCreators() async {
        guard !subscriptions.isEmpty else { return }
        
        // Extract creator IDs from subscriptions
        let creatorIds = subscriptions.map { $0.creator }
        guard !creatorIds.isEmpty else { return }
        
        do {
            // Fetch creator details including live stream info
            let creators = try await api.getCreatorsByIds(ids: creatorIds)
            
            // Filter creators that have potential live streams
            let potentialLiveCreators = creators.filter { creator in
                return creator.liveStream != nil
            }
            
            var confirmedLiveCreators: [Creator] = []
            
            // verify each potential stream by checking delivery info AND polling HLS
            await withTaskGroup(of: Creator?.self) { group in
                for creator in potentialLiveCreators {
                    guard let liveStreamId = creator.liveStream?.id else { continue }
                    
                    group.addTask {
                        do {
                            // 1. Get Delivery Info
                            let deliveryInfo = try await self.api.getDeliveryInfo(
                                scenario: .live,
                                entityId: liveStreamId,
                                outputKind: .hlsFmp4 // Use HLS just like Android's hlsPeriodMpegts/fmp4 choice
                            )
                            
                            // 2. Extract Master URL
                            // Logic: groups[0].origins[0].url + groups[0].variants[0].url
                            if let group = deliveryInfo.groups.first,
                               let variant = group.variants.first {
                                
                                let origin = variant.origins?.first ?? group.origins?.first
                                
                                if let origin = origin {
                                    // origin.url is likely a String based on the error, or needs to be cast. 
                                    // OpenAPI generator sometimes treats URI as String or URL.
                                    // Error says "Value of type 'String' has no member 'absoluteString'", so it IS a String.
                                    let masterURLString = origin.url + variant.url
                                    
                                    // 3. Poll the URL
                                    if await self.isURLReachable(urlString: masterURLString) {
                                        return creator
                                    }
                                }
                            }
                        } catch {
                            // Offline or error
                        }
                        return nil
                    }
                }
                
                for await result in group {
                    if let creator = result {
                        confirmedLiveCreators.append(creator)
                    }
                }
            }
            
            self.liveCreators = confirmedLiveCreators
            
        } catch {
            print("❌ Failed to check live creators: \(error)")
        }
    }

    private func isURLReachable(urlString: String) async -> Bool {
        guard let url = URL(string: urlString) else { return false }
        
        var request = URLRequest(url: url)
        request.httpMethod = "GET" // Using GET as some CDNs dislike HEAD for HLS masters
        request.timeoutInterval = 5
        
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            if let httpResponse = response as? HTTPURLResponse {
                return httpResponse.statusCode == 200
            }
            return false
        } catch {
            return false
        }
    }
}
