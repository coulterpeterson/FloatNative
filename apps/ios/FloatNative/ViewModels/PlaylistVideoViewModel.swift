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

        // Fetch posts with limited concurrency to avoid overwhelming the API
        // Process in chunks of 5 at a time
        let chunkSize = 5
        let chunks = playlist.videoIds.chunked(into: chunkSize)

        for chunk in chunks {
            await withTaskGroup(of: BlogPost?.self) { group in
                for videoId in chunk {
                    group.addTask {
                        do {
                            let detailedPost = try await self.api.getBlogPost(id: videoId)
                            // Convert BlogPostDetailedWithInteraction to BlogPost
                            return try? await self.convertToBlogPost(detailedPost)
                        } catch {
                            return nil
                        }
                    }
                }

                for await post in group {
                    if let post = post {
                        loadedPosts.append(post)
                    }
                }
            }
        }

        // Sort by release date, newest first
        posts = loadedPosts.sorted { $0.releaseDate > $1.releaseDate }

        isLoading = false
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
        let owner = BlogPostModelV3CreatorOwner(
            id: post.creator.owner, // Use owner string as ID (best we can do)
            username: post.creator.owner // Use owner string as username too
        )
        
        // Convert category string to CreatorModelV3Category
        let category = CreatorModelV3Category(
            id: post.creator.category, // Use category string as ID
            title: post.creator.category // Use category string as title
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

// MARK: - Array Extension for Chunking

extension Array {
    func chunked(into size: Int) -> [[Element]] {
        return stride(from: 0, to: count, by: size).map {
            Array(self[$0..<Swift.min($0 + size, count)])
        }
    }
}

