//
//  DownloadManager.swift
//  FloatNative
//
//  Downloads videos into the FloatNative folder using a background URLSession,
//  so they keep going when the app is suspended or the system terminates it.
//

#if !os(tvOS)
import Combine
import UIKit

struct ActiveDownload: Identifiable, Equatable {
    enum State: Equatable {
        case queued
        case preparing
        case downloading(fraction: Double)
        case failed(String)
    }

    let metadata: DownloadMetadata
    var state: State
    var taskIdentifier: Int?
    /// The quality to match when this one's turn comes.
    var preferredHeight: Int?

    var id: String { metadata.videoId }

    var isInFlight: Bool {
        switch state {
        case .preparing, .downloading: return true
        case .queued, .failed: return false
        }
    }
}

/// When the next download may start. A burst of download requests makes
/// Cloudflare answer with a 429 and a five minute Retry-After, while requests
/// about a minute apart have gone through every time. So the queue runs one at a
/// time and spaces out its requests, and waits out any 429 that happens anyway.
/// Only download requests are limited, so the rest of the app is unaffected.
enum DownloadQueue {
    /// What Floatplane has been sending, for a response without the header.
    static let defaultDelay: TimeInterval = 300
    /// Time between download requests. The exact limit isn't published.
    static let spacing: TimeInterval = 65
    /// Quality for downloads started from a feed's menu, where there's no player
    /// to take the current quality from.
    static let menuDownloadHeight = 1080
    /// Starting right as the block ends risks hitting it again.
    static let margin: TimeInterval = 2

    static func next(in active: [ActiveDownload], waitingUntil: Date?, now: Date) -> ActiveDownload? {
        if let waitingUntil, waitingUntil > now { return nil }
        guard !active.contains(where: \.isInFlight) else { return nil }
        return active.first { $0.state == .queued }
    }

    /// When the next request may go out, if that's still in the future.
    static func earliestStart(lastRequestAt: Date?, waitingUntil: Date?, now: Date) -> Date? {
        let spaced = lastRequestAt?.addingTimeInterval(spacing)
        guard let start = [spaced, waitingUntil].compactMap({ $0 }).max(), start > now else { return nil }
        return start
    }

    /// The best version no taller than requested, so a 1080p request never pulls
    /// a 4K file. Without a request, or if everything is taller, the closest is used.
    /// `qualities` is sorted tallest first.
    static func quality(from qualities: [QualityVariant], preferredHeight: Int?) -> QualityVariant? {
        guard let preferredHeight else { return qualities.first }
        return qualities.first { $0.height <= preferredHeight } ?? qualities.last
    }

    static func waitEnd(retryAfter: TimeInterval?, now: Date) -> Date {
        now.addingTimeInterval((retryAfter ?? defaultDelay) + margin)
    }
}

/// Downloads that haven't started yet, kept on disk because working through a
/// long queue can outlast the app being open. Ones already started belong to the
/// background URLSession, which reconnects by itself.
struct SavedDownloadQueue: Codable, Equatable {
    struct Item: Codable, Equatable {
        let metadata: DownloadMetadata
        let preferredHeight: Int?
    }

    var items: [Item]
    var waitingUntil: Date?
    var lastRequestAt: Date? = nil

    static var fileURL: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("DownloadQueue.json")
    }

    static func load(from url: URL = fileURL) -> SavedDownloadQueue? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(SavedDownloadQueue.self, from: data)
    }

    func save(to url: URL = fileURL) {
        guard let data = try? JSONEncoder().encode(self) else { return }
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? data.write(to: url, options: .atomic)
    }
}

/// Everything needed to finish a download, carried in the task's `taskDescription`
/// so it survives the app being terminated mid-download. It never includes the
/// signed download URL.
private struct DownloadTaskInfo: Codable {
    let metadata: DownloadMetadata
    let fileName: String
    let pathExtension: String
    var preferredHeight: Int? = nil
    /// Continuing an interrupted transfer. If that fails, its link has probably
    /// expired, so the download starts over instead of reporting an error.
    var isResumed: Bool? = nil
}

@MainActor
final class DownloadManager: ObservableObject {
    static let shared = DownloadManager()
    private static let sessionIdentifier = "com.coulterpeterson.floatnative.downloads"

    @Published private(set) var active: [ActiveDownload] = []
    /// Set while waiting out Floatplane's download limit.
    @Published private(set) var waitingUntil: Date?

    /// Set by the app delegate when iOS relaunches the app for finished
    /// background transfers; must be called once their events are delivered.
    var backgroundEventsCompletion: (() -> Void)?

    private lazy var session: URLSession = {
        let configuration = URLSessionConfiguration.background(withIdentifier: Self.sessionIdentifier)
        configuration.sessionSendsLaunchEvents = true
        configuration.isDiscretionary = false
        return URLSession(configuration: configuration, delegate: sessionDelegate, delegateQueue: nil)
    }()
    private let sessionDelegate = DownloadSessionDelegate()

    /// Nothing starts until any tasks from a previous launch are reconnected,
    /// or the queue could run two at once.
    private var hasReconnected = false
    private var lastRequestAt: Date?
    private var wakeTask: Task<Void, Never>?
    private var foregroundObserver: NSObjectProtocol?
    private var networkObserver: AnyCancellable?

    private init() {
        if let saved = SavedDownloadQueue.load() {
            waitingUntil = saved.waitingUntil
            lastRequestAt = saved.lastRequestAt
            active = saved.items.map {
                ActiveDownload(metadata: $0.metadata, state: .queued, taskIdentifier: nil, preferredHeight: $0.preferredHeight)
            }
        }

        // Reconnect to anything still running from a previous launch.
        session.getAllTasks { tasks in
            let restored: [(Int, DownloadTaskInfo, Double)] = tasks.compactMap { task in
                guard task.state == .running || task.state == .suspended,
                      let info = DownloadSessionDelegate.info(from: task) else { return nil }
                return (task.taskIdentifier, info, task.progress.fractionCompleted)
            }
            Task { @MainActor in
                for (identifier, info, fraction) in restored {
                    self.setActive(ActiveDownload(
                        metadata: info.metadata,
                        state: .downloading(fraction: fraction),
                        taskIdentifier: identifier
                    ))
                }
                self.hasReconnected = true
                self.advance()
            }
        }

        networkObserver = NetworkMonitor.shared.$isOnline
            .removeDuplicates()
            .sink { [weak self] isOnline in
                guard isOnline else { return }
                // Published before the value changes, so check on the next pass.
                Task { @MainActor in self?.advance() }
            }

        // iOS stops the wait timer while the app is suspended.
        foregroundObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.advance() }
        }
    }

    func download(for videoId: String) -> ActiveDownload? {
        active.first { $0.id == videoId }
    }

    /// The download that starts next, which is the one a wait applies to.
    var nextQueued: ActiveDownload? {
        active.first { $0.state == .queued }
    }

    // MARK: - Starting

    /// Adds a video to the queue. `preferredHeight` caps the quality, as the one
    /// being watched or the feed's default; nil means the best available.
    func start(post: BlogPost, videoId: String, attachmentTitle: String?, duration: Double?, preferredHeight: Int?) {
        guard download(for: videoId) == nil else { return }

        var title = post.title
        if let attachmentTitle, !attachmentTitle.isEmpty, attachmentTitle != post.title {
            title += " - \(attachmentTitle)"
        }
        let channel = post.channel.channelObject
        let metadata = DownloadMetadata(
            postId: post.id,
            videoId: videoId,
            title: title,
            creatorName: post.creator.title,
            channelName: channel?.title,
            releaseDate: post.releaseDate,
            descriptionHTML: post.text,
            durationSeconds: duration.map { Int($0) },
            qualityLabel: nil,
            downloadedAt: Date()
        )
        saveImage(from: post.thumbnail?.fullURL, to: DownloadFiles.thumbnailURL(videoId: videoId))
        saveImage(from: channel?.icon.fullURL ?? post.creator.icon.fullURL, to: DownloadFiles.iconURL(videoId: videoId))
        setActive(ActiveDownload(metadata: metadata, state: .queued, taskIdentifier: nil, preferredHeight: preferredHeight))
        queueChanged()
    }

    /// Queues a post from somewhere that only has the feed's copy of it, which
    /// doesn't include its videos. Multi-part posts download their first part.
    /// Returns what to tell the user.
    func requestDownload(of post: BlogPost, preferredHeight: Int?) async -> (message: String, icon: String) {
        let failureIcon = "exclamationmark.circle.fill"
        let attachments: [VideoAttachmentModel]
        do {
            attachments = try await FloatplaneAPI.shared.getBlogPost(id: post.id).post.orderedVideoAttachments
        } catch where error.isOffline {
            return ("You're offline. Connect to add a download.", "wifi.slash")
        } catch {
            return ("Couldn't add download: \(error.localizedDescription)", failureIcon)
        }
        guard let attachment = attachments.first else {
            return ("No video available for download", failureIcon)
        }

        await DownloadLibrary.shared.refreshAndWait()
        if DownloadLibrary.shared.video(forVideoId: attachment.id) != nil {
            return ("Already in Downloads", "checkmark.circle.fill")
        }
        if download(for: attachment.id) != nil {
            return ("Already in your Downloads queue", "arrow.down.circle.fill")
        }

        start(
            post: post,
            videoId: attachment.id,
            attachmentTitle: attachments.count > 1 ? attachment.title : nil,
            duration: attachment.duration,
            preferredHeight: preferredHeight
        )
        return ("Added to Downloads", "arrow.down.circle.fill")
    }

    func retry(_ download: ActiveDownload) {
        setState(.queued, for: download.id)
        queueChanged()
    }

    // MARK: - Queue

    private func queueChanged() {
        saveQueue()
        advance()
    }

    private func saveQueue() {
        let items = active.filter { $0.state == .queued || $0.state == .preparing }
        SavedDownloadQueue(
            items: items.map { .init(metadata: $0.metadata, preferredHeight: $0.preferredHeight) },
            waitingUntil: waitingUntil,
            lastRequestAt: lastRequestAt
        ).save()
    }

    /// Starts the next download if nothing is running and no wait is in force.
    private func advance() {
        guard hasReconnected, NetworkMonitor.shared.isOnline else { return }

        let now = Date()
        guard let next = DownloadQueue.next(in: active, waitingUntil: nil, now: now) else {
            // Nothing is waiting on the clock, whatever it says.
            if !active.contains(where: \.isInFlight), waitingUntil != nil {
                waitingUntil = nil
                saveQueue()
            }
            return
        }

        // Continuing a transfer reuses its link, so no request counts toward the limit.
        if let resumeData = try? Data(contentsOf: DownloadFiles.resumeDataURL(videoId: next.id)) {
            try? FileManager.default.removeItem(at: DownloadFiles.resumeDataURL(videoId: next.id))
            resume(next, from: resumeData)
            return
        }

        if let start = DownloadQueue.earliestStart(lastRequestAt: lastRequestAt, waitingUntil: waitingUntil, now: now) {
            if waitingUntil != start {
                waitingUntil = start
                saveQueue()
            }
            scheduleWake(at: start)
            return
        }

        waitingUntil = nil
        lastRequestAt = now
        begin(next)
        saveQueue()
    }

    private func scheduleWake(at date: Date) {
        wakeTask?.cancel()
        wakeTask = Task { [weak self] in
            let delay = max(0, date.timeIntervalSinceNow)
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            guard !Task.isCancelled else { return }
            self?.advance()
        }
    }

    private func setState(_ state: ActiveDownload.State, for videoId: String) {
        guard let index = active.firstIndex(where: { $0.id == videoId }) else { return }
        active[index].state = state
    }

    private func resume(_ item: ActiveDownload, from resumeData: Data) {
        let info = DownloadTaskInfo(
            metadata: item.metadata,
            fileName: DownloadFiles.sanitizedName(item.metadata.title),
            pathExtension: "mp4",
            preferredHeight: item.preferredHeight,
            isResumed: true
        )
        let task = session.downloadTask(withResumeData: resumeData)
        task.taskDescription = (try? JSONEncoder().encode(info)).flatMap { String(data: $0, encoding: .utf8) }
        setActive(ActiveDownload(
            metadata: item.metadata,
            state: .downloading(fraction: 0),
            taskIdentifier: task.taskIdentifier,
            preferredHeight: item.preferredHeight
        ))
        saveQueue()
        task.resume()
    }

    private func begin(_ item: ActiveDownload) {
        let metadata = item.metadata
        let preferredHeight = item.preferredHeight
        setState(.preparing, for: metadata.videoId)

        Task {
            do {
                let deliveryInfo = try await FloatplaneAPI.shared.getDeliveryInfo(
                    scenario: .download,
                    entityId: metadata.videoId,
                    outputKind: .flat
                )
                let qualities = deliveryInfo.availableVariants()
                guard let quality = DownloadQueue.quality(from: qualities, preferredHeight: preferredHeight),
                      let url = URL(string: quality.url) else {
                    throw FloatplaneAPIError.malformedData(message: "No download variants available")
                }
                await saveCaptions(videoId: metadata.videoId)

                // Cancelled while we were waiting on Floatplane.
                guard download(for: metadata.videoId) != nil else { return }

                var tagged = metadata
                tagged.qualityLabel = quality.label
                let info = DownloadTaskInfo(
                    metadata: tagged,
                    fileName: DownloadFiles.sanitizedName(tagged.title),
                    pathExtension: url.pathExtension.isEmpty ? "mp4" : url.pathExtension,
                    preferredHeight: preferredHeight
                )
                let task = session.downloadTask(with: url)
                task.taskDescription = String(data: try JSONEncoder().encode(info), encoding: .utf8)
                setActive(ActiveDownload(metadata: tagged, state: .downloading(fraction: 0), taskIdentifier: task.taskIdentifier, preferredHeight: preferredHeight))
                saveQueue()
                task.resume()
            } catch FloatplaneAPIError.rateLimited(let retryAfter) {
                handleRateLimited(videoId: metadata.videoId, retryAfter: retryAfter)
            } catch {
                guard download(for: metadata.videoId) != nil else { return }
                // A request cut off by the app being suspended or the connection
                // dropping isn't a failure. It tries again when it can.
                if error.isOffline || (error is URLError && UIApplication.shared.applicationState != .active) {
                    setState(.queued, for: metadata.videoId)
                    saveQueue()
                    return
                }
                setState(.failed(Self.message(for: error)), for: metadata.videoId)
                queueChanged()
            }
        }
    }

    /// Puts the download back in line and waits out the delay Floatplane gave.
    private func handleRateLimited(videoId: String, retryAfter: TimeInterval?) {
        setState(.queued, for: videoId)
        waitingUntil = DownloadQueue.waitEnd(retryAfter: retryAfter, now: Date())
        queueChanged()
    }

    /// Stops a running download, or removes a queued or failed one.
    func cancel(_ download: ActiveDownload) {
        active.removeAll { $0.id == download.id }
        queueChanged()
        try? FileManager.default.removeItem(at: DownloadFiles.thumbnailURL(videoId: download.id))
        try? FileManager.default.removeItem(at: DownloadFiles.iconURL(videoId: download.id))
        try? FileManager.default.removeItem(at: DownloadFiles.pendingCaptionsURL(videoId: download.id))
        try? FileManager.default.removeItem(at: DownloadFiles.resumeDataURL(videoId: download.id))
        guard let identifier = download.taskIdentifier else { return }
        session.getAllTasks { tasks in
            tasks.first { $0.taskIdentifier == identifier }?.cancel()
        }
    }

    private func setActive(_ download: ActiveDownload) {
        if let index = active.firstIndex(where: { $0.id == download.id }) {
            active[index] = download
        } else {
            active.append(download)
        }
    }

    /// Fetched now because the download may outlive the connection, and these
    /// are needed offline. Failure is fine: the thumbnail falls back to a frame
    /// from the video, and the channel row to no icon.
    private func saveImage(from url: URL?, to destination: URL) {
        guard let url else { return }
        Task.detached(priority: .utility) {
            guard let (data, _) = try? await URLSession.shared.data(from: url) else { return }
            try? data.write(to: destination, options: .atomic)
        }
    }

    /// Held in app storage until the video lands, then moved next to it. A video
    /// without captions, or a failed fetch, just has no CC button.
    private func saveCaptions(videoId: String) async {
        guard let tracks = try? await FloatplaneAPI.shared.getVideoContent(id: videoId).textTracks,
              let captions = await fetchCaptionFile(from: tracks) else { return }
        try? captions.write(to: DownloadFiles.pendingCaptionsURL(videoId: videoId), options: .atomic)
    }

    // MARK: - Delegate Events

    fileprivate func handleProgress(videoId: String, taskIdentifier: Int, fraction: Double) {
        guard let index = active.firstIndex(where: { $0.id == videoId }) else { return }
        // Called for every chunk received; only redraw for visible changes.
        if case .downloading(let current) = active[index].state, fraction - current < 0.01 { return }
        active[index].state = .downloading(fraction: fraction)
        active[index].taskIdentifier = taskIdentifier
    }

    fileprivate func handleFinished(videoId: String) {
        active.removeAll { $0.id == videoId }
        DownloadLibrary.shared.refresh()
        queueChanged()
    }

    fileprivate func handleFailure(info: DownloadTaskInfo, message: String, resumeData: Data? = nil) {
        saveResumeData(resumeData, videoId: info.metadata.videoId)
        setActive(ActiveDownload(metadata: info.metadata, state: .failed(message), taskIdentifier: nil, preferredHeight: info.preferredHeight))
        queueChanged()
    }

    /// iOS stopped a transfer the user didn't cancel, as it does for every running
    /// download when the app is force-quit. It goes back to the front of the queue.
    /// Without resume data it starts over, which the same path also handles for a
    /// resumed transfer whose link has expired.
    fileprivate func handleInterrupted(info: DownloadTaskInfo, resumeData: Data?) {
        saveResumeData(resumeData, videoId: info.metadata.videoId)
        active.removeAll { $0.id == info.metadata.videoId }
        active.insert(
            ActiveDownload(metadata: info.metadata, state: .queued, taskIdentifier: nil, preferredHeight: info.preferredHeight),
            at: 0
        )
        queueChanged()
    }

    private func saveResumeData(_ resumeData: Data?, videoId: String) {
        try? resumeData?.write(to: DownloadFiles.resumeDataURL(videoId: videoId), options: .atomic)
    }

    /// The file server refused the signed URL for the same reason.
    fileprivate func handleFileRateLimited(info: DownloadTaskInfo, retryAfter: TimeInterval?) {
        setActive(ActiveDownload(metadata: info.metadata, state: .queued, taskIdentifier: nil, preferredHeight: info.preferredHeight))
        handleRateLimited(videoId: info.metadata.videoId, retryAfter: retryAfter)
    }

    fileprivate func handleBackgroundEventsFinished() {
        backgroundEventsCompletion?()
        backgroundEventsCompletion = nil
    }

    nonisolated fileprivate static func message(for error: Error) -> String {
        if let urlError = error as? URLError {
            switch urlError.code {
            case .notConnectedToInternet, .networkConnectionLost:
                return "Network error. Check your connection."
            case .timedOut:
                return "Download timed out"
            case .cannotWriteToFile:
                return "Storage error. Check available space."
            default:
                break
            }
        }
        let nsError = error as NSError
        if nsError.domain == NSCocoaErrorDomain && nsError.code == NSFileWriteOutOfSpaceError {
            return "Not enough storage space"
        }
        return "Download failed: \(error.localizedDescription)"
    }
}

// MARK: - Session Delegate

private final class DownloadSessionDelegate: NSObject, URLSessionDownloadDelegate {
    static func info(from task: URLSessionTask) -> DownloadTaskInfo? {
        guard let data = task.taskDescription?.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(DownloadTaskInfo.self, from: data)
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        guard totalBytesExpectedToWrite > 0, let info = Self.info(from: downloadTask) else { return }
        let fraction = Double(totalBytesWritten) / Double(totalBytesExpectedToWrite)
        let identifier = downloadTask.taskIdentifier
        Task { @MainActor in
            DownloadManager.shared.handleProgress(videoId: info.metadata.videoId, taskIdentifier: identifier, fraction: fraction)
        }
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        guard let info = Self.info(from: downloadTask) else { return }

        // An HTTP error (say, an expired signed URL) also lands here, with the
        // error page as the "download".
        if let response = downloadTask.response as? HTTPURLResponse, response.statusCode == 429 {
            let retryAfter = response.value(forHTTPHeaderField: "Retry-After").flatMap(TimeInterval.init)
            Task { @MainActor in
                DownloadManager.shared.handleFileRateLimited(info: info, retryAfter: retryAfter)
            }
            return
        }
        if let response = downloadTask.response as? HTTPURLResponse, !(200...299).contains(response.statusCode) {
            let message = "Download failed (HTTP \(response.statusCode)). Try again."
            Task { @MainActor in
                if info.isResumed == true {
                    DownloadManager.shared.handleInterrupted(info: info, resumeData: nil)
                } else {
                    DownloadManager.shared.handleFailure(info: info, message: message)
                }
            }
            return
        }

        // The temp file is deleted when this method returns, so move it now.
        do {
            _ = try DownloadFiles.install(
                tempFile: location,
                name: info.fileName,
                pathExtension: info.pathExtension,
                metadata: info.metadata,
                in: DownloadFiles.folderURL
            )
        } catch {
            let message = DownloadManager.message(for: error)
            Task { @MainActor in
                DownloadManager.shared.handleFailure(info: info, message: message)
            }
            return
        }

        Task { @MainActor in
            DownloadManager.shared.handleFinished(videoId: info.metadata.videoId)
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let error, let info = Self.info(from: task) else { return }
        let nsError = error as NSError
        let resumeData = nsError.userInfo[NSURLSessionDownloadTaskResumeData] as? Data

        if nsError.code == NSURLErrorCancelled {
            // Without a reason, the user cancelled; the entry is already gone.
            guard nsError.userInfo[NSURLErrorBackgroundTaskCancelledReasonKey] != nil else { return }
            Task { @MainActor in
                DownloadManager.shared.handleInterrupted(info: info, resumeData: resumeData)
            }
            return
        }
        if info.isResumed == true {
            Task { @MainActor in
                DownloadManager.shared.handleInterrupted(info: info, resumeData: resumeData)
            }
            return
        }

        let message = DownloadManager.message(for: error)
        Task { @MainActor in
            DownloadManager.shared.handleFailure(info: info, message: message, resumeData: resumeData)
        }
    }

    func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
        Task { @MainActor in
            DownloadManager.shared.handleBackgroundEventsFinished()
        }
    }
}
#endif
