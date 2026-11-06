//
//  SaveToPlaylistSheet.swift
//  FloatNative
//
//  The "Save to Playlist" sheet for a single post, and the bookmark button on
//  the video page that opens it.
//

#if !os(tvOS)
import SwiftUI

struct SaveToPlaylistSheet: View {
    let post: BlogPost

    @Environment(\.dismiss) private var dismiss

    @State private var playlists: [Playlist] = []
    @State private var playlistsWithVideo: Set<String> = []
    @State private var isLoading = true
    @State private var showCreatePlaylist = false

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                if isLoading {
                    ProgressView()
                        .tint(.floatplaneBlue)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if playlists.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "tray")
                            .font(.system(size: 50))
                            .foregroundStyle(Color.adaptiveSecondaryText)
                        Text(NetworkMonitor.shared.isOnline ? "No playlists found" : "You're offline. Connect to save to a playlist.")
                            .font(.subheadline)
                            .foregroundStyle(Color.adaptiveSecondaryText)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        ForEach(playlists) { playlist in
                            Button {
                                Task {
                                    await toggle(playlist)
                                }
                            } label: {
                                HStack(spacing: 12) {
                                    Text(playlist.name)
                                        .font(.body)
                                        .foregroundStyle(Color.adaptiveText)

                                    Spacer()

                                    Image(systemName: playlistsWithVideo.contains(playlist.id) ? "bookmark.fill" : "bookmark")
                                        .font(.body)
                                        .foregroundStyle(Color.adaptiveText)
                                }
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Save to Playlist")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        showCreatePlaylist = true
                    } label: {
                        Label("New Playlist", systemImage: "plus")
                    }
                }

                ToolbarItem(placement: .confirmationAction) {
                    Button("Done", systemImage: "checkmark") {
                        dismiss()
                    }
                }
            }
            .sheet(isPresented: $showCreatePlaylist) {
                CreatePlaylistView { name in
                    await createPlaylist(name: name)
                }
                .presentationDetents([.fraction(0.3), .medium])
                .presentationDragIndicator(.visible)
            }
            .task {
                await load()
            }
        }
    }

    private func load() async {
        do {
            let loaded = try await CompanionAPI.shared.getPlaylists(includeWatchLater: true)
            playlists = loaded
            playlistsWithVideo = Set(loaded.filter { $0.videoIds.contains(post.id) }.map(\.id))
        } catch {
            print("❌ Failed to load playlists for sheet: \(error)")
        }
        isLoading = false
    }

    private func toggle(_ playlist: Playlist) async {
        let wasInPlaylist = playlistsWithVideo.contains(playlist.id)

        // Optimistic update, rolled back if the request fails
        if wasInPlaylist {
            playlistsWithVideo.remove(playlist.id)
        } else {
            playlistsWithVideo.insert(playlist.id)
        }

        do {
            let updated = wasInPlaylist
                ? try await CompanionAPI.shared.removeFromPlaylist(playlistId: playlist.id, videoId: post.id)
                : try await CompanionAPI.shared.addToPlaylist(playlistId: playlist.id, videoId: post.id)
            if let index = playlists.firstIndex(where: { $0.id == playlist.id }) {
                playlists[index] = updated
            }
            // Bookmark badges update on their own: CompanionAPI feeds every
            // playlist response into PlaylistMembershipStore.
        } catch {
            if wasInPlaylist {
                playlistsWithVideo.insert(playlist.id)
            } else {
                playlistsWithVideo.remove(playlist.id)
            }
            print("❌ Failed to toggle playlist membership: \(error)")
        }
    }

    private func createPlaylist(name: String) async {
        do {
            playlists.insert(try await CompanionAPI.shared.createPlaylist(name: name), at: 0)
        } catch {
            print("❌ Failed to create playlist from sheet: \(error)")
        }
    }
}

/// Filled when the post is in any playlist, matching the bookmark badge on cards.
struct SaveToPlaylistButton: View {
    let post: BlogPost

    @ObservedObject private var membership = PlaylistMembershipStore.shared
    @State private var showSheet = false

    var body: some View {
        Button {
            showSheet = true
        } label: {
            Image(systemName: membership.contains(post.id) ? "bookmark.fill" : "bookmark")
                .font(.title2)
                .foregroundColor(membership.contains(post.id) ? Color.floatplaneBlue : Color.adaptiveText)
        }
        .accessibilityLabel("Save to Playlist")
        .sheet(isPresented: $showSheet) {
            SaveToPlaylistSheet(post: post)
                .presentationDetents([.fraction(0.3), .medium])
                .presentationDragIndicator(.visible)
        }
        .task {
            if !membership.hasLoadedFullListing {
                await membership.refresh()
            }
        }
    }
}
#endif
