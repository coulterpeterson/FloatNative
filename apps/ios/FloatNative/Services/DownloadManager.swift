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
        case preparing
        case downloading(fraction: Double)
        case failed(String)
    }

    let metadata: DownloadMetadata
    var state: State
    var taskIdentifier: Int?

    var id: String { metadata.videoId }
}

/// Everything needed to finish a download, carried in the task's `taskDescription`
/// so it survives the app being terminated mid-download. It never includes the
/// signed download URL.
private struct DownloadTaskInfo: Codable {
    let metadata: DownloadMetadata
    let fileName: String
    let pathExtension: String
}

@MainActor
final class DownloadManager: ObservableObject {
    static let shared = DownloadManager()
    private static let sessionIdentifier = "com.coulterpeterson.floatnative.downloads"

    @Published private(set) var active: [ActiveDownload] = []

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

    private init() {
        // Reconnect to anything still running from a previous launch.
        session.getAllTasks { tasks in
            let restored: [(Int, DownloadTaskInfo, Double)] = tasks.compactMap { task in
                guard task.state == .running || task.state == .suspended,
                      let info = DownloadSessionDelegate.info(from: task) else { return nil }
                return (task.taskIdentifier, info, task.progress.fractionCompleted)
            }
            Task { @MainActor in
                for (identifier, info, fraction) in restored where !self.active.contains(where: { $0.id == info.metadata.videoId }) {
                    self.active.append(ActiveDownload(
                        metadata: info.metadata,
                        state: .downloading(fraction: fraction),
                        taskIdentifier: identifier
                    ))
                }
            }
        }
    }

    func download(for videoId: String) -> ActiveDownload? {
        active.first { $0.id == videoId }
    }

    // MARK: - Starting

    /// Queues a video. `preferredHeight` keeps the quality the user is watching
    /// when Floatplane offers it for download; otherwise the best one is used.
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
        begin(metadata: metadata, preferredHeight: preferredHeight)
    }

    func retry(_ download: ActiveDownload) {
        begin(metadata: download.metadata, preferredHeight: nil)
    }

    private func begin(metadata: DownloadMetadata, preferredHeight: Int?) {
        setActive(ActiveDownload(metadata: metadata, state: .preparing, taskIdentifier: nil))

        Task {
            do {
                let deliveryInfo = try await FloatplaneAPI.shared.getDeliveryInfo(
                    scenario: .download,
                    entityId: metadata.videoId,
                    outputKind: .flat
                )
                let qualities = deliveryInfo.availableVariants()
                let matching = qualities.first { $0.height == preferredHeight }
                guard let quality = matching ?? qualities.first, let url = URL(string: quality.url) else {
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
                    pathExtension: url.pathExtension.isEmpty ? "mp4" : url.pathExtension
                )
                let task = session.downloadTask(with: url)
                task.taskDescription = String(data: try JSONEncoder().encode(info), encoding: .utf8)
                setActive(ActiveDownload(metadata: tagged, state: .downloading(fraction: 0), taskIdentifier: task.taskIdentifier))
                task.resume()
            } catch {
                setActive(ActiveDownload(metadata: metadata, state: .failed(Self.message(for: error)), taskIdentifier: nil))
            }
        }
    }

    /// Stops a running download, or clears a failed one.
    func cancel(_ download: ActiveDownload) {
        active.removeAll { $0.id == download.id }
        try? FileManager.default.removeItem(at: DownloadFiles.thumbnailURL(videoId: download.id))
        try? FileManager.default.removeItem(at: DownloadFiles.iconURL(videoId: download.id))
        try? FileManager.default.removeItem(at: DownloadFiles.pendingCaptionsURL(videoId: download.id))
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
    }

    fileprivate func handleFailure(metadata: DownloadMetadata, message: String) {
        setActive(ActiveDownload(metadata: metadata, state: .failed(message), taskIdentifier: nil))
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
        if let response = downloadTask.response as? HTTPURLResponse, !(200...299).contains(response.statusCode) {
            let message = "Download failed (HTTP \(response.statusCode)). Try again."
            Task { @MainActor in
                DownloadManager.shared.handleFailure(metadata: info.metadata, message: message)
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
                DownloadManager.shared.handleFailure(metadata: info.metadata, message: message)
            }
            return
        }

        Task { @MainActor in
            DownloadManager.shared.handleFinished(videoId: info.metadata.videoId)
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let error, let info = Self.info(from: task) else { return }
        // The user cancelled; the entry is already gone.
        if (error as NSError).code == NSURLErrorCancelled { return }

        let message = DownloadManager.message(for: error)
        Task { @MainActor in
            DownloadManager.shared.handleFailure(metadata: info.metadata, message: message)
        }
    }

    func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
        Task { @MainActor in
            DownloadManager.shared.handleBackgroundEventsFinished()
        }
    }
}
#endif
