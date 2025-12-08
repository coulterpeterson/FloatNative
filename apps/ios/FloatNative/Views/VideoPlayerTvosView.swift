//
//  VideoPlayerTvosView.swift
//  FloatNative
//
//  Custom tvOS video player with YouTube-style side panel
//  Features: Description/Comments sidebar, Like/Dislike, Quality selector
//

import SwiftUI
import AVKit

// Identifiable wrapper for description paragraphs to ensure stable list rendering
struct DescriptionParagraph: Identifiable {
    let id = UUID()
    let content: AttributedString
}

struct VideoPlayerTvosView: View {
    let post: BlogPost

    @StateObject private var playerManager = AVPlayerManager.shared
    @StateObject private var api = FloatplaneAPI.shared
    @Environment(\.dismiss) private var dismiss

    // Video state
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var isStreamOffline = false

    // Side panel state
    @State private var showSidePanel = false
    @State private var sidePanelMode: SidePanelMode = .description
    @FocusState private var sidePanelContentFocused: Bool

    // Like/Dislike state
    @State private var currentLikes: Int = 0
    @State private var currentDislikes: Int = 0
    @State private var userInteraction: ContentPostV3Response.UserInteraction?

    // Comments state
    @State private var comments: [Comment] = []
    @State private var isLoadingComments = false
    @State private var visibleComments: Set<String> = []

    // Description state
    @State private var parsedDescription: AttributedString = AttributedString("")
    @State private var descriptionParagraphs: [DescriptionParagraph] = []
    @State private var hasParsedDescription = false

    // Quality selector state
    @State private var isChangingQuality = false

    // Transport bar items (tvOS)
    #if os(tvOS)
    @State private var customTransportBarItems: [UIMenuElement] = []
    #endif

    enum SidePanelMode {
        case description
        case comments
    }

    var hasLiked: Bool {
        userInteraction == .like
    }

    var hasDisliked: Bool {
        userInteraction == .dislike
    }

    var isVideoPost: Bool {
        post.metadata.hasVideo
    }

    var isLivestream: Bool {
        post.isLivestream
    }


    #if os(tvOS)
    // Helper to create quality menu with placeholder items (for immediate display)
    func createPlaceholderQualityMenu() -> UIMenu {
        let placeholderQualities = ["360p", "480p", "720p", "1080p"]
        let qualityChildren = placeholderQualities.map { label in
            UIAction(title: label, image: nil) { _ in
                print("⚠️ Quality menu not ready yet")
            }
        }
        return UIMenu(
            title: "Quality",
            image: UIImage(systemName: "gearshape"),
            children: qualityChildren
        )
    }

    // Helper to create quality menu with actual available qualities
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
                    await changeQuality(to: quality)
                }
            }
        }
        return UIMenu(
            title: "Quality",
            image: UIImage(systemName: "gearshape"),
            children: qualityChildren
        )
    }

    // Build full transport bar items with all state (after API data loaded)
    func buildFullTransportBarItems() -> [UIMenuElement] {
        // For livestreams, return empty array to hide all controls
        if isLivestream {
            return []
        }


        // Like button (with interaction state)
        let likeIconName = hasLiked ? "hand.thumbsup.fill" : "hand.thumbsup"
        let likeAction: UIAction = UIAction(
            title: "\(currentLikes)",
            image: UIImage(systemName: likeIconName)
        ) { _ in
            Task {
                await self.handleLike()
            }
        }

        // Dislike button (with interaction state)
        let dislikeIconName = hasDisliked ? "hand.thumbsdown.fill" : "hand.thumbsdown"
        let dislikeAction: UIAction = UIAction(
            title: "\(currentDislikes)",
            image: UIImage(systemName: dislikeIconName)
        ) { _ in
            Task {
                await self.handleDislike()
            }
        }

        // Description button
        let descriptionAction: UIAction = UIAction(
            title: "Description",
            image: UIImage(systemName: "doc.text")
        ) { _ in
            self.sidePanelMode = .description
            withAnimation {
                self.showSidePanel.toggle()
            }
        }

        // Comments button
        let commentsAction: UIAction = UIAction(
            title: "Comments",
            image: UIImage(systemName: "bubble.left.and.bubble.right")
        ) { _ in
            self.sidePanelMode = .comments
            withAnimation {
                self.showSidePanel.toggle()
            }
        }

        let qualityMenu: UIMenu = createQualityMenu()

        let items: [UIMenuElement] = [
            likeAction,
            dislikeAction,
            descriptionAction,
            commentsAction,
            qualityMenu
        ]
        return items
    }
    #endif

    // MARK: - Main Content

    private var mainContent: some View {
        GeometryReader { geometry in
            ZStack {
                // Background
                Color.black
                    .ignoresSafeArea()

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
                                await loadVideo()
                            }
                        }
                        #if os(tvOS)
                        .buttonStyle(.card)
                        #endif
                    }
                    .padding()
                }

                // Stream offline overlay (for livestreams that have ended)
                if isStreamOffline && isLivestream {
                    StreamOfflineOverlay {
                        dismiss()
                    }
                }
            }
        }
    }

    var body: some View {
        withStateObservers(
            withNavigationModifiers(
                withLifecycleModifiers(mainContent)
            )
        )
    }

    // MARK: - Modifier Groups

    private func withLifecycleModifiers<Content: View>(_ content: Content) -> some View {
        content
            .onAppear {
                currentLikes = post.likes
                currentDislikes = post.dislikes





                #if os(tvOS)
                // Initialize transport bar items immediately with initial data
                // This ensures buttons are accessible right away, even before API data loads
                customTransportBarItems = buildFullTransportBarItems()
                #endif
            }
            .task {
                await loadVideo()
            }
            .task(priority: .userInitiated) {
                // Guard to ensure we only parse description once
                guard !hasParsedDescription else { return }

                // Capture text to avoid capturing self in detached task
                let htmlText = post.text

                // Run parsing in background to avoid blocking main thread
                let attributedParagraphs = await Task.detached(priority: .userInitiated) {
                    return VideoPlayerTvosView.parseHTMLParagraphsStatic(htmlText)
                }.value


                // Wrap each paragraph in DescriptionParagraph with stable UUID
                descriptionParagraphs = attributedParagraphs.map { paragraph in
                    DescriptionParagraph(content: paragraph)
                }

                // Store full parsed description for reference (optional)
                if let firstParagraph = attributedParagraphs.first {
                    parsedDescription = firstParagraph
                }

                // Set flag to prevent re-parsing
                hasParsedDescription = true
            }
            .task(priority: .userInitiated) {
                await loadInteractionState()
                #if os(tvOS)
                // Build FULL transport bar items after interaction state is loaded
                customTransportBarItems = buildFullTransportBarItems()
                #endif
            }
            .task(priority: .userInitiated) {
                await loadComments(limit: 20)
            }
            .onDisappear {
                // Save progress (skip for livestreams)
                if !isLivestream {
                    Task {
                        await playerManager.saveProgress()
                    }
                }
                Task {
                    #if os(tvOS)
                    // On tvOS, stop playback when leaving the video player
                    // (Unlike iOS where we want PiP to continue)
                    playerManager.pause()
                    // Reset player to stop any ongoing loading that might auto-play
                    playerManager.reset()
                    #endif
                }
            }
    }

    private func withNavigationModifiers<Content: View>(_ content: Content) -> some View {
        content
            // Only hide toolbar for video posts (full-screen experience)
            // For non-video posts, show the navigation bar so back button works
            .toolbar(isVideoPost ? .hidden : .visible, for: .navigationBar)
            .toolbar(.hidden, for: .tabBar)
            .onChange(of: showSidePanel) { _, newValue in
                if newValue {
                    // Auto-focus the list content when panel opens
                    sidePanelContentFocused = true
                }
            }
            #if os(tvOS)
            // Handle Menu button (back) press on tvOS
            .onExitCommand {
                if showSidePanel {
                    // If side panel is visible, close it first
                    withAnimation {
                        showSidePanel = false
                    }
                } else {
                    // If side panel is closed, dismiss the video player
                    dismiss()
                }
            }
            #endif
    }

    private func withStateObservers<Content: View>(_ content: Content) -> some View {
        #if os(tvOS)
        return content
            .onChange(of: currentLikes) { _, newValue in
                customTransportBarItems = buildFullTransportBarItems()
            }
            .onChange(of: currentDislikes) { _, newValue in
                customTransportBarItems = buildFullTransportBarItems()
            }
            .onChange(of: userInteraction) { _, newValue in
                customTransportBarItems = buildFullTransportBarItems()
            }
            .onChange(of: playerManager.availableQualities) { _, newValue in
                customTransportBarItems = buildFullTransportBarItems()
            }

        #else
        return content
        #endif
    }

    // MARK: - Video Player Area

    #if os(tvOS)
    private var videoPlayerArea: some View {
        return Group {
            if isVideoPost {
                // Video post - show player
                ZStack {
                    if let player = playerManager.player {

                        CustomVideoPlayer(
                            player: player,
                            showsPlaybackControls: true,
                            customMenuItems: customTransportBarItems,  // Use state variable
                            contextualActions: []
                        )
                        .id(post.id)  // Force recreation when navigating between videos
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
            } else {
                // Image or text post - show non-video layout
                nonVideoLayout
            }
        }
    }
    #else
    private var videoPlayerArea: some View {
        return Group {
            if isVideoPost {
                // Video post - show player
                ZStack {
                    if let player = playerManager.player {
                        CustomVideoPlayer(player: player, showsPlaybackControls: true)
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
            // iOS only supports video posts
        }
    }
    #endif

    // MARK: - Non-Video Layout (Image/Text posts)

    #if os(tvOS)
    private var nonVideoLayout: some View {
        List {
            Section {
                // Optional Image (50% smaller, centered)
                if post.metadata.hasPicture, let imageURL = post.thumbnail?.fullURL {
                    HStack {
                        Spacer()
                        CachedAsyncImage(url: imageURL) { image in
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fit)
                        } placeholder: {
                            Rectangle()
                                .fill(Color.floatplaneGray.opacity(0.3))
                        }
                        .frame(width: 600)
                        .aspectRatio(16/9, contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        Spacer()
                    }
                    .listRowBackground(Color.clear)
                    .focusable(true)
                }

                // Title
                Text(post.title)
                    .font(.largeTitle)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .listRowBackground(Color.clear)
                    .focusable(true)

                // Channel/Creator Info
                HStack(spacing: 12) {
                    if let channelIcon = post.channel.channelObject?.icon.fullURL {
                        CachedAsyncImage(url: channelIcon) { image in
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Circle()
                                .fill(Color.floatplaneGray.opacity(0.3))
                        }
                        .frame(width: 60, height: 60)
                        .clipShape(Circle())
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        if let channel = post.channel.channelObject {
                            Text(channel.title)
                                .font(.title3)
                                .fontWeight(.semibold)
                                .foregroundColor(.white)
                        }
                        Text(post.creator.title)
                            .font(.subheadline)
                            .foregroundColor(.white.opacity(0.8))
                    }
                }
                .listRowBackground(Color.clear)
                .focusable(true)

                // Like/Dislike Buttons
                HStack(spacing: 16) {
                    Button {
                        Task {
                            await handleLike()
                        }
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: hasLiked ? "hand.thumbsup.fill" : "hand.thumbsup")
                            Text("\(currentLikes)")
                        }
                        .font(.title3)
                        .foregroundColor(hasLiked ? .floatplaneBlue : .white)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .background(Color.floatplaneGray.opacity(0.3))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(.card)

                    Button {
                        Task {
                            await handleDislike()
                        }
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: hasDisliked ? "hand.thumbsdown.fill" : "hand.thumbsdown")
                            Text("\(currentDislikes)")
                        }
                        .font(.title3)
                        .foregroundColor(hasDisliked ? .red : .white)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .background(Color.floatplaneGray.opacity(0.3))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(.card)
                }
                .listRowBackground(Color.clear)

                // Description
                VStack(alignment: .leading, spacing: 12) {
                    Text("Description")
                        .font(.title2)
                        .fontWeight(.semibold)
                        .foregroundColor(.white)

                    ForEach(descriptionParagraphs) { paragraph in
                        Text(paragraph.content)
                            .font(.body)
                            .foregroundColor(.white.opacity(0.9))
                            .focusable(true)
                    }
                }
                .listRowBackground(Color.clear)

                // View Comments Button
                Button {
                    sidePanelMode = .comments
                    withAnimation {
                        showSidePanel = true
                    }
                } label: {
                    HStack {
                        Image(systemName: "bubble.left.and.bubble.right")
                        Text("View Comments (\(post.comments))")
                    }
                    .font(.title3)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Color.floatplaneBlue)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.card)
                .listRowBackground(Color.clear)
            }
        }
        .listStyle(.plain)
        .padding(.leading, 20)
        .background(Color.black)
    }
    #endif

    // MARK: - Side Panel

    private var sidePanel: some View {
        VStack(spacing: 0) {
            // Tab Picker (only show for video posts)
            if isVideoPost {
                Picker("", selection: $sidePanelMode) {
                    Text("Description").tag(SidePanelMode.description)
                    Text("Comments").tag(SidePanelMode.comments)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
            }

            // Content - Use List for proper tvOS focus and scrolling
            if isVideoPost && sidePanelMode == .description {
                descriptionList
            } else {
                commentsList
            }
        }
        .background(Color.gray.opacity(0.2))
    }

    // Description as a List for proper tvOS scrolling (same pattern as comments)
    private var descriptionList: some View {
        List {
            Section(header: Text("Description").font(.headline).foregroundColor(.white)) {
                // Invisible anchor item to capture initial focus and prevent auto-scroll
                Color.clear
                    .frame(height: 1)
                    .listRowBackground(Color.clear)
                    .focusable(true)

                ForEach(descriptionParagraphs) { paragraph in
                    Text(paragraph.content)
                        .font(.body)
                        .foregroundColor(.white.opacity(0.9))
                        // No vertical padding for most compact spacing
                        .listRowBackground(Color.clear)
                        .focusable(true)
                }
            }
        }
        .listStyle(.plain)
        .padding(.leading, 20)
        .focused($sidePanelContentFocused)
    }

    // Comments as a List for proper tvOS scrolling
    private var commentsList: some View {
        List {
            Section(header: Text("Comments").font(.headline).foregroundColor(.white)) {
                if isLoadingComments {
                    ProgressView()
                        .tint(.white)
                        .listRowBackground(Color.clear)
                } else if comments.isEmpty {
                    Text("No comments yet")
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.6))
                        .listRowBackground(Color.clear)
                } else {
                    ForEach(comments) { comment in
                        CommentRow(comment: comment)
                            .padding(12)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(Color(white: 0.15))
                            )
                            .listRowBackground(Color.clear)
                            .focusable(true)
                            .onAppear {
                                visibleComments.insert(comment.id)
                            }
                            .onDisappear {
                                visibleComments.remove(comment.id)
                            }
                    }
                }
            }
        }
        .listStyle(.plain)
        .padding(.leading, 20)
        .focused($sidePanelContentFocused)
    }

    // MARK: - Helper Functions

    private func formatDate(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: Date())
    }

    /// Parse HTML into paragraphs with character-based grouping for better readability
    /// Groups consecutive <p> tags until reaching ~400 characters for natural reading chunks
    private nonisolated static func parseHTMLParagraphsStatic(_ html: String) -> [AttributedString] {
        // Split by closing </p> tags to get individual <p> chunks
        let paragraphChunks = html.components(separatedBy: "</p>")
        var groupedParagraphs: [AttributedString] = []
        var currentGroupHTML = ""
        var currentGroupCharCount = 0
        let targetCharsPerGroup = 400

        for chunk in paragraphChunks {
            // Skip empty chunks
            let trimmed = chunk.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { continue }

            // Find the opening <p> tag or use the whole chunk
            var paragraphHTML = trimmed
            if let pTagRange = trimmed.range(of: "<p[^>]*>", options: .regularExpression) {
                paragraphHTML = String(trimmed[pTagRange.lowerBound...])
            }

            // Add closing tag back
            if !paragraphHTML.hasSuffix("</p>") {
                paragraphHTML += "</p>"
            }

            // Filter out empty paragraphs like <p><br /></p>
            let tempAttributed = htmlToAttributedStringStatic(paragraphHTML)
            let plainText = String(tempAttributed.characters).trimmingCharacters(in: .whitespacesAndNewlines)
            guard !plainText.isEmpty else { continue }

            // Add to current group
            currentGroupHTML += paragraphHTML
            currentGroupCharCount += plainText.count

            // If we've reached target size, finalize this group
            if currentGroupCharCount >= targetCharsPerGroup {
                let groupedAttributed = htmlToAttributedStringStatic(currentGroupHTML)
                if !String(groupedAttributed.characters).trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    groupedParagraphs.append(groupedAttributed)
                }
                currentGroupHTML = ""
                currentGroupCharCount = 0
            }
        }

        // Add any remaining content as final paragraph
        if !currentGroupHTML.isEmpty {
            let groupedAttributed = htmlToAttributedStringStatic(currentGroupHTML)
            if !String(groupedAttributed.characters).trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                groupedParagraphs.append(groupedAttributed)
            }
        }

        // If no paragraphs found, treat entire HTML as one paragraph
        if groupedParagraphs.isEmpty {
            groupedParagraphs.append(htmlToAttributedStringStatic(html))
        }

        return groupedParagraphs
    }

    private nonisolated static func htmlToAttributedStringStatic(_ html: String) -> AttributedString {
        guard let data = html.data(using: .utf8) else {
            return AttributedString(html)
        }

        let options: [NSAttributedString.DocumentReadingOptionKey: Any] = [
            .documentType: NSAttributedString.DocumentType.html,
            .characterEncoding: String.Encoding.utf8.rawValue
        ]

        if let nsAttributedString = try? NSAttributedString(data: data, options: options, documentAttributes: nil) {
            var attributedString = AttributedString(nsAttributedString)

            // Strip embedded HTML colors and apply adaptive color
            // This ensures text is white in dark mode, black in light mode
            for run in attributedString.runs {
                // Don't modify links - they should stay blue
                if run.link == nil {
                    attributedString[run.range].foregroundColor = .white.opacity(0.9)
                }
            }

            // Apply system font (sans-serif) to match app
            attributedString.font = .subheadline

            // Shorten long URLs while preserving links
            // Collect changes first to avoid mutation during iteration
            var urlChanges: [(range: Range<AttributedString.Index>, link: URL, shortened: String)] = []
            for run in attributedString.runs {
                if let link = run.link {
                    var linkText = String(attributedString[run.range].characters)

                    // Strip protocol (http:// or https://)
                    if linkText.hasPrefix("https://") {
                        linkText = String(linkText.dropFirst(8))
                    } else if linkText.hasPrefix("http://") {
                        linkText = String(linkText.dropFirst(7))
                    }

                    // Strip www. if present
                    if linkText.hasPrefix("www.") {
                        linkText = String(linkText.dropFirst(4))
                    }

                    // Truncate if longer than 40 characters
                    if linkText.count > 40 {
                        let shortened = String(linkText.prefix(40)) + "..."
                        urlChanges.append((range: run.range, link: link, shortened: shortened))
                    } else {
                        // Use cleaned URL even if not truncated
                        urlChanges.append((range: run.range, link: link, shortened: linkText))
                    }
                }
            }

            // Apply changes in reverse order to preserve indices
            for change in urlChanges.reversed() {
                var shortenedString = AttributedString(change.shortened)
                shortenedString.link = change.link
                shortenedString.foregroundColor = .blue
                attributedString.replaceSubrange(change.range, with: shortenedString)
            }

            // Detect and linkify timestamps (e.g., "3:05", "1:23:45")
            let fullText = String(attributedString.characters)
            let timestampPattern = #"\b(\d{1,2}):(\d{2})(?::(\d{2}))?\b"#

            if let regex = try? NSRegularExpression(pattern: timestampPattern) {
                let matches = regex.matches(in: fullText, range: NSRange(fullText.startIndex..., in: fullText))

                for match in matches.reversed() {
                    guard let range = Range(match.range, in: fullText) else { continue }
                    let timestampText = String(fullText[range])

                    // Parse timestamp to seconds
                    let components = timestampText.split(separator: ":")
                    var seconds = 0
                    if components.count == 2 {
                        // Format: M:SS or MM:SS
                        seconds = (Int(components[0]) ?? 0) * 60 + (Int(components[1]) ?? 0)
                    } else if components.count == 3 {
                        // Format: H:MM:SS
                        seconds = (Int(components[0]) ?? 0) * 3600 + (Int(components[1]) ?? 0) * 60 + (Int(components[2]) ?? 0)
                    }

                    // Create custom URL for timestamp seeking
                    let timestampURL = URL(string: "floatplane://seek/\(seconds)")!

                    // Find range in AttributedString and apply link
                    if let attrRange = attributedString.range(of: timestampText) {
                        attributedString[attrRange].link = timestampURL
                        attributedString[attrRange].foregroundColor = .blue
                    }
                }
            }

            return attributedString
        }

        return AttributedString(html)
    }

    // MARK: - API Functions

    private func changeQuality(to quality: QualityVariant) async {
        // Set loading state
        isChangingQuality = true

        do {
            // Change quality using AVPlayerManager
            try await playerManager.changeQuality(quality)

            // Clear loading state
            isChangingQuality = false
        } catch {
            // Handle error
            isChangingQuality = false
            print("Failed to change quality: \(error)")
        }
    }

    private func loadVideo() async {
        // Handle livestream loading differently

        if isLivestream {
            await loadLivestream()
            return
        }

        // Skip video loading for non-video posts
        guard post.metadata.hasVideo else {
            isLoading = false
            return
        }


        guard let videoId = post.videoAttachments?.first else {
            errorMessage = "No video available for this post"
            isLoading = false
            return
        }



        isLoading = true
        errorMessage = nil

        do {
            // Get video content
            let content = try await api.getVideoContent(id: videoId)

            // Get delivery info
            let deliveryInfo = try await api.getDeliveryInfo(
                scenario: .onDemand,
                entityId: videoId,
                outputKind: .hlsFmp4
            )

            let qualities = deliveryInfo.availableVariants()


            // Load video into player
            try await playerManager.loadVideo(
                videoId: videoId,
                title: post.title,
                post: post,
                startTime: Double(content.progress ?? 0),
                qualities: qualities
            )


            // Add to watch history
            WatchHistoryManager.shared.addToHistory(postId: post.id, videoId: videoId)

            // Auto-play
            playerManager.play()

            isLoading = false
        } catch {
            errorMessage = "Failed to load video: \(error.localizedDescription)"
            isLoading = false
        }
    }

    private func loadLivestream() async {
        guard let liveStream = post.creator.liveStream else {
            errorMessage = "No livestream available"
            isLoading = false
            return
        }

        isLoading = true
        errorMessage = nil

        do {
            // Get delivery info for livestream
            let deliveryInfo = try await api.getDeliveryInfo(
                scenario: .live,
                entityId: liveStream.id,
                outputKind: .hlsFmp4
            )

            let qualities = deliveryInfo.availableVariants()


            // Load livestream into player (no start time for live content)
            try await playerManager.loadVideo(
                videoId: liveStream.id,
                title: post.title,
                post: post,
                startTime: 0,  // No start time for livestreams
                qualities: qualities
            )

            // Auto-play
            playerManager.play()

            isLoading = false

            // TODO: Add AVPlayer observation to detect when livestream ends
            // Monitor player.status and player.error to set isStreamOffline = true
            // when the stream goes offline or encounters a fatal error
        } catch {
            errorMessage = "Failed to load livestream: \(error.localizedDescription)"
            isLoading = false
        }
    }

    private func loadInteractionState() async {
        do {
            let detailedPost = try await api.getBlogPost(id: post.id)


            currentLikes = detailedPost.likes
            currentDislikes = detailedPost.dislikes
            userInteraction = detailedPost.selfUserInteraction
        } catch {
            print("❌ loadInteractionState: Failed - \(error)")
        }
    }

    private func loadComments(limit: Int) async {
        isLoadingComments = true
        do {
            comments = try await api.getComments(blogPostId: post.id, limit: limit)
            isLoadingComments = false
        } catch {
            print("Failed to load comments: \(error)")
            isLoadingComments = false
        }
    }

    private func handleLike() async {
        // Save previous state for rollback

        let previousLikes = currentLikes
        let previousDislikes = currentDislikes
        let previousInteraction = userInteraction

        // Optimistically update UI
        switch userInteraction {
        case .like:
            // Unlike: remove like
            print("👍 handleLike: Removing like (unlike)")
            currentLikes -= 1
            userInteraction = nil
        case .dislike:
            // Switch from dislike to like
            print("👍 handleLike: Switching from dislike to like")
            currentDislikes -= 1
            currentLikes += 1
            userInteraction = .like
        case nil:
            // Add like
            print("👍 handleLike: Adding like")
            currentLikes += 1
            userInteraction = .like
        }


        // Call API in background
        do {
            _ = try await api.likeContent(contentType: "blogPost", id: post.id)
            print("👍 handleLike: API call succeeded")
        } catch {
            // Rollback on failure
            print("❌ handleLike: API call failed, rolling back")
            currentLikes = previousLikes
            currentDislikes = previousDislikes
            userInteraction = previousInteraction
            print("Failed to update like: \(error)")
        }
    }

    private func handleDislike() async {
        // Save previous state for rollback

        let previousLikes = currentLikes
        let previousDislikes = currentDislikes
        let previousInteraction = userInteraction

        // Optimistically update UI
        switch userInteraction {
        case .dislike:
            // Undislike: remove dislike
            print("👎 handleDislike: Removing dislike (undislike)")
            currentDislikes -= 1
            userInteraction = nil
        case .like:
            // Switch from like to dislike
            print("👎 handleDislike: Switching from like to dislike")
            currentLikes -= 1
            currentDislikes += 1
            userInteraction = .dislike
        case nil:
            // Add dislike
            print("👎 handleDislike: Adding dislike")
            currentDislikes += 1
            userInteraction = .dislike
        }


        // Call API in background
        do {
            _ = try await api.dislikeContent(contentType: "blogPost", id: post.id)
            print("👎 handleDislike: API call succeeded")
        } catch {
            // Rollback on failure
            print("❌ handleDislike: API call failed, rolling back")
            currentLikes = previousLikes
            currentDislikes = previousDislikes
            userInteraction = previousInteraction
            print("Failed to update dislike: \(error)")
        }
    }
}

// MARK: - Comment Row

struct CommentRow: View {
    let comment: Comment

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(comment.user.username)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)

                Text(formatDate(comment.postDate))
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.6))
            }

            Text(comment.text)
                .font(.body)
                .foregroundColor(.white.opacity(0.9))
        }
        .padding(.vertical, 12)
    }

    private func formatDate(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}

#Preview {
    // Preview not available for tvOS-specific view
    EmptyView()
}
