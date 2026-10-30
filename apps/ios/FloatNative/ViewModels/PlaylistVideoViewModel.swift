//
//  PlaylistVideoViewModel.swift
//  FloatNative
//
//  ViewModel for loading BlogPost objects from playlist video IDs
//

import SwiftUI

@MainActor
class PlaylistVideoViewModel: ObservableObject {
    @Published var posts: [BlogPost] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let api = FloatplaneAPI.shared
    
    /// Load BlogPost objects from a playlist's video IDs
    func loadPostsFromPlaylist(playlist: Playlist) async {
        guard !isLoading else {
            return
        }

        isLoading = true
        errorMessage = nil
        posts = []

        guard !playlist.videoIds.isEmpty else {
            // Empty playlist
            isLoading = false
            return
        }

        // Fetch each video ID to get the full BlogPost object
        // Note: This requires individual API calls as there's no batch endpoint
        var loadedPosts: [BlogPost] = []

        // Keep up to `maxConcurrent` requests in flight, starting the next as
        // soon as any one finishes. The old fixed chunks of 5 waited for the
        // slowest request in each chunk, so a 66-video Watch Later took 14
        // back-to-back rounds.
        let maxConcurrent = 8
        var remaining = playlist.videoIds.makeIterator()

        await withTaskGroup(of: BlogPost?.self) { group in
            for _ in 0..<maxConcurrent {
                guard let videoId = remaining.next() else { break }
                group.addTask { await self.fetchPost(videoId) }
            }

            for await post in group {
                if let post = post {
                    loadedPosts.append(post)
                }
                if let videoId = remaining.next() {
                    group.addTask { await self.fetchPost(videoId) }
                }
            }
        }

        // Sort by release date, newest first
        posts = loadedPosts.sorted { $0.releaseDate > $1.releaseDate }

        isLoading = false
    }
    
    private func fetchPost(_ videoId: String) async -> BlogPost? {
        do {
            let detailedPost = try await api.getBlogPost(id: videoId)
            // Convert BlogPostDetailedWithInteraction to BlogPost
            return try? await convertToBlogPost(detailedPost)
        } catch {
            return nil
        }
    }

    /// Convert BlogPostDetailedWithInteraction to BlogPost (BlogPostModelV3)
    private func convertToBlogPost(_ detailedPost: BlogPostDetailedWithInteraction) async throws -> BlogPost {
        let post = detailedPost.post
        
        // We need to convert ContentPostV3Response to BlogPostModelV3
        // This is a simplified conversion - we may need to adjust based on actual model differences
        // For now, we'll use the post's ID to fetch the simpler BlogPost version
        // Actually, let's try to construct it from the detailed post
        
        // Get the channel - ContentPostV3Response uses ChannelModel directly
        let channel: BlogPostModelV3Channel = .typeChannelModel(post.channel)
        
        // Get creator - convert CreatorModelV2 to BlogPostModelV3Creator
        // Note: Some fields need conversion since the models differ
        // owner/category are absent from Floatplane's slim post creator.
        let owner = post.creator.owner.map {
            BlogPostModelV3CreatorOwner(id: $0, username: $0) // owner string is all we have
        }
        
        // Convert category string to CreatorModelV3Category
        let category = CreatorModelV3Category(
            id: post.creator.category ?? "",
            title: post.creator.category ?? ""
        )
        
        let creator = BlogPostModelV3Creator(
            id: post.creator.id,
            owner: owner,
            title: post.creator.title,
            urlname: post.creator.urlname,
            description: post.creator.description,
            about: post.creator.about,
            category: category,
            cover: post.creator.cover,
            icon: post.creator.icon,
            liveStream: post.creator.liveStream,
            subscriptionPlans: post.creator.subscriptionPlans ?? [],
            discoverable: post.creator.discoverable,
            subscriberCountDisplay: post.creator.subscriberCountDisplay,
            incomeDisplay: post.creator.incomeDisplay,
            defaultChannel: post.creator.defaultChannel,
            channels: nil,
            card: nil
        )
        
        // Extract attachment IDs from attachment models
        let videoAttachmentIds = post.videoAttachments?.map { $0.id } ?? nil
        let audioAttachmentIds = post.audioAttachments?.map { $0.id } ?? nil
        let pictureAttachmentIds = post.pictureAttachments?.map { $0.id } ?? nil
        // Gallery attachments are AnyCodable, so we'll handle them differently if needed
        
        // Construct BlogPostModelV3
        return BlogPostModelV3(
            id: post.id,
            guid: post.guid,
            title: post.title,
            text: post.text,
            type: .blogpost,
            channel: channel,
            tags: post.tags,
            attachmentOrder: post.attachmentOrder,
            metadata: post.metadata,
            releaseDate: post.releaseDate,
            likes: post.likes,
            dislikes: post.dislikes,
            score: post.score,
            comments: post.comments,
            creator: creator,
            wasReleasedSilently: post.wasReleasedSilently,
            thumbnail: post.thumbnail,
            isAccessible: post.isAccessible,
            videoAttachments: videoAttachmentIds,
            audioAttachments: audioAttachmentIds,
            pictureAttachments: pictureAttachmentIds,
            galleryAttachments: nil // Gallery attachments complex to convert
        )
    }
}

