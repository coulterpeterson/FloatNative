//
//  AVPlayerManager.swift
//  FloatNative
//
//  Advanced video player manager with background audio and PIP support
//  Created by Claude on 2025-10-08.
//

import AVKit
import AVFoundation
import Combine
import SwiftUI
import MediaPlayer

// MARK: - Player State

enum PlayerState: Equatable {
    case idle
    case loading
    case playing
    case paused
    case buffering
    case failed(Error)

    static func == (lhs: PlayerState, rhs: PlayerState) -> Bool {
        switch (lhs, rhs) {
        case (.idle, .idle),
             (.loading, .loading),
             (.playing, .playing),
             (.paused, .paused),
             (.buffering, .buffering):
            return true
        case (.failed, .failed):
            return true
        default:
            return false
        }
    }
}

// MARK: - AVPlayer Manager

@MainActor
class AVPlayerManager: NSObject, ObservableObject {

    // MARK: - Singleton

    static let shared = AVPlayerManager()

    // MARK: - Published Properties

    @Published private(set) var player: AVPlayer?
    @Published private(set) var playerState: PlayerState = .idle
    @Published private(set) var currentTime: Double = 0
    @Published private(set) var duration: Double = 0
    @Published private(set) var isPlaying = false
    @Published private(set) var availableQualities: [QualityVariant] = []
    @Published private(set) var currentQuality: QualityVariant?

    // MARK: - PIP State (managed by CustomVideoPlayer)

    @Published var isPIPActive = false // True only while PiP window is actively displayed
    @Published var isPIPSupported = true // AVPlayerViewController always supports PiP if device supports it

    // Track if we have a PiP session that can be restored (true even when paused)
    // This stays true as long as playerViewController/delegate are stored for restoration
    @Published var hasPIPSession = false

    // Keep playerViewController and its delegate alive during PiP
    // This prevents the delegate from being deallocated when the view is popped
    var playerViewController: AVPlayerViewController?
    var playerViewControllerDelegate: NSObject? // Stores the Coordinator

    // MARK: - Current Video Info

    private(set) var currentVideoId: String?
    private(set) var currentVideoTitle: String?
    @Published var currentPost: BlogPost?
    @Published var shouldRestoreVideoPlayer: Bool = false

    // MARK: - Observers

    private var timeObserver: Any?
    private var statusObserver: AnyCancellable?
    private var itemObserver: AnyCancellable?
    private var cancellables = Set<AnyCancellable>()
    private var progressTimer: DispatchSourceTimer?
    private var backgroundObserver: NSObjectProtocol?
    private var videoEndObserver: NSObjectProtocol?

    // MARK: - Buffer State

    @Published private(set) var isBuffering = false
    @Published private(set) var bufferProgress: Double = 0
    private var lastBufferUpdate: TimeInterval = 0

    // MARK: - Audio Session

    private let audioSession = AVAudioSession.sharedInstance()

    // MARK: - Initialization

    private override init() {
        super.init()
        setupAudioSession()
        setupRemoteCommandCenter()
        setupBackgroundObserver()
    }

    // MARK: - Audio Session Setup

    private func setupAudioSession() {
        do {
            // Configure audio session for background playback
            try audioSession.setCategory(.playback, mode: .moviePlayback)
            try audioSession.setActive(true)
        } catch {
            print("⚠️ Failed to setup audio session: \(error)")
        }
    }

    // MARK: - Remote Command Center (Lock Screen Controls)

    private func setupRemoteCommandCenter() {
        let commandCenter = MPRemoteCommandCenter.shared()

        // Play command
        commandCenter.playCommand.isEnabled = true
        commandCenter.playCommand.addTarget { [weak self] _ in
            self?.play()
            return .success
        }

        // Pause command
        commandCenter.pauseCommand.isEnabled = true
        commandCenter.pauseCommand.addTarget { [weak self] _ in
            self?.pause()
            return .success
        }

        // Skip forward
        commandCenter.skipForwardCommand.isEnabled = true
        commandCenter.skipForwardCommand.preferredIntervals = [15]
        commandCenter.skipForwardCommand.addTarget { [weak self] _ in
            self?.seek(by: 15)
            return .success
        }

        // Skip backward
        commandCenter.skipBackwardCommand.isEnabled = true
        commandCenter.skipBackwardCommand.preferredIntervals = [15]
        commandCenter.skipBackwardCommand.addTarget { [weak self] _ in
            self?.seek(by: -15)
            return .success
        }

        // Seek command
        commandCenter.changePlaybackPositionCommand.isEnabled = true
        commandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            self?.seek(to: event.positionTime)
            return .success
        }
    }

    // MARK: - Background & Lifecycle Observers

    private func setupBackgroundObserver() {
        backgroundObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.willResignActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                await self?.saveProgress()
            }
        }
    }

    private func setupVideoEndObserver() {
        guard let playerItem = player?.currentItem else { return }

        videoEndObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: playerItem,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                await self?.saveProgress()
            }
        }
    }

    // MARK: - Now Playing Info

    private func updateNowPlayingInfo() {
        var nowPlayingInfo = [String: Any]()

        nowPlayingInfo[MPMediaItemPropertyTitle] = currentVideoTitle ?? "Floatplane Video"
        nowPlayingInfo[MPMediaItemPropertyPlaybackDuration] = duration
        nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = player?.rate ?? 0

        // TODO: Add artwork from thumbnail
        // if let thumbnail = currentThumbnail {
        //     nowPlayingInfo[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(...)
        // }

        MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
    }

    // MARK: - Load Video

    func loadVideo(
        videoId: String,
        title: String,
        post: BlogPost? = nil,
        startTime: Double = 0,
        qualities: [QualityVariant]
    ) async throws {
        self.currentVideoId = videoId
        self.currentVideoTitle = title
        self.currentPost = post
        self.availableQualities = qualities
        self.playerState = .loading

        // Use highest quality by default
        guard let quality = qualities.first else {
            throw FloatplaneAPIError.invalidResponse
        }

        self.currentQuality = quality

        try await loadStream(url: quality.url, startTime: startTime)
    }

    /// Load stream from URL
    private func loadStream(url: String, startTime: Double = 0) async throws {
        guard let streamURL = URL(string: url) else {
            throw FloatplaneAPIError.invalidURL
        }

        // Clean up old player
        cleanupPlayer()

        // Create new player with headers
        let headers: [String: String] = [
            "User-Agent": "FloatNative/1.0 (iOS), CFNetwork",
            "Authorization": "Bearer \(FloatplaneAPI.shared.accessToken ?? "")"
        ]
        let asset = AVURLAsset(url: streamURL, options: ["AVURLAssetHTTPHeaderFieldsKey": headers])
        let playerItem = AVPlayerItem(asset: asset)

        // Configure buffer limits for tvOS to prevent memory issues during long playback
        #if os(tvOS)
        // Limit buffer to 30 seconds ahead to reduce memory pressure on tvOS
        playerItem.preferredForwardBufferDuration = 30
        #else
        // iOS can handle larger buffers
        playerItem.preferredForwardBufferDuration = 60
        #endif

        let newPlayer = AVPlayer(playerItem: playerItem)
        newPlayer.allowsExternalPlayback = true
        newPlayer.appliesMediaSelectionCriteriaAutomatically = true

        // Configure player to minimize stalling while respecting buffer limits
        if #available(iOS 10.0, tvOS 10.0, *) {
            newPlayer.automaticallyWaitsToMinimizeStalling = true
        }

        self.player = newPlayer

        // Observe player status
        observePlayer()

        // Setup video end observer
        setupVideoEndObserver()

        // Seek to start time if specified
        if startTime > 0 {
            await seek(to: startTime)
        }

        // Update state
        playerState = .paused
    }

    // MARK: - Change Quality

    func changeQuality(_ quality: QualityVariant) async throws {
        guard let player = player else { return }

        let currentTime = player.currentTime().seconds
        self.currentQuality = quality

        try await loadStream(url: quality.url, startTime: currentTime)

        if isPlaying {
            play()
        }
    }

    // MARK: - Picture in Picture
    // PiP is now handled by CustomVideoPlayer (AVPlayerViewController)
    // which has native PiP support built-in

    // MARK: - Player Observation

    private func observePlayer() {
        guard let player = player else { return }

        // Time observer
        let interval = CMTime(seconds: 0.5, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
        timeObserver = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            Task { @MainActor in
                // Update without animation to avoid AttributeGraph cycles
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) {
                    self?.currentTime = time.seconds
                }
                self?.updateNowPlayingInfo()
            }
        }

        // Duration observer
        player.currentItem?.publisher(for: \.duration)
            .sink { [weak self] duration in
                Task { @MainActor in
                    if duration.isNumeric {
                        self?.duration = duration.seconds
                        self?.updateNowPlayingInfo()
                    }
                }
            }
            .store(in: &cancellables)

        // Status observer
        player.publisher(for: \.timeControlStatus)
            .sink { [weak self] status in
                Task { @MainActor in
                    switch status {
                    case .playing:
                        self?.isPlaying = true
                        self?.playerState = .playing
                    case .paused:
                        self?.isPlaying = false
                        if self?.playerState == .playing {
                            self?.playerState = .paused
                        }
                    case .waitingToPlayAtSpecifiedRate:
                        self?.playerState = .buffering
                    @unknown default:
                        break
                    }
                    self?.updateNowPlayingInfo()
                }
            }
            .store(in: &cancellables)

        // Item status observer
        player.currentItem?.publisher(for: \.status)
            .sink { [weak self] status in
                Task { @MainActor in
                    switch status {
                    case .failed:
                        if let error = player.currentItem?.error {
                            self?.playerState = .failed(error)
                        }
                    case .readyToPlay:
                        if self?.playerState == .loading {
                            self?.playerState = .paused
                        }
                    default:
                        break
                    }
                }
            }
            .store(in: &cancellables)

        // Buffer empty observer - critical for detecting stalling
        player.currentItem?.publisher(for: \.isPlaybackBufferEmpty)
            .sink { [weak self] isEmpty in
                Task { @MainActor in
                    if isEmpty {

                        self?.isBuffering = true
                        self?.playerState = .buffering
                    }
                }
            }
            .store(in: &cancellables)

        // Buffer likely to keep up observer
        player.currentItem?.publisher(for: \.isPlaybackLikelyToKeepUp)
            .sink { [weak self] likelyToKeepUp in
                Task { @MainActor in
                    if likelyToKeepUp {

                        self?.isBuffering = false
                        if self?.isPlaying == true {
                            self?.playerState = .playing
                        }
                    }
                }
            }
            .store(in: &cancellables)

        // Buffer full observer
        player.currentItem?.publisher(for: \.isPlaybackBufferFull)
            .sink { [weak self] isFull in
                Task { @MainActor in
                    if isFull {
                        print("✅ Buffer full at \(self?.currentTime ?? 0)s")
                    }
                }
            }
            .store(in: &cancellables)

        // Loaded time ranges observer - track buffer progress
        player.currentItem?.publisher(for: \.loadedTimeRanges)
            .sink { [weak self] timeRanges in
                Task { @MainActor in
                    guard let self = self,
                          let currentTime = self.player?.currentTime(),
                          let timeRange = timeRanges.first?.timeRangeValue else {
                        return
                    }

                    // Throttle updates to once every 2 seconds to prevent excessive view redraws
                    // This is critical for tvOS performance where view updates are expensive
                    let now = Date().timeIntervalSince1970
                    guard now - self.lastBufferUpdate > 2.0 else { return }

                    let bufferEnd = CMTimeGetSeconds(CMTimeRangeGetEnd(timeRange))
                    let currentTimeSeconds = CMTimeGetSeconds(currentTime)
                    let bufferedAhead = bufferEnd - currentTimeSeconds

                    self.bufferProgress = max(0, bufferedAhead)
                    self.lastBufferUpdate = now

                    #if os(tvOS)
                    // Log buffer status on tvOS to help diagnose issues
                    if Int(currentTimeSeconds) % 60 == 0 && Int(currentTimeSeconds) > 0 {
                        print("📊 tvOS Buffer Status at \(Int(currentTimeSeconds))s: \(bufferedAhead)s ahead")
                    }
                    #endif
                }
            }
            .store(in: &cancellables)
    }

    // MARK: - Playback Controls

    func play() {
        player?.play()
        updateNowPlayingInfo()
        startProgressTimer()
        setIdleTimerDisabled(true)  // Keep screen awake during playback
    }

    func pause() {
        player?.pause()
        updateNowPlayingInfo()
        stopProgressTimer()
        setIdleTimerDisabled(false)  // Allow screen to sleep when paused
    }

    // MARK: - Idle Timer Management

    private func setIdleTimerDisabled(_ disabled: Bool) {
        DispatchQueue.main.async {
            UIApplication.shared.isIdleTimerDisabled = disabled
        }
    }

    func togglePlayPause() {
        if isPlaying {
            pause()
        } else {
            play()
        }
    }

    func seek(to time: Double) {
        let cmTime = CMTime(seconds: time, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
        player?.seek(to: cmTime) { [weak self] _ in
            Task { @MainActor in
                self?.updateNowPlayingInfo()
            }
        }
    }

    func seek(by seconds: Double) {
        let newTime = currentTime + seconds
        let clampedTime = max(0, min(newTime, duration))
        seek(to: clampedTime)
    }

    func setRate(_ rate: Float) {
        player?.rate = rate
    }

    // MARK: - Progress Tracking

    /// Start periodic progress saving (every 2 minutes)
    private func startProgressTimer() {
        stopProgressTimer()

        // Use DispatchSourceTimer on background queue to avoid blocking main thread
        let timer = DispatchSource.makeTimerSource(queue: DispatchQueue.global(qos: .utility))
        timer.schedule(deadline: .now() + 120, repeating: 120)
        timer.setEventHandler { [weak self] in
            Task { @MainActor in
                await self?.saveProgressWithTimeout()
            }
        }
        timer.resume()
        progressTimer = timer
    }

    /// Stop periodic progress saving
    private func stopProgressTimer() {
        progressTimer?.cancel()
        progressTimer = nil
    }

    /// Save progress to Floatplane API with timeout protection
    private func saveProgressWithTimeout() async {
        // Use Task.withTimeout-like pattern to prevent hanging
        await withTaskGroup(of: Void.self) { group in
            group.addTask {
                await self.saveProgress()
            }

            // Add timeout task (10 seconds)
            group.addTask {
                try? await Task.sleep(nanoseconds: 10_000_000_000)
            }

            // Wait for first to complete, then cancel others
            await group.next()
            group.cancelAll()
        }
    }

    /// Save progress to Floatplane API
    func saveProgress() async {
        guard let videoId = currentVideoId else { return }

        let progressInSeconds = Int(currentTime)

        do {
            _ = try await FloatplaneAPI.shared.updateProgress(
                videoId: videoId,
                contentType: "video",
                progress: progressInSeconds
            )
        } catch {
            print("Failed to save progress: \(error)")
        }
    }

    // MARK: - Cleanup

    private func cleanupPlayer() {
        // FIRST: Immediately stop playback and audio to prevent overlap
        player?.pause()
        player?.replaceCurrentItem(with: nil)

        // Re-enable idle timer (allow screen to sleep)
        setIdleTimerDisabled(false)

        // Remove time observer
        if let timeObserver = timeObserver {
            player?.removeTimeObserver(timeObserver)
            self.timeObserver = nil
        }

        // Remove background observer
        if let backgroundObserver = backgroundObserver {
            NotificationCenter.default.removeObserver(backgroundObserver)
            self.backgroundObserver = nil
        }

        // Remove video end observer
        if let videoEndObserver = videoEndObserver {
            NotificationCenter.default.removeObserver(videoEndObserver)
            self.videoEndObserver = nil
        }

        // Stop progress timer
        stopProgressTimer()

        // Cancel all subscriptions
        cancellables.removeAll()

        player = nil
    }

    func reset() {
        cleanupPlayer()
        currentVideoId = nil
        currentVideoTitle = nil
        currentPost = nil
        shouldRestoreVideoPlayer = false
        availableQualities = []
        currentQuality = nil
        currentTime = 0
        duration = 0
        isPlaying = false
        playerState = .idle
        playerViewController = nil
        playerViewControllerDelegate = nil
        hasPIPSession = false
    }

    // Note: deinit cannot call async methods, so cleanup happens via onDisappear in views
}
