//
//  VideoFeedView.swift
//  FloatNative
//
//  Clean video feed with NavigationStack-based navigation
//  iOS 18+ native SwiftUI patterns
//

import SwiftUI

struct VideoFeedView: View {
    let initialFilter: FeedFilter?
    let preloadedPosts: [BlogPost]?
    let customTitle: String?
    let currentPlaylistId: String?
    let onPlaylistEmpty: (() -> Void)?

    @StateObject private var viewModel = HomeFeedViewModel()
    @StateObject private var api = FloatplaneAPI.shared
    @StateObject private var companionAPI = CompanionAPI.shared
    @EnvironmentObject var tabCoordinator: TabCoordinator
    @Environment(\.dismiss) var dismiss
    @Environment(\.scenePhase) var scenePhase
    
    // Mutable posts list when viewing a playlist (for optimistic removal)
    @State private var displayPosts: [BlogPost] = []

    // tvOS action sidebar state
    @State private var selectedPost: BlogPost?
    @State private var showActionSidebar = false
    @State private var sidebarPost: BlogPost?
    @FocusState private var sidebarFocused: Bool
    @FocusState private var playlistListFocused: Bool
    @FocusState private var focusedVideoId: String?
    @State private var previousFocusedVideoId: String?
    @State private var shouldAutoSetFocus = true
    @State private var isReturningFromNavigation = false

    // Sidebar Like/Dislike state (local tracking only)
    @State private var sidebarLikes: Int = 0
    @State private var sidebarDislikes: Int = 0
    @State private var sidebarHasLiked: Bool = false
    @State private var sidebarHasDisliked: Bool = false

    // Watch Later state
    @State private var isAddingToWatchLater = false
    @State private var watchLaterError: String?

    // Live Stream state
    @State private var selectedLiveCreator: Creator?

    // Playlist state
    enum SidebarViewState {
        case actionButtons
        case playlistList
    }
    @State private var sidebarViewState: SidebarViewState = .actionButtons
    @State private var playlists: [Playlist] = []
    @State private var playlistsWithVideo: Set<String> = []
    @State private var isLoadingPlaylists = false
    @State private var watchLaterPlaylistId: String?

    // Bookmark indicators: Set of all video IDs that exist in any playlist (for O(1) lookup)
    @State private var allPlaylistVideoIds: Set<String> = []

    // Watch progress tracking: Maps post IDs to progress values (0.0 to 1.0)
    @State private var progressData: [String: Double] = [:]

    // iOS Menu state
    @State private var menuSelectedPost: BlogPost?
    @State private var showPlaylistSheet = false
    @State private var isAddingToWatchLaterMenu = false
    @State private var menuPlaylistsWithVideo: Set<String> = []
    @State private var showCreatePlaylistFromMenu = false
    @State private var showToast = false

    init(filter: FeedFilter? = nil) {
        self.initialFilter = filter
        self.preloadedPosts = nil
        self.customTitle = nil
        self.currentPlaylistId = nil
        self.onPlaylistEmpty = nil
    }

    init(posts: [BlogPost], title: String?, playlistId: String? = nil, onPlaylistEmpty: (() -> Void)? = nil) {
        self.initialFilter = nil
        self.preloadedPosts = posts
        self.customTitle = title
        self.currentPlaylistId = playlistId
        self.onPlaylistEmpty = onPlaylistEmpty
    }

    // Computed property to get posts from either displayPosts (for playlists), preloaded, or viewModel
    private var postsToDisplay: [BlogPost] {
        // If viewing a playlist, use mutable displayPosts once it's populated
        // Fall back to preloadedPosts during initial load before displayPosts is set
        if currentPlaylistId != nil {
            // If displayPosts is empty, fall back to preloadedPosts (initial load)
            // Once displayPosts has data, use it exclusively (enables optimistic removal)
            if displayPosts.isEmpty, let preloadedPosts = preloadedPosts {
                return preloadedPosts
            }
            return displayPosts
        }
        // Otherwise use preloaded posts if available, or viewModel posts
        if let preloadedPosts = preloadedPosts {
            return preloadedPosts
        }
        return viewModel.posts
    }
    
    // Computed property for display title
    private var displayTitle: String {
        if let customTitle = customTitle {
            return customTitle
        }
        return "Videos"
    }

    // Grid columns: tvOS uses custom button wrappers with dynamic columns, iOS uses standard NavigationLink grid
    private var gridColumns: [GridItem] {
        #if os(tvOS)
        // tvOS: Dynamic column count based on sidebar state (uses TvOSLongPressCard for long-press support)
        // Full width: 4 columns for maximum content
        // Sidebar open: 3 columns to maintain larger thumbnail sizes
        let columnCount = showActionSidebar ? 3 : 4
        return Array(repeating: GridItem(.flexible(), spacing: 20), count: columnCount)
        #else
        // iOS: Standard adaptive grid (no custom wrappers, just NavigationLink)
        if UIDevice.current.userInterfaceIdiom == .pad {
            // iPad: Adaptive grid that fits optimal number of columns
            // With 16:9 thumbnails + metadata, ~320-380pt is optimal width per card
            // Portrait (~768pt - padding): 2 columns
            // Landscape (~1024pt - padding): 3 columns
            return [GridItem(.adaptive(minimum: 320, maximum: 400), spacing: 16)]
        } else {
            // iPhone: Single flexible column
            return [GridItem(.flexible())]
        }
        #endif
    }

    var body: some View {
        Group {
            #if os(tvOS)
            GeometryReader { geometry in
                ZStack {
                // Background
                Color.adaptiveBackground
                    .ignoresSafeArea()

                HStack(spacing: 0) {
                    // Main content area (grid)
                    VStack(spacing: 0) {
                        // Filter bar (if filtered) - above the grid
                        if viewModel.filter.isFiltered {
                            filterBar
                                .padding(.horizontal, 20)
                                .padding(.top, 0)
                                .padding(.bottom, 4)
                                .focusSection()
                        }

                        ScrollView {
                            LazyVGrid(columns: gridColumns, spacing: 25) {
                                // Live Stream Tile (First item if active)
                                if let liveCreator = viewModel.liveCreators.first, !viewModel.filter.isFiltered {
                                    #if os(tvOS)
                                    // Calculate column width with dynamic column count
                                    let gridWidth = showActionSidebar ? geometry.size.width * 0.7 : geometry.size.width
                                    let columnCount: CGFloat = showActionSidebar ? 3 : 4
                                    let horizontalPadding: CGFloat = 40
                                    let columnSpacing: CGFloat = 20 * (columnCount - 1)
                                    let totalSpacing = horizontalPadding + columnSpacing
                                    let columnWidth = (gridWidth - totalSpacing) / columnCount
                                    let thumbnailHeight = columnWidth / (16.0 / 9.0)
                                    let metadataHeight: CGFloat = 125
                                    let cardHeight = thumbnailHeight + metadataHeight

                                    Button {
                                        selectedLiveCreator = liveCreator
                                    } label: {
                                        LiveVideoCard(creator: liveCreator)
                                    }
                                    .buttonStyle(.card)
                                    .frame(width: columnWidth, height: cardHeight)
                                    #endif
                                }

                                ForEach(postsToDisplay) { post in
                                    #if os(tvOS)
                                    // Calculate column width with dynamic column count
                                    // Use outer geometry and account for sidebar state
                                    let gridWidth = showActionSidebar ? geometry.size.width * 0.7 : geometry.size.width
                                    let columnCount: CGFloat = showActionSidebar ? 3 : 4
                                    // Calculate total spacing: horizontal padding + gaps between columns
                                    let horizontalPadding: CGFloat = 40  // 20pt on each side
                                    let columnSpacing: CGFloat = 20 * (columnCount - 1)  // 20pt gaps between columns
                                    let totalSpacing = horizontalPadding + columnSpacing
                                    let columnWidth = (gridWidth - totalSpacing) / columnCount
                                    // Calculate card height: thumbnail + metadata section
                                    let thumbnailHeight = columnWidth / (16.0 / 9.0)
                                    // Metadata height (avatar 36 + padding 22 + title 28 + creator 16 + spacing 4 + buffer 19)
                                    let metadataHeight: CGFloat = 125
                                    let cardHeight = thumbnailHeight + metadataHeight

                                        TvOSLongPressCard(
                                            width: columnWidth,
                                            progress: progressData[post.id],
                                            onTap: {
                                                // Regular tap: Navigate to video
                                                // Save current focus to restore later and disable auto-focus
                                                shouldAutoSetFocus = false
                                                previousFocusedVideoId = post.id
                                                selectedPost = post
                                            },
                                            onLongPress: {
                                                // Long press: Show action sidebar
                                                // Save current focus to restore later and disable auto-focus
                                                shouldAutoSetFocus = false
                                                previousFocusedVideoId = focusedVideoId
                                                sidebarPost = post
                                                sidebarLikes = post.likes
                                                sidebarDislikes = post.dislikes
                                                sidebarHasLiked = false  // Start unknown
                                                sidebarHasDisliked = false
                                                showActionSidebar = true
                                                // Load playlists immediately to enable conditional Watch Later button
                                                Task { @MainActor in
                                                    await loadPlaylists()
                                                }
                                            }
                                        ) {
                                            VideoCard(
                                                post: post,
                                                isBookmarked: allPlaylistVideoIds.contains(post.id),
                                                progress: progressData[post.id]
                                            )
                                        }
                                        .frame(width: columnWidth, height: cardHeight)
                                        .buttonStyle(.card)
                                        .focused($focusedVideoId, equals: post.id)
                                        #else
                                        NavigationLink(value: post) {
                                            VideoCard(
                                                post: post,
                                                isBookmarked: allPlaylistVideoIds.contains(post.id),
                                                progress: progressData[post.id]
                                            )
                                        }
                                        .buttonStyle(PlainButtonStyle())
                                        #endif
                                    }

                                    // Loading more indicator (only if not using preloaded posts)
                                    if preloadedPosts == nil && viewModel.isLoadingMore {
                                        ProgressView()
                                            .gridCellColumns(gridColumns.count)
                                            .tint(.floatplaneBlue)
                                            .padding()
                                    }

                                    // Load more trigger (only if not using preloaded posts)
                                    if preloadedPosts == nil && !postsToDisplay.isEmpty && !viewModel.isLoadingMore {
                                        Color.clear
                                            .gridCellColumns(gridColumns.count)
                                            .frame(height: 1)
                                            .onAppear {
                                                Task {
                                                    await viewModel.loadMore()
                                                    // Fetch progress for newly loaded posts (async, non-blocking)
                                                    Task {
                                                        await fetchWatchProgress(for: viewModel.posts)
                                                    }
                                                }
                                            }
                                    }
                            }
                            .padding(.horizontal, 20)
                            .padding(.top, viewModel.filter.isFiltered ? 100 : 60)
                            .padding(.bottom, 40)
                            .focusSection()
                        }
                        .refreshable {
                            await Task.detached {
                                await viewModel.refresh()
                            }.value
                            // Fetch updated watch progress after refresh (async, non-blocking)
                            Task {
                                await fetchWatchProgress(for: viewModel.posts)
                            }
                        }
                    }
                    .frame(width: showActionSidebar ? geometry.size.width * 0.7 : geometry.size.width)
                    .animation(.easeInOut(duration: 0.3), value: showActionSidebar)

                    // Action Sidebar
                    if showActionSidebar {
                        actionSidebar
                            .frame(width: geometry.size.width * 0.3)
                            .transition(.move(edge: .trailing))
                    }
                }

                // Initial loading (only if not using preloaded posts)
                if preloadedPosts == nil && viewModel.isLoading && postsToDisplay.isEmpty {
                    ProgressView()
                        .tint(.floatplaneBlue)
                        .scaleEffect(1.5)
                }

                // Error state
                if let error = viewModel.errorMessage {
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.system(size: 50))
                            .foregroundStyle(.red)

                        Text(error)
                            .foregroundStyle(Color.adaptiveText)
                            .multilineTextAlignment(.center)

                        Button("Retry") {
                            Task {
                                await viewModel.loadFeed()
                            }
                        }
                        .buttonStyle(LiquidGlassButtonStyle(tint: .floatplaneBlue))
                    }
                    .padding()
                }
            }
            }
            .navigationTitle("")
            .toolbarBackground(.hidden, for: .navigationBar)
            .navigationDestination(item: $selectedPost) { post in
                VideoPlayerTvosView(post: post)
            }
            .navigationDestination(item: $selectedLiveCreator) { creator in
                LivePlayerTvosView(creator: creator)
            }
            .onChange(of: showActionSidebar) { _, isShowing in
                if isShowing {
                    // Auto-focus sidebar when it opens
                    sidebarFocused = true
                } else {
                    // Sidebar closing - restore focus to previously focused video
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: 150_000_000) // 0.15 seconds
                        focusedVideoId = previousFocusedVideoId
                        // Re-enable auto-focus after restoration
                        try? await Task.sleep(nanoseconds: 50_000_000) // 0.05 seconds
                        shouldAutoSetFocus = true
                    }
                }
            }
            .onChange(of: selectedPost) { oldPost, newPost in
                // When navigating to video player (selectedPost goes from nil to non-nil)
                if oldPost == nil && newPost != nil {
                    // Set flag to prevent refresh on return
                    isReturningFromNavigation = true
                }
                // When returning from video player (selectedPost goes from non-nil to nil)
                else if oldPost != nil && newPost == nil {
                    // Restore focus to previously opened video
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: 150_000_000) // 0.15 seconds
                        focusedVideoId = previousFocusedVideoId
                        // Re-enable auto-focus after restoration
                        try? await Task.sleep(nanoseconds: 50_000_000) // 0.05 seconds
                        shouldAutoSetFocus = true
                    }
                }
            }
            #else
            ZStack {
            // Background
            Color.adaptiveBackground
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Filter bar (if filtered) - above the grid
                if viewModel.filter.isFiltered {
                    filterBar
                        .padding(.horizontal, 16)
                        .padding(.top, 8)
                        .padding(.bottom, 12)
                }

                ScrollView {
                    // iOS: Use clean NavigationLink-based grid (no custom wrappers)
                    LazyVGrid(columns: gridColumns, spacing: 20) {
                        // Live Stream Tile (First item if active)
                        if let liveCreator = viewModel.liveCreators.first, !viewModel.filter.isFiltered {
                            Button {
                                selectedLiveCreator = liveCreator
                            } label: {
                                LiveVideoCard(creator: liveCreator)
                            }
                            .buttonStyle(PlainButtonStyle())
                        }

                        ForEach(postsToDisplay) { post in
                            NavigationLink(value: post) {
                                VideoCard(
                                    post: post,
                                    isBookmarked: allPlaylistVideoIds.contains(post.id),
                                    currentPlaylistId: currentPlaylistId,
                                    onMenuWatchLater: {
                                        Task { @MainActor in
                                            await handleMenuWatchLater(post: post)
                                        }
                                    },
                                    onMenuSaveToPlaylist: {
                                        menuSelectedPost = post
                                        showPlaylistSheet = true
                                    },
                                    onMenuRemoveFromPlaylist: {
                                        Task { @MainActor in
                                            await handleMenuRemoveFromPlaylist(post: post)
                                        }
                                    },
                                    onMenuMarkAsWatched: {
                                        Task { @MainActor in
                                            await markAsWatched(post: post)
                                        }
                                    },
                                    progress: progressData[post.id]
                                )
                            }
                            .buttonStyle(PlainButtonStyle())
                        }

                        // Loading more indicator
                        if viewModel.isLoadingMore {
                            ProgressView()
                                .gridCellColumns(gridColumns.count)
                                .tint(.floatplaneBlue)
                                .padding()
                        }

                        // Load more trigger (only if not using preloaded posts)
                        if preloadedPosts == nil && !postsToDisplay.isEmpty && !viewModel.isLoadingMore {
                            Color.clear
                                .gridCellColumns(gridColumns.count)
                                .frame(height: 1)
                                .onAppear {
                                    Task {
                                        await viewModel.loadMore()
                                        // Fetch progress for newly loaded posts (async, non-blocking)
                                        Task {
                                            await fetchWatchProgress(for: viewModel.posts)
                                        }
                                    }
                                }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical)
                }
                .refreshable {
                    await Task.detached {
                        await viewModel.refresh()
                    }.value
                    // Fetch updated watch progress after refresh (async, non-blocking)
                    Task {
                        await fetchWatchProgress(for: viewModel.posts)
                    }
                }
            }

            // Initial loading (only if not using preloaded posts)
            if preloadedPosts == nil && viewModel.isLoading && postsToDisplay.isEmpty {
                ProgressView()
                    .tint(.floatplaneBlue)
                    .scaleEffect(1.5)
            }

            // Error state
            if let error = viewModel.errorMessage {
                VStack(spacing: 16) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.system(size: 50))
                        .foregroundStyle(.red)

                    Text(error)
                        .foregroundStyle(Color.adaptiveText)
                        .multilineTextAlignment(.center)

                    Button("Retry") {
                        Task {
                            await viewModel.loadFeed()
                        }
                    }
                    .buttonStyle(LiquidGlassButtonStyle(tint: .floatplaneBlue))
                }
                .padding()
            }
            }
            .navigationTitle(displayTitle)
            .toolbarBackground(.visible, for: .navigationBar)
            .navigationDestination(item: $selectedLiveCreator) { creator in
                LivePlayerView(creator: creator)
            }
            .sheet(isPresented: $showPlaylistSheet, onDismiss: {
                // Reset state when sheet is dismissed
                menuSelectedPost = nil
                menuPlaylistsWithVideo = []
            }) {
                playlistPickerSheet
                    .presentationDetents([.fraction(0.3), .medium])
                    .presentationDragIndicator(.visible)
            }
            #endif
        }

        .globalMenu()
        .task {
            // Sync displayPosts from preloadedPosts if viewing a playlist
            // Use syncDisplayPosts to handle cases where preloadedPosts might be empty initially
            syncDisplayPosts()

            // Load playlists for bookmark indicators
            await loadPlaylistsForBookmarks()

            // Only load from API if we don't have preloaded posts AND we're not returning from navigation
            if preloadedPosts == nil && !isReturningFromNavigation {
                if let filter = initialFilter {
                    await viewModel.loadFeed(filter: filter)
                } else {
                    await viewModel.loadFeed()
                }

                // Fetch watch progress for loaded posts (async, non-blocking)
                Task {
                    await fetchWatchProgress(for: viewModel.posts)
                }
            } else if currentPlaylistId != nil {
                // If we have preloadedPosts and it's a playlist, keep syncing until posts are loaded
                // This handles the case where PlaylistsView loads posts asynchronously
                var attempts = 0
                while displayPosts.isEmpty && preloadedPosts?.isEmpty == false && attempts < 10 {
                    attempts += 1
                    try? await Task.sleep(nanoseconds: 100_000_000) // 0.1 seconds
                    syncDisplayPosts()
                    if !displayPosts.isEmpty {
                        break
                    }
                }

                // Fetch watch progress for preloaded posts (async, non-blocking)
                if let posts = preloadedPosts {
                    Task {
                        await fetchWatchProgress(for: posts)
                    }
                }
            }

            // Reset navigation flag after task completes
            isReturningFromNavigation = false
        }
        .onAppear {
            // Sync displayPosts on appear - this runs when the view appears and can catch updates
            syncDisplayPosts()

            #if os(tvOS)
            // Focus first video when appearing with playlist content (only if auto-focus is enabled)
            if shouldAutoSetFocus && currentPlaylistId != nil, !postsToDisplay.isEmpty {
                Task {
                    try? await Task.sleep(nanoseconds: 50_000_000) // 0.05 seconds
                    focusedVideoId = postsToDisplay.first?.id
                }
            }
            #endif
        }
        .onChange(of: preloadedPosts) { oldPosts, newPosts in
            // Sync displayPosts when preloadedPosts array reference changes
            // This happens when PlaylistsView updates playlistPosts and creates a new VideoFeedView
            if currentPlaylistId != nil {
                syncDisplayPosts()

                #if os(tvOS)
                // Set focus when posts first load (transition from empty to populated, only if auto-focus is enabled)
                if shouldAutoSetFocus && (oldPosts?.isEmpty ?? true) && !(newPosts?.isEmpty ?? true) {
                    Task {
                        try? await Task.sleep(nanoseconds: 50_000_000) // 0.05 seconds
                        focusedVideoId = postsToDisplay.first?.id
                    }
                }
                #endif
            }
        }
        .onChange(of: scenePhase) { oldPhase, newPhase in
            // Auto-refresh when app returns to foreground if enough time has passed (only if not using preloaded posts)
            if preloadedPosts == nil && oldPhase != .active && newPhase == .active {
                Task {
                    await viewModel.refreshIfNeeded()
                    // Fetch updated watch progress after foreground refresh (async, non-blocking)
                    Task {
                        await fetchWatchProgress(for: viewModel.posts)
                    }
                }
            }
        }
        .onChange(of: displayPosts) { oldPosts, newPosts in
            // Detect when playlist becomes empty (on tvOS only, when viewing a playlist)
            #if os(tvOS)
            if currentPlaylistId != nil && !oldPosts.isEmpty && newPosts.isEmpty {
                // Playlist is now empty - navigate back to playlist list
                onPlaylistEmpty?()
            }
            #endif
        }
        #if !os(tvOS)
        .toast(
            isPresented: $showToast,
            message: "Saved to Watch Later",
            icon: "clock.fill"
        )
        #endif
    }

    // MARK: - Action Sidebar (tvOS)

    #if os(tvOS)
    private var actionSidebar: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let post = sidebarPost {
                // Thumbnail
                if let thumbnail = post.thumbnail {
                    CachedAsyncImage_Phase(url: thumbnail.fullURL) { phase in
                        switch phase {
                        case .success(let image):
                            image
                                .resizable()
                                .scaledToFill()
                        default:
                            Rectangle()
                                .fill(Color.floatplaneGray.opacity(0.3))
                        }
                    }
                    .frame(height: 200)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal, 20)
                    .padding(.top, 40)
                }

                // Title
                Text(post.title)
                    .font(.title3)
                    .fontWeight(.semibold)
                    .foregroundStyle(Color.adaptiveText)
                    .lineLimit(3)
                    .multilineTextAlignment(.leading)
                    .padding(.horizontal, 20)
                    .padding(.top, 16)

                // Creator info
                HStack(spacing: 12) {
                    // Creator avatar
                    if let creatorIcon = post.creator.icon.fullURL {
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

                    Text(post.creator.title)
                        .font(.subheadline)
                        .foregroundStyle(Color.adaptiveSecondaryText)
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)

                // Conditional content based on sidebar state
                if sidebarViewState == .playlistList {
                    playlistListView
                        .padding(.horizontal, 20)
                        .padding(.top, 24)
                        .focusSection()
                } else {
                    // Action buttons
                    VStack(spacing: 12) {
                        ActionPillButton(
                            icon: "play.fill",
                            label: "Play",
                            isPrimary: true,
                            action: {
                                // Close sidebar and navigate
                                showActionSidebar = false
                                selectedPost = post
                            }
                        )
                        .focused($sidebarFocused)

                        ActionPillButton(
                            icon: sidebarHasLiked ? "hand.thumbsup.fill" : "hand.thumbsup",
                            label: "Like (\(sidebarLikes))",
                            action: {
                                Task { @MainActor in
                                    await handleSidebarLike()
                                }
                            }
                        )

                        ActionPillButton(
                            icon: sidebarHasDisliked ? "hand.thumbsdown.fill" : "hand.thumbsdown",
                            label: "Dislike (\(sidebarDislikes))",
                            action: {
                                Task { @MainActor in
                                    await handleSidebarDislike()
                                }
                            }
                        )

                        ActionPillButton(
                            icon: (watchLaterPlaylistId != nil && playlistsWithVideo.contains(watchLaterPlaylistId!)) ? "clock.fill" : "clock",
                            label: {
                                if isAddingToWatchLater {
                                    return "Adding..."
                                } else if let watchLaterId = watchLaterPlaylistId, playlistsWithVideo.contains(watchLaterId) {
                                    return "Remove from Watch Later"
                                } else {
                                    return "Save to Watch Later"
                                }
                            }(),
                            action: {
                                Task { @MainActor in
                                    await handleSidebarWatchLater()
                                }
                            }
                        )
                        .disabled(isAddingToWatchLater)

                        ActionPillButton(
                            icon: "checkmark.circle",
                            label: "Mark as Watched",
                            action: {
                                Task { @MainActor in
                                    await markAsWatched(post: post)
                                }
                            }
                        )

                        // Remove from current playlist (only show when viewing a playlist)
                        if let currentPlaylistId = currentPlaylistId {
                            ActionPillButton(
                                icon: "minus.circle",
                                label: "Remove from Playlist",
                                action: {
                                    Task { @MainActor in
                                        // Load playlists if not already loaded
                                        if playlists.isEmpty {
                                            await loadPlaylists()
                                        }
                                        // Find current playlist and remove video from it
                                        if let currentPlaylist = playlists.first(where: { $0.id == currentPlaylistId }) {
                                            await togglePlaylistMembership(playlist: currentPlaylist)
                                            // Close sidebar after removal
                                            showActionSidebar = false
                                        }
                                    }
                                }
                            )
                        }

                        ActionPillButton(
                            icon: "text.badge.plus",
                            label: "Save to Playlist",
                            action: {
                                // Playlists are already loaded when sidebar opens
                                // Just switch to playlist list view
                                sidebarViewState = .playlistList
                            }
                        )
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 24)
                    .focusSection()
                }

                Spacer()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.adaptiveBackground)
        .onExitCommand {
            // Handle back navigation: playlist list -> action buttons -> close sidebar
            if sidebarViewState == .playlistList {
                sidebarViewState = .actionButtons
            } else {
                showActionSidebar = false
                sidebarViewState = .actionButtons // Reset state when closing
            }
        }
        .onChange(of: showActionSidebar) { _, isShowing in
            if !isShowing {
                // Reset to action buttons when sidebar closes
                sidebarViewState = .actionButtons
                playlistListFocused = false
            } else {
                // Auto-focus sidebar when it opens
                sidebarFocused = true
            }
        }
        .onChange(of: sidebarViewState) { _, newState in
            if newState == .actionButtons {
                // When returning to action buttons, focus the Play button
                // Use a small delay to ensure view is ready
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 50_000_000) // 0.05 seconds
                    sidebarFocused = true
                    playlistListFocused = false
                }
            } else if newState == .playlistList {
                // Reset sidebar focus when entering playlist list
                sidebarFocused = false
                // Set focus to first playlist
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 50_000_000) // 0.05 seconds
                    playlistListFocused = true
                }
            }
        }
    }
    #endif

    // MARK: - Filter Bar

    private var filterBar: some View {
        HStack(spacing: 12) {
            // Filter icon
            Image(systemName: "line.3.horizontal.decrease.circle.fill")
                .foregroundColor(.floatplaneBlue)
                .font(.title3)

            // Channel/Creator icon and name
            if let icon = viewModel.filter.icon, let iconURL = icon.fullURL {
                CachedAsyncImage(url: iconURL) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } placeholder: {
                    Circle()
                        .fill(Color.floatplaneGray.opacity(0.3))
                }
                .frame(width: 28, height: 28)
                .clipShape(Circle())
            }

            Text(viewModel.filter.displayName)
                .font(.subheadline)
                .fontWeight(.medium)
                .foregroundColor(Color.adaptiveText)

            Spacer()

            // Clear button - return to Creators list
            Button {
                dismiss()
            } label: {
                Text("Clear")
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(.floatplaneBlue)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.floatplaneGray.opacity(0.2))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    // MARK: - Playlist Picker Sheet (iOS)

    #if !os(tvOS)
    private var playlistPickerSheet: some View {
        NavigationView {
            VStack(spacing: 0) {
                if isLoadingPlaylists {
                    ProgressView()
                        .tint(.floatplaneBlue)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if playlists.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "tray")
                            .font(.system(size: 50))
                            .foregroundStyle(Color.adaptiveSecondaryText)
                        Text("No playlists found")
                            .font(.subheadline)
                            .foregroundStyle(Color.adaptiveSecondaryText)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        ForEach(playlists) { playlist in
                            Button {
                                Task { @MainActor in
                                    await togglePlaylistFromMenu(playlist: playlist)
                                }
                            } label: {
                                HStack(spacing: 12) {
                                    Text(playlist.name)
                                        .font(.body)
                                        .foregroundStyle(Color.adaptiveText)

                                    Spacer()

                                    // Bookmark indicator
                                    Image(systemName: menuPlaylistsWithVideo.contains(playlist.id) ? "bookmark.fill" : "bookmark")
                                        .font(.body)
                                        .foregroundStyle(Color.adaptiveText)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Save to Playlist")
            #if !os(tvOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        showCreatePlaylistFromMenu = true
                    } label: {
                        Label("New Playlist", systemImage: "plus")
                    }
                }

                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        showPlaylistSheet = false
                    }
                }
            }
            .sheet(isPresented: $showCreatePlaylistFromMenu) {
                CreatePlaylistView { name in
                    await handleCreatePlaylistFromMenu(name: name)
                }
                .presentationDetents([.fraction(0.3), .medium])
                .presentationDragIndicator(.visible)
            }
            .task {
                await loadPlaylistsForMenu()
            }
        }
    }
    #endif

    // MARK: - Sidebar Like/Dislike Handlers

    private func handleSidebarLike() async {
        guard let post = sidebarPost else { return }

        // Save state for rollback
        let wasLiked = sidebarHasLiked
        let wasDisliked = sidebarHasDisliked
        let prevLikes = sidebarLikes
        let prevDislikes = sidebarDislikes

        // Optimistic update: toggle like
        if sidebarHasLiked {
            // Unliking
            sidebarHasLiked = false
            sidebarLikes -= 1
        } else {
            // Liking (remove dislike if present)
            if sidebarHasDisliked {
                sidebarHasDisliked = false
                sidebarDislikes -= 1
            }
            sidebarHasLiked = true
            sidebarLikes += 1
        }

        // API call
        do {
            _ = try await api.likeContent(contentType: "blogPost", id: post.id)
        } catch {
            // Rollback on failure
            sidebarHasLiked = wasLiked
            sidebarHasDisliked = wasDisliked
            sidebarLikes = prevLikes
            sidebarDislikes = prevDislikes
            print("❌ Failed to like post: \(error)")
        }
    }

    private func handleSidebarDislike() async {
        guard let post = sidebarPost else { return }

        // Save state for rollback
        let wasLiked = sidebarHasLiked
        let wasDisliked = sidebarHasDisliked
        let prevLikes = sidebarLikes
        let prevDislikes = sidebarDislikes

        // Optimistic update: toggle dislike
        if sidebarHasDisliked {
            // Undisliking
            sidebarHasDisliked = false
            sidebarDislikes -= 1
        } else {
            // Disliking (remove like if present)
            if sidebarHasLiked {
                sidebarHasLiked = false
                sidebarLikes -= 1
            }
            sidebarHasDisliked = true
            sidebarDislikes += 1
        }

        // API call
        do {
            _ = try await api.dislikeContent(contentType: "blogPost", id: post.id)
        } catch {
            // Rollback on failure
            sidebarHasLiked = wasLiked
            sidebarHasDisliked = wasDisliked
            sidebarLikes = prevLikes
            sidebarDislikes = prevDislikes
            print("❌ Failed to dislike post: \(error)")
        }
    }

    private func handleSidebarWatchLater() async {
        guard let watchLaterId = watchLaterPlaylistId else {
            print("❌ Watch Later playlist not found")
            return
        }

        // Find Watch Later playlist
        guard let watchLaterPlaylist = playlists.first(where: { $0.id == watchLaterId }) else {
            print("❌ Watch Later playlist not found in playlists array")
            return
        }

        isAddingToWatchLater = true
        watchLaterError = nil

        // Use existing togglePlaylistMembership to add/remove from Watch Later
        await togglePlaylistMembership(playlist: watchLaterPlaylist)

        isAddingToWatchLater = false
    }

    #if os(tvOS)
    // MARK: - Playlist List View

    private var playlistListView: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Title
            Text("Save to...")
                .font(.title2)
                .fontWeight(.semibold)
                .foregroundStyle(Color.adaptiveText)
                .padding(.bottom, 24)

            if isLoadingPlaylists {
                ProgressView()
                    .tint(.floatplaneBlue)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 40)
            } else if playlists.isEmpty {
                Text("No playlists found")
                    .font(.subheadline)
                    .foregroundStyle(Color.adaptiveSecondaryText)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 40)
            } else {
                ScrollView {
                    VStack(spacing: 12) {
                        ForEach(Array(playlists.enumerated()), id: \.element.id) { index, playlist in
                            playlistRow(playlist: playlist, isFirst: index == 0)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                }
            }
        }
    }

    @ViewBuilder
    private func playlistRow(playlist: Playlist, isFirst: Bool = false) -> some View {
        let button = ActionPillButton(
            icon: playlistsWithVideo.contains(playlist.id) ? "bookmark.fill" : "bookmark",
            label: playlist.name,
            action: {
                Task { @MainActor in
                    await togglePlaylistMembership(playlist: playlist)
                }
            }
        )

        if isFirst {
            button.focused($playlistListFocused)
        } else {
            button
        }
    }
    #endif

    // MARK: - Display Posts Sync

    private func syncDisplayPosts() {
        guard let preloadedPosts = preloadedPosts, currentPlaylistId != nil else {
            return
        }
        
        // If displayPosts is empty or has fewer items than preloadedPosts, sync it
        // This handles the case where preloadedPosts gets populated after displayPosts was initialized empty
        if displayPosts.isEmpty || (preloadedPosts.count > displayPosts.count && !preloadedPosts.isEmpty) {
            // Only sync if we're not in the middle of an optimistic removal
            // Check if all items in preloadedPosts are in displayPosts (or displayPosts is empty)
            let allPreloadedInDisplay = displayPosts.isEmpty || preloadedPosts.allSatisfy { preloadedPost in
                displayPosts.contains(where: { $0.id == preloadedPost.id })
            }
            
            if allPreloadedInDisplay || displayPosts.isEmpty {
                // Safe to sync: either displayPosts is empty or all preloaded items are already there
                // This means we haven't optimistically removed anything yet
                displayPosts = preloadedPosts
            } else {
                // displayPosts has items that aren't in preloadedPosts - might be mid-removal
                // Merge: keep displayPosts items that are in preloadedPosts, add any new items from preloadedPosts
                let existingIds = Set(displayPosts.map { $0.id })
                let preloadedIds = Set(preloadedPosts.map { $0.id })
                
                // Keep items that are in both, add new items from preloadedPosts
                let merged = displayPosts.filter { preloadedIds.contains($0.id) } +
                             preloadedPosts.filter { !existingIds.contains($0.id) }
                
                // Sort to maintain playlist order (use preloadedPosts order as reference)
                let sorted = preloadedPosts.compactMap { preloadedPost in
                    merged.first { $0.id == preloadedPost.id }
                }
                
                displayPosts = sorted
            }
        }
    }

    // MARK: - Bookmark Indicator Helpers

    private func loadPlaylistsForBookmarks() async {
        do {
            let loadedPlaylists = try await companionAPI.getPlaylists(includeWatchLater: true)

            // Create efficient lookup set of all video IDs in any playlist
            allPlaylistVideoIds = Set(loadedPlaylists.flatMap { $0.videoIds })
        } catch {
            // Fail silently - bookmarks just won't show
        }
    }

    // MARK: - Watch Progress Helpers

    /// Mark a video as fully watched by updating progress to full duration
    private func markAsWatched(post: BlogPost) async {
        guard post.metadata.hasVideo else {
            return
        }

        let durationSeconds = Int(post.metadata.videoDuration)

        // Optimistically update local state
        await MainActor.run {
            progressData[post.id] = 1.0
        }

        do {
            // Update progress on server - use video attachment ID if available
            if let videoAttachmentId = post.videoAttachments?.first {
                _ = try await api.updateProgress(
                    videoId: videoAttachmentId,
                    contentType: "video",
                    progress: durationSeconds
                )
            }
        } catch {
            // Silently fail - keep optimistic update
            print("Failed to mark as watched: \(error)")
        }
    }

    /// Fetch watch progress for posts with retry logic (async, non-blocking)
    /// Retries up to 2 times with 3 second delays, then silently fails
    private func fetchWatchProgress(for posts: [BlogPost]) async {
        // Filter to only posts with videos
        let videoPosts = posts.filter { $0.metadata.hasVideo }

        guard !videoPosts.isEmpty else {
            return
        }

        let postIds = videoPosts.map { $0.id }

        // Batch the post IDs (20 per batch to stay under API limit)
        let batchSize = 20
        let batches = stride(from: 0, to: postIds.count, by: batchSize).map {
            Array(postIds[$0..<min($0 + batchSize, postIds.count)])
        }

        var allProgressResponses: [ProgressResponse] = []

        // Fetch each batch
        for (_, batchIds) in batches.enumerated() {
            var retryCount = 0
            let maxRetries = 2

            while retryCount <= maxRetries {
                do {
                    let progressResponses = try await api.getProgress(postIds: batchIds, contentType: "blogPost")
                    allProgressResponses.append(contentsOf: progressResponses)
                    break  // Success - move to next batch

                } catch {
                    retryCount += 1

                    if retryCount <= maxRetries {
                        try? await Task.sleep(nanoseconds: 3_000_000_000)
                    }
                }
            }
        }

        // Convert progress from percentage (0-100) to decimal (0.0-1.0)
        var newProgressData: [String: Double] = [:]
        for response in allProgressResponses {
            if let _ = videoPosts.first(where: { $0.id == response.id }) {
                let progressPercent = Double(response.progress)  // API returns 0-100

                // Convert from 0-100 range to 0.0-1.0 range
                let progressPercentage = min(progressPercent / 100.0, 1.0)

                newProgressData[response.id] = progressPercentage
            }
        }

        // Update state on main actor
        await MainActor.run {
            // Merge with existing progress data
            progressData.merge(newProgressData) { (_, new) in new }
        }
    }

    // MARK: - Playlist Handlers

    private func loadPlaylists() async {
        guard let post = sidebarPost else { return }

        isLoadingPlaylists = true

        do {
            let loadedPlaylists = try await companionAPI.getPlaylists(includeWatchLater: true)
            playlists = loadedPlaylists

            // Find and store Watch Later playlist ID
            watchLaterPlaylistId = loadedPlaylists.first(where: { $0.isWatchLater })?.id

            // Determine which playlists contain this video
            playlistsWithVideo = Set(loadedPlaylists.filter { playlist in
                playlist.videoIds.contains(post.id)
            }.map { $0.id })

            // Set focus to first playlist after loading completes (only when in playlist list view)
            if !loadedPlaylists.isEmpty && sidebarViewState == .playlistList {
                // Small delay to ensure view is ready
                try? await Task.sleep(nanoseconds: 50_000_000) // 0.05 seconds
                playlistListFocused = true
            }
        } catch {
            print("❌ Failed to load playlists: \(error)")
            // Keep existing playlists on error
        }

        isLoadingPlaylists = false
    }

    private func togglePlaylistMembership(playlist: Playlist) async {
        // Use sidebarPost (tvOS sidebar) or menuSelectedPost (iOS menu) - whichever is set
        guard let post = sidebarPost ?? menuSelectedPost else {
            return
        }

        // Determine context: tvOS sidebar uses playlistsWithVideo, iOS menu uses menuPlaylistsWithVideo
        let isSidebarContext = sidebarPost != nil
        let isCurrentlyInPlaylist = isSidebarContext
            ? playlistsWithVideo.contains(playlist.id)
            : menuPlaylistsWithVideo.contains(playlist.id)

        let playlistIndex = playlists.firstIndex { $0.id == playlist.id }
        let isCurrentPlaylist = currentPlaylistId == playlist.id

        // Store original state for rollback if removing from current playlist
        let originalDisplayPosts = isCurrentPlaylist ? displayPosts : nil
        let removedPostIndex = isCurrentPlaylist && isCurrentlyInPlaylist ? displayPosts.firstIndex(where: { $0.id == post.id }) : nil

        // Optimistic update for playlist membership tracking
        if isCurrentlyInPlaylist {
            if isSidebarContext {
                playlistsWithVideo.remove(playlist.id)
            } else {
                menuPlaylistsWithVideo.remove(playlist.id)
            }
        } else {
            if isSidebarContext {
                playlistsWithVideo.insert(playlist.id)
            } else {
                menuPlaylistsWithVideo.insert(playlist.id)
            }
        }

        // If removing from currently viewed playlist, optimistically remove from display
        if isCurrentlyInPlaylist && isCurrentPlaylist {
            // Optimistically remove from displayPosts
            displayPosts.removeAll { $0.id == post.id }
        }

        // API call
        do {
            let updatedPlaylist: Playlist
            if isCurrentlyInPlaylist {
                updatedPlaylist = try await companionAPI.removeFromPlaylist(playlistId: playlist.id, videoId: post.id)
            } else {
                updatedPlaylist = try await companionAPI.addToPlaylist(playlistId: playlist.id, videoId: post.id)
            }

            // Update the playlist in our array with the server's response
            if let index = playlistIndex {
                playlists[index] = updatedPlaylist
            }

            // If removing from current playlist, confirm removal by syncing displayPosts with server response
            if isCurrentlyInPlaylist && isCurrentPlaylist {
                // Update displayPosts to match server response (filter out removed video)
                displayPosts = displayPosts.filter { updatedPlaylist.videoIds.contains($0.id) }
            }

            // Update bookmark indicator state
            if isCurrentlyInPlaylist {
                // Video was removed - check if it still exists in any other playlist
                let stillInAnyPlaylist = playlists.contains { $0.videoIds.contains(post.id) }
                if !stillInAnyPlaylist {
                    allPlaylistVideoIds.remove(post.id)
                }
            } else {
                // Video was added - add to bookmark indicators
                allPlaylistVideoIds.insert(post.id)
            }
        } catch {
            // Rollback optimistic updates on failure
            if isCurrentlyInPlaylist {
                if isSidebarContext {
                    playlistsWithVideo.insert(playlist.id)
                } else {
                    menuPlaylistsWithVideo.insert(playlist.id)
                }
                // Restore post to displayPosts if it was removed from current playlist
                if isCurrentPlaylist, let originalPosts = originalDisplayPosts, let _ = removedPostIndex {
                    // Restore original displayPosts to maintain order
                    displayPosts = originalPosts
                }
            } else {
                if isSidebarContext {
                    playlistsWithVideo.remove(playlist.id)
                } else {
                    menuPlaylistsWithVideo.remove(playlist.id)
                }
            }
            print("❌ Failed to \(isCurrentlyInPlaylist ? "remove from" : "add to") playlist: \(error)")
        }
    }

    // MARK: - iOS Menu Helpers

    #if !os(tvOS)
    private func loadPlaylistsForMenu() async {
        guard let post = menuSelectedPost else { return }

        isLoadingPlaylists = true

        do {
            let loadedPlaylists = try await companionAPI.getPlaylists(includeWatchLater: true)
            playlists = loadedPlaylists

            // Determine which playlists contain this video
            menuPlaylistsWithVideo = Set(loadedPlaylists.filter { playlist in
                playlist.videoIds.contains(post.id)
            }.map { $0.id })
        } catch {
            print("❌ Failed to load playlists for menu: \(error)")
            // Keep existing playlists on error
        }

        isLoadingPlaylists = false
    }

    private func handleMenuWatchLater(post: BlogPost) async {
        isAddingToWatchLaterMenu = true

        do {
            _ = try await companionAPI.addToWatchLater(videoId: post.id)
            // Update bookmark indicator
            allPlaylistVideoIds.insert(post.id)
            // Show success toast
            withAnimation {
                showToast = true
            }
        } catch {
            print("❌ Failed to add to Watch Later from menu: \(error)")
        }

        isAddingToWatchLaterMenu = false
    }

    private func handleMenuRemoveFromPlaylist(post: BlogPost) async {
        guard let currentPlaylistId = currentPlaylistId else {
            return
        }

        // Set menuSelectedPost BEFORE loading playlists
        // (loadPlaylistsForMenu needs menuSelectedPost to be set)
        let previousPost = menuSelectedPost
        menuSelectedPost = post

        // Load playlists if not already loaded
        if playlists.isEmpty {
            await loadPlaylistsForMenu()
        }

        // Find current playlist and remove video from it
        if let currentPlaylist = playlists.first(where: { $0.id == currentPlaylistId }) {
            await togglePlaylistMembership(playlist: currentPlaylist)
            menuSelectedPost = previousPost
        }
    }

    private func togglePlaylistFromMenu(playlist: Playlist) async {
        guard let post = menuSelectedPost else { return }

        let isCurrentlyInPlaylist = menuPlaylistsWithVideo.contains(playlist.id)
        let playlistIndex = playlists.firstIndex { $0.id == playlist.id }

        // Optimistic update for playlist membership tracking
        if isCurrentlyInPlaylist {
            menuPlaylistsWithVideo.remove(playlist.id)
        } else {
            menuPlaylistsWithVideo.insert(playlist.id)
        }

        // API call
        do {
            let updatedPlaylist: Playlist
            if isCurrentlyInPlaylist {
                updatedPlaylist = try await companionAPI.removeFromPlaylist(playlistId: playlist.id, videoId: post.id)
            } else {
                updatedPlaylist = try await companionAPI.addToPlaylist(playlistId: playlist.id, videoId: post.id)
            }

            // Update the playlist in our array with the server's response
            if let index = playlistIndex {
                playlists[index] = updatedPlaylist
            }

            // Update bookmark indicator state
            if isCurrentlyInPlaylist {
                // Video was removed - check if it still exists in any other playlist
                let stillInAnyPlaylist = playlists.contains { $0.videoIds.contains(post.id) }
                if !stillInAnyPlaylist {
                    allPlaylistVideoIds.remove(post.id)
                }
            } else {
                // Video was added - add to bookmark indicators
                allPlaylistVideoIds.insert(post.id)
            }
        } catch {
            // Rollback optimistic update on failure
            if isCurrentlyInPlaylist {
                menuPlaylistsWithVideo.insert(playlist.id)
            } else {
                menuPlaylistsWithVideo.remove(playlist.id)
            }
            print("❌ Failed to \(isCurrentlyInPlaylist ? "remove from" : "add to") playlist from menu: \(error)")
        }
    }

    private func handleCreatePlaylistFromMenu(name: String) async {
        do {
            // Call API to create playlist
            let newPlaylist = try await companionAPI.createPlaylist(name: name)

            // Insert new playlist at the beginning of the array
            playlists.insert(newPlaylist, at: 0)

            // Update menuPlaylistsWithVideo if the current video should be marked
            if let post = menuSelectedPost {
                // Check if this new playlist contains the video (it shouldn't for a new playlist)
                if newPlaylist.videoIds.contains(post.id) {
                    menuPlaylistsWithVideo.insert(newPlaylist.id)
                }
            }
        } catch {
            print("❌ Failed to create playlist from menu: \(error)")
        }
    }
    #endif
}

// MARK: - Video Card

struct VideoCard: View {
    let post: BlogPost
    let isBookmarked: Bool
    var currentPlaylistId: String? = nil
    var onMenuWatchLater: (() -> Void)? = nil
    var onMenuSaveToPlaylist: (() -> Void)? = nil
    var onMenuRemoveFromPlaylist: (() -> Void)? = nil
    var onMenuMarkAsWatched: (() -> Void)? = nil
    var progress: Double? = nil  // Watch progress (0.0 to 1.0)

    // Livestream detection
    private var isLivestream: Bool {
        post.isLivestream
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Thumbnail with duration
            Color.clear
                #if os(tvOS)
                .aspectRatio(16/9, contentMode: .fill)  // tvOS: fill for custom tile sizing
                #else
                .aspectRatio(16/9, contentMode: .fit)   // iOS: fit for proper adaptive grid layout
                #endif
                .overlay(
                    ZStack(alignment: .bottomTrailing) {
                        if let thumbnail = post.thumbnail {
                            CachedAsyncImage_Phase(url: thumbnail.fullURL) { phase in
                                switch phase {
                                case .success(let image):
                                    image
                                        .resizable()
                                        .scaledToFill()
                                default:
                                    Rectangle()
                                        .fill(Color.floatplaneGray.opacity(0.3))
                                }
                            }
                        }

                        // Duration badge (only for non-livestream videos)
                        if post.metadata.hasVideo && !isLivestream {
                            Text(formatDuration(post.metadata.videoDuration))
                                .font(.caption2)
                                .fontWeight(.bold)
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 3)
                                .background(Color.black.opacity(0.8))
                                .clipShape(RoundedRectangle(cornerRadius: 4))
                                .padding(8)
                        }
                    }
                )
                .overlay(
                    // Bookmark indicator (top-right corner)
                    VStack {
                        HStack {
                            Spacer()
                            if isBookmarked {
                                Image(systemName: "bookmark.fill")
                                    .font(.caption2)
                                    .fontWeight(.bold)
                                    .foregroundStyle(.white)
                                    .shadow(color: .black, radius: 0, x: -1, y: -1)
                                    .shadow(color: .black, radius: 0, x: 1, y: -1)
                                    .shadow(color: .black, radius: 0, x: -1, y: 1)
                                    .shadow(color: .black, radius: 0, x: 1, y: 1)
                                    .padding(8)
                            }
                        }
                        Spacer()
                    }
                )
                .overlay(
                    // LIVE indicator (top-left corner)
                    VStack {
                        HStack {
                            if isLivestream {
                                HStack(spacing: 4) {
                                    Circle()
                                        .fill(.red)
                                        .frame(width: 8, height: 8)
                                        .modifier(PulsingAnimationModifier())
                                    Text("LIVE")
                                        .font(.caption2)
                                        .fontWeight(.bold)
                                }
                                .foregroundStyle(.white)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color.red.opacity(0.9))
                                .clipShape(RoundedRectangle(cornerRadius: 6))
                                .padding(8)
                            }
                            Spacer()
                        }
                        Spacer()
                    }
                )
                .overlay(
                    // Watch progress bar (bottom)
                    Group {
                        if let progress = progress, progress > 0 {
                            VStack {
                                Spacer()
                                GeometryReader { geometry in
                                    ZStack(alignment: .leading) {
                                        // Background
                                        Rectangle()
                                            .fill(Color.white.opacity(0.3))
                                            .frame(height: 3)

                                        // Progress
                                        Rectangle()
                                            .fill(Color.red)
                                            .frame(width: geometry.size.width * progress, height: 3)
                                    }
                                }
                                .frame(height: 3)
                            }
                        }
                    }
                )
                #if os(tvOS)
                .clipped()  // tvOS: needed with .fill to prevent overflow
                #endif
                .clipShape(RoundedRectangle(cornerRadius: 12))

            // Video info
            HStack(alignment: .top, spacing: 12) {
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
                    .frame(width: 36, height: 36)
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
                    .frame(width: 36, height: 36)
                    .clipShape(Circle())
                }

                VStack(alignment: .leading, spacing: 4) {
                    // Title
                    Text(post.title)
                        #if os(tvOS)
                        .font(.caption2)               // Smaller font for more text visibility
                        #else
                        .font(.subheadline)
                        #endif
                        .fontWeight(.medium)
                        .foregroundStyle(Color.adaptiveText)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)

                    // Channel/Creator and metadata
                    #if os(tvOS)
                    // tvOS: Two-line layout (creator on line 1, date/livestream info on line 2)
                    VStack(alignment: .leading, spacing: 2) {
                        if let channel = post.channel.channelObject {
                            Text(channel.title)
                                .lineLimit(1)
                        } else {
                            Text(post.creator.title)
                                .lineLimit(1)
                        }
                        RelativeTimestampView(date: post.releaseDate, isLivestream: isLivestream)
                            .lineLimit(1)
                    }
                    .font(.caption)
                    .foregroundStyle(Color.adaptiveSecondaryText)
                    #else
                    // iOS/iPadOS: Keep existing one-line layout with bullet
                    HStack(spacing: 4) {
                        // Show channel name if available, otherwise creator name
                        if let channel = post.channel.channelObject {
                            Text(channel.title)
                        } else {
                            Text(post.creator.title)
                        }
                        Text("•")
                        RelativeTimestampView(date: post.releaseDate, isLivestream: isLivestream)
                    }
                    .font(.caption)
                    .foregroundStyle(Color.adaptiveSecondaryText)
                    #endif
                }

                Spacer()

                #if !os(tvOS)
                // iOS: 3-dot menu for video actions
                Menu {
                    Button {
                        onMenuWatchLater?()
                    } label: {
                        Label("Watch Later", systemImage: "clock")
                    }

                    Button {
                        onMenuMarkAsWatched?()
                    } label: {
                        Label("Mark as Watched", systemImage: "checkmark.circle")
                    }

                    // Only show when viewing a playlist
                    if currentPlaylistId != nil {
                        Button {
                            onMenuRemoveFromPlaylist?()
                        } label: {
                            Label("Remove from Playlist", systemImage: "minus.circle")
                        }
                    }

                    Button {
                        onMenuSaveToPlaylist?()
                    } label: {
                        Label("Save to Playlist", systemImage: "text.badge.plus")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .font(.title3)
                        .foregroundStyle(Color.adaptiveSecondaryText)
                }
                .buttonStyle(.plain)
                #endif
            }
            #if os(tvOS)
            .padding(.horizontal, 12)  // Add side breathing room
            .padding(.top, 12)
            .padding(.bottom, 8)       // Add bottom padding
            #else
            .padding(.top, 12)         // iOS/iPadOS unchanged
            #endif
        }
        .frame(maxHeight: .infinity, alignment: .top)
    }

    private func formatDuration(_ seconds: Double) -> String {
        let totalSeconds = Int(seconds)
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        let secs = totalSeconds % 60

        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, secs)
        } else {
            return String(format: "%d:%02d", minutes, secs)
        }
    }
}

// MARK: - Action Pill Button (tvOS)

#if os(tvOS)
struct ActionPillButton: View {
    let icon: String
    let label: String
    var isPrimary: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.body)
                Text(label)
                    .font(.body)
                    .fontWeight(.medium)
            }
            .foregroundStyle(Color.adaptiveText)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .padding(.horizontal, 20)
        }
        .buttonStyle(.card)
    }
}
#endif

// MARK: - Relative Timestamp View

struct RelativeTimestampView: View {
    let date: Date
    let isLivestream: Bool

    @State private var currentTime = Date()

    // Timer that fires every 60 seconds to update the timestamp
    private let timer = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    var body: some View {
        Text(formattedTimestamp)
            .onReceive(timer) { _ in
                currentTime = Date()
            }
    }

    private var formattedTimestamp: String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = isLivestream ? .abbreviated : .short
        return isLivestream
            ? "Started \(formatter.localizedString(for: date, relativeTo: currentTime))"
            : formatter.localizedString(for: date, relativeTo: currentTime)
    }
}

// MARK: - Pulsing Animation

struct PulsingAnimationModifier: ViewModifier {
    @State private var isPulsing = false

    func body(content: Content) -> some View {
        content
            .scaleEffect(isPulsing ? 1.3 : 1.0)
            .opacity(isPulsing ? 0.6 : 1.0)
            .animation(
                Animation.easeInOut(duration: 1.5)
                    .repeatForever(autoreverses: true),
                value: isPulsing
            )
            .onAppear {
                isPulsing = true
            }
    }
}

#Preview {
    VideoFeedView()
}
