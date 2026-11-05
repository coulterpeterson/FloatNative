//
//  DownloadTests.swift
//  FloatNativeTests
//
//  The on-device folder and the offline progress queue.
//

import Testing
import Foundation
@testable import FloatNative

private func makeTempFolder() throws -> URL {
    let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
    try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
    return url
}

private func metadata(videoId: String = "vid1", fileSize: Int64? = nil) -> DownloadMetadata {
    DownloadMetadata(
        postId: "post1",
        videoId: videoId,
        title: "A Video",
        creatorName: "LinusTechTips",
        channelName: "ShortCircuit",
        releaseDate: Date(timeIntervalSince1970: 1_800_000_000),
        descriptionHTML: "<p>Description with a / slash</p>",
        durationSeconds: 600,
        qualityLabel: "1080p",
        downloadedAt: Date(timeIntervalSince1970: 1_800_000_000),
        fileSize: fileSize
    )
}

/// Writes a five-byte "video" and a sidecar that matches it.
private func makeDownload(_ name: String, in folder: URL, videoId: String = "vid1", content: String = "video") throws -> URL {
    let url = folder.appendingPathComponent(name)
    let data = Data(content.utf8)
    try data.write(to: url)
    DownloadFiles.writeMetadata(metadata(videoId: videoId, fileSize: Int64(data.count)), for: url)
    return url
}

struct DownloadFilesTests {
    @Test func sanitizedNameRemovesWhatFileSystemsReject() {
        #expect(DownloadFiles.sanitizedName("WAN Show: Oct/30") == "WAN Show Oct 30")
        #expect(DownloadFiles.sanitizedName("...hidden") == "hidden")
        #expect(DownloadFiles.sanitizedName("   ") == "Video")
        #expect(DownloadFiles.sanitizedName("/:?") == "Video")
        #expect(DownloadFiles.sanitizedName(String(repeating: "é", count: 200)).utf8.count <= 200)
    }

    @Test func uniqueDestinationAvoidsVideosAndSidecars() throws {
        let folder = try makeTempFolder()
        let first = DownloadFiles.uniqueDestination(name: "A", pathExtension: "mp4", in: folder)
        #expect(first.lastPathComponent == "A.mp4")
        try Data().write(to: first)
        #expect(DownloadFiles.uniqueDestination(name: "A", pathExtension: "mp4", in: folder).lastPathComponent == "A (2).mp4")

        // A leftover sidecar would otherwise attach itself to the new file.
        try Data().write(to: folder.appendingPathComponent("B.json"))
        #expect(DownloadFiles.uniqueDestination(name: "B", pathExtension: "mp4", in: folder).lastPathComponent == "B (2).mp4")
    }

    @Test func installSavesSidecarsNextToVideoAndExcludesItFromBackup() throws {
        let folder = try makeTempFolder()
        let temp = try makeTempFolder().appendingPathComponent("download.tmp")
        try Data("video".utf8).write(to: temp)
        let videoId = UUID().uuidString
        try Data("WEBVTT".utf8).write(to: DownloadFiles.pendingCaptionsURL(videoId: videoId))

        let installed = try DownloadFiles.install(tempFile: temp, name: "A", pathExtension: "mp4", metadata: metadata(videoId: videoId), in: folder)

        #expect(!FileManager.default.fileExists(atPath: temp.path))
        #expect(try installed.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup == true)
        #expect(FileManager.default.fileExists(atPath: folder.appendingPathComponent("A.json").path))
        #expect(FileManager.default.fileExists(atPath: folder.appendingPathComponent("A.vtt").path))
        #expect(!FileManager.default.fileExists(atPath: DownloadFiles.pendingCaptionsURL(videoId: videoId).path))

        let scanned = try #require(DownloadFiles.scan(in: folder).first)
        #expect(scanned.metadata == metadata(videoId: videoId, fileSize: 5))
    }

    @Test func sidecarsFollowVideoWhenRenamedOrMoved() throws {
        let folder = try makeTempFolder()
        let original = try makeDownload("A.mp4", in: folder)
        try Data("WEBVTT".utf8).write(to: DownloadFiles.captionsURL(for: original))

        let subfolder = folder.appendingPathComponent("Sub", isDirectory: true)
        try FileManager.default.createDirectory(at: subfolder, withIntermediateDirectories: true)
        let moved = subfolder.appendingPathComponent("Renamed.mp4")
        try FileManager.default.moveItem(at: original, to: moved)

        let scanned = try #require(DownloadFiles.scan(in: folder).first)
        #expect(scanned.metadata?.videoId == "vid1")
        #expect(FileManager.default.fileExists(atPath: subfolder.appendingPathComponent("Renamed.json").path))
        #expect(FileManager.default.fileExists(atPath: subfolder.appendingPathComponent("Renamed.vtt").path))
        #expect(!FileManager.default.fileExists(atPath: folder.appendingPathComponent("A.json").path))
    }

    @Test func deletingVideoInFilesCleansUpItsSidecars() throws {
        let folder = try makeTempFolder()
        let video = try makeDownload("A.mp4", in: folder)
        try Data("WEBVTT".utf8).write(to: DownloadFiles.captionsURL(for: video))
        try FileManager.default.removeItem(at: video)

        #expect(DownloadFiles.scan(in: folder).isEmpty)
        #expect(try FileManager.default.contentsOfDirectory(atPath: folder.path).isEmpty)
    }

    @Test func otherPeoplesFilesAreLeftAlone() throws {
        let folder = try makeTempFolder()
        // JSON and captions that aren't ours, with and without a matching video.
        try Data("{\"hello\":1}".utf8).write(to: folder.appendingPathComponent("config.json"))
        try Data("{\"hello\":1}".utf8).write(to: folder.appendingPathComponent("Home.json"))
        try Data("x".utf8).write(to: folder.appendingPathComponent("Home.mp4"))
        try Data("WEBVTT".utf8).write(to: folder.appendingPathComponent("Other.vtt"))

        let videos = DownloadFiles.scan(in: folder)

        #expect(videos.count == 1)
        #expect(videos.first?.metadata == nil)
        let remaining = try FileManager.default.contentsOfDirectory(atPath: folder.path).sorted()
        #expect(remaining == ["Home.json", "Home.mp4", "Other.vtt", "config.json"])
    }

    @Test func sidecarDoesNotApplyToDifferentVideoWithSameName() throws {
        let folder = try makeTempFolder()
        let video = try makeDownload("A.mp4", in: folder)
        // Replaced in Files with a different video under the same name.
        try Data("a much longer video".utf8).write(to: video)

        let scanned = try #require(DownloadFiles.scan(in: folder).first)
        #expect(scanned.metadata == nil)
        #expect(scanned.title == "A")
    }

    @Test func scanFindsOnlyVisibleVideosIncludingSubfolders() throws {
        let folder = try makeTempFolder()
        for name in ["a.mp4", "b.mov", "c.m4v", "notes.txt", ".hidden.mp4"] {
            try Data("x".utf8).write(to: folder.appendingPathComponent(name))
        }
        for subfolder in ["Sub", "Inbox"] {
            let url = folder.appendingPathComponent(subfolder, isDirectory: true)
            try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
            try Data("x".utf8).write(to: url.appendingPathComponent("\(subfolder).mp4"))
        }

        let paths = DownloadFiles.scan(in: folder).map(\.relativePath).sorted()
        #expect(paths == ["Sub/Sub.mp4", "a.mp4", "b.mov", "c.m4v"])
    }

    @Test func scanUsesMetadataForDownloadsAndFileNameForTheRest() throws {
        let folder = try makeTempFolder()
        _ = try makeDownload("download.mp4", in: folder, videoId: "vid9")
        try Data("a different size".utf8).write(to: folder.appendingPathComponent("My Home Movie.mp4"))

        let videos = DownloadFiles.scan(in: folder)
        let linked = try #require(videos.first { $0.metadata != nil })
        let unlinked = try #require(videos.first { $0.metadata == nil })
        #expect(linked.title == "A Video")
        #expect(linked.progressKey == "vid9")
        #expect(unlinked.title == "My Home Movie")
        #expect(unlinked.progressKey == "file:My Home Movie.mp4")
    }
}

@MainActor
struct DownloadProgressStoreTests {
    /// Records uploads and fails them on demand, like a connection coming and going.
    private final class FakeServer {
        var uploads: [(String, Int)] = []
        var failure: Error?
        var remoteProgress: Int?
        var remoteFailure: Error?
        var onUpload: (() -> Void)?
    }

    private func makeStore(_ server: FakeServer, fileURL: URL? = nil) throws -> DownloadProgressStore {
        let url = try fileURL ?? makeTempFolder().appendingPathComponent("progress.json")
        return DownloadProgressStore(
            fileURL: url,
            upload: { videoId, seconds in
                if let failure = server.failure { throw failure }
                server.uploads.append((videoId, seconds))
                server.onUpload?()
            },
            fetchRemote: { _ in
                if let failure = server.remoteFailure { throw failure }
                return server.remoteProgress
            }
        )
    }

    @Test func positionsWatchedOfflineAreSentOnceBackOnline() async throws {
        let server = FakeServer()
        let store = try makeStore(server)

        server.failure = URLError(.notConnectedToInternet)
        store.record(key: "vid1", videoId: "vid1", seconds: 120)
        await store.syncPending()
        #expect(store.entries["vid1"]?.needsSync == true)
        #expect(server.uploads.isEmpty)

        server.failure = nil
        await store.syncPending()
        #expect(store.entries["vid1"]?.needsSync == false)
        #expect(server.uploads.count == 1)
        #expect(server.uploads.first?.0 == "vid1")
        #expect(server.uploads.first?.1 == 120)

        // Nothing left to send.
        await store.syncPending()
        #expect(server.uploads.count == 1)
    }

    @Test func queueSurvivesRelaunch() async throws {
        let server = FakeServer()
        let fileURL = try makeTempFolder().appendingPathComponent("progress.json")
        try makeStore(server, fileURL: fileURL).record(key: "vid1", videoId: "vid1", seconds: 300)

        let relaunched = try makeStore(server, fileURL: fileURL)
        #expect(relaunched.entries["vid1"]?.needsSync == true)
        await relaunched.syncPending()
        #expect(server.uploads.first?.1 == 300)

        let relaunchedAgain = try makeStore(server, fileURL: fileURL)
        #expect(relaunchedAgain.entries["vid1"]?.needsSync == false)
    }

    @Test(arguments: [
        FloatplaneAPIError.httpError(statusCode: 500, message: nil),
        FloatplaneAPIError.httpError(statusCode: 401, message: nil),
        FloatplaneAPIError.httpError(statusCode: 429, message: nil),
        FloatplaneAPIError.notAuthenticated,
    ])
    func temporaryFailuresStayQueued(error: FloatplaneAPIError) async throws {
        let server = FakeServer()
        let store = try makeStore(server)
        store.record(key: "vid1", videoId: "vid1", seconds: 60)

        server.failure = error
        await store.syncPending()
        #expect(store.entries["vid1"]?.needsSync == true)
    }

    @Test func rejectedPositionsAreNotRetriedForever() async throws {
        let server = FakeServer()
        let store = try makeStore(server)
        store.record(key: "gone", videoId: "gone", seconds: 60)

        server.failure = FloatplaneAPIError.httpError(statusCode: 404, message: nil)
        await store.syncPending()

        #expect(store.entries["gone"]?.needsSync == false)
        #expect(store.entries["gone"]?.seconds == 60) // Still resumes locally.
    }

    @Test func positionRecordedDuringUploadStaysQueued() async throws {
        let server = FakeServer()
        let store = try makeStore(server)
        store.record(key: "vid1", videoId: "vid1", seconds: 60)

        server.onUpload = {
            server.onUpload = nil
            store.record(key: "vid1", videoId: "vid1", seconds: 90)
        }
        await store.syncPending()
        #expect(store.entries["vid1"]?.needsSync == true)

        await store.syncPending()
        #expect(store.entries["vid1"]?.needsSync == false)
        #expect(server.uploads.last?.1 == 90)
    }

    @Test func filesAddedByTheUserAreNeverUploaded() async throws {
        let server = FakeServer()
        let store = try makeStore(server)
        store.record(key: "file:home.mp4", videoId: nil, seconds: 60)

        await store.syncPending()

        #expect(server.uploads.isEmpty)
        #expect(store.entries["file:home.mp4"]?.seconds == 60)
    }

    @Test func zeroIsNotRecorded() throws {
        let store = try makeStore(FakeServer())
        store.record(key: "vid1", videoId: "vid1", seconds: 500)
        store.record(key: "vid1", videoId: "vid1", seconds: 0)
        #expect(store.entries["vid1"]?.seconds == 500)
    }

    @Test func loggingOutDropsQueueButKeepsResumePoints() async throws {
        let server = FakeServer()
        let store = try makeStore(server)
        store.record(key: "vid1", videoId: "vid1", seconds: 60)

        store.discardPendingSync()
        await store.syncPending()

        #expect(server.uploads.isEmpty)
        #expect(store.entries["vid1"]?.seconds == 60)
    }

    @Test func resumePrefersUnsyncedLocalThenServerThenSyncedLocal() async throws {
        let server = FakeServer()
        let store = try makeStore(server)
        server.remoteProgress = 900

        // Nothing local: Floatplane knows best.
        #expect(await store.resumeSeconds(key: "vid1", videoId: "vid1") == 900)

        // Watched offline and not synced yet: the local position is newer.
        store.record(key: "vid1", videoId: "vid1", seconds: 100)
        #expect(await store.resumeSeconds(key: "vid1", videoId: "vid1") == 100)

        // Synced, then watched further on another device: Floatplane is newer.
        await store.syncPending()
        #expect(await store.resumeSeconds(key: "vid1", videoId: "vid1") == 900)

        // Can't reach Floatplane: use what we have.
        server.remoteFailure = URLError(.notConnectedToInternet)
        #expect(await store.resumeSeconds(key: "vid1", videoId: "vid1") == 100)

        // A file the user added never asks Floatplane.
        store.record(key: "file:a.mp4", videoId: nil, seconds: 42)
        server.remoteFailure = nil
        #expect(await store.resumeSeconds(key: "file:a.mp4", videoId: nil) == 42)
    }
}
