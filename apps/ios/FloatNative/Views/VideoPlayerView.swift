//
//  VideoPlayerView.swift
//  FloatNative
//
//  Clean video player with simple lifecycle
//  iOS 18+ native SwiftUI patterns
//

import SwiftUI
import AVKit

struct VideoPlayerView: View {
    let post: BlogPost

    @StateObject private var playerManager = AVPlayerManager.shared
    @StateObject private var api = FloatplaneAPI.shared
    @Environment(\.dismiss) private var dismiss

    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var isDescriptionExpanded = false
    @State private var parsedDescription: AttributedString = AttributedString("")
    @State private var isStreamOffline = false

    // Like/Dislike state (will be initialized from post)
    @State private var currentLikes: Int = 0
    @State private var currentDislikes: Int = 0
    @State private var userInteraction: ContentPostV3Response.UserInteraction?

    // Comments state
    @State private var showComments = false
    @State private var comments: [Comment] = []
    @State private var isLoadingComments = false
    @State private var commentsLoadError = false
    @State private var commentsRetryCount = 0
    @State private var newCommentText = ""
    @State private var isPostingComment = false
    @FocusState private var isCommentFieldFocused: Bool

    // Download state
    @State private var isDownloading = false
    @State private var showDownloadToast = false
    @State private var downloadToastMessage = ""
    @State private var downloadToastIcon = "checkmark.circle.fill"

    // Quality selector state
    @State private var isChangingQuality = false

    // Computed properties for UI
    private var hasLiked: Bool {
        userInteraction == .like
    }

    private var hasDisliked: Bool {
        userInteraction == .dislike
    }

    private var isLivestream: Bool {
        post.isLivestream
    }

    var body: some View {
        GeometryReader { geometry in
            let isLandscape = geometry.size.width > geometry.size.height

            ZStack {
                // Background
                Color.adaptiveBackground
                    .ignoresSafeArea()

                if isLandscape {
                    // Fullscreen video in landscape
                    fullscreenVideoPlayer
                } else {
                    // Normal portrait layout
                    portraitLayout
                }

                // Error state
                if let error = errorMessage {
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.system(size: 50))
                            .foregroundStyle(.red)

                        Text(error)
                            .foregroundStyle(Color.adaptiveText)
                            .multilineTextAlignment(.center)

                        Button("Retry") {
                            Task {
                                await loadVideo()
                            }
                        }
                        .buttonStyle(LiquidGlassButtonStyle(tint: .floatplaneBlue))
                    }
                    .padding()
                }

                // Stream offline overlay (for livestreams that have ended)
                if isStreamOffline && isLivestream {
                    StreamOfflineOverlay {
                        dismiss()
                    }
                }

                // Bottom comment input overlay (only in portrait, not for livestreams, and when comments loaded successfully)
                if !isLandscape && !isLivestream && !commentsLoadError {
                    VStack {
                        Spacer()
                        bottomCommentInput
                    }
                }
            }
            #if !os(tvOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .navigationBarBackButtonHidden(false)
            #if !os(tvOS)
            .statusBar(hidden: isLandscape) // Hide status bar in landscape
            #endif
            .toolbar(isLandscape ? .hidden : .visible, for: .tabBar) // Hide tab bar in landscape
            .globalMenu()
        }
        .onAppear {
            // Initialize like/dislike counts from post
            currentLikes = post.likes
            currentDislikes = post.dislikes

            // Debug: Log post details
            print("📱 [VideoPlayerView] Post loaded:")
            print("📱   ID: \(post.id)")
            print("📱   Type: \(post.type)")
            print("📱   Title: \(post.title)")
            print("📱   Metadata - hasVideo: \(post.metadata.hasVideo)")
            print("📱   Metadata - hasPicture: \(post.metadata.hasPicture)")
            print("📱   Metadata - hasGallery: \(post.metadata.hasGallery)")
            print("📱   Video attachments: \(post.videoAttachments ?? [])")
            print("📱   Picture attachments: \(post.pictureAttachments ?? [])")
            print("📱   Gallery attachments: \(post.galleryAttachments ?? [])")
        }
        .task {
            await loadVideo()
        }
        .task(priority: .userInitiated) {
            // Parse HTML description once in background
            // Capture text to avoid capturing self in detached task
            let htmlText = post.text
            
            // Run parsing in background to avoid blocking main thread
            parsedDescription = await Task.detached(priority: .userInitiated) {
                return VideoPlayerView.htmlToAttributedString(htmlText)
            }.value
        }
        .task(priority: .low) {
            // Load user interaction state after view is stable
            try? await Task.sleep(nanoseconds: 200_000_000) // 0.2 seconds
            await loadInteractionState()
        }
        .task(priority: .background) {
            // Load comments after view hierarchy is stable
            try? await Task.sleep(nanoseconds: 300_000_000) // 0.3 seconds
            await loadComments(limit: 20)
        }
        .toast(
            isPresented: $showDownloadToast,
            message: downloadToastMessage,
            icon: downloadToastIcon,
            duration: 3.0
        )
        .onDisappear {
            // Save progress when leaving the view (skip for livestreams)
            if !isLivestream {
                Task {
                    await playerManager.saveProgress()
                }
            }

            // If we're not in PiP mode, pause and cleanup the player
            // This prevents background audio when quickly navigating back
            if !playerManager.hasPIPSession {
                playerManager.pause()
                // Reset player to stop any ongoing loading that might auto-play
                playerManager.reset()
            }
            // Note: PiP is automatically handled by AVPlayerViewController
            // It will continue playing when in PiP mode (if user has enabled it)
        }
    }

    // MARK: - Layout Views

    private var fullscreenVideoPlayer: some View {
        ZStack {
            if let player = playerManager.player {
                CustomVideoPlayer(player: player, showsPlaybackControls: true)
                    .ignoresSafeArea()
            } else {
                Rectangle()
                    .fill(Color.black)
                    .ignoresSafeArea()
                    .overlay {
                        if isLoading {
                            ProgressView()
                                .tint(.white)
                        }
                    }
            }
        }
    }

    private var portraitLayout: some View {
        VStack(spacing: 0) {
            // Video Player or Image
            if post.metadata.hasVideo {
                // Video post - show player
                if let player = playerManager.player {
                    CustomVideoPlayer(player: player, showsPlaybackControls: true)
                        .frame(maxWidth: .infinity)
                        .aspectRatio(16/9, contentMode: .fit)
                } else {
                    Rectangle()
                        .fill(Color.black)
                        .aspectRatio(16/9, contentMode: .fit)
                        .overlay {
                            if isLoading {
                                ProgressView()
                                    .tint(.white)
                            }
                        }
                }
            } else {
                // Image or text post - show image if available
                if post.metadata.hasPicture, let imageURL = post.thumbnail?.fullURL {
                    CachedAsyncImage(url: imageURL) { image in
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Rectangle()
                            .fill(Color.floatplaneGray.opacity(0.3))
                    }
                    .frame(maxWidth: .infinity)
                    .aspectRatio(16/9, contentMode: .fit)
                    .clipped()
                } else {
                    // Text-only post - no image
                    // Show a placeholder or nothing
                    EmptyView()
                }
            }

            // Video Info
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Title
                    Text(post.title)
                        .font(.title3)
                        .fontWeight(.semibold)
                        .foregroundColor(Color.adaptiveText)
                        .multilineTextAlignment(.leading)

                    // Metadata
                    HStack(spacing: 8) {
                        Text(formatDate(post.releaseDate))
                    }
                    .font(.subheadline)
                    .foregroundColor(Color.adaptiveSecondaryText)

                    // Action Buttons (Like/Dislike)
                    actionButtons

                    Divider()
                        .background(Color.floatplaneGray.opacity(0.3))

                    // Channel/Creator Info
                    HStack(spacing: 12) {
                        // Channel or Creator avatar
                        if let channel = post.channel.channelObject, let channelIcon = channel.icon.fullURL {
                            // Show channel icon if available
                            CachedAsyncImage(url: channelIcon) { image in
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Circle()
                                    .fill(Color.floatplaneGray.opacity(0.3))
                            }
                            .frame(width: 40, height: 40)
                            .clipShape(Circle())
                        } else if let creatorIcon = post.creator.icon.fullURL {
                            // Fall back to creator icon
                            CachedAsyncImage(url: creatorIcon) { image in
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Circle()
                                    .fill(Color.floatplaneGray.opacity(0.3))
                            }
                            .frame(width: 40, height: 40)
                            .clipShape(Circle())
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            // Show channel name if available, otherwise creator name
                            if let channel = post.channel.channelObject {
                                Text(channel.title)
                                    .font(.body)
                                    .fontWeight(.semibold)
                                    .foregroundColor(Color.adaptiveText)

                                Text("Channel")
                                    .font(.caption)
                                    .foregroundColor(Color.adaptiveSecondaryText)
                            } else {
                                Text(post.creator.title)
                                    .font(.body)
                                    .fontWeight(.semibold)
                                    .foregroundColor(Color.adaptiveText)

                                Text("Creator")
                                    .font(.caption)
                                    .foregroundColor(Color.adaptiveSecondaryText)
                            }
                        }

                        Spacer()
                    }

                    Divider()
                        .background(Color.floatplaneGray.opacity(0.3))

                    // Description
                    descriptionSection

                    Divider()
                        .background(Color.floatplaneGray.opacity(0.3))

                    // Comments Section
                    commentsSection
                }
                .padding()
                .padding(.bottom, 100) // Space for floating comment input
            }
        }
    }

    // MARK: - Load Video

    private func loadInteractionState() async {
        do {
            print("📱 [VideoPlayerView.loadInteractionState] Fetching detailed post for ID: \(post.id)")
            let detailedPost = try await api.getBlogPost(id: post.id)

            print("📱 [VideoPlayerView.loadInteractionState] Detailed post response:")
            print("📱   ID: \(detailedPost.id)")
            print("📱   Likes: \(detailedPost.likes)")
            print("📱   Dislikes: \(detailedPost.dislikes)")
            print("📱   Self interaction: \(String(describing: detailedPost.selfUserInteraction))")
            print("📱   Full detailed post: \(detailedPost)")

            await MainActor.run {
                // Update counts with server values
                currentLikes = detailedPost.likes
                currentDislikes = detailedPost.dislikes
                userInteraction = detailedPost.selfUserInteraction
            }
        } catch {
            // Silently fail - likes/dislikes are not critical
            print("📱 [VideoPlayerView.loadInteractionState] Failed to load interaction state: \(error)")
        }
    }

    private func loadVideo() async {
        print("📱 [VideoPlayerView.loadVideo] Checking post type...")
        print("📱   hasVideo: \(post.metadata.hasVideo)")
        print("📱   isLivestream: \(isLivestream)")
        print("📱   videoAttachments: \(post.videoAttachments ?? [])")

        // Handle livestream loading differently
        if isLivestream {
            await loadLivestream()
            return
        }

        // Skip video loading for non-video posts
        guard post.metadata.hasVideo else {
            print("📱 [VideoPlayerView.loadVideo] Non-video post, skipping video load")
            isLoading = false
            return
        }

        guard let videoId = post.videoAttachments?.first else {
            print("📱 [VideoPlayerView.loadVideo] No video attachment found!")
            errorMessage = "No video available for this post"
            isLoading = false
            return
        }

        print("📱 [VideoPlayerView.loadVideo] Video ID: \(videoId)")

        // Check if we should reuse existing PiP player
        // Use hasPIPSession instead of isPIPActive so we reuse even when paused
        let shouldReusePlayer = playerManager.hasPIPSession &&
                               playerManager.currentPost?.id == post.id &&
                               playerManager.player != nil

        if shouldReusePlayer {
            print("📱 [VideoPlayerView.loadVideo] Reusing existing PiP player")
            // Reusing existing PiP player - don't create new one
            await MainActor.run {
                WatchHistoryManager.shared.addToHistory(postId: post.id, videoId: videoId)
                isLoading = false
            }
            return
        }

        // Loading a different video - clean up old PiP session if it exists
        if playerManager.hasPIPSession {
            await MainActor.run {
                playerManager.playerViewController = nil
                playerManager.playerViewControllerDelegate = nil
                playerManager.hasPIPSession = false
            }
        }
        isLoading = true
        errorMessage = nil

        do {
            // Get video content
            print("📱 [VideoPlayerView.loadVideo] Fetching video content for ID: \(videoId)")
            let content = try await api.getVideoContent(id: videoId)
            print("📱 [VideoPlayerView.loadVideo] Video content response:")
            print("📱   Progress: \(content.progress ?? 0)")
            print("📱   Content: \(content)")

            // Get delivery info
            let deliveryInfo = try await api.getDeliveryInfo(
                scenario: .onDemand,
                entityId: videoId,
                outputKind: .hlsMpegts
            )

            let qualities = deliveryInfo.availableVariants()

            // Load video into player
            try await playerManager.loadVideo(
                videoId: videoId,
                title: post.title,
                post: post,
                startTime: Double(content.progress ?? 0),
                qualities: qualities
            )

            // Add to watch history
            print("▶️ VideoPlayerView: About to add to watch history - postId: \(post.id), videoId: \(videoId)")
            await MainActor.run {
                WatchHistoryManager.shared.addToHistory(postId: post.id, videoId: videoId)
            }

            // Auto-play
            playerManager.play()

            isLoading = false
        } catch {
            errorMessage = "Failed to load video: \(error.localizedDescription)"
            isLoading = false
        }
    }

    private func loadLivestream() async {
        print("📱 [VideoPlayerView.loadLivestream] Loading livestream...")

        guard let liveStream = post.creator.liveStream else {
            print("📱 [VideoPlayerView.loadLivestream] No livestream data found!")
            errorMessage = "No livestream available"
            isLoading = false
            return
        }

        print("📱 [VideoPlayerView.loadLivestream] Livestream ID: \(liveStream.id)")

        isLoading = true
        errorMessage = nil

        do {
            // Get delivery info for livestream
            print("📱 [VideoPlayerView.loadLivestream] Fetching delivery info for livestream ID: \(liveStream.id)")
            let deliveryInfo = try await api.getDeliveryInfo(
                scenario: .live,
                entityId: liveStream.id,
                outputKind: .hlsFmp4
            )
            print("📱 [VideoPlayerView.loadLivestream] Delivery info response:")
            print("📱   Delivery info: \(deliveryInfo)")

            let qualities = deliveryInfo.availableVariants()
            print("📱 [VideoPlayerView.loadLivestream] Available qualities: \(qualities.count)")

            // Load livestream into player (no start time for live content)
            try await playerManager.loadVideo(
                videoId: liveStream.id,
                title: post.title,
                post: post,
                startTime: 0,  // No start time for livestreams
                qualities: qualities
            )

            // Auto-play
            playerManager.play()

            isLoading = false

            // TODO: Add AVPlayer observation to detect when livestream ends
            // Monitor player.status and player.error to set isStreamOffline = true
            // when the stream goes offline or encounters a fatal error
        } catch {
            errorMessage = "Failed to load livestream: \(error.localizedDescription)"
            isLoading = false
        }
    }

    // MARK: - Content Interactions

    private func likeContent() async {
        // Save previous state for rollback
        let previousLikes = currentLikes
        let previousDislikes = currentDislikes
        let previousInteraction = userInteraction

        // Optimistically update UI
        await MainActor.run {
            switch userInteraction {
            case .like:
                // Unlike: remove like
                currentLikes -= 1
                userInteraction = nil
            case .dislike:
                // Switch from dislike to like
                currentDislikes -= 1
                currentLikes += 1
                userInteraction = .like
            case .none, nil:
                // Add like
                currentLikes += 1
                userInteraction = .like
            }
        }

        // Call API in background
        do {
            _ = try await api.likeContent(contentType: "blogPost", id: post.id)
        } catch {
            // Rollback on failure
            await MainActor.run {
                currentLikes = previousLikes
                currentDislikes = previousDislikes
                userInteraction = previousInteraction
                errorMessage = "Failed to update like: \(error.localizedDescription)"
            }
        }
    }

    private func dislikeContent() async {
        // Save previous state for rollback
        let previousLikes = currentLikes
        let previousDislikes = currentDislikes
        let previousInteraction = userInteraction

        // Optimistically update UI
        await MainActor.run {
            switch userInteraction {
            case .dislike:
                // Undislike: remove dislike
                currentDislikes -= 1
                userInteraction = nil
            case .like:
                // Switch from like to dislike
                currentLikes -= 1
                currentDislikes += 1
                userInteraction = .dislike
            case .none, nil:
                // Add dislike
                currentDislikes += 1
                userInteraction = .dislike
            }
        }

        // Call API in background
        do {
            _ = try await api.dislikeContent(contentType: "blogPost", id: post.id)
        } catch {
            // Rollback on failure
            await MainActor.run {
                currentLikes = previousLikes
                currentDislikes = previousDislikes
                userInteraction = previousInteraction
                errorMessage = "Failed to update dislike: \(error.localizedDescription)"
            }
        }
    }

    private func downloadVideo() async {
        guard let videoId = post.videoAttachments?.first else {
            await MainActor.run {
                downloadToastMessage = "No video available for download"
                downloadToastIcon = "exclamationmark.circle.fill"
                showDownloadToast = true
            }
            return
        }

        // Set loading state
        await MainActor.run {
            isDownloading = true
        }

        var tempURL: URL?
        var localURL: URL?

        do {
            let deliveryInfo = try await api.getDeliveryInfo(
                scenario: .download,
                entityId: videoId,
                outputKind: .flat
            )

            let qualities = deliveryInfo.availableVariants()

            // Try to match the currently selected quality by resolution
            var selectedQuality: QualityVariant?
            if let currentQuality = playerManager.currentQuality {
                // Match by resolution (width x height)
                selectedQuality = qualities.first { quality in
                    quality.width == currentQuality.width &&
                    quality.height == currentQuality.height
                }
            }

            // Fall back to best quality if no match found or no current quality selected
            guard let downloadQuality = selectedQuality ?? qualities.first else {
                await MainActor.run {
                    downloadToastMessage = "No download variants available"
                    downloadToastIcon = "exclamationmark.circle.fill"
                    showDownloadToast = true
                    isDownloading = false
                }
                return
            }

            guard let url = URL(string: downloadQuality.url) else {
                await MainActor.run {
                    downloadToastMessage = "Invalid download URL"
                    downloadToastIcon = "exclamationmark.circle.fill"
                    showDownloadToast = true
                    isDownloading = false
                }
                return
            }

            // Download the file
            let downloadResult = try await URLSession.shared.download(from: url)
            localURL = downloadResult.0

            // Move to temporary location with proper filename
            tempURL = FileManager.default.temporaryDirectory
                .appendingPathComponent("\(post.title).mp4")
            try? FileManager.default.removeItem(at: tempURL!)
            try FileManager.default.moveItem(at: localURL!, to: tempURL!)

            // Present share sheet on main thread
            await MainActor.run {
                isDownloading = false
                #if !os(tvOS)
                presentShareSheet(url: tempURL!)
                #else
                // On tvOS, just clean up the temp file since there's no share sheet
                try? FileManager.default.removeItem(at: tempURL!)
                downloadToastMessage = "Download complete"
                downloadToastIcon = "checkmark.circle.fill"
                showDownloadToast = true
                #endif
            }
        } catch {
            // Clean up any partial downloads immediately
            if let localURL = localURL {
                try? FileManager.default.removeItem(at: localURL)
            }
            if let tempURL = tempURL {
                try? FileManager.default.removeItem(at: tempURL)
            }

            await MainActor.run {
                isDownloading = false

                // Determine error message based on error type
                let message: String
                if let urlError = error as? URLError {
                    switch urlError.code {
                    case .notConnectedToInternet, .networkConnectionLost:
                        message = "Network error. Check your connection."
                    case .timedOut:
                        message = "Download timed out"
                    case .cannotWriteToFile:
                        message = "Storage error. Check available space."
                    default:
                        message = "Download failed: \(urlError.localizedDescription)"
                    }
                } else {
                    message = "Download failed: \(error.localizedDescription)"
                }

                downloadToastMessage = message
                downloadToastIcon = "exclamationmark.circle.fill"
                showDownloadToast = true

                #if DEBUG
                print("❌ Download failed: \(error)")
                #endif
            }
        }
    }

    #if !os(tvOS)
    @MainActor
    private func presentShareSheet(url: URL) {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootViewController = windowScene.windows.first?.rootViewController else {
            return
        }

        let activityVC = UIActivityViewController(
            activityItems: [url],
            applicationActivities: nil
        )

        // Cleanup temp file after share sheet is dismissed
        activityVC.completionWithItemsHandler = { _, _, _, _ in
            // Delete temporary file regardless of whether user saved/shared/cancelled
            try? FileManager.default.removeItem(at: url)
        }

        // For iPad popover
        if let popover = activityVC.popoverPresentationController {
            popover.sourceView = rootViewController.view
            popover.sourceRect = CGRect(
                x: rootViewController.view.bounds.midX,
                y: rootViewController.view.bounds.midY,
                width: 0,
                height: 0
            )
        }

        rootViewController.present(activityVC, animated: true)
    }
    #endif

    // MARK: - Quality Change

    private func changeQuality(to quality: QualityVariant) async {
        // Set loading state
        await MainActor.run {
            isChangingQuality = true
        }

        do {
            // Change quality using AVPlayerManager
            try await playerManager.changeQuality(quality)

            // Clear loading state
            await MainActor.run {
                isChangingQuality = false
            }
        } catch {
            // Handle error
            await MainActor.run {
                isChangingQuality = false
                errorMessage = "Failed to change quality: \(error.localizedDescription)"
            }
        }
    }

    // MARK: - Comments Functions

    private func loadComments(limit: Int = 20, isRetry: Bool = false) async {
        // Only show loading state on initial load, not retries
        if !isRetry {
            await MainActor.run {
                isLoadingComments = true
                // Don't reset commentsLoadError here - only clear it after successful load
            }
        }

        do {
            let loadedComments = try await api.getComments(blogPostId: post.id, limit: limit)

            // Transform flat structure into nested tree in background
            // Capture loaded comments to avoid capturing self
            let flatComments = loadedComments
            
            let commentTree = await Task.detached(priority: .userInitiated) {
                return VideoPlayerView.buildCommentTree(from: flatComments)
            }.value

            await MainActor.run {
                comments = commentTree
                commentsLoadError = false  // Only set to false after successful load
                commentsRetryCount = 0

                // Auto-expand comments section after loading
                if !commentTree.isEmpty {
                    showComments = true
                }

                isLoadingComments = false

                #if DEBUG
                print("✅ [Comments] Successfully loaded \(commentTree.count) top-level comments for post \(post.id)")
                if isRetry {
                    print("   (Loaded on retry)")
                }
                #endif
            }
        } catch {
            // Don't show full-screen error overlay, just mark as error
            await MainActor.run {
                commentsLoadError = true
                isLoadingComments = false

                #if DEBUG
                // Detailed error logging to help debug comment loading issues
                print("❌ [Comments] Failed to load for post \(post.id)")
                print("   Error type: \(type(of: error))")
                print("   Description: \(error.localizedDescription)")

                // Check if it's a FloatplaneAPIError for more details
                if let apiError = error as? FloatplaneAPIError {
                    switch apiError {
                    case .httpError(let statusCode, let message):
                        print("   HTTP \(statusCode): \(message ?? "No message")")
                    case .malformedData(let message):
                        print("   Malformed data: \(message ?? "Unknown structure")")
                    case .decodingError(let decodingError):
                        print("   Decoding error: \(decodingError)")
                    case .networkError(let networkError):
                        print("   Network error: \(networkError)")
                    default:
                        print("   API error: \(apiError)")
                    }
                }
                #endif
            }

            // Schedule retry in a new task so we don't block playback
            // Only retry if we haven't exceeded max retries
            if commentsRetryCount < 3 {
                let nextRetryCount = commentsRetryCount + 1
                await MainActor.run {
                    commentsRetryCount = nextRetryCount
                }

                #if DEBUG
                await MainActor.run {
                    print("⏳ [Comments] Scheduling retry \(nextRetryCount)/3 in 5 seconds...")
                }
                #endif

                // Spawn retry in new task and return immediately (don't block)
                Task {
                    try? await Task.sleep(nanoseconds: 5_000_000_000) // 5 seconds
                    await loadComments(limit: limit, isRetry: true)
                }
            } else {
                #if DEBUG
                await MainActor.run {
                    print("❌ [Comments] Max retries reached. Comments will remain unavailable.")
                }
                #endif
            }
        }
    }

    /// Transform flat comment list into nested tree structure
    /// The API returns replies as separate items with `replying` field pointing to parent ID
    /// We need to nest them into their parent's `replies` array for proper rendering
    private nonisolated static func buildCommentTree(from flatComments: [Comment]) -> [Comment] {
        // Separate top-level comments from replies
        var topLevelComments: [Comment] = []
        var repliesByParentId: [String: [Comment]] = [:]

        for comment in flatComments {
            if comment.replying == nil {
                // Top-level comment
                topLevelComments.append(comment)
            } else if let parentId = comment.replying {
                // Reply - group by parent ID
                if repliesByParentId[parentId] == nil {
                    repliesByParentId[parentId] = []
                }
                repliesByParentId[parentId]?.append(comment)
            }
        }

        // Recursively attach replies to their parents
        return topLevelComments.map { comment in
            attachReplies(to: comment, repliesMap: repliesByParentId)
        }
    }

    /// Recursively attach replies to a comment and its nested replies
    private nonisolated static func attachReplies(to comment: Comment, repliesMap: [String: [Comment]]) -> Comment {
        // Get direct replies to this comment
        guard let directReplies = repliesMap[comment.id] else {
            // No replies - return as is
            return comment
        }

        // Recursively process nested replies
        let nestedReplies = directReplies.map { reply in
            attachReplies(to: reply, repliesMap: repliesMap)
        }

        // Create new comment with replies attached
        return Comment(
            id: comment.id,
            blogPost: comment.blogPost,
            user: comment.user,
            text: comment.text,
            replying: comment.replying,
            postDate: comment.postDate,
            editDate: comment.editDate,
            editCount: comment.editCount,
            isEdited: comment.isEdited,
            likes: comment.likes,
            dislikes: comment.dislikes,
            score: comment.score,
            interactionCounts: comment.interactionCounts,
            totalReplies: comment.totalReplies,
            replies: nestedReplies,
            userInteraction: comment.userInteraction
        )
    }

    private func debugLogComment(_ comment: Comment, level: Int) {
        let indent = String(repeating: "  ", count: level)
        let textPreview = String(comment.text.prefix(50))
        print("\(indent)├─ ID: \(comment.id)")
        print("\(indent)│  User: \(comment.user.username)")
        print("\(indent)│  Text: \"\(textPreview)...\"")
        print("\(indent)│  Replying to: \(comment.replying ?? "nil (top-level)")")
        print("\(indent)│  Replies count: \(comment.replies?.count ?? 0)")

        if let replies = comment.replies, !replies.isEmpty {
            print("\(indent)│  Nested replies:")
            for (index, reply) in replies.enumerated() {
                print("\(indent)│  └─ Reply #\(index + 1):")
                debugLogComment(reply, level: level + 2)
            }
        }
    }

    private func postComment() async {
        // Save text before clearing (needed for rollback)
        let commentTextToPost = newCommentText

        guard !commentTextToPost.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return
        }

        // Create optimistic comment with temporary ID
        let optimisticId = "optimistic-\(UUID().uuidString)"

        await MainActor.run {
            isPostingComment = true

            // Add optimistic comment immediately
            let optimisticComment = Comment(
                id: optimisticId,
                blogPost: post.id,
                user: api.currentUser ?? UserModel(
                    id: "unknown",
                    username: "You",
                    profileImage: ImageModel(width: 0, height: 0, path: "", childImages: nil)
                ),
                text: commentTextToPost,
                replying: nil,  // Top-level comment, not a reply
                postDate: Date(),
                editDate: nil,
                editCount: 0,
                isEdited: false,
                likes: 0,
                dislikes: 0,
                score: 0,
                interactionCounts: InteractionCounts(like: 0, dislike: 0),
                totalReplies: 0,
                replies: nil,
                userInteraction: nil
            )

            comments.insert(optimisticComment, at: 0)
            newCommentText = ""
            isCommentFieldFocused = false  // Dismiss keyboard after posting
        }

        do {
            let realComment = try await api.postComment(blogPostId: post.id, text: commentTextToPost)

            await MainActor.run {
                // Replace optimistic comment with real one
                if let index = comments.firstIndex(where: { $0.id == optimisticId }) {
                    comments[index] = realComment
                }

                isPostingComment = false
            }
        } catch {
            await MainActor.run {
                // Remove optimistic comment on failure
                comments.removeAll { $0.id == optimisticId }

                errorMessage = "Failed to post comment: \(error.localizedDescription)"
                isPostingComment = false
            }
        }
    }

    // MARK: - Action Buttons

    private var actionButtons: some View {
        Group {
            if !isLivestream {
                HStack(spacing: 12) {
                    // Like button
                    Button {
                Task {
                    await likeContent()
                }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: hasLiked ? "hand.thumbsup.fill" : "hand.thumbsup")
                    Text("\(currentLikes)")
                }
                .font(.subheadline)
                .fontWeight(.medium)
                .foregroundColor(hasLiked ? .floatplaneBlue : Color.adaptiveText)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }
            .liquidGlass(tint: hasLiked ? .floatplaneBlue : .gray, opacity: hasLiked ? 0.8 : 0.6)

            // Dislike button
            Button {
                Task {
                    await dislikeContent()
                }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: hasDisliked ? "hand.thumbsdown.fill" : "hand.thumbsdown")
                    Text("\(currentDislikes)")
                }
                .font(.subheadline)
                .fontWeight(.medium)
                .foregroundColor(hasDisliked ? .red : Color.adaptiveText)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }
            .liquidGlass(tint: hasDisliked ? .red : .gray, opacity: hasDisliked ? 0.8 : 0.6)

            Spacer()

            // Download button (only for video posts)
            if post.metadata.hasVideo {
                Button {
                    Task {
                        await downloadVideo()
                    }
                } label: {
                    if isDownloading {
                        ProgressView()
                            .tint(.white)
                    } else {
                        Image(systemName: "arrow.down.circle")
                            .font(.title2)
                            .foregroundColor(Color.adaptiveText)
                    }
                }
                .disabled(isDownloading)
            }

            // Quality selector button (only for video posts)
            if post.metadata.hasVideo {
                Menu {
                ForEach(playerManager.availableQualities.sorted(by: { $0.order < $1.order })) { quality in
                    Button {
                        Task {
                            await changeQuality(to: quality)
                        }
                    } label: {
                        HStack {
                            Text(quality.label)
                            if playerManager.currentQuality?.id == quality.id {
                                Image(systemName: "checkmark")
                            }
                        }
                    }
                }
            } label: {
                if isChangingQuality {
                    ProgressView()
                        .tint(.white)
                } else {
                    HStack(spacing: 4) {
                        Image(systemName: "gearshape")
                        if let currentQuality = playerManager.currentQuality {
                            Text(currentQuality.label)
                                .font(.caption)
                        }
                    }
                    .font(.title2)
                    .foregroundColor(Color.adaptiveText)
                }
            }
            .disabled(isChangingQuality || playerManager.availableQualities.isEmpty)
            }
                }
            }
        }
    }

    // MARK: - Comments Section

    private var commentsSection: some View {
        Group {
            // Only show comments section for non-livestreams and when there's no loading error
            if !isLivestream && !commentsLoadError {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("Comments")
                    .font(.headline)
                    .foregroundColor(Color.adaptiveText)

                Text("\(post.comments)")
                    .font(.subheadline)
                    .foregroundColor(Color.adaptiveSecondaryText)

                Spacer()

                Button {
                    withAnimation {
                        showComments.toggle()
                    }
                } label: {
                    Image(systemName: showComments ? "chevron.up" : "chevron.down")
                        .foregroundColor(Color.adaptiveSecondaryText)
                }
            }

            if showComments {
                VStack(spacing: 16) {
                    // Comments list
                    if isLoadingComments {
                        ProgressView()
                            .tint(.floatplaneBlue)
                            .padding()
                    } else if comments.isEmpty {
                        Text("No comments yet. Be the first to comment!")
                            .font(.subheadline)
                            .foregroundColor(Color.adaptiveSecondaryText)
                            .padding()
                    } else {
                        LazyVStack(alignment: .leading, spacing: 12) {
                            ForEach(comments) { comment in
                                commentView(comment, depth: 0)
                            }
                        }
                    }
                }
                .padding(.vertical, 8)
            }
                }
            }
        }
    }

    // Iterative comment rendering with explicit type to avoid recursive inference
    @ViewBuilder
    private func commentView(_ comment: Comment, depth: Int) -> AnyView {
        AnyView(
            VStack(alignment: .leading, spacing: 8) {
                // Comment content with explicit depth-based padding
                HStack(alignment: .top, spacing: 12) {
                // User avatar
                CachedAsyncImage(url: comment.user.profileImage.fullURL) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } placeholder: {
                    Circle()
                        .fill(Color.floatplaneGray.opacity(0.3))
                }
                .frame(width: 32, height: 32)
                .clipShape(Circle())

                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(comment.user.username)
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundColor(Color.adaptiveText)

                        Text(formatCommentDate(comment.postDate))
                            .font(.caption)
                            .foregroundColor(Color.adaptiveSecondaryText)
                    }

                    Text(comment.text)
                        .font(.subheadline)
                        .foregroundColor(Color.adaptiveText)
                        .fixedSize(horizontal: false, vertical: true)

                    HStack(spacing: 16) {
                        // Like button
                        Button {
                            Task {
                                await likeComment(comment)
                            }
                        } label: {
                            HStack(spacing: 4) {
                                let hasLiked = comment.userInteraction?.contains(.like) ?? false
                                Image(systemName: hasLiked ? "hand.thumbsup.fill" : "hand.thumbsup")
                                    .font(.caption)
                                Text("\(comment.interactionCounts.like)")
                                    .font(.caption)
                            }
                            .foregroundColor(hasLiked ? .floatplaneBlue : Color.adaptiveSecondaryText)
                        }

                        // Dislike button
                        Button {
                            Task {
                                await dislikeComment(comment)
                            }
                        } label: {
                            HStack(spacing: 4) {
                                let hasDisliked = comment.userInteraction?.contains(.dislike) ?? false
                                Image(systemName: hasDisliked ? "hand.thumbsdown.fill" : "hand.thumbsdown")
                                    .font(.caption)
                                Text("\(comment.interactionCounts.dislike)")
                                    .font(.caption)
                            }
                            .foregroundColor(hasDisliked ? .red : Color.adaptiveSecondaryText)
                        }

                        if let totalReplies = comment.totalReplies, totalReplies > 0 {
                            Text("\(totalReplies) \(totalReplies == 1 ? "reply" : "replies")")
                                .font(.caption)
                                .foregroundColor(.floatplaneBlue)
                        }
                    }
                    .padding(.top, 4)
                }
            }
            .padding(.leading, CGFloat(depth * 40)) // Explicit depth-based padding

                // Render nested replies iteratively
                if let replies = comment.replies, !replies.isEmpty {
                    ForEach(replies) { reply in
                        commentView(reply, depth: depth + 1)
                    }
                }
            }
        )
    }

    private func formatCommentDate(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: Date())
    }

    // MARK: - Comment Interaction Helpers

    /// Recursively update a comment by ID in the nested comment structure
    private func updateComment(in comments: [Comment], commentId: String, with updater: (inout Comment) -> Void) -> [Comment] {
        var updatedComments = comments

        for i in 0..<updatedComments.count {
            if updatedComments[i].id == commentId {
                // Found the comment, update it
                updater(&updatedComments[i])
                return updatedComments
            }

            // Check nested replies
            if let replies = updatedComments[i].replies, !replies.isEmpty {
                let updatedReplies = updateComment(in: replies, commentId: commentId, with: updater)
                updatedComments[i].replies = updatedReplies
            }
        }

        return updatedComments
    }

    /// Like a comment with optimistic update
    private func likeComment(_ comment: Comment) async {
        // Determine current state
        let hasLiked = comment.userInteraction?.contains(.like) ?? false
        let hasDisliked = comment.userInteraction?.contains(.dislike) ?? false

        // Save previous state for rollback
        let previousComments = comments

        // Optimistically update UI
        await MainActor.run {
            comments = updateComment(in: comments, commentId: comment.id) { updatedComment in
                var newLikeCount = updatedComment.interactionCounts.like
                var newDislikeCount = updatedComment.interactionCounts.dislike
                var newUserInteraction: [Comment.UserInteraction]? = updatedComment.userInteraction ?? []

                if hasLiked {
                    // Unlike: remove like
                    newLikeCount -= 1
                    newUserInteraction?.removeAll { $0 == .like }
                } else if hasDisliked {
                    // Switch from dislike to like
                    newDislikeCount -= 1
                    newLikeCount += 1
                    newUserInteraction?.removeAll { $0 == .dislike }
                    newUserInteraction?.append(.like)
                } else {
                    // Add like
                    newLikeCount += 1
                    newUserInteraction?.append(.like)
                }

                updatedComment.interactionCounts = CommentV3PostResponseInteractionCounts(
                    like: newLikeCount,
                    dislike: newDislikeCount
                )
                updatedComment.userInteraction = newUserInteraction?.isEmpty == true ? nil : newUserInteraction
            }
        }

        // Call API in background
        do {
            _ = try await api.likeComment(commentId: comment.id, blogPostId: post.id)
        } catch {
            // Rollback on failure
            await MainActor.run {
                comments = previousComments
                errorMessage = "Failed to update like: \(error.localizedDescription)"
            }
        }
    }

    /// Dislike a comment with optimistic update
    private func dislikeComment(_ comment: Comment) async {
        // Determine current state
        let hasLiked = comment.userInteraction?.contains(.like) ?? false
        let hasDisliked = comment.userInteraction?.contains(.dislike) ?? false

        // Save previous state for rollback
        let previousComments = comments

        // Optimistically update UI
        await MainActor.run {
            comments = updateComment(in: comments, commentId: comment.id) { updatedComment in
                var newLikeCount = updatedComment.interactionCounts.like
                var newDislikeCount = updatedComment.interactionCounts.dislike
                var newUserInteraction: [Comment.UserInteraction]? = updatedComment.userInteraction ?? []

                if hasDisliked {
                    // Undislike: remove dislike
                    newDislikeCount -= 1
                    newUserInteraction?.removeAll { $0 == .dislike }
                } else if hasLiked {
                    // Switch from like to dislike
                    newLikeCount -= 1
                    newDislikeCount += 1
                    newUserInteraction?.removeAll { $0 == .like }
                    newUserInteraction?.append(.dislike)
                } else {
                    // Add dislike
                    newDislikeCount += 1
                    newUserInteraction?.append(.dislike)
                }

                updatedComment.interactionCounts = CommentV3PostResponseInteractionCounts(
                    like: newLikeCount,
                    dislike: newDislikeCount
                )
                updatedComment.userInteraction = newUserInteraction?.isEmpty == true ? nil : newUserInteraction
            }
        }

        // Call API in background
        do {
            _ = try await api.dislikeComment(commentId: comment.id, blogPostId: post.id)
        } catch {
            // Rollback on failure
            await MainActor.run {
                comments = previousComments
                errorMessage = "Failed to update dislike: \(error.localizedDescription)"
            }
        }
    }

    // MARK: - Bottom Comment Input

    private var bottomCommentInput: some View {
        HStack(spacing: 12) {
            TextField("Add a comment...", text: $newCommentText, axis: .vertical)
                .textFieldStyle(.plain)
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                .lineLimit(1...4)
                .disabled(isPostingComment)
                .foregroundColor(.primary)
                .focused($isCommentFieldFocused)

            // X button to dismiss keyboard (shown when focused or has text)
            if isCommentFieldFocused || !newCommentText.isEmpty {
                Button {
                    newCommentText = ""
                    isCommentFieldFocused = false
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title2)
                        .foregroundColor(.secondary)
                }
                .padding(.trailing, !newCommentText.isEmpty ? 0 : 12)
            }

            // Send button (only shown when text is not empty)
            if !newCommentText.isEmpty {
                Button {
                    Task {
                        await postComment()
                    }
                } label: {
                    if isPostingComment {
                        ProgressView()
                            .tint(.floatplaneBlue)
                    } else {
                        Image(systemName: "arrow.up.circle.fill")
                            .font(.title2)
                            .foregroundColor(.floatplaneBlue)
                    }
                }
                .disabled(isPostingComment)
                .padding(.trailing, 12)
            }
        }
        .frame(minHeight: 48)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .padding(.horizontal, 16)
        .padding(.bottom, 12)
        .shadow(color: .black.opacity(0.2), radius: 12, y: -4)
    }

    // MARK: - Description Section

    private var descriptionSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Make description text tappable when collapsed
            Group {
                if !isDescriptionExpanded && post.text.count > 150 {
                    Button {
                        withAnimation {
                            isDescriptionExpanded = true
                        }
                    } label: {
                        Text(parsedDescription)
                            .font(.subheadline)
                            .foregroundColor(Color.adaptiveText)
                            .lineLimit(3)
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(PlainButtonStyle())
                    .environment(\.openURL, OpenURLAction { url in
                        // Handle timestamp seeking
                        if url.scheme == "floatplane", url.host == "seek",
                           let secondsString = url.pathComponents.last,
                           let seconds = Int(secondsString) {
                            // Seek video to timestamp
                            playerManager.seek(to: TimeInterval(seconds))
                            return .handled
                        }
                        // Let system handle other URLs
                        return .systemAction
                    })
                } else {
                    Text(parsedDescription)
                        .font(.subheadline)
                        .foregroundColor(Color.adaptiveText)
                        .lineLimit(isDescriptionExpanded ? nil : 3)
                        .environment(\.openURL, OpenURLAction { url in
                            // Handle timestamp seeking
                            if url.scheme == "floatplane", url.host == "seek",
                               let secondsString = url.pathComponents.last,
                               let seconds = Int(secondsString) {
                                // Seek video to timestamp
                                playerManager.seek(to: TimeInterval(seconds))
                                return .handled
                            }
                            // Let system handle other URLs
                            return .systemAction
                        })
                }
            }

            if post.text.count > 150 {
                Button {
                    withAnimation {
                        isDescriptionExpanded.toggle()
                    }
                } label: {
                    Text(isDescriptionExpanded ? "Show less" : "Show more")
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(.floatplaneBlue)
                }
            }
        }
    }

    // MARK: - HTML Parsing Helper

    private nonisolated static func htmlToAttributedString(_ html: String) -> AttributedString {
        guard let data = html.data(using: .utf8) else {
            return AttributedString(html)
        }

        let options: [NSAttributedString.DocumentReadingOptionKey: Any] = [
            .documentType: NSAttributedString.DocumentType.html,
            .characterEncoding: String.Encoding.utf8.rawValue
        ]

        if let nsAttributedString = try? NSAttributedString(data: data, options: options, documentAttributes: nil) {
            var attributedString = AttributedString(nsAttributedString)

            // Strip embedded HTML colors and apply adaptive color
            // This ensures text is white in dark mode, black in light mode
            for run in attributedString.runs {
                // Don't modify links - they should stay blue
                if run.link == nil {
                    attributedString[run.range].foregroundColor = Color.adaptiveText
                }
            }

            // Apply system font (sans-serif) to match app
            attributedString.font = .subheadline

            // Shorten long URLs while preserving links
            // Collect changes first to avoid mutation during iteration
            var urlChanges: [(range: Range<AttributedString.Index>, link: URL, shortened: String)] = []
            for run in attributedString.runs {
                if let link = run.link {
                    var linkText = String(attributedString[run.range].characters)

                    // Strip protocol (http:// or https://)
                    if linkText.hasPrefix("https://") {
                        linkText = String(linkText.dropFirst(8))
                    } else if linkText.hasPrefix("http://") {
                        linkText = String(linkText.dropFirst(7))
                    }

                    // Strip www. if present
                    if linkText.hasPrefix("www.") {
                        linkText = String(linkText.dropFirst(4))
                    }

                    // Truncate if longer than 40 characters
                    if linkText.count > 40 {
                        let shortened = String(linkText.prefix(40)) + "..."
                        urlChanges.append((range: run.range, link: link, shortened: shortened))
                    } else {
                        // Use cleaned URL even if not truncated
                        urlChanges.append((range: run.range, link: link, shortened: linkText))
                    }
                }
            }

            // Apply changes in reverse order to preserve indices
            for change in urlChanges.reversed() {
                var shortenedString = AttributedString(change.shortened)
                shortenedString.link = change.link
                shortenedString.foregroundColor = .blue
                attributedString.replaceSubrange(change.range, with: shortenedString)
            }

            // Detect and linkify timestamps (e.g., "3:05", "1:23:45")
            let fullText = String(attributedString.characters)
            let timestampPattern = #"\b(\d{1,2}):(\d{2})(?::(\d{2}))?\b"#

            if let regex = try? NSRegularExpression(pattern: timestampPattern) {
                let matches = regex.matches(in: fullText, range: NSRange(fullText.startIndex..., in: fullText))

                for match in matches.reversed() {
                    guard let range = Range(match.range, in: fullText) else { continue }
                    let timestampText = String(fullText[range])

                    // Parse timestamp to seconds
                    let components = timestampText.split(separator: ":")
                    var seconds = 0
                    if components.count == 2 {
                        // Format: M:SS or MM:SS
                        seconds = (Int(components[0]) ?? 0) * 60 + (Int(components[1]) ?? 0)
                    } else if components.count == 3 {
                        // Format: H:MM:SS
                        seconds = (Int(components[0]) ?? 0) * 3600 + (Int(components[1]) ?? 0) * 60 + (Int(components[2]) ?? 0)
                    }

                    // Create custom URL for timestamp seeking
                    let timestampURL = URL(string: "floatplane://seek/\(seconds)")!

                    // Find range in AttributedString and apply link
                    if let attrRange = attributedString.range(of: timestampText) {
                        attributedString[attrRange].link = timestampURL
                        attributedString[attrRange].foregroundColor = .blue
                    }
                }
            }

            return attributedString
        }

        return AttributedString(html)
    }

    // MARK: - Helpers

    private func formatDate(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .full
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}

#Preview {
    NavigationStack {
        VideoPlayerView(
            post: BlogPost(
                id: "1",
                guid: "guid-1",
                title: "Sample Video Title",
                text: "This is a sample description for the video.",
                type: .blogpost,
                channel: .typeString("channel-1"),
                tags: [],
                attachmentOrder: [],
                metadata: PostMetadataModel(
                    hasVideo: true,
                    videoCount: 1,
                    videoDuration: 600,
                    hasAudio: false,
                    audioCount: 0,
                    audioDuration: 0,
                    hasPicture: false,
                    pictureCount: 0,
                    hasGallery: false,
                    galleryCount: 0,
                    isFeatured: false
                ),
                releaseDate: Date(),
                likes: 42,
                dislikes: 2,
                score: 40,
                comments: 15,
                creator: BlogPostModelV3Creator(
                    id: "creator-1",
                    owner: BlogPostModelV3CreatorOwner(id: "owner-1", username: "creator"),
                    title: "Sample Creator",
                    urlname: "sample-creator",
                    description: "A sample creator",
                    about: "About the creator",
                    category: CreatorModelV3Category(id: "tech", title: "Technology"),
                    cover: nil,
                    icon: ImageModel(width: 100, height: 100, path: "/path/to/icon.jpg", childImages: nil),
                    liveStream: nil,
                    subscriptionPlans: [],
                    discoverable: true,
                    subscriberCountDisplay: "1.2K",
                    incomeDisplay: true,
                    defaultChannel: nil,
                    channels: nil,
                    card: nil
                ),
                wasReleasedSilently: false,
                thumbnail: ImageModel(width: 1920, height: 1080, path: "/path/to/thumbnail.jpg", childImages: nil),
                isAccessible: true,
                videoAttachments: ["sample-video-id"],
                audioAttachments: [],
                pictureAttachments: [],
                galleryAttachments: []
            )
        )
    }
}
