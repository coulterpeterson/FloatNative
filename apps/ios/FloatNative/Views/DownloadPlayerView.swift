//
//  DownloadPlayerView.swift
//  FloatNative
//
//  Plays a file from the FloatNative folder, laid out like the streaming player.
//  Everything shown comes from the video's sidecar files, so it works with no
//  connection. Files the user added have no sidecars and show just their name.
//

#if !os(tvOS)
import AVFoundation
import SwiftUI

struct DownloadPlayerView: View {
    let video: LocalVideo

    @StateObject private var playerManager = AVPlayerManager.shared
    @State private var errorMessage: String?
    @State private var channelIcon: UIImage?
    @State private var parsedDescription = AttributedString("")

    var body: some View {
        ZStack {
            Color.adaptiveBackground
                .ignoresSafeArea()

            PlayerPageLayout(hasSide: video.metadata != nil) {
                playerArea
                    .frame(maxWidth: .infinity)
                    .aspectRatio(16 / 9, contentMode: .fit)
            } details: {
                Text(video.title)
                    .font(.title3)
                    .fontWeight(.semibold)
                    .foregroundColor(Color.adaptiveText)
                    .multilineTextAlignment(.leading)

                if let releaseDate = video.metadata?.releaseDate {
                    Text(RelativeDateTimeFormatter().localizedString(for: releaseDate, relativeTo: Date()))
                        .font(.subheadline)
                        .foregroundColor(Color.adaptiveSecondaryText)
                }

                playerButtons
            } side: {
                if let metadata = video.metadata {
                    channelRow(metadata)

                    if let html = metadata.descriptionHTML, !html.isEmpty {
                        Divider()
                            .background(Color.floatplaneGray.opacity(0.3))

                        PostDescriptionView(description: parsedDescription, rawLength: html.count)
                    }
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await load()
        }
        .task(priority: .userInitiated) {
            await loadDetails()
        }
        .onChange(of: playerManager.playerState) { _, state in
            if case .failed = state {
                errorMessage = "This video can't be played. FloatNative can play MP4, MOV and M4V files."
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIDevice.orientationDidChangeNotification)) { _ in
            playerManager.followDeviceRotation()
        }
        .onDisappear {
            // Also fires when AVKit presents fullscreen over this view, and PiP
            // keeps playing after the view is gone. See VideoPlayerView.
            playerManager.recordLocalProgress()
            Task {
                await DownloadProgressStore.shared.syncPending()
            }
            if !playerManager.isPIPActive && !playerManager.isInFullScreenTransition {
                ReviewPrompt.shared.recordVideoFinished(watchedTo: playerManager.currentTime)
                playerManager.pause()
                playerManager.reset()
            }
        }
    }

    @ViewBuilder
    private var playerArea: some View {
        if let errorMessage {
            Color.black.overlay {
                Text(errorMessage)
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .padding()
            }
        } else if let player = playerManager.player {
            CustomVideoPlayer(player: player, showsPlaybackControls: true)
        } else {
            Color.black.overlay {
                ProgressView()
                    .tint(.white)
            }
        }
    }

    /// The caption and fullscreen buttons, in the same spot as on the streaming player.
    private var playerButtons: some View {
        HStack(spacing: 16) {
            Spacer()

            if !playerManager.captionCues.isEmpty {
                Button {
                    playerManager.captionsEnabled.toggle()
                } label: {
                    Image(systemName: playerManager.captionsEnabled ? "captions.bubble.fill" : "captions.bubble")
                        .font(.title2)
                        .foregroundColor(playerManager.captionsEnabled ? .floatplaneBlue : Color.adaptiveText)
                }
                .accessibilityLabel(playerManager.captionsEnabled ? "Hide captions" : "Show captions")
            }

            PlayerFullScreenButton()
        }
    }

    private func channelRow(_ metadata: DownloadMetadata) -> some View {
        HStack(spacing: 12) {
            if let channelIcon {
                Image(uiImage: channelIcon)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 40, height: 40)
                    .clipShape(Circle())
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(metadata.channelName ?? metadata.creatorName)
                    .font(.body)
                    .fontWeight(.semibold)
                    .foregroundColor(Color.adaptiveText)

                Text(metadata.channelName != nil ? "Channel" : "Creator")
                    .font(.caption)
                    .foregroundColor(Color.adaptiveSecondaryText)
            }

            Spacer()
        }
    }

    private func loadDetails() async {
        guard let metadata = video.metadata else { return }
        channelIcon = UIImage(contentsOfFile: DownloadFiles.iconURL(videoId: metadata.videoId).path)
        if let html = metadata.descriptionHTML {
            parsedDescription = await Task.detached(priority: .userInitiated) {
                VideoPlayerView.htmlToAttributedString(html)
            }.value
        }
    }

    private func load() async {
        // Returning from fullscreen or PiP: the same file is still loaded.
        if playerManager.localProgressKey == video.progressKey, playerManager.player != nil {
            return
        }

        var startTime = Double(await DownloadProgressStore.shared.resumeSeconds(
            key: video.progressKey,
            videoId: video.metadata?.videoId
        ))

        // Finished last time, so start over.
        if let duration = try? await AVURLAsset(url: video.url).load(.duration),
           duration.isNumeric, startTime >= duration.seconds - 10 {
            startTime = 0
        }

        let captionsURL = DownloadFiles.captionsURL(for: video.url)
        let captionCues = await Task.detached(priority: .userInitiated) {
            (try? Data(contentsOf: captionsURL)).map { VTTCueParser.parse($0) } ?? []
        }.value

        do {
            try await playerManager.loadLocalFile(
                video.url,
                title: video.title,
                videoId: video.metadata?.videoId,
                progressKey: video.progressKey,
                startTime: startTime,
                captionCues: captionCues
            )
            playerManager.play()
        } catch {
            errorMessage = "This video can't be played."
        }
    }
}
#endif
