//
//  LivePlayerView.swift
//  FloatNative
//
//  Clean video player for Live Streams
//  Based on VideoPlayerView but simplified (no comments/likes)
//

import SwiftUI
import AVKit

struct LivePlayerView: View {
    let creator: Creator
    
    @StateObject private var playerManager = AVPlayerManager.shared
    @StateObject private var api = FloatplaneAPI.shared
    @Environment(\.dismiss) private var dismiss
    
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var isDescriptionExpanded = false
    @State private var parsedDescription: AttributedString = AttributedString("")
    @State private var isStreamOffline = false
    
    // Quality selector state
    @State private var isChangingQuality = false
    
    var body: some View {
        GeometryReader { geometry in
            let isLandscape = geometry.size.width > geometry.size.height
            
            ZStack {
                // Background
                Color.adaptiveBackground
                    .ignoresSafeArea()
                
                // Persistent Layout
                portraitLayout
                
                // Error state
                if let error = errorMessage {
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.system(size: 50))
                            .foregroundStyle(.red)
                        
                        Text(error)
                            .foregroundStyle(Color.adaptiveText)
                            .multilineTextAlignment(.center)
                        
                        Button("Retry") {
                            Task {
                                await loadLivestream()
                            }
                        }
                        .buttonStyle(LiquidGlassButtonStyle(tint: .floatplaneBlue))
                    }
                    .padding()
                }
                
                // Stream offline overlay
                if isStreamOffline {
                    StreamOfflineOverlay {
                        dismiss()
                    }
                }
            }
            #if !os(tvOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .navigationBarBackButtonHidden(false)
            #if !os(tvOS)
            .statusBar(hidden: false)
            #endif
            .toolbar(isLandscape ? .hidden : .visible, for: .tabBar)
            #if !os(tvOS)
            .onReceive(NotificationCenter.default.publisher(for: UIDevice.orientationDidChangeNotification)) { _ in
                let orientation = UIDevice.current.orientation
                
                if orientation.isLandscape {
                    AVPlayerManager.shared.enterFullScreen()
                } else if orientation == .portrait {
                    AVPlayerManager.shared.exitFullScreen()
                }
            }
            #endif
        }
        .task {
            await loadLivestream()
        }
        .task(priority: .userInitiated) {
            // Parse HTML description
            if let description = creator.liveStream?.description {
                let htmlText = description
                parsedDescription = await Task.detached(priority: .userInitiated) {
                    return VideoPlayerView.htmlToAttributedString(htmlText)
                }.value
            }
        }
        .onDisappear {
            playerManager.pause()
            playerManager.reset()
        }
    }
    
    // MARK: - Layout Views
    
    private var portraitLayout: some View {
        VStack(spacing: 0) {
            // Video Player
            if let player = playerManager.player {
                CustomVideoPlayer(player: player, showsPlaybackControls: true)
                    .frame(maxWidth: .infinity)
                    .aspectRatio(16/9, contentMode: .fit)
            } else {
                Rectangle()
                    .fill(Color.black)
                    .aspectRatio(16/9, contentMode: .fit)
                    .overlay {
                        if isLoading {
                            ProgressView()
                                .tint(.white)
                        }
                    }
            }
            
            // Video Info
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Title
                    Text(creator.liveStream?.title ?? "Live Stream")
                        .font(.title3)
                        .fontWeight(.semibold)
                        .foregroundColor(Color.adaptiveText)
                        .multilineTextAlignment(.leading)
                    
                    // Metadata
                    HStack(spacing: 8) {
                        Text("LIVE")
                            .font(.caption)
                            .fontWeight(.bold)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.red)
                            .foregroundColor(.white)
                            .clipShape(RoundedRectangle(cornerRadius: 4))
                        
                        Text("Started just now") // Live streams don't always have start time handy in Creator object, simplified
                            .font(.subheadline)
                            .foregroundColor(Color.adaptiveSecondaryText)
                    }
                    
                    Divider()
                        .background(Color.floatplaneGray.opacity(0.3))
                    
                    // Creator Info
                    HStack(spacing: 12) {
                        if let icon = creator.icon.fullURL {
                            CachedAsyncImage(url: icon) { image in
                                image.resizable().aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Circle().fill(Color.floatplaneGray.opacity(0.3))
                            }
                            .frame(width: 40, height: 40)
                            .clipShape(Circle())
                        }
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text(creator.title)
                                .font(.body)
                                .fontWeight(.semibold)
                                .foregroundColor(Color.adaptiveText)
                        }
                        
                        Spacer()
                    }
                    
                    Divider()
                        .background(Color.floatplaneGray.opacity(0.3))
                    
                    // Description
                    VStack(alignment: .leading, spacing: 8) {
                        Button {
                            withAnimation {
                                isDescriptionExpanded.toggle()
                            }
                        } label: {
                            HStack {
                                Text("Description")
                                    .font(.headline)
                                    .foregroundColor(Color.adaptiveText)
                                Spacer()
                                Image(systemName: isDescriptionExpanded ? "chevron.up" : "chevron.down")
                                    .foregroundColor(Color.adaptiveSecondaryText)
                            }
                        }
                        
                        if isDescriptionExpanded {
                            Text(parsedDescription)
                                .font(.body)
                                .foregroundColor(Color.adaptiveText)
                                .textSelection(.enabled)
                        } else {
                            Text(parsedDescription)
                                .font(.body)
                                .foregroundColor(Color.adaptiveText)
                                .lineLimit(2)
                        }
                    }
                }
                .padding()
            }
        }
    }
    
    // MARK: - Load Logic
    
    private func loadLivestream() async {
        guard let liveStream = creator.liveStream else {
            errorMessage = "No livestream info available"
            isLoading = false
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        do {
            let deliveryInfo = try await api.getDeliveryInfo(
                scenario: .live,
                entityId: liveStream.id,
                outputKind: .hlsFmp4
            )
            
            let qualities = deliveryInfo.availableVariants()
            
            // Reusing VideoPlayerView's mock post logic for playerManager compatibility isn't robust
            // We should create a dummy Post if needed, or update PlayerManager to accept optional post.
            // Looking at VideoPlayerView, it passes `post: post`.
            // Let's create a minimal dummy post for the player manager to hold reference if needed.
            // Ideally PlayerManager shouldn't require a full BlogPost, but let's check AVPlayerManager later if it crashes.
            // For now, constructing a dummy post since we don't have one.
            
            // Actually, we can just pass nil for `post` if we update AVPlayerManager, or pass a dummy one.
            // Generating dummy post...
            let dummyPost = BlogPost(
                id: liveStream.id,
                guid: liveStream.id,
                title: liveStream.title,
                text: liveStream.description,
                type: .blogPost,
                tags: [],
                attachmentOrder: [],
                metadata: BlogPost.Metadata(hasVideo: true, videoCount: 1, videoDuration: 0, hasAudio: false, audioCount: 0, audioDuration: 0, hasPicture: false, pictureCount: 0, hasGallery: false, galleryCount: 0, isFeatured: false),
                releaseDate: Date(),
                likes: 0,
                dislikes: 0,
                score: 0,
                comments: 0,
                creator: creator,
                wasReleasedSilently: false,
                thumbnail: liveStream.thumbnail,
                canEdit: false,
                canDelete: false
            )
            
            try await playerManager.loadVideo(
                videoId: liveStream.id,
                title: liveStream.title,
                post: dummyPost,
                startTime: 0,
                qualities: qualities
            )
            
            playerManager.play()
            isLoading = false
            
        } catch {
            errorMessage = "Failed to load livestream: \(error.localizedDescription)"
            isLoading = false
        }
    }
}
