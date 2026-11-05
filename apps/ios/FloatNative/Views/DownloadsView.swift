//
//  DownloadsView.swift
//  FloatNative
//
//  Everything in the on-device FloatNative folder: downloads from Floatplane
//  plus any video added through the Files app.
//

#if !os(tvOS)
import SwiftUI

struct DownloadsView: View {
    @ObservedObject private var library = DownloadLibrary.shared
    @ObservedObject private var manager = DownloadManager.shared
    @ObservedObject private var progressStore = DownloadProgressStore.shared
    @EnvironmentObject private var tabCoordinator: TabCoordinator
    @Environment(\.openURL) private var openURL

    @State private var videoToDelete: LocalVideo?

    var body: some View {
        ZStack {
            Color.adaptiveBackground
                .ignoresSafeArea()

            if library.videos.isEmpty && manager.active.isEmpty {
                emptyState
            } else {
                list
            }
        }
        .navigationTitle("Downloads")
        .navigationBarTitleDisplayMode(.inline)
        .globalMenu()
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    openFolderInFiles()
                } label: {
                    Image(systemName: "folder")
                        .foregroundColor(Color.adaptiveText)
                }
                .accessibilityLabel("Show in Files")
            }
        }
        .onAppear {
            library.refresh()
        }
        .confirmationDialog(
            "Delete this video from your device?",
            isPresented: Binding(
                get: { videoToDelete != nil },
                set: { if !$0 { videoToDelete = nil } }
            ),
            titleVisibility: .visible,
            presenting: videoToDelete
        ) { video in
            Button("Delete", role: .destructive) {
                library.delete(video)
            }
        } message: { video in
            Text(video.title)
        }
    }

    private var list: some View {
        List {
            if !manager.active.isEmpty {
                Section("Downloading") {
                    ForEach(manager.active) { download in
                        ActiveDownloadRow(download: download)
                            .listRowBackground(Color.clear)
                    }
                }
            }

            if !library.videos.isEmpty {
                Section {
                    ForEach(library.videos) { video in
                        Button {
                            tabCoordinator.path.append(NavigationDestination.downloadPlayer(video))
                        } label: {
                            LocalVideoRow(video: video, entry: progressStore.entries[video.progressKey])
                        }
                        .buttonStyle(.plain)
                        .listRowBackground(Color.clear)
                        .swipeActions {
                            Button("Delete", role: .destructive) {
                                videoToDelete = video
                            }
                        }
                        .contextMenu {
                            ShareLink(item: video.url)
                            Button("Delete", systemImage: "trash", role: .destructive) {
                                videoToDelete = video
                            }
                        }
                    }
                } footer: {
                    folderHint
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "arrow.down.circle")
                .font(.system(size: 50))
                .foregroundStyle(Color.adaptiveSecondaryText)

            Text("No downloads yet")
                .font(.headline)
                .foregroundStyle(Color.adaptiveText)

            folderHint
                .multilineTextAlignment(.center)
        }
        .padding()
    }

    private var folderHint: some View {
        Text("Downloads are saved to the FloatNative folder in the Files app, under On My \(UIDevice.current.userInterfaceIdiom == .pad ? "iPad" : "iPhone"). Videos you add to that folder show up here too.")
            .font(.caption)
            .foregroundStyle(Color.adaptiveSecondaryText)
    }

    /// The Files app's URL scheme: the same path with `shareddocuments` as the scheme.
    private func openFolderInFiles() {
        guard var components = URLComponents(url: DownloadFiles.folderURL, resolvingAgainstBaseURL: false) else { return }
        components.scheme = "shareddocuments"
        if let url = components.url {
            openURL(url)
        }
    }
}

// MARK: - Rows

private struct LocalVideoRow: View {
    let video: LocalVideo
    let entry: DownloadProgressStore.Entry?

    @State private var thumbnail: UIImage?
    @State private var duration: Double?

    var body: some View {
        HStack(spacing: 12) {
            ZStack(alignment: .bottom) {
                Color.adaptiveSecondaryText.opacity(0.15)

                if let thumbnail {
                    Image(uiImage: thumbnail)
                        .resizable()
                        .scaledToFill()
                } else {
                    Image(systemName: "film")
                        .foregroundStyle(Color.adaptiveSecondaryText)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }

                if let fraction = watchedFraction {
                    GeometryReader { geometry in
                        Color.floatplaneBlue
                            .frame(width: geometry.size.width * fraction)
                    }
                    .frame(height: 3)
                }
            }
            .frame(width: 128, height: 72)
            .clipShape(RoundedRectangle(cornerRadius: 8))

            VStack(alignment: .leading, spacing: 4) {
                Text(video.title)
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundStyle(Color.adaptiveText)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)

                Text(details)
                    .font(.caption)
                    .foregroundStyle(Color.adaptiveSecondaryText)
                    .lineLimit(1)

                if entry?.needsSync == true {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.triangle.2.circlepath")
                        Text("Progress syncs when online")
                    }
                    .font(.caption2)
                    .foregroundStyle(Color.adaptiveSecondaryText)
                }
            }

            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
        .task(id: video) {
            thumbnail = await DownloadLibrary.thumbnail(for: video)
            duration = await DownloadLibrary.duration(of: video)
        }
    }

    private var details: String {
        var parts: [String] = []
        if let metadata = video.metadata { parts.append(metadata.channelName ?? metadata.creatorName) }
        if let duration { parts.append(Self.formatDuration(duration)) }
        if let quality = video.metadata?.qualityLabel { parts.append(quality) }
        parts.append(ByteCountFormatter.string(fromByteCount: video.fileSize, countStyle: .file))
        return parts.joined(separator: " • ")
    }

    private var watchedFraction: Double? {
        guard let seconds = entry?.seconds, let duration, duration > 0 else { return nil }
        return min(Double(seconds) / duration, 1)
    }

    private static func formatDuration(_ seconds: Double) -> String {
        let total = Int(seconds)
        if total >= 3600 {
            return String(format: "%d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
        }
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}

private struct ActiveDownloadRow: View {
    let download: ActiveDownload

    @ObservedObject private var manager = DownloadManager.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(download.metadata.title)
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundStyle(Color.adaptiveText)
                    .lineLimit(2)

                Spacer()

                if case .failed = download.state {
                    Button {
                        manager.retry(download)
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel("Retry")
                }

                Button {
                    manager.cancel(download)
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(Color.adaptiveSecondaryText)
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Cancel")
            }

            switch download.state {
            case .preparing:
                ProgressView()
                    .progressViewStyle(.linear)
                    .tint(.floatplaneBlue)
            case .downloading(let fraction):
                ProgressView(value: fraction)
                    .tint(.floatplaneBlue)
            case .failed(let message):
                Text(message)
                    .font(.caption)
                    .foregroundStyle(.red)
            }
        }
    }
}

// MARK: - Player Button

/// The download button on the video page, in its own view so download progress
/// doesn't redraw the whole player page.
struct DownloadStatusButton: View {
    let videoId: String?
    let onDownload: () -> Void
    let onAlreadyDownloaded: () -> Void
    let onInProgress: () -> Void

    @ObservedObject private var library = DownloadLibrary.shared
    @ObservedObject private var manager = DownloadManager.shared

    var body: some View {
        Button {
            if isDownloaded {
                onAlreadyDownloaded()
            } else if activeDownload != nil, !hasFailed {
                onInProgress()
            } else {
                onDownload()
            }
        } label: {
            label
                .font(.title2)
                .foregroundColor(isDownloaded ? Color.floatplaneBlue : Color.adaptiveText)
                // Same size in every state, so the buttons beside it don't shift.
                .frame(width: 26, height: 26)
        }
        .accessibilityLabel(isDownloaded ? "Downloaded" : "Download")
    }

    @ViewBuilder
    private var label: some View {
        if isDownloaded {
            Image(systemName: "checkmark.circle.fill")
        } else if case .downloading(let fraction)? = activeDownload?.state {
            ZStack {
                Circle()
                    .stroke(Color.adaptiveSecondaryText.opacity(0.3), lineWidth: 3)
                Circle()
                    .trim(from: 0, to: fraction)
                    .stroke(Color.floatplaneBlue, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    .rotationEffect(.degrees(-90))
            }
            .padding(1.5)
        } else if case .preparing? = activeDownload?.state {
            ProgressView()
        } else if hasFailed {
            Image(systemName: "exclamationmark.arrow.circlepath")
        } else {
            Image(systemName: "arrow.down.circle")
        }
    }

    private var activeDownload: ActiveDownload? {
        videoId.flatMap { manager.download(for: $0) }
    }

    private var isDownloaded: Bool {
        videoId.flatMap { library.video(forVideoId: $0) } != nil
    }

    private var hasFailed: Bool {
        if case .failed? = activeDownload?.state { return true }
        return false
    }
}
#endif
