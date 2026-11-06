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
    let selfUserInteraction: ContentPostV3Response.SelfUserInteraction?

    enum CodingKeys: String, CodingKey {
        case selfUserInteraction
    }

    init(from decoder: Decoder) throws {
        // Get the container first before decoding post (which would consume the decoder)
        let container = try decoder.container(keyedBy: CodingKeys.self)

        // Decode selfUserInteraction from the container
        selfUserInteraction = try? container.decodeIfPresent(ContentPostV3Response.SelfUserInteraction.self, forKey: .selfUserInteraction)

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

extension ContentPostV3Response {
    /// Video attachments in the author-intended order. The `videoAttachments`
    /// array on the response is unordered relative to `attachmentOrder`
    /// (confirmed against the C3GeAE0LmM fixture, GH #23), so feeding the
    /// raw array into a picker would mis-sequence multi-part posts.
    var orderedVideoAttachments: [VideoAttachmentModel] {
        let attachments = videoAttachments ?? []
        guard !attachmentOrder.isEmpty else { return attachments }
        let byId = Dictionary(uniqueKeysWithValues: attachments.map { ($0.id, $0) })
        let ordered = attachmentOrder.compactMap { byId[$0] }
        // Append anything that wasn't in attachmentOrder so we never silently
        // drop a video Floatplane sent.
        let missing = attachments.filter { att in !attachmentOrder.contains(att.id) }
        return ordered + missing
    }
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
//
// Wraps `ContentCreatorListV3Response` with element-tolerant decoding for the
// `blogPosts` array. A single malformed post (e.g. an unexpected schema
// variation on a new content type) gets logged and skipped instead of failing
// the whole feed. The shape is otherwise identical to the generated type, so
// call sites don't change.

typealias FetchCursor = ContentCreatorListLastItems
typealias ContentMetadata = PostMetadataModel

struct CreatorListResponse: Codable {
    let blogPosts: [BlogPost]
    let lastElements: [FetchCursor]

    enum CodingKeys: String, CodingKey {
        case blogPosts, lastElements
    }

    init(blogPosts: [BlogPost], lastElements: [FetchCursor]) {
        self.blogPosts = blogPosts
        self.lastElements = lastElements
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        // Lossy decode: skip individual posts that fail rather than failing
        // the whole feed. See LossyArray.
        let lossy = try container.decode(LossyArray<BlogPost>.self, forKey: .blogPosts)
        self.blogPosts = lossy.wrappedValue
        self.lastElements = try container.decode([FetchCursor].self, forKey: .lastElements)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(blogPosts, forKey: .blogPosts)
        try container.encode(lastElements, forKey: .lastElements)
    }
}

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

/// Reply requests hit a different endpoint (`POST /api/v3/comment/reply`)
/// from top-level comments — the top-level endpoint rejects any extra
/// fields with a Joi "X is not allowed" 400 (GH #13). See spec-overlay.json.
struct PostReplyRequest: Codable {
    let blogPost: String
    let text: String
    let replyTo: String
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

struct WatchHistoryResponse: Codable {
    let userId: String
    let contentId: String
    let contentType: String
    let progress: Int
    let updatedAt: Date
    let blogPost: BlogPost
}
