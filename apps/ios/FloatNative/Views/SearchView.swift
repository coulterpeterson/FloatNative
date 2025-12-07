//
//  SearchView.swift
//  FloatNative
//
//  Search across all subscribed creators
//

import SwiftUI

struct SearchView: View {
    let initialSearchText: String

    @Environment(\.dismiss) private var dismiss
    @StateObject private var api = FloatplaneAPI.shared
    @StateObject private var companionAPI = CompanionAPI.shared

    @State private var searchText = ""
    @State private var searchResults: [BlogPost] = []
    @State private var isSearching = false
    @State private var errorMessage: String?
    @State private var hasSearched = false
    @State private var searchTask: Task<Void, Never>?
    @State private var isSearchPresented = false

    // Enhanced LTT Search setting
    @AppStorage("enhancedLttSearchEnabled") private var enhancedLttSearchEnabled = false

    // Bookmark indicators: Set of all video IDs that exist in any playlist (for O(1) lookup)
    @State private var allPlaylistVideoIds: Set<String> = []

    // iOS Menu state
    @State private var menuSelectedPost: BlogPost?
    @State private var showPlaylistSheet = false
    @State private var isAddingToWatchLaterMenu = false
    @State private var menuPlaylistsWithVideo: Set<String> = []
    @State private var showCreatePlaylistFromMenu = false
    @State private var showToast = false
    @State private var playlists: [Playlist] = []
    @State private var isLoadingPlaylists = false

    init(initialSearchText: String = "") {
        self.initialSearchText = initialSearchText
    }

    // Grid columns: tvOS uses 4 columns, iPad uses adaptive grid, iPhone uses single column
    private var gridColumns: [GridItem] {
        #if os(tvOS)
        // tvOS: 4 column grid with tighter spacing for more content visibility
        // Match Wasserflug layout: 30pt between columns for better space utilization
        return Array(repeating: GridItem(.flexible(), spacing: 30), count: 4)
        #else
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
        NavigationStack {
            ZStack {
                Color.adaptiveBackground
                    .ignoresSafeArea()

                if isSearching {
                    ProgressView()
                        .tint(.floatplaneBlue)
                        .scaleEffect(1.5)
                } else if let error = errorMessage {
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.system(size: 50))
                            .foregroundStyle(.red)

                        Text(error)
                            .foregroundStyle(Color.adaptiveText)
                            .multilineTextAlignment(.center)
                    }
                    .padding()
                } else if searchResults.isEmpty && hasSearched {
                    VStack(spacing: 16) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 50))
                            .foregroundColor(Color.adaptiveSecondaryText)

                        Text("No results found")
                            .font(.headline)
                            .foregroundColor(Color.adaptiveText)

                        Text("Try a different search term")
                            .font(.subheadline)
                            .foregroundColor(Color.adaptiveSecondaryText)
                    }
                } else if searchResults.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 50))
                            .foregroundColor(Color.adaptiveSecondaryText)

                        Text("Search videos")
                            .font(.headline)
                            .foregroundColor(Color.adaptiveText)

                        Text("Search across all your subscribed creators")
                            .font(.subheadline)
                            .foregroundColor(Color.adaptiveSecondaryText)
                    }
                } else {
                    ScrollView {
                        LazyVGrid(columns: gridColumns, spacing: 20) {
                            ForEach(searchResults) { post in
                                NavigationLink(value: post) {
                                    VideoCard(
                                        post: post,
                                        isBookmarked: allPlaylistVideoIds.contains(post.id),
                                        onMenuWatchLater: {
                                            Task { @MainActor in
                                                await handleMenuWatchLater(post: post)
                                            }
                                        },
                                        onMenuSaveToPlaylist: {
                                            menuSelectedPost = post
                                            showPlaylistSheet = true
                                        }
                                    )
                                }
                                #if os(tvOS)
                                .buttonStyle(.card)
                                #else
                                .buttonStyle(PlainButtonStyle())
                                #endif
                            }
                        }
                        #if os(tvOS)
                        .padding(.horizontal, 30)  // Reduced padding for wider cards
                        .padding(.bottom, 40)      // Reduced bottom spacing for tighter layout
                        .focusSection()
                        #else
                        .padding(.horizontal, 16)
                        .padding(.vertical)
                        #endif
                    }
                }
            }
            .navigationTitle("Search")
            #if !os(tvOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .globalMenu()
            #if os(tvOS)
            .searchable(text: $searchText, prompt: "Search videos")
            #else
            .searchable(text: $searchText, isPresented: $isSearchPresented, prompt: "Search videos")
            #endif
            .onSubmit(of: .search) {
                if !searchText.isEmpty {
                    hasSearched = true
                    Task {
                        await performSearch(query: searchText)
                    }
                }
            }
            .onChange(of: searchText) { oldValue, newValue in
                // Cancel any pending search
                searchTask?.cancel()

                if newValue.isEmpty {
                    searchResults = []
                    hasSearched = false
                    return
                }

                #if os(tvOS)
                // tvOS: Auto-search after 3+ characters with 1 second debounce
                if newValue.count >= 3 {
                    searchTask = Task {
                        // Wait 1 second
                        try? await Task.sleep(nanoseconds: 1_000_000_000)

                        // Check if not cancelled
                        guard !Task.isCancelled else { return }

                        // Perform search
                        hasSearched = true
                        await performSearch(query: newValue)
                    }
                }
                #endif
            }
            .onAppear {
                // On iPad, automatically present the search field for immediate typing
                #if !os(tvOS)
                if UIDevice.current.userInterfaceIdiom == .pad {
                    isSearchPresented = true
                }
                #endif

                // Load playlists for bookmark indicators
                Task {
                    await loadPlaylistsForBookmarks()
                }

                searchText = initialSearchText
                if !initialSearchText.isEmpty {
                    hasSearched = true
                    Task {
                        await performSearch(query: initialSearchText)
                    }
                }
            }
            .navigationDestination(for: BlogPost.self) { post in
                #if os(tvOS)
                VideoPlayerTvosView(post: post)
                #else
                VideoPlayerView(post: post)
                #endif
            }
            #if !os(tvOS)
            .sheet(isPresented: $showPlaylistSheet, onDismiss: {
                // Reset state when sheet is dismissed
                menuSelectedPost = nil
                menuPlaylistsWithVideo = []
            }) {
                playlistPickerSheet
            }
            .toast(
                isPresented: $showToast,
                message: "Saved to Watch Later",
                icon: "clock.fill"
            )
            #endif
        }
    }

    private func loadPlaylistsForBookmarks() async {
        do {
            let loadedPlaylists = try await companionAPI.getPlaylists(includeWatchLater: true)

            // Create efficient lookup set of all video IDs in any playlist
            allPlaylistVideoIds = Set(loadedPlaylists.flatMap { $0.videoIds })

            print("✅ SearchView: Loaded \(loadedPlaylists.count) playlists for bookmark indicators (\(allPlaylistVideoIds.count) unique videos)")
        } catch {
            print("⚠️ SearchView: Failed to load playlists for bookmarks: \(error)")
            // Fail silently - bookmarks just won't show
        }
    }

    private func performSearch(query: String) async {
        let LTT_CREATOR_ID = "59f94c0bdd241b70349eb72b"

        isSearching = true
        errorMessage = nil

        do {
            // Get subscribed creators
            let subscriptions = try await api.getSubscriptions()

            // Check if enhanced LTT search should be used
            let isLttOnlySubscriber = subscriptions.count == 1 &&
                                     subscriptions.first?.creator == LTT_CREATOR_ID

            if enhancedLttSearchEnabled && isLttOnlySubscriber {
                // Use enhanced LTT search endpoint
                do {
                    let results = try await companionAPI.searchLTT(query: query)

                    await MainActor.run {
                        // Sort by release date (newest first)
                        searchResults = results.sorted { $0.releaseDate > $1.releaseDate }
                        isSearching = false
                    }
                } catch {
                    // Fall back to standard search if enhanced search fails
                    print("Enhanced LTT search failed, falling back to standard search: \(error)")
                    await performStandardSearch(query: query, subscriptions: subscriptions)
                }
            } else {
                // Use standard Floatplane search
                await performStandardSearch(query: query, subscriptions: subscriptions)
            }
        } catch {
            await MainActor.run {
                errorMessage = "Search failed: \(error.localizedDescription)"
                isSearching = false
            }
        }
    }

    private func performStandardSearch(query: String, subscriptions: [UserSubscriptionModel]) async {
        let creatorIds = subscriptions.map { $0.creator }

        // Search across all creators in parallel
        await withTaskGroup(of: [BlogPost].self) { group in
            for creatorId in creatorIds {
                group.addTask {
                    do {
                        return try await api.getCreatorContent(
                            creatorId: creatorId,
                            limit: 10,
                            search: query
                        )
                    } catch {
                        return []
                    }
                }
            }

            var allResults: [BlogPost] = []
            for await results in group {
                allResults.append(contentsOf: results)
            }

            await MainActor.run {
                // Sort by release date (newest first)
                searchResults = allResults.sorted { $0.releaseDate > $1.releaseDate }
                isSearching = false
            }
        }
    }

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
            print("❌ Failed to toggle playlist membership: \(error)")
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
}

#Preview {
    SearchView()
}
