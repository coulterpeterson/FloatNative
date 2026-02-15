//
//  PlaylistsView.swift
//  FloatNative
//
//  View for displaying and navigating playlists
//

import SwiftUI

struct PlaylistsView: View {
    @StateObject private var companionAPI = CompanionAPI.shared
    @StateObject private var playlistVideoViewModel = PlaylistVideoViewModel()
    @State private var playlists: [Playlist] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var selectedPlaylist: Playlist?
    @State private var playlistPosts: [BlogPost] = []
    @State private var isLoadingPlaylistPosts = false
    
    // Thumbnail cache: playlist ID -> BlogPost thumbnail
    @State private var thumbnailCache: [String: ImageModel?] = [:]

    // Create playlist sheet
    @State private var showCreatePlaylistSheet = false

    var body: some View {
        Group {
            if let selectedPlaylist = selectedPlaylist {
                // Show VideoFeedView with playlist's posts
                VideoFeedView(
                    posts: playlistPosts,
                    title: selectedPlaylist.name,
                    playlistId: selectedPlaylist.id,
                    onPlaylistEmpty: {
                        // When playlist becomes empty, return to playlist grid
                        self.selectedPlaylist = nil
                        self.playlistPosts = []
                    }
                )
                    #if !os(tvOS)
                    .toolbar {
                        ToolbarItem(placement: .principal) {
                            Button {
                                // Return to playlist grid
                                self.selectedPlaylist = nil
                                self.playlistPosts = []
                            } label: {
                                HStack {
                                    Image(systemName: "chevron.left")
                                    Text("Back to Playlists")
                                }
                            }
                        }
                    }
                    #endif
                    #if os(tvOS)
                    .onExitCommand {
                        // Return to playlist grid on Menu button press
                        self.selectedPlaylist = nil
                        self.playlistPosts = []
                    }
                    #endif
            } else {
                // Show playlist grid
                playlistGridView
            }
        }
        .onAppear {
            // Refresh when tab switching or view reappears
            Task {
                await loadPlaylists()
            }
        }
        .onChange(of: selectedPlaylist) { oldValue, newValue in
            // Refresh when returning from playlist detail view
            if oldValue != nil && newValue == nil {
                Task {
                    await loadPlaylists()
                }
            }
        }
    }

    // MARK: - Playlist Grid View

    private var playlistGridView: some View {
        Group {
            #if os(tvOS)
            GeometryReader { geometry in
                ZStack {
                    // Background
                    Color.adaptiveBackground
                        .ignoresSafeArea()

                    ScrollView {
                        LazyVGrid(columns: gridColumns, spacing: 15) {
                            ForEach(playlists) { playlist in
                                // Calculate column width
                                let gridWidth = geometry.size.width
                                let columnCount: CGFloat = 4
                                let horizontalPadding: CGFloat = 40
                                let columnSpacing: CGFloat = 20 * (columnCount - 1)
                                let totalSpacing = horizontalPadding + columnSpacing
                                let columnWidth = (gridWidth - totalSpacing) / columnCount
                                let thumbnailHeight = columnWidth / (16.0 / 9.0)
                                let metadataHeight: CGFloat = 80 // Title + video count
                                let cardHeight = thumbnailHeight + metadataHeight

                                TvOSLongPressCard(
                                    width: columnWidth,
                                    progress: nil,
                                    onTap: {
                                        selectPlaylist(playlist)
                                    },
                                    onLongPress: {
                                        // Could show playlist actions in future
                                    }
                                ) {
                                    PlaylistCard(playlist: playlist, thumbnail: thumbnailCache[playlist.id])
                                }
                                .frame(width: columnWidth, height: cardHeight)
                                .buttonStyle(.card)
                                .id("\(playlist.id)-\(playlist.videoIds.count)-\(playlist.updatedAt.timeIntervalSince1970)")
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 60)
                        .padding(.bottom, 40)
                        .focusSection()
                    }

                    // Initial loading
                    if isLoading && playlists.isEmpty {
                        ProgressView()
                            .tint(.floatplaneBlue)
                            .scaleEffect(1.5)
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
                                    await loadPlaylists()
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
            #else
            ZStack {
                // Background
                Color.adaptiveBackground
                    .ignoresSafeArea()

                VStack(spacing: 0) {
                    ScrollView {
                        LazyVGrid(columns: gridColumns, spacing: 20) {
                            ForEach(playlists) { playlist in
                                PlaylistCard(
                                    playlist: playlist,
                                    thumbnail: thumbnailCache[playlist.id],
                                    onDelete: {
                                        Task { @MainActor in
                                            await handleDeletePlaylist(playlist: playlist)
                                        }
                                    }
                                )
                                .id("\(playlist.id)-\(playlist.videoIds.count)")
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical)
                    }
                    .refreshable {
                        await Task.detached {
                            await loadPlaylists()
                        }.value
                    }
                }

                // Initial loading
                if isLoading && playlists.isEmpty {
                    ProgressView()
                        .tint(.floatplaneBlue)
                        .scaleEffect(1.5)
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
                                await loadPlaylists()
                            }
                        }
                        .buttonStyle(LiquidGlassButtonStyle(tint: .floatplaneBlue))
                    }
                    .padding()
                    }
            }
            .navigationTitle("Playlists")
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        showCreatePlaylistSheet = true
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showCreatePlaylistSheet) {
                CreatePlaylistView { name in
                    await handleCreatePlaylist(name: name)
                }
                .presentationDetents([.fraction(0.3), .medium])
                .presentationDragIndicator(.visible)
            }
            .navigationDestination(for: Playlist.self) { playlist in
                // iOS/iPadOS: Navigate to playlist videos
                PlaylistVideosView(playlist: playlist)
            }
            #endif
        }
    }

    // Grid columns: tvOS uses 4 columns, iPad uses adaptive, iPhone uses single
    private var gridColumns: [GridItem] {
        #if os(tvOS)
        return Array(repeating: GridItem(.flexible(), spacing: 20), count: 4)
        #else
        if UIDevice.current.userInterfaceIdiom == .pad {
            return [GridItem(.adaptive(minimum: 320, maximum: 400), spacing: 16)]
        } else {
            return [GridItem(.flexible())]
        }
        #endif
    }

    // MARK: - Load Playlists

    private func loadPlaylists() async {
        // Prevent concurrent loads (race condition guard)
        guard !isLoading else {
            return
        }

        isLoading = true
        errorMessage = nil

        do {
            let loadedPlaylists = try await companionAPI.getPlaylists(includeWatchLater: true)

            // Load thumbnails for playlists with videos BEFORE updating state
            // This ensures thumbnailCache is populated before view renders
            await loadThumbnails(for: loadedPlaylists)

            // Now update playlists state - view will render with thumbnails ready
            playlists = loadedPlaylists
        } catch {
            errorMessage = "Failed to load playlists: \(error.localizedDescription)"
            print("❌ Failed to load playlists: \(error)")
        }

        isLoading = false
    }

    // MARK: - Load Thumbnails

    private func loadThumbnails(for playlists: [Playlist]) async {
        // Load thumbnails concurrently for playlists that have videos
        await withTaskGroup(of: (String, ImageModel?).self) { group in
            for playlist in playlists where !playlist.videoIds.isEmpty {
                group.addTask {
                    // Fetch first video to get thumbnail
                    do {
                        let api = FloatplaneAPI.shared
                        let detailedPost = try await api.getBlogPost(id: playlist.videoIds[0])
                        return (playlist.id, detailedPost.post.thumbnail)
                    } catch {
                        return (playlist.id, nil)
                    }
                }
            }

            for await (playlistId, thumbnail) in group {
                thumbnailCache[playlistId] = thumbnail
            }
        }
    }

    // MARK: - Select Playlist

    private func selectPlaylist(_ playlist: Playlist) {
        // Prevent selecting empty playlists
        guard !playlist.videoIds.isEmpty else {
            return
        }

        selectedPlaylist = playlist
        isLoadingPlaylistPosts = true
        playlistPosts = []

        Task {
            // First, reload playlists to ensure we have the freshest data
            // This prevents stale data when user taps playlist before .onAppear reload completes
            do {
                let freshPlaylists = try await companionAPI.getPlaylists(includeWatchLater: true)

                // Find the fresh version of the selected playlist
                guard let freshPlaylist = freshPlaylists.first(where: { $0.id == playlist.id }) else {
                    print("❌ Playlist '\(playlist.name)' not found in fresh data")
                    isLoadingPlaylistPosts = false
                    return
                }

                // Update the playlists state with fresh data
                playlists = freshPlaylists

                // Update selectedPlaylist to the fresh version
                selectedPlaylist = freshPlaylist

                // Also reload thumbnails for the fresh playlists
                await loadThumbnails(for: freshPlaylists)

                // Now load posts from the fresh playlist
                await playlistVideoViewModel.loadPostsFromPlaylist(playlist: freshPlaylist)

                playlistPosts = playlistVideoViewModel.posts
                isLoadingPlaylistPosts = false
            } catch {
                print("❌ Failed to reload playlists: \(error)")
                isLoadingPlaylistPosts = false
            }
        }
    }

    // MARK: - Create Playlist

    private func handleCreatePlaylist(name: String) async {
        do {
            // Call API to create playlist
            let newPlaylist = try await companionAPI.createPlaylist(name: name)

            // Insert new playlist at the beginning of the array
            playlists.insert(newPlaylist, at: 0)

            // Load thumbnail for new playlist if it has videos
            if !newPlaylist.videoIds.isEmpty {
                await loadThumbnails(for: [newPlaylist])
            }
        } catch {
            errorMessage = "Failed to create playlist: \(error.localizedDescription)"
            print("❌ Failed to create playlist: \(error)")
        }
    }

    // MARK: - Delete Playlist

    private func handleDeletePlaylist(playlist: Playlist) async {
        // Optimistically remove the playlist from the array
        let originalIndex = playlists.firstIndex { $0.id == playlist.id }
        playlists.removeAll { $0.id == playlist.id }

        do {
            // Call API to delete playlist
            try await companionAPI.deletePlaylist(playlistId: playlist.id)
        } catch {
            // Rollback on error - re-insert playlist at original position
            if let index = originalIndex {
                playlists.insert(playlist, at: min(index, playlists.count))
            } else {
                playlists.insert(playlist, at: 0)
            }
            errorMessage = "Failed to delete playlist: \(error.localizedDescription)"
            print("❌ Failed to delete playlist: \(error)")
        }
    }
}

// MARK: - Playlist Card

struct PlaylistCard: View {
    let playlist: Playlist
    let thumbnail: ImageModel??
    var onDelete: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            #if !os(tvOS)
            // iOS: Wrap only thumbnail and metadata in NavigationLink
            NavigationLink(value: playlist) {
                VStack(alignment: .leading, spacing: 0) {
                    // Thumbnail
                    thumbnailView

                    // Playlist info (without menu)
                    HStack(alignment: .top, spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            // Title
                            Text(playlist.name)
                                .font(.subheadline)
                                .fontWeight(.medium)
                                .foregroundStyle(Color.adaptiveText)
                                .lineLimit(2)
                                .multilineTextAlignment(.leading)

                            // Video count
                            Text("\(playlist.videoIds.count) \(playlist.videoIds.count == 1 ? "video" : "videos")")
                                .font(.caption)
                                .foregroundStyle(Color.adaptiveSecondaryText)
                        }

                        Spacer()
                    }
                    .padding(.top, 12)
                }
            }
            .buttonStyle(PlainButtonStyle())
            .disabled(playlist.videoIds.isEmpty)

            // Context menu positioned over the card (outside NavigationLink)
            HStack {
                Spacer()
                Menu {
                    Button(role: .destructive) {
                        onDelete?()
                    } label: {
                        Text("Delete")
                            .foregroundStyle(.red)
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .font(.title3)
                        .foregroundStyle(Color.adaptiveSecondaryText)
                }
                .buttonStyle(.plain)
            }
            .padding(.top, -40) // Overlay on top of the metadata area
            .padding(.trailing, 0)
            #else
            // tvOS: Keep original structure
            thumbnailView

            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(playlist.name)
                        .font(.caption2)
                        .fontWeight(.medium)
                        .foregroundStyle(Color.adaptiveText)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)

                    Text("\(playlist.videoIds.count) \(playlist.videoIds.count == 1 ? "video" : "videos")")
                        .font(.caption)
                        .foregroundStyle(Color.adaptiveSecondaryText)
                }

                Spacer()
            }
            .padding(.horizontal, 12)
            .padding(.top, 12)
            .padding(.bottom, 8)
            #endif
        }
        .frame(maxHeight: .infinity, alignment: .top)
    }

    // Shared thumbnail view
    private var thumbnailView: some View {
        Color.clear
            .aspectRatio(16/9, contentMode: .fill)
            .overlay(
                Group {
                    if let thumbnailModel = thumbnail?.flatMap({ $0 }), let thumbnailURL = thumbnailModel.fullURL {
                        CachedAsyncImage_Phase(url: thumbnailURL) { phase in
                            switch phase {
                            case .success(let image):
                                image
                                    .resizable()
                                    .scaledToFill()
                            default:
                                emptyPlaceholder
                            }
                        }
                    } else {
                        emptyPlaceholder
                    }
                }
            )
            .clipped()
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var emptyPlaceholder: some View {
        Rectangle()
            .fill(Color.floatplaneGray.opacity(0.3))
            .overlay(
                Text("No Videos")
                    .font(.caption)
                    .foregroundStyle(Color.adaptiveSecondaryText)
            )
    }
}

// MARK: - iOS/iPadOS Playlist Videos View

#if !os(tvOS)
struct PlaylistVideosView: View {
    let playlist: Playlist

    @StateObject private var playlistVideoViewModel = PlaylistVideoViewModel()

    var body: some View {
        VideoFeedView(posts: playlistVideoViewModel.posts, title: playlist.name, playlistId: playlist.id)
            .task {
                await playlistVideoViewModel.loadPostsFromPlaylist(playlist: playlist)
            }
    }
}
#endif

