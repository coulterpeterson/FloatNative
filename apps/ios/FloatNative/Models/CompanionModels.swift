//
//  CompanionModels.swift
//  FloatNative
//
//  Models for FloatNative Companion API
//

import Foundation

// MARK: - API Error

enum CompanionAPIError: LocalizedError {
    case invalidURL
    case invalidResponse
    case httpError(statusCode: Int, message: String?)
    case decodingError(Error)
    case notAuthenticated
    case networkError(Error)
    case registrationFailed
    case unknown

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid URL"
        case .invalidResponse:
            return "Invalid response from server"
        case .httpError(let statusCode, let message):
            return "HTTP Error \(statusCode): \(message ?? "Unknown error")"
        case .decodingError(let error):
            return "Failed to decode response: \(error.localizedDescription)"
        case .notAuthenticated:
            return "Not authenticated. Please register first."
        case .networkError(let error):
            return "Network error: \(error.localizedDescription)"
        case .registrationFailed:
            return "Failed to register with companion API"
        case .unknown:
            return "An unknown error occurred"
        }
    }
}

// MARK: - Registration Models

struct CompanionLoginRequest: Codable {
    let accessToken: String
    let dpopProof: String?

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case dpopProof = "dpop_proof"
    }
}

struct CompanionRegisterResponse: Codable {
    let apiKey: String
    let floatplaneUserId: String
    let message: String

    enum CodingKeys: String, CodingKey {
        case apiKey = "api_key"
        case floatplaneUserId = "floatplane_user_id"
        case message
    }
}

struct CompanionLogoutResponse: Codable {
    let message: String
}

// MARK: - Watch Later Models

struct WatchLaterAddRequest: Codable {
    let videoId: String

    enum CodingKeys: String, CodingKey {
        case videoId = "video_id"
    }
}

struct WatchLaterResponse: Codable {
    let id: String
    let videoIds: [String]
    let updatedAt: Date

    enum CodingKeys: String, CodingKey {
        case id
        case videoIds = "video_ids"
        case updatedAt = "updated_at"
    }
}

// MARK: - Playlist Models

struct Playlist: Codable, Identifiable, Hashable {
    let id: String
    let floatplaneUserId: String
    let name: String
    let isWatchLater: Bool
    let videoIds: [String]
    let createdAt: Date
    let updatedAt: Date

    enum CodingKeys: String, CodingKey {
        case id
        case floatplaneUserId = "floatplane_user_id"
        case name
        case isWatchLater = "is_watch_later"
        case videoIds = "video_ids"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
    }
}

struct PlaylistResponse: Codable {
    let playlists: [Playlist]
    let count: Int
}

struct PlaylistAddRequest: Codable {
    let videoId: String

    enum CodingKeys: String, CodingKey {
        case videoId = "video_id"
    }
}

struct PlaylistRemoveRequest: Codable {
    let videoId: String

    enum CodingKeys: String, CodingKey {
        case videoId = "video_id"
    }
}

struct PlaylistCreateRequest: Codable {
    let name: String
}

// MARK: - Empty Response

/// Empty response type for DELETE requests with no response body
struct Empty: Codable {}

/// Empty body type for requests that require an empty JSON object
struct EmptyBody: Codable {}

// MARK: - LTT Search Models

struct LTTSearchResponse: Codable {
    let query: String
    let count: Int
    let results: [LTTSearchResult]
}

struct LTTSearchResult: Codable {
    let id: String
    let title: String
    let creatorName: String
    let channelTitle: String
    let channelIconUrl: String?
    let thumbnailUrl: String?
    let videoDuration: Int
    let hasVideo: Bool
    let releaseDate: Date

    enum CodingKeys: String, CodingKey {
        case id
        case title
        case creatorName = "creator_name"
        case channelTitle = "channel_title"
        case channelIconUrl = "channel_icon_url"
        case thumbnailUrl = "thumbnail_url"
        case videoDuration = "video_duration"
        case hasVideo = "has_video"
        case releaseDate = "release_date"
    }
}

// MARK: - QR Code Authentication Models

struct QRCodeGenerateRequest: Codable {
    let deviceInfo: String?

    enum CodingKeys: String, CodingKey {
        case deviceInfo = "device_info"
    }
}

struct QRCodeGenerateResponse: Codable {
    let sessionId: String
    let loginUrl: String
    let expiresAt: Date
    let expiresInSeconds: Int

    enum CodingKeys: String, CodingKey {
        case sessionId = "session_id"
        case loginUrl = "login_url"
        case expiresAt = "expires_at"
        case expiresInSeconds = "expires_in_seconds"
    }
}

struct QRCodePollResponse: Codable {
    let status: QRCodeStatus
    let message: String
    let apiKey: String?
    let floatplaneUserId: String?
    let expiresInSeconds: Int?
    let sailsSid: String?  // Floatplane token for login

    enum CodingKeys: String, CodingKey {
        case status
        case message
        case apiKey = "api_key"
        case floatplaneUserId = "floatplane_user_id"
        case expiresInSeconds = "expires_in_seconds"
        case sailsSid = "sails_sid"
    }
}

enum QRCodeStatus: String, Codable {
    case pending
    case completed
    case expired
}

