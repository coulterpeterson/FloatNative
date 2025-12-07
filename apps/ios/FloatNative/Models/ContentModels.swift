//
//  ContentModels.swift
//  FloatNative
//
//  Created by Claude on 2025-10-08.
//
//  Note: Using OpenAPI-generated models where available
//

import Foundation

// MARK: - Blog Post Models
// Using OpenAPI-generated BlogPostModelV3 for accuracy and type safety

typealias BlogPost = BlogPostModelV3
typealias BlogPostChannel = BlogPostModelV3Channel
typealias BlogPostCreator = BlogPostModelV3Creator
typealias BlogPostCreatorOwner = BlogPostModelV3CreatorOwner

// BlogPostDetailed is returned by /api/v3/content/post (uses CreatorModelV2 with string owner)
typealias BlogPostDetailed = ContentPostV3Response

// Wrapper to add selfUserInteraction field that's missing from OpenAPI spec
struct BlogPostDetailedWithInteraction: Codable {
    let post: ContentPostV3Response
    let selfUserInteraction: ContentPostV3Response.UserInteraction?

    enum CodingKeys: String, CodingKey {
        case selfUserInteraction
    }

    init(from decoder: Decoder) throws {
        // Get the container first before decoding post (which would consume the decoder)
        let container = try decoder.container(keyedBy: CodingKeys.self)

        // Decode selfUserInteraction from the container
        selfUserInteraction = try? container.decodeIfPresent(ContentPostV3Response.UserInteraction.self, forKey: .selfUserInteraction)

        // Now decode the main post (this will ignore the selfUserInteraction field)
        post = try ContentPostV3Response(from: decoder)
    }

    func encode(to encoder: Encoder) throws {
        try post.encode(to: encoder)
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(selfUserInteraction, forKey: .selfUserInteraction)
    }
}

// Helper to access post fields directly
extension BlogPostDetailedWithInteraction {
    var id: String { post.id }
    var guid: String { post.guid }
    var title: String { post.title }
    var text: String { post.text }
    var likes: Int { post.likes }
    var dislikes: Int { post.dislikes }
    var score: Int { post.score }
    var releaseDate: Date { post.releaseDate }
    var userInteraction: [ContentPostV3Response.UserInteraction]? { post.userInteraction }
}

// Helper extension for BlogPostChannel compatibility
extension BlogPostModelV3Channel {
    /// Get the channel object if this is a channel (not just an ID string)
    var channelObject: ChannelModel? {
        if case .typeChannelModel(let channel) = self {
            return channel
        }
        return nil
    }

    /// Get the channel ID regardless of whether it's an object or string
    var channelId: String {
        switch self {
        case .typeChannelModel(let channel):
            return channel.id
        case .typeString(let id):
            return id
        }
    }
}

// MARK: - Multi-Creator Feed Response
// Using OpenAPI-generated models

typealias CreatorListResponse = ContentCreatorListV3Response
typealias FetchCursor = ContentCreatorListLastItems
typealias ContentMetadata = PostMetadataModel

// MARK: - Video Content
// Using OpenAPI-generated models

typealias VideoContent = ContentVideoV3Response
typealias VideoLevel = ContentVideoV3ResponseLevelsInner

// MARK: - Picture Content
// Using OpenAPI-generated models

typealias PictureContent = ContentPictureV3Response
typealias ImageFile = ImageFileModel

// MARK: - Comments
// Using OpenAPI-generated models

typealias Comment = CommentModel
typealias InteractionCounts = CommentV3PostResponseInteractionCounts

struct PostCommentRequest: Codable {
    let blogPost: String
    let text: String
}

struct CommentInteractionRequest: Codable {
    let comment: String
    let blogPost: String
}

// MARK: - Content Interaction

struct ContentInteractionRequest: Codable {
    let contentType: String
    let id: String
}

// MARK: - Content Tags

typealias ContentTags = [String: Int]

// MARK: - Progress Tracking

struct ProgressRequest: Codable {
    let ids: [String]
    let contentType: String
}

struct ProgressResponse: Codable, Identifiable {
    let id: String
    let progress: Int
}

struct UpdateProgressRequest: Codable {
    let id: String
    let contentType: String
    let progress: Int
}

// MARK: - Watch History

/// BlogPost format returned by watch history API - has string attachment arrays instead of objects
struct WatchHistoryBlogPost: Codable {
    let id: String
    let guid: String
    let title: String
    let text: String
    let type: String
    let channel: ChannelModel
    let tags: [String]
    let attachmentOrder: [String]
    let metadata: PostMetadataModel
    let releaseDate: Date
    let likes: Int
    let dislikes: Int
    let score: Int
    let comments: Int
    let creator: CreatorModelV2
    let wasReleasedSilently: Bool
    let thumbnail: ImageModel?
    let isAccessible: Bool
    // Attachments are string IDs in watch history response (not objects)
    let videoAttachments: [String]?
    let audioAttachments: [String]?
    let pictureAttachments: [String]?
    let galleryAttachments: [String]?
}

struct WatchHistoryResponse: Codable {
    let userId: String
    let contentId: String
    let contentType: String
    let progress: Int
    let updatedAt: Date
    let blogPost: WatchHistoryBlogPost
}
