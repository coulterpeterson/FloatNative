//
//  PlaylistMembershipStore.swift
//  FloatNative
//
//  Single source of truth for "is this post in any playlist?" — the state
//  behind the bookmark badge on video cards.
//
//  Before this existed every feed (Home, Creators, each pushed playlist, and
//  Search) kept its own `allPlaylistVideoIds` set, fetched once in `.task`
//  and then hand-maintained after each add/remove. Mutations made in one
//  view never reached the others, so the Home grid would happily show a
//  bookmark for a video removed from the Playlists tab (false positive) and
//  miss one added from Search (missing). CompanionAPI now feeds this store
//  from every playlist response it receives, so every observer sees the
//  same set at the same time.
//

import Foundation

@MainActor
final class PlaylistMembershipStore: ObservableObject {

    static let shared = PlaylistMembershipStore()

    /// Post IDs that appear in at least one playlist (Watch Later included).
    @Published private(set) var bookmarkedVideoIds: Set<String> = []

    /// True once a full listing (with Watch Later) has been ingested. Views
    /// use this to avoid redundant fetches on re-appear.
    private(set) var hasLoadedFullListing = false

    /// Everything we know, keyed by playlist id. Only `id`, `isWatchLater`
    /// and `videoIds` matter here; the rest rides along.
    private var playlists: [String: Playlist] = [:]

    private init() {}

    func contains(_ postId: String) -> Bool {
        bookmarkedVideoIds.contains(postId)
    }

    /// Fetch the full playlist listing and rebuild. CompanionAPI.getPlaylists
    /// routes its result through `ingest`, so this is just a convenience.
    func refresh() async {
        do {
            _ = try await CompanionAPI.shared.getPlaylists(includeWatchLater: true)
        } catch {
            TVLog.log(.playlist, "refresh failed: \(error)", notable: true)
        }
    }

    /// Replace our picture of the user's playlists with a fresh listing.
    /// A listing fetched without Watch Later says nothing about Watch Later,
    /// so those entries are kept as-is in that case.
    func ingest(_ listing: [Playlist], includesWatchLater: Bool) {
        var next = includesWatchLater ? [:] : playlists.filter { $0.value.isWatchLater }
        for playlist in listing {
            next[playlist.id] = playlist
        }
        playlists = next
        if includesWatchLater { hasLoadedFullListing = true }
        recompute(reason: "ingest \(listing.count) playlists (watchLater=\(includesWatchLater))")
    }

    /// Upsert a single playlist from an add/remove/create response.
    func apply(_ playlist: Playlist) {
        playlists[playlist.id] = playlist
        recompute(reason: "apply \"\(playlist.name)\" (\(playlist.videoIds.count) videos)")
    }

    /// Watch Later responses come back as a thinner shape; keep whatever
    /// metadata we already had for it and swap in the new video list.
    func applyWatchLater(_ response: WatchLaterResponse) {
        let existing = playlists[response.id]
        playlists[response.id] = Playlist(
            id: response.id,
            floatplaneUserId: existing?.floatplaneUserId ?? "",
            name: existing?.name ?? "Watch Later",
            isWatchLater: true,
            videoIds: response.videoIds,
            createdAt: existing?.createdAt ?? response.updatedAt,
            updatedAt: response.updatedAt
        )
        recompute(reason: "apply Watch Later (\(response.videoIds.count) videos)")
    }

    func remove(playlistId: String) {
        let removed = playlists.removeValue(forKey: playlistId)
        recompute(reason: "remove \"\(removed?.name ?? playlistId)\"")
    }

    private func recompute(reason: String) {
        let next = Set(playlists.values.flatMap { $0.videoIds })
        let added = next.subtracting(bookmarkedVideoIds).count
        let dropped = bookmarkedVideoIds.subtracting(next).count
        // Only publish on a real change — every feed observes this and a
        // no-op publish would re-render all their cards for nothing.
        if next != bookmarkedVideoIds {
            bookmarkedVideoIds = next
        }
        TVLog.log(.playlist,
                  "\(reason) -> \(next.count) bookmarked (+\(added) -\(dropped)) across \(playlists.count) playlists")
    }
}
