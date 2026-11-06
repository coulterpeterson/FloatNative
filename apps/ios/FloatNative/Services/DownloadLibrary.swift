//
//  DownloadLibrary.swift
//  FloatNative
//
//  The on-device "FloatNative" folder. The app's Documents directory is exposed
//  in the Files app (UIFileSharingEnabled + LSSupportsOpeningDocumentsInPlace),
//  where iOS labels it with the app's name, the same way VLC's folder works.
//  Downloads land here, and any video the user drops in shows up in the app.
//

#if !os(tvOS)
import AVFoundation
import Combine
import CryptoKit
import UIKit
import UniformTypeIdentifiers

// MARK: - Models

/// What we know about a video downloaded from Floatplane, saved as a JSON file
/// next to the video ("Title.json" beside "Title.mp4") so it can be read offline.
struct DownloadMetadata: Codable, Hashable {
    let postId: String
    let videoId: String
    let title: String
    let creatorName: String
    /// nil when the post isn't on a channel; the creator is shown instead.
    let channelName: String?
    let releaseDate: Date?
    /// The post's description, as the HTML Floatplane sends.
    let descriptionHTML: String?
    let durationSeconds: Int?
    var qualityLabel: String?
    let downloadedAt: Date
    /// Size of the video this describes, set when it's saved. Lets a sidecar find
    /// its video again after a rename in the Files app, and stops it applying to
    /// a different video that happens to share the name.
    var fileSize: Int64?
}

struct LocalVideo: Identifiable, Hashable {
    let url: URL
    /// Path relative to the FloatNative folder.
    let relativePath: String
    /// nil for videos the user added through the Files app.
    let metadata: DownloadMetadata?
    let fileSize: Int64
    let addedAt: Date

    var id: String { relativePath }
    var title: String { metadata?.title ?? url.deletingPathExtension().lastPathComponent }
    /// Local progress key. Files added by the user have no Floatplane video to sync to.
    var progressKey: String { metadata?.videoId ?? "file:\(relativePath)" }
}

// MARK: - File Helpers

/// File-system operations, free of app state so the download delegate can call
/// them synchronously off the main actor.
enum DownloadFiles {
    private static let metadataAttribute = "com.coulterpeterson.floatnative.download"

    static var folderURL: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }

    /// Thumbnails, channel icons and captions waiting on their video, kept out
    /// of the user-visible folder.
    static var assetsURL: URL {
        let url = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("DownloadAssets", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    static func thumbnailURL(videoId: String) -> URL {
        assetsURL.appendingPathComponent("\(videoId).jpg")
    }

    static func iconURL(videoId: String) -> URL {
        assetsURL.appendingPathComponent("\(videoId)-icon.jpg")
    }

    static func pendingCaptionsURL(videoId: String) -> URL {
        assetsURL.appendingPathComponent("\(videoId).vtt")
    }

    static func resumeDataURL(videoId: String) -> URL {
        assetsURL.appendingPathComponent("\(videoId).resume")
    }

    static func sidecarURL(for video: URL) -> URL {
        video.deletingPathExtension().appendingPathExtension("json")
    }

    static func captionsURL(for video: URL) -> URL {
        video.deletingPathExtension().appendingPathExtension("vtt")
    }

    /// Turns a post title into a safe file name, without the extension.
    static func sanitizedName(_ title: String) -> String {
        let illegal = CharacterSet(charactersIn: "/\\:?*\"<>|").union(.controlCharacters).union(.newlines)
        var name = title.components(separatedBy: illegal).joined(separator: " ")
            .components(separatedBy: .whitespaces).filter { !$0.isEmpty }.joined(separator: " ")
        // A leading dot would hide the file from the Files app and from scan(in:).
        while name.hasPrefix(".") { name.removeFirst() }
        // File names are capped at 255 bytes; leave room for " (2).mp4".
        while name.utf8.count > 200 { name.removeLast() }
        return name.isEmpty ? "Video" : name
    }

    /// A destination that doesn't collide with an existing file or sidecar:
    /// "Title.mp4", then "Title (2).mp4", and so on.
    static func uniqueDestination(name: String, pathExtension: String, in folder: URL) -> URL {
        var candidate = folder.appendingPathComponent(name).appendingPathExtension(pathExtension)
        var suffix = 2
        while FileManager.default.fileExists(atPath: candidate.path)
                || FileManager.default.fileExists(atPath: sidecarURL(for: candidate).path) {
            candidate = folder.appendingPathComponent("\(name) (\(suffix))").appendingPathExtension(pathExtension)
            suffix += 1
        }
        return candidate
    }

    /// Moves a finished download into the folder and saves its sidecar files.
    static func install(tempFile: URL, name: String, pathExtension: String, metadata: DownloadMetadata, in folder: URL) throws -> URL {
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        var destination = uniqueDestination(name: name, pathExtension: pathExtension, in: folder)
        try FileManager.default.moveItem(at: tempFile, to: destination)

        var metadata = metadata
        let size = try? destination.resourceValues(forKeys: [.fileSizeKey]).fileSize
        metadata.fileSize = size.flatMap { $0 }.map(Int64.init)
        writeMetadata(metadata, for: destination)

        let pendingCaptions = pendingCaptionsURL(videoId: metadata.videoId)
        if FileManager.default.fileExists(atPath: pendingCaptions.path) {
            try? FileManager.default.moveItem(at: pendingCaptions, to: captionsURL(for: destination))
        }

        // Documents is included in iCloud and device backups by default, and
        // these files can be re-downloaded.
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? destination.setResourceValues(values)
        return destination
    }

    static func writeMetadata(_ metadata: DownloadMetadata, for video: URL) {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        guard let data = try? encoder.encode(metadata) else { return }
        try? data.write(to: sidecarURL(for: video), options: .atomic)
    }

    /// nil for any JSON file that isn't one of ours.
    private static func readSidecar(at url: URL) -> DownloadMetadata? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try? decoder.decode(DownloadMetadata.self, from: data)
    }

    /// Removes a video's sidecar files.
    static func removeSidecars(of video: URL) {
        try? FileManager.default.removeItem(at: sidecarURL(for: video))
        try? FileManager.default.removeItem(at: captionsURL(for: video))
    }

    /// Every video in the folder and its subfolders, newest first. Also keeps
    /// sidecars in step with changes made in the Files app: a renamed or moved
    /// video gets its sidecars back, and a deleted video's sidecars are removed.
    static func scan(in folder: URL) -> [LocalVideo] {
        let keys: [URLResourceKey] = [.isRegularFileKey, .fileSizeKey, .creationDateKey, .contentTypeKey]
        guard let enumerator = FileManager.default.enumerator(
            at: folder,
            includingPropertiesForKeys: keys,
            options: [.skipsHiddenFiles, .skipsPackageDescendants]
        ) else { return [] }

        var files: [(url: URL, size: Int64, created: Date?)] = []
        var sidecars: [URL: DownloadMetadata] = [:]
        for case let url as URL in enumerator {
            // iOS's holding area for files handed over by other apps.
            if url.lastPathComponent == "Inbox" {
                enumerator.skipDescendants()
                continue
            }
            guard let values = try? url.resourceValues(forKeys: Set(keys)),
                  values.isRegularFile == true else { continue }
            if values.contentType?.conforms(to: .movie) == true {
                files.append((url, Int64(values.fileSize ?? 0), values.creationDate))
            } else if url.pathExtension == "json", let metadata = readSidecar(at: url) {
                sidecars[url] = metadata
            }
        }

        // Pair each video with the sidecar sharing its name and size.
        var metadataByVideo: [URL: DownloadMetadata] = [:]
        for file in files {
            let sidecar = sidecarURL(for: file.url)
            if let metadata = sidecars[sidecar], metadata.fileSize == file.size {
                metadataByVideo[file.url] = metadata
                sidecars[sidecar] = nil
            }
        }

        // Whatever is left lost its video. Size finds it again after a rename.
        for file in files where metadataByVideo[file.url] == nil {
            guard let (sidecar, metadata) = sidecars.first(where: { $0.value.fileSize == file.size }) else { continue }
            try? FileManager.default.moveItem(at: sidecar, to: sidecarURL(for: file.url))
            try? FileManager.default.moveItem(
                at: sidecar.deletingPathExtension().appendingPathExtension("vtt"),
                to: captionsURL(for: file.url)
            )
            metadataByVideo[file.url] = metadata
            sidecars[sidecar] = nil
        }
        for sidecar in sidecars.keys {
            try? FileManager.default.removeItem(at: sidecar)
            try? FileManager.default.removeItem(at: sidecar.deletingPathExtension().appendingPathExtension("vtt"))
        }

        let rootPath = folder.resolvingSymlinksInPath().path + "/"
        return files.map { file in
            let metadata = metadataByVideo[file.url]
            let path = file.url.resolvingSymlinksInPath().path
            return LocalVideo(
                url: file.url,
                relativePath: path.hasPrefix(rootPath) ? String(path.dropFirst(rootPath.count)) : file.url.lastPathComponent,
                metadata: metadata,
                fileSize: file.size,
                addedAt: metadata?.downloadedAt ?? file.created ?? .distantPast
            )
        }
        .sorted { $0.addedAt > $1.addedAt }
    }
}

// MARK: - Library

@MainActor
final class DownloadLibrary: ObservableObject {
    static let shared = DownloadLibrary()

    @Published private(set) var videos: [LocalVideo] = [] {
        didSet { downloadedPostIds = Set(videos.compactMap { $0.metadata?.postId }) }
    }
    /// Posts with a video on the device, for the badge on feed cards. A multi-part
    /// post counts once any of its parts is downloaded.
    private(set) var downloadedPostIds: Set<String> = []

    private var folderWatcher: DispatchSourceFileSystemObject?
    private var foregroundObserver: NSObjectProtocol?

    private init() {
        refresh()
        watchFolder()
        foregroundObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.refresh() }
        }
    }

    func refresh() {
        Task { await refreshAndWait() }
    }

    func refreshAndWait() async {
        let found = await Task.detached(priority: .utility) {
            DownloadFiles.scan(in: DownloadFiles.folderURL)
        }.value
        if found != videos { videos = found }
    }

    func video(forVideoId videoId: String) -> LocalVideo? {
        videos.first { $0.metadata?.videoId == videoId }
    }

    func delete(_ video: LocalVideo) {
        try? FileManager.default.removeItem(at: video.url)
        DownloadFiles.removeSidecars(of: video.url)
        if let videoId = video.metadata?.videoId {
            try? FileManager.default.removeItem(at: DownloadFiles.thumbnailURL(videoId: videoId))
            try? FileManager.default.removeItem(at: DownloadFiles.iconURL(videoId: videoId))
        }
        DownloadProgressStore.shared.remove(key: video.progressKey)
        videos.removeAll { $0.id == video.id }
    }

    /// Picks up files added, renamed or removed through the Files app while the
    /// app is open. Only sees changes at the top level; subfolders are picked up
    /// when the app next comes to the foreground.
    private func watchFolder() {
        let descriptor = open(DownloadFiles.folderURL.path, O_EVTONLY)
        guard descriptor >= 0 else { return }
        let source = DispatchSource.makeFileSystemObjectSource(
            fileDescriptor: descriptor,
            eventMask: [.write, .delete, .rename],
            queue: .main
        )
        source.setEventHandler { [weak self] in
            Task { @MainActor in self?.refresh() }
        }
        source.setCancelHandler { close(descriptor) }
        source.resume()
        folderWatcher = source
    }

    // MARK: - Thumbnails

    /// The saved Floatplane thumbnail, or a frame from the file itself for
    /// videos added through the Files app (cached, since it's slow to generate).
    nonisolated static func thumbnail(for video: LocalVideo) async -> UIImage? {
        if let videoId = video.metadata?.videoId,
           let image = UIImage(contentsOfFile: DownloadFiles.thumbnailURL(videoId: videoId).path) {
            return image
        }

        let identity = "\(video.relativePath)|\(video.fileSize)"
        let digest = SHA256.hash(data: Data(identity.utf8)).map { String(format: "%02x", $0) }.joined()
        let cacheURL = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("\(digest).jpg")
        if let image = UIImage(contentsOfFile: cacheURL.path) {
            return image
        }

        let generator = AVAssetImageGenerator(asset: AVURLAsset(url: video.url))
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: 480, height: 480)
        guard let frame = try? await generator.image(at: CMTime(seconds: 5, preferredTimescale: 600)).image else {
            return nil
        }
        let image = UIImage(cgImage: frame)
        try? image.jpegData(compressionQuality: 0.8)?.write(to: cacheURL)
        return image
    }

    /// Duration from Floatplane's metadata, or read from the file.
    nonisolated static func duration(of video: LocalVideo) async -> Double? {
        if let seconds = video.metadata?.durationSeconds, seconds > 0 {
            return Double(seconds)
        }
        guard let duration = try? await AVURLAsset(url: video.url).load(.duration), duration.isNumeric else {
            return nil
        }
        return duration.seconds
    }
}
#endif
