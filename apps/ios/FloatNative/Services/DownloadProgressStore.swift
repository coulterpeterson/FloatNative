//
//  DownloadProgressStore.swift
//  FloatNative
//
//  Play progress for downloaded videos. It's always written locally first, so
//  playback works with no connection, and anything Floatplane hasn't seen yet
//  is sent when the device is next online and the app is running.
//

#if !os(tvOS)
import Combine
import Foundation
import UIKit

@MainActor
final class DownloadProgressStore: ObservableObject {
    struct Entry: Codable, Equatable {
        var seconds: Int
        /// nil for videos added through the Files app, which have nothing to sync to.
        var videoId: String?
        var needsSync: Bool
    }

    static let shared = DownloadProgressStore(
        fileURL: FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("DownloadProgress.json"),
        upload: { videoId, seconds in
            try await FloatplaneAPI.shared.updateProgress(videoId: videoId, contentType: "video", progress: seconds)
        },
        fetchRemote: { videoId in
            try await FloatplaneAPI.shared.getVideoContent(id: videoId).progress
        },
        isOnline: { NetworkMonitor.shared.isOnline }
    )

    @Published private(set) var entries: [String: Entry] = [:]

    private let fileURL: URL
    private let upload: (String, Int) async throws -> Void
    private let fetchRemote: (String) async throws -> Int?
    private var isSyncing = false
    private var networkObserver: AnyCancellable?
    private let isOnline: () -> Bool
    private var foregroundObserver: NSObjectProtocol?

    init(
        fileURL: URL,
        upload: @escaping (String, Int) async throws -> Void,
        fetchRemote: @escaping (String) async throws -> Int?,
        isOnline: @escaping () -> Bool = { true }
    ) {
        self.isOnline = isOnline
        self.fileURL = fileURL
        self.upload = upload
        self.fetchRemote = fetchRemote
        if let data = try? Data(contentsOf: fileURL),
           let saved = try? JSONDecoder().decode([String: Entry].self, from: data) {
            entries = saved
        }
    }

    /// Syncs whenever the app comes to the foreground or the network comes back.
    func startMonitoring() {
        guard networkObserver == nil else { return }

        networkObserver = NetworkMonitor.shared.$isOnline
            .removeDuplicates()
            .dropFirst()
            .filter { $0 }
            .sink { [weak self] _ in
                Task { @MainActor in await self?.syncPending() }
            }

        foregroundObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in await self?.syncPending() }
        }
    }

    // MARK: - Recording

    func record(key: String, videoId: String?, seconds: Int) {
        // Nothing has played yet (or the player is still seeking to the resume
        // point), so there's no position worth keeping.
        guard seconds > 0, entries[key]?.seconds != seconds else { return }
        entries[key] = Entry(seconds: seconds, videoId: videoId, needsSync: videoId != nil)
        persist()
    }

    func remove(key: String) {
        guard entries.removeValue(forKey: key) != nil else { return }
        persist()
    }

    /// Queued positions belong to whoever watched them, so they must not be sent
    /// to the next account that logs in. Local resume points stay.
    func discardPendingSync() {
        for key in entries.keys where entries[key]?.needsSync == true {
            entries[key]?.needsSync = false
        }
        persist()
    }

    // MARK: - Resume Position

    /// Where to start a video. A position Floatplane hasn't seen yet is the most
    /// recent one we know of. Otherwise ask Floatplane, since the video may have
    /// been watched on another device, and fall back to the local position.
    func resumeSeconds(key: String, videoId: String?) async -> Int {
        let local = entries[key]
        if local?.needsSync == true, let local { return local.seconds }

        if let videoId, isOnline(), let remote = await fetchRemoteWithTimeout(videoId) {
            return remote
        }
        return local?.seconds ?? 0
    }

    private func fetchRemoteWithTimeout(_ videoId: String) async -> Int? {
        let fetchRemote = self.fetchRemote
        return await withTaskGroup(of: Int?.self) { group in
            group.addTask { try? await fetchRemote(videoId) }
            // A weak connection shouldn't hold up an offline video.
            group.addTask {
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                return nil
            }
            let first = await group.next() ?? nil
            group.cancelAll()
            return first
        }
    }

    // MARK: - Sync

    func syncPending() async {
        guard !isSyncing else { return }
        isSyncing = true
        defer { isSyncing = false }

        for (key, entry) in entries where entry.needsSync {
            guard let videoId = entry.videoId else { continue }
            do {
                try await upload(videoId, entry.seconds)
            } catch {
                if Self.isPermanentFailure(error) {
                    // Floatplane rejected it (e.g. the video was removed), so
                    // retrying would only fail forever. The local position stays.
                    markSynced(key: key, seconds: entry.seconds)
                    continue
                }
                // Offline, signed out or a server problem: keep it and stop.
                // The next foreground or reconnect tries again.
                return
            }
            markSynced(key: key, seconds: entry.seconds)
        }
    }

    /// Leaves the entry pending if playback moved on during the upload.
    private func markSynced(key: String, seconds: Int) {
        guard entries[key]?.seconds == seconds else { return }
        entries[key]?.needsSync = false
        persist()
    }

    private static func isPermanentFailure(_ error: Error) -> Bool {
        guard case FloatplaneAPIError.httpError(let statusCode, _) = error else { return false }
        return (400..<500).contains(statusCode) && statusCode != 401 && statusCode != 403 && statusCode != 408 && statusCode != 429
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(entries) else { return }
        // Application Support doesn't exist until something creates it.
        try? FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? data.write(to: fileURL, options: .atomic)
    }
}
#endif
