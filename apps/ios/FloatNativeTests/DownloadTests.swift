//
//  DownloadTests.swift
//  FloatNativeTests
//
//  The on-device folder and the offline progress queue.
//

import Testing
import Foundation
import SwiftUI
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
        FloatplaneAPIError.rateLimited(retryAfter: 300),
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

struct DownloadQueueTests {
    private func download(_ id: String, _ state: ActiveDownload.State) -> ActiveDownload {
        ActiveDownload(metadata: metadata(videoId: id), state: state, taskIdentifier: nil)
    }

    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    @Test func startsTheOldestQueuedFirst() {
        let active = [download("a", .queued), download("b", .queued)]
        #expect(DownloadQueue.next(in: active, waitingUntil: nil, now: now)?.id == "a")
    }

    @Test(arguments: [ActiveDownload.State.preparing, .downloading(fraction: 0.5)])
    func runsOneAtATime(running: ActiveDownload.State) {
        let active = [download("a", running), download("b", .queued)]
        #expect(DownloadQueue.next(in: active, waitingUntil: nil, now: now) == nil)
    }

    @Test func aFailedDownloadDoesNotHoldUpTheQueue() {
        let active = [download("a", .failed("Nope")), download("b", .queued)]
        #expect(DownloadQueue.next(in: active, waitingUntil: nil, now: now)?.id == "b")
    }

    @Test func waitsOutTheLimit() {
        let active = [download("a", .queued)]
        #expect(DownloadQueue.next(in: active, waitingUntil: now.addingTimeInterval(1), now: now) == nil)
        #expect(DownloadQueue.next(in: active, waitingUntil: now.addingTimeInterval(-1), now: now)?.id == "a")
    }

    @Test func waitEndUsesFloatplanesDelayOrTheDefault() {
        #expect(DownloadQueue.waitEnd(retryAfter: 120, now: now) == now.addingTimeInterval(122))
        #expect(DownloadQueue.waitEnd(retryAfter: nil, now: now) == now.addingTimeInterval(302))
    }

    @Test func spacesOutRequests() {
        // First ever request goes straight away.
        #expect(DownloadQueue.earliestStart(lastRequestAt: nil, waitingUntil: nil, now: now) == nil)

        let justNow = now.addingTimeInterval(-5)
        #expect(DownloadQueue.earliestStart(lastRequestAt: justNow, waitingUntil: nil, now: now) == justNow.addingTimeInterval(65))

        let longAgo = now.addingTimeInterval(-66)
        #expect(DownloadQueue.earliestStart(lastRequestAt: longAgo, waitingUntil: nil, now: now) == nil)
    }

    @Test func aRateLimitOutranksSpacing() {
        let block = now.addingTimeInterval(280)
        #expect(DownloadQueue.earliestStart(lastRequestAt: now, waitingUntil: block, now: now) == block)
    }

    private func variants(_ heights: Int...) -> [QualityVariant] {
        heights.map { QualityVariant(name: "\($0)", label: "\($0)p", url: "u", resolution: "", width: 0, height: $0, order: 0) }
    }

    @Test func choosesRequestedQualityWhenOffered() {
        let picked = DownloadQueue.quality(from: variants(2160, 1080, 720, 360), preferredHeight: 1080)
        #expect(picked?.height == 1080)
    }

    @Test func neverGoesAboveTheRequestedQuality() {
        // No 1080p on offer, and 4K isn't what was asked for.
        #expect(DownloadQueue.quality(from: variants(2160, 720, 360), preferredHeight: 1080)?.height == 720)
        // A smaller source just gets its best.
        #expect(DownloadQueue.quality(from: variants(720, 480), preferredHeight: 1080)?.height == 720)
        // Wide videos have shorter heights at the same width.
        #expect(DownloadQueue.quality(from: variants(1600, 800, 536), preferredHeight: 1080)?.height == 800)
    }

    @Test func fallsBackWhenNothingFitsOrNothingWasRequested() {
        #expect(DownloadQueue.quality(from: variants(2160, 1440), preferredHeight: 1080)?.height == 1440)
        #expect(DownloadQueue.quality(from: variants(2160, 1080), preferredHeight: nil)?.height == 2160)
        #expect(DownloadQueue.quality(from: [], preferredHeight: 1080) == nil)
    }

    @Test func emptyQueueStartsNothing() {
        #expect(DownloadQueue.next(in: [download("a", .failed("Nope"))], waitingUntil: nil, now: now) == nil)
    }

    @Test func savedQueueSurvivesARoundTrip() throws {
        let url = try makeTempFolder().appendingPathComponent("queue.json")
        let saved = SavedDownloadQueue(
            items: [.init(metadata: metadata(videoId: "a"), preferredHeight: 1080)],
            waitingUntil: now,
            lastRequestAt: now
        )
        saved.save(to: url)
        #expect(SavedDownloadQueue.load(from: url) == saved)
    }
}

struct OfflineErrorTests {
    @Test func noConnectionCountsAsOffline() {
        #expect(URLError(.notConnectedToInternet).isOffline)
        #expect(URLError(.dataNotAllowed).isOffline)
        #expect(FloatplaneAPIError.networkError(URLError(.notConnectedToInternet)).isOffline)
        #expect(CompanionAPIError.networkError(URLError(.notConnectedToInternet)).isOffline)
    }

    @Test func realFailuresStillShowAsErrors() {
        #expect(!URLError(.timedOut).isOffline)
        #expect(!URLError(.badServerResponse).isOffline)
        #expect(!FloatplaneAPIError.httpError(statusCode: 500, message: nil).isOffline)
        #expect(!FloatplaneAPIError.networkError(URLError(.timedOut)).isOffline)
    }
}

struct TokenRefreshDecisionTests {
    private func body(_ names: String...) -> Data {
        let errors = names.map { "{\"id\":\"x\",\"name\":\"\($0)\",\"message\":\"m\"}" }.joined(separator: ",")
        return Data("{\"errors\":[\(errors)],\"message\":\"m\"}".utf8)
    }

    @Test func expiredTokenOn403Refreshes() {
        #expect(FloatplaneAPI.shouldRefreshToken(statusCode: 403, body: body("notLoggedInError"), allowRefreshOn403: false))
        // Floatplane sends both when it can't tell who's asking.
        #expect(FloatplaneAPI.shouldRefreshToken(statusCode: 403, body: body("notLoggedInError", "notSubscribedError"), allowRefreshOn403: false))
    }

    @Test func paywalled403DoesNotRefresh() {
        #expect(!FloatplaneAPI.shouldRefreshToken(statusCode: 403, body: body("notSubscribedError"), allowRefreshOn403: false))
    }

    @Test func htmlOrEmpty403DoesNotRefresh() {
        #expect(!FloatplaneAPI.shouldRefreshToken(statusCode: 403, body: Data("<html>Forbidden</html>".utf8), allowRefreshOn403: false))
        #expect(!FloatplaneAPI.shouldRefreshToken(statusCode: 403, body: Data(), allowRefreshOn403: false))
    }

    @Test func callsThatAlwaysRefreshOn403StillDo() {
        #expect(FloatplaneAPI.shouldRefreshToken(statusCode: 403, body: Data(), allowRefreshOn403: true))
    }

    @Test func otherStatuses() {
        #expect(FloatplaneAPI.shouldRefreshToken(statusCode: 401, body: Data(), allowRefreshOn403: false))
        #expect(!FloatplaneAPI.shouldRefreshToken(statusCode: 500, body: body("notLoggedInError"), allowRefreshOn403: false))
        #expect(!FloatplaneAPI.shouldRefreshToken(statusCode: 429, body: Data(), allowRefreshOn403: true))
    }
}

@MainActor
struct TabCoordinatorPlayTests {
    private let video = LocalVideo(
        url: URL(fileURLWithPath: "/tmp/video.mp4"),
        relativePath: "video.mp4",
        metadata: nil,
        fileSize: 1,
        addedAt: Date()
    )

    @Test func replacesTheCurrentScreen() {
        let coordinator = TabCoordinator()
        coordinator.path.append(NavigationDestination.history)
        coordinator.path.append(NavigationDestination.settings)

        coordinator.play(video, replacingCurrent: true)

        #expect(coordinator.path.count == 2)
    }

    @Test func pushesByDefault() {
        let coordinator = TabCoordinator()
        coordinator.path.append(NavigationDestination.history)

        coordinator.play(video)

        #expect(coordinator.path.count == 2)
    }

    @Test func replacingWithNothingOnThePathStillPlays() {
        let coordinator = TabCoordinator()

        coordinator.play(video, replacingCurrent: true)

        #expect(coordinator.path.count == 1)
    }
}

#if !os(tvOS)
struct AdaptiveLayoutTests {
    /// Apple says an open iPhone Duo is regular in both directions and a closed one acts
    /// like any other iPhone. Its point sizes come from third-party measurements.
    @Test(arguments: [
        (CGSize(width: 951, height: 669), UserInterfaceSizeClass.regular, UserInterfaceSizeClass.regular, true),   // Duo open, wide
        (CGSize(width: 669, height: 951), .regular, .regular, false),  // Duo open, upright
        (CGSize(width: 466, height: 678), .compact, .regular, false),  // Duo closed
        (CGSize(width: 678, height: 466), .compact, .compact, false),  // Duo closed, like a tent
        (CGSize(width: 402, height: 874), .compact, .regular, false),  // iPhone
        (CGSize(width: 956, height: 440), .regular, .compact, false),  // Large iPhone on its side
        (CGSize(width: 1194, height: 834), .regular, .regular, true),  // iPad landscape
        (CGSize(width: 834, height: 1194), .regular, .regular, false)  // iPad portrait
    ])
    func playerGoesBesideDetailsOnlyWithRoom(
        size: CGSize,
        horizontal: UserInterfaceSizeClass,
        vertical: UserInterfaceSizeClass,
        expected: Bool
    ) {
        #expect(PlayerPageArrangement.isSideBySide(size: size, horizontal: horizontal, vertical: vertical) == expected)
    }

    @Test func playerColumnEndsAtTheFold() {
        typealias Arrangement = PlayerPageArrangement
        #expect(Arrangement.playerColumnWidth(totalWidth: 1000, foldX: nil) == 620)
        #expect(Arrangement.playerColumnWidth(totalWidth: 1000, foldX: 500) == 500)
        #expect(Arrangement.playerColumnWidth(totalWidth: 951, foldX: 475.5) == 476)
        // Not a fold worth lining up with: it would leave no room for one column.
        #expect(Arrangement.playerColumnWidth(totalWidth: 1000, foldX: 100) == 620)
        #expect(Arrangement.playerColumnWidth(totalWidth: 1000, foldX: 900) == 620)
    }

    @Test func rotationLeavesFullscreenAloneOnAnOpenDuoOnly() {
        typealias Manager = AVPlayerManager
        #expect(!Manager.rotationControlsFullScreen(idiom: .phone, horizontal: .regular, vertical: .regular))
        #expect(Manager.rotationControlsFullScreen(idiom: .phone, horizontal: .compact, vertical: .regular))
        #expect(Manager.rotationControlsFullScreen(idiom: .phone, horizontal: .compact, vertical: .compact))
        // A large iPhone on its side, after the rotation has happened.
        #expect(Manager.rotationControlsFullScreen(idiom: .phone, horizontal: .regular, vertical: .compact))
        #expect(Manager.rotationControlsFullScreen(idiom: .pad, horizontal: .regular, vertical: .regular))
    }

    /// SwiftUI's rule for how many columns an adaptive grid fits.
    private func columnCount(contentWidth: CGFloat) -> Int {
        Int((contentWidth + 16) / (AdaptiveGrid.minimumColumnWidth + 16))
    }

    @Test func columnsCentreOnTheFold() {
        // The bars on an iPhone Duo's inner screen leave the fold right of centre.
        #expect(Fold.columnsInset(totalWidth: 868, foldX: 475.5) == 83)
        #expect(Fold.columnsInset(totalWidth: 868, foldX: 392.5) == -83)
        // Not folded, or a division too far to one side to use.
        #expect(Fold.columnsInset(totalWidth: 868, foldX: nil) == 0)
        #expect(Fold.columnsInset(totalWidth: 868, foldX: 100) == 0)
        // A fold from the closed screen on its side, applied to the open screen's width.
        #expect(Fold.columnsInset(totalWidth: 867, foldX: 297) == 0)
        // Columns already indented from the leading edge need less to reach the fold.
        #expect(Fold.columnsInset(totalWidth: 868, foldX: 475.5, leadingIndent: 40) == 43)
    }

    @Test func twoColumnsSurviveCentringOnTheFold() {
        let width: CGFloat = 868
        let inset = Fold.columnsInset(totalWidth: width, foldX: 475.5)
        let padding: CGFloat = 32
        #expect(columnCount(contentWidth: width - padding) == 2)
        #expect(columnCount(contentWidth: width - padding - inset) == 2)
    }

    @Test func gridFitsEachScreen() {
        let padding: CGFloat = 32
        #expect(columnCount(contentWidth: 320 - padding) == 1)  // Smallest iPhone
        #expect(columnCount(contentWidth: 466 - padding) == 1)  // Duo closed
        #expect(columnCount(contentWidth: 669 - padding) == 2)  // Duo open, upright
        #expect(columnCount(contentWidth: 834 - padding) == 2)  // iPad portrait, same as before
        #expect(columnCount(contentWidth: 1194 - padding) == 3) // iPad landscape, same as before
        #expect(columnCount(contentWidth: 1366 - padding) == 4) // Large iPad landscape, same as before
    }
}
#endif
