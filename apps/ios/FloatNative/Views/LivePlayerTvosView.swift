//
//  LivePlayerTvosView.swift
//  FloatNative
//
//  Clean tvOS video player for Live Streams
//  Based on VideoPlayerTvosView but simplified (no comments/likes/transport bar)
//

import SwiftUI
import AVKit

struct LivePlayerTvosView: View {
    let creator: Creator

    @StateObject private var playerManager = AVPlayerManager.shared
    @StateObject private var api = FloatplaneAPI.shared
    @Environment(\.dismiss) private var dismiss

    // Video state
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var isStreamOffline = false

    // Side panel state (Description only for Live)
    @State private var showSidePanel = false
    @FocusState private var sidePanelContentFocused: Bool
    
    // Description state
    @State private var parsedDescription: AttributedString = AttributedString("")
    @State private var descriptionParagraphs: [DescriptionParagraph] = []
    @State private var hasParsedDescription = false

    // Transport bar items
    #if os(tvOS)
    @State private var customTransportBarItems: [UIMenuElement] = []
    #endif

    // Helper to create quality menu
    #if os(tvOS)
    func createPlaceholderQualityMenu() -> UIMenu {
        let placeholderQualities = ["360p", "480p", "720p", "1080p"]
        let qualityChildren = placeholderQualities.map { label in
            UIAction(title: label, image: nil) { _ in }
        }
        return UIMenu(title: "Quality", image: UIImage(systemName: "gearshape"), children: qualityChildren)
    }

    func createQualityMenu() -> UIMenu {
        guard !playerManager.availableQualities.isEmpty else {
            return createPlaceholderQualityMenu()
        }

        let qualityChildren = playerManager.availableQualities.sorted(by: { $0.order < $1.order }).map { quality in
            UIAction(
                title: quality.label,
                image: playerManager.currentQuality?.id == quality.id ? UIImage(systemName: "checkmark") : nil
            ) { _ in
                Task {
                    try? await playerManager.changeQuality(quality)
                }
            }
        }
        return UIMenu(title: "Quality", image: UIImage(systemName: "gearshape"), children: qualityChildren)
    }

    func buildTransportBarItems() -> [UIMenuElement] {
        // Description button
        let descriptionAction: UIAction = UIAction(
            title: "Description",
            image: UIImage(systemName: "doc.text")
        ) { _ in
            withAnimation {
                self.showSidePanel.toggle()
            }
        }
        
        let qualityMenu: UIMenu = createQualityMenu()
        return [descriptionAction, qualityMenu]
    }
    #endif

    var body: some View {
        withNavigationModifiers(
            withLifecycleModifiers(mainContent)
        )
    }
    
    // MARK: - Main Content

    private var mainContent: some View {
        GeometryReader { geometry in
            ZStack {
                // Background
                Color.black.ignoresSafeArea()

                HStack(spacing: 0) {
                    // Video Player Area
                    videoPlayerArea
                        .frame(width: showSidePanel ? geometry.size.width * 0.65 : geometry.size.width)
                        .animation(.easeInOut(duration: 0.3), value: showSidePanel)

                    // Side Panel
                    if showSidePanel {
                        sidePanel
                            .frame(width: geometry.size.width * 0.35)
                            .transition(.move(edge: .trailing))
                    }
                }

                // Error State
                if let error = errorMessage {
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.system(size: 50))
                            .foregroundStyle(.red)

                        Text(error)
                            .foregroundStyle(.white)
                            .multilineTextAlignment(.center)

                        Button("Retry") {
                            Task {
                                await loadLivestream()
                            }
                        }
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
        }
    }
    
    // MARK: - Modifiers
    
    private func withLifecycleModifiers<Content: View>(_ content: Content) -> some View {
        content
            .onAppear {
                #if os(tvOS)
                customTransportBarItems = buildTransportBarItems()
                #endif
            }
            .task {
                await loadLivestream()
            }
            .task(priority: .userInitiated) {
                // Parse description logic
                guard !hasParsedDescription, let description = creator.liveStream?.description else { return }
                
                let htmlText = description
                let attributedParagraphs = await Task.detached(priority: .userInitiated) {
                    return VideoPlayerTvosView.parseHTMLParagraphsStatic(htmlText)
                }.value

                descriptionParagraphs = attributedParagraphs.map { DescriptionParagraph(content: $0) }
                hasParsedDescription = true
            }
            .onDisappear {
                playerManager.pause()
                playerManager.reset()
            }
    }

    private func withNavigationModifiers<Content: View>(_ content: Content) -> some View {
        content
            .toolbar(.hidden, for: .navigationBar)
            .toolbar(.hidden, for: .tabBar)
            .onChange(of: showSidePanel) { _, newValue in
                if newValue {
                    sidePanelContentFocused = true
                }
            }
            #if os(tvOS)
            .onExitCommand {
                if showSidePanel {
                    withAnimation {
                        showSidePanel = false
                    }
                } else {
                    dismiss()
                }
            }
            .onChange(of: playerManager.availableQualities) { _, _ in
                customTransportBarItems = buildTransportBarItems()
            }
            #endif
    }
    
    // MARK: - Components

    #if os(tvOS)
    private var videoPlayerArea: some View {
        ZStack {
            if let player = playerManager.player {
                CustomVideoPlayer(
                    player: player,
                    showsPlaybackControls: true,
                    customMenuItems: customTransportBarItems,
                    contextualActions: []
                )
                .ignoresSafeArea()
            } else {
                Rectangle()
                    .fill(Color.black)
                    .overlay {
                        if isLoading {
                            ProgressView()
                                .tint(.white)
                                .scaleEffect(1.5)
                        }
                    }
            }
        }
    }
    #else
    private var videoPlayerArea: some View {
        EmptyView() // Shared codebase fallback
    }
    #endif

    private var sidePanel: some View {
        VStack(spacing: 0) {
            // Description Header
            Text("Description")
                .font(.headline)
                .foregroundColor(.white)
                .padding()
            
            // Description List
            List {
                Color.clear
                    .frame(height: 1)
                    .listRowBackground(Color.clear)
                    .focusable(true)

                ForEach(descriptionParagraphs) { paragraph in
                    Text(paragraph.content)
                        .font(.body)
                        .foregroundColor(.white.opacity(0.9))
                        .listRowBackground(Color.clear)
                        .focusable(true)
                }
            }
            .listStyle(.plain)
            .padding(.leading, 20)
            .focused($sidePanelContentFocused)
        }
        .background(Color.gray.opacity(0.2))
    }
    
    // MARK: - Logic
    
    private func loadLivestream() async {
        guard let liveStream = creator.liveStream else {
            errorMessage = "No livestream info available"
            isLoading = false
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        do {
            // Revert to nil (Android behavior)
            let deliveryInfo = try await api.getDeliveryInfo(
                scenario: .live,
                entityId: liveStream.id,
                outputKind: nil
            )
            
            let qualities = deliveryInfo.availableVariants()
            
            // Dummy post construction for player manager interface
            // We must map CreatorModelV3 to BlogPostModelV3Creator
            let postCreator = BlogPostModelV3Creator(
                id: creator.id,
                owner: BlogPostModelV3CreatorOwner(id: creator.owner.id, username: creator.owner.username),
                title: creator.title,
                urlname: creator.urlname,
                description: creator.description,
                about: creator.about,
                category: creator.category,
                cover: creator.cover,
                icon: creator.icon,
                liveStream: creator.liveStream,
                
                // Fields required by BlogPostModelV3Creator
                subscriptionPlans: creator.subscriptionPlans ?? [],
                discoverable: creator.discoverable,
                subscriberCountDisplay: creator.subscriberCountDisplay,
                incomeDisplay: creator.incomeDisplay,
                
                // Mismatched types: [ChannelModel] vs [String]?
                defaultChannel: creator.defaultChannel,
                channels: creator.channels.map { $0.id }, // Map Channel objects to IDs
                card: creator.card
            )

            let dummyPost = BlogPost(
                id: liveStream.id,
                guid: liveStream.id,
                title: liveStream.title,
                text: liveStream.description,
                type: .blogpost, // Corrected case
                channel: .typeString(creator.defaultChannel), // Use default channel ID or empty
                tags: [],
                attachmentOrder: [],
                metadata: PostMetadataModel( // Corrected type
                    hasVideo: true,
                    videoCount: 1,
                    videoDuration: 0,
                    hasAudio: false,
                    audioCount: 0,
                    audioDuration: 0,
                    hasPicture: false,
                    pictureCount: 0,
                    hasGallery: false,
                    galleryCount: 0,
                    isFeatured: false
                ),
                releaseDate: Date(),
                likes: 0,
                dislikes: 0,
                score: 0,
                comments: 0,
                creator: postCreator, // Use converted creator
                wasReleasedSilently: false,
                thumbnail: liveStream.thumbnail,
                isAccessible: true
                // Remove invalid args: canEdit, canDelete
            )
            
            try await playerManager.loadVideo(
                videoId: liveStream.id,
                title: liveStream.title,
                post: dummyPost,
                startTime: 0,
                qualities: qualities,
                isLive: true
            )
            
            playerManager.play()
            isLoading = false
            
        } catch {
            errorMessage = "Failed to load livestream: \(error.localizedDescription)"
            isLoading = false
        }
    }
}
