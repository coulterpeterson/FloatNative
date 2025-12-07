//
//  CompanionAPI.swift
//  FloatNative
//
//  FloatNative Companion API Client for Watch Later and Playlists
//

import Foundation

// MARK: - Companion API Client

@MainActor
class CompanionAPI: ObservableObject {

    // MARK: - Singleton

    static let shared = CompanionAPI()

    // MARK: - Configuration

    private let baseURL = "https://api.floatnative.coulterpeterson.com"

    // MARK: - Session

    private var session: URLSession

    // MARK: - Initialization

    private init() {
        let config = URLSessionConfiguration.default
        self.session = URLSession(configuration: config)
    }

    // MARK: - API Key Management

    private var apiKey: String? {
        get {
            return KeychainManager.shared.getAPIKey()
        }
        set {
            if let key = newValue {
                _ = KeychainManager.shared.saveAPIKey(key)
            } else {
                KeychainManager.shared.clearAPIKey()
            }
        }
    }

    // MARK: - Network Request Helpers

    private func request<T: Decodable>(
        endpoint: String,
        method: String = "GET",
        body: Encodable? = nil,
        requiresAuth: Bool = false
    ) async throws -> T {
        guard let url = URL(string: baseURL + endpoint) else {
            throw CompanionAPIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("FloatNative/1.0 (iOS), CFNetwork", forHTTPHeaderField: "User-Agent")

        // Add auth header if required
        if requiresAuth {
            guard let apiKey = apiKey else {
                throw CompanionAPIError.notAuthenticated
            }
            request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        }

        // Encode body if present and set Content-Type header
        if let body = body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONEncoder().encode(body)
        }

        // Perform request
        do {
            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw CompanionAPIError.invalidResponse
            }

            // Handle HTTP errors
            guard (200...299).contains(httpResponse.statusCode) else {
                let errorMessage = String(data: data, encoding: .utf8) ?? nil
                throw CompanionAPIError.httpError(statusCode: httpResponse.statusCode, message: errorMessage)
            }

            // Decode response
            do {
                // Handle empty response for Empty type (204 No Content, DELETE requests)
                if data.isEmpty && T.self == Empty.self {
                    return Empty() as! T
                }

                let decoder = JSONDecoder()

                // Use flexible date decoding to handle various ISO8601 formats
                decoder.dateDecodingStrategy = .custom { decoder in
                    let container = try decoder.singleValueContainer()
                    let dateString = try container.decode(String.self)

                    // Try standard ISO8601 formatter first
                    let formatter = ISO8601DateFormatter()
                    if let date = formatter.date(from: dateString) {
                        return date
                    }

                    // Try ISO8601 with fractional seconds
                    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
                    if let date = formatter.date(from: dateString) {
                        return date
                    }

                    // If both fail, throw error
                    throw DecodingError.dataCorrupted(
                        DecodingError.Context(
                            codingPath: decoder.codingPath,
                            debugDescription: "Date string '\(dateString)' does not match expected ISO8601 format"
                        )
                    )
                }

                return try decoder.decode(T.self, from: data)
            } catch {
                throw CompanionAPIError.decodingError(error)
            }
        } catch let error as CompanionAPIError {
            throw error
        } catch {
            throw CompanionAPIError.networkError(error)
        }
    }

    // MARK: - Authentication

    /// Register with companion API using Floatplane session cookie
    func register(sailsSid: String) async throws -> String {
        let registerRequest = CompanionRegisterRequest(sailsSid: sailsSid)

        let response: CompanionRegisterResponse = try await request(
            endpoint: "/auth/register",
            method: "POST",
            body: registerRequest,
            requiresAuth: false
        )

        // Store API key
        apiKey = response.apiKey
        return response.apiKey
    }

    /// Auto-register using FloatplaneAPI's session cookie
    func autoRegister() async throws -> String {
        // Get Floatplane session cookie
        guard let sailsSid = UserDefaults.standard.string(forKey: "sails.sid") else {
            throw CompanionAPIError.notAuthenticated
        }

        return try await register(sailsSid: sailsSid)
    }

    // MARK: - Watch Later

    /// Add a video to Watch Later
    func addToWatchLater(videoId: String) async throws -> WatchLaterResponse {
        // Check if we have an API key, if not, try to register
        if apiKey == nil {
            do {
                _ = try await autoRegister()
            } catch {
                throw CompanionAPIError.registrationFailed
            }
        }

        let addRequest = WatchLaterAddRequest(videoId: videoId)

        do {
            return try await request(
                endpoint: "/watch-later/add",
                method: "PATCH",
                body: addRequest,
                requiresAuth: true
            )
        } catch CompanionAPIError.httpError(let statusCode, _) where statusCode == 401 {
            // If we get 401, try to re-register and retry once
            do {
                _ = try await autoRegister()
                return try await request(
                    endpoint: "/watch-later/add",
                    method: "PATCH",
                    body: addRequest,
                    requiresAuth: true
                )
            } catch {
                throw CompanionAPIError.registrationFailed
            }
        }
    }

    // MARK: - Playlists

    /// Get all playlists for the authenticated user
    func getPlaylists(includeWatchLater: Bool = false) async throws -> [Playlist] {
        // Check if we have an API key, if not, try to register
        if apiKey == nil {
            do {
                _ = try await autoRegister()
            } catch {
                throw CompanionAPIError.registrationFailed
            }
        }

        let includeParam = includeWatchLater ? "true" : "false"
        let response: PlaylistResponse = try await request(
            endpoint: "/playlists?include_watch_later=\(includeParam)",
            method: "GET",
            requiresAuth: true
        )

        return response.playlists
    }

    /// Create a new playlist
    func createPlaylist(name: String) async throws -> Playlist {
        // Check if we have an API key, if not, try to register
        if apiKey == nil {
            do {
                _ = try await autoRegister()
            } catch {
                throw CompanionAPIError.registrationFailed
            }
        }

        let createRequest = PlaylistCreateRequest(name: name)

        do {
            return try await request(
                endpoint: "/playlists",
                method: "POST",
                body: createRequest,
                requiresAuth: true
            )
        } catch CompanionAPIError.httpError(let statusCode, _) where statusCode == 401 {
            // If we get 401, try to re-register and retry once
            do {
                _ = try await autoRegister()
                return try await request(
                    endpoint: "/playlists",
                    method: "POST",
                    body: createRequest,
                    requiresAuth: true
                )
            } catch {
                throw CompanionAPIError.registrationFailed
            }
        }
    }

    /// Delete a playlist
    func deletePlaylist(playlistId: String) async throws {
        // Check if we have an API key, if not, try to register
        if apiKey == nil {
            do {
                _ = try await autoRegister()
            } catch {
                throw CompanionAPIError.registrationFailed
            }
        }

        do {
            let _: Empty = try await request(
                endpoint: "/playlists/\(playlistId)",
                method: "DELETE",
                requiresAuth: true
            )
        } catch CompanionAPIError.httpError(let statusCode, _) where statusCode == 401 {
            // If we get 401, try to re-register and retry once
            do {
                _ = try await autoRegister()
                let _: Empty = try await request(
                    endpoint: "/playlists/\(playlistId)",
                    method: "DELETE",
                    requiresAuth: true
                )
            } catch {
                throw CompanionAPIError.registrationFailed
            }
        }
    }

    /// Add a video to a playlist
    func addToPlaylist(playlistId: String, videoId: String) async throws -> Playlist {
        // Check if we have an API key, if not, try to register
        if apiKey == nil {
            do {
                _ = try await autoRegister()
            } catch {
                throw CompanionAPIError.registrationFailed
            }
        }

        let addRequest = PlaylistAddRequest(videoId: videoId)

        do {
            return try await request(
                endpoint: "/playlists/\(playlistId)/add",
                method: "PATCH",
                body: addRequest,
                requiresAuth: true
            )
        } catch CompanionAPIError.httpError(let statusCode, _) where statusCode == 401 {
            // If we get 401, try to re-register and retry once
            do {
                _ = try await autoRegister()
                return try await request(
                    endpoint: "/playlists/\(playlistId)/add",
                    method: "PATCH",
                    body: addRequest,
                    requiresAuth: true
                )
            } catch {
                throw CompanionAPIError.registrationFailed
            }
        }
    }

    /// Remove a video from a playlist
    func removeFromPlaylist(playlistId: String, videoId: String) async throws -> Playlist {
        // Check if we have an API key, if not, try to register
        if apiKey == nil {
            do {
                _ = try await autoRegister()
            } catch {
                throw CompanionAPIError.registrationFailed
            }
        }

        let removeRequest = PlaylistRemoveRequest(videoId: videoId)

        do {
            return try await request(
                endpoint: "/playlists/\(playlistId)/remove",
                method: "PATCH",
                body: removeRequest,
                requiresAuth: true
            )
        } catch CompanionAPIError.httpError(let statusCode, _) where statusCode == 401 {
            // If we get 401, try to re-register and retry once
            do {
                _ = try await autoRegister()
                return try await request(
                    endpoint: "/playlists/\(playlistId)/remove",
                    method: "PATCH",
                    body: removeRequest,
                    requiresAuth: true
                )
            } catch {
                throw CompanionAPIError.registrationFailed
            }
        }
    }

    // MARK: - LTT Search

    /// Search LTT videos using the enhanced companion API search endpoint
    /// Returns full BlogPost objects by fetching details for each search result
    func searchLTT(query: String) async throws -> [BlogPost] {
        let LTT_CREATOR_ID = "59f94c0bdd241b70349eb72b"

        print("🔍 [Enhanced LTT Search] Starting search")
        print("🔍   Query: \"\(query)\"")

        // Check if we have an API key, if not, try to register
        if apiKey == nil {
            do {
                _ = try await autoRegister()
            } catch {
                throw CompanionAPIError.registrationFailed
            }
        }

        // URL encode the query
        guard let encodedQuery = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            print("🔍   ❌ Failed to URL encode query")
            throw CompanionAPIError.invalidURL
        }

        let endpoint = "/ltt/search?q=\(encodedQuery)"
        print("🔍   Endpoint: GET \(endpoint)")
        print("🔍   Base URL: \(baseURL)")

        let searchResponse: LTTSearchResponse
        do {
            searchResponse = try await request(
                endpoint: endpoint,
                method: "GET",
                requiresAuth: true
            )
        } catch CompanionAPIError.httpError(let statusCode, _) where statusCode == 401 {
            // If we get 401, try to re-register and retry once
            print("🔍   ⚠️ Got 401, re-registering and retrying...")
            do {
                _ = try await autoRegister()
                searchResponse = try await request(
                    endpoint: endpoint,
                    method: "GET",
                    requiresAuth: true
                )
            } catch {
                print("🔍   ❌ Re-registration failed")
                throw CompanionAPIError.registrationFailed
            }
        }

        // Log the API response
        print("🔍 [Enhanced LTT Search] Companion API Response:")
        print("🔍   Query returned: \"\(searchResponse.query)\"")
        print("🔍   Result count: \(searchResponse.count)")
        print("🔍   Results:")
        for (index, result) in searchResponse.results.enumerated() {
            print("🔍     [\(index + 1)] \(result.id)")
            print("🔍         Title: \"\(result.title)\"")
            print("🔍         Creator: \(result.creatorName)")
            print("🔍         Channel: \(result.channelTitle)")
            print("🔍         Has Video: \(result.hasVideo)")
            print("🔍         Duration: \(result.videoDuration)s")
            print("🔍         Release Date: \(result.releaseDate)")
            if let thumbnailUrl = result.thumbnailUrl {
                print("🔍         Thumbnail: \(thumbnailUrl)")
            }
        }

        // If no results, return empty array
        if searchResponse.results.isEmpty {
            print("🔍   No results found, returning empty array")
            return []
        }

        // Convert LTTSearchResult objects directly to BlogPost objects
        print("🔍 [Enhanced LTT Search] Converting search results to BlogPost objects:")

        let blogPosts: [BlogPost] = searchResponse.results.compactMap { result in
            return convertSearchResultToBlogPost(result, creatorId: LTT_CREATOR_ID)
        }

        print("🔍   Successfully converted \(blogPosts.count) search results to BlogPost objects")
        print("🔍   Returning \(blogPosts.count) posts")

        return blogPosts
    }

    // MARK: - QR Code Authentication

    /// Generate a QR code session for device login
    func generateQRCode(deviceInfo: String? = nil) async throws -> QRCodeGenerateResponse {
        let request = QRCodeGenerateRequest(deviceInfo: deviceInfo)

        let response: QRCodeGenerateResponse = try await self.request(
            endpoint: "/auth/qr/generate",
            method: "POST",
            body: request,
            requiresAuth: false
        )

        return response
    }

    /// Poll for QR code session completion
    func pollQRCode(sessionId: String) async throws -> QRCodePollResponse {
        let response: QRCodePollResponse = try await self.request(
            endpoint: "/auth/qr/poll/\(sessionId)",
            method: "GET",
            requiresAuth: false
        )

        return response
    }

    // MARK: - Helper Methods

    /// Convert LTTSearchResult to BlogPost for display
    private func convertSearchResultToBlogPost(_ result: LTTSearchResult, creatorId: String) -> BlogPost {
        // Create thumbnail from URL if available
        let thumbnail: ImageModel? = {
            guard let thumbnailUrl = result.thumbnailUrl else { return nil }
            return ImageModel(
                width: 1920,
                height: 1080,
                path: thumbnailUrl,
                childImages: nil
            )
        }()

        // Create metadata with video info
        let metadata = PostMetadataModel(
            hasVideo: result.hasVideo,
            videoCount: result.hasVideo ? 1 : nil,
            videoDuration: Double(result.videoDuration),
            hasAudio: false,
            audioCount: nil,
            audioDuration: 0,
            hasPicture: false,
            pictureCount: nil,
            hasGallery: nil,
            galleryCount: nil,
            isFeatured: false
        )

        // Create minimal creator icon (placeholder)
        let creatorIcon = ImageModel(
            width: 512,
            height: 512,
            path: "/creator/icon/placeholder",
            childImages: nil
        )

        // Create creator owner
        let creatorOwner = BlogPostModelV3CreatorOwner(
            id: creatorId,
            username: result.creatorName
        )

        // Create creator category
        let creatorCategory = CreatorModelV3Category(
            id: "technology",
            title: "Technology"
        )

        // Create creator
        let creator = BlogPostModelV3Creator(
            id: creatorId,
            owner: creatorOwner,
            title: result.creatorName,
            urlname: result.creatorName.lowercased().replacingOccurrences(of: " ", with: ""),
            description: result.creatorName,
            about: "",
            category: creatorCategory,
            cover: nil,
            icon: creatorIcon,
            liveStream: nil,
            subscriptionPlans: [],
            discoverable: true,
            subscriberCountDisplay: "",
            incomeDisplay: false,
            defaultChannel: nil,
            channels: nil,
            card: nil
        )

        // Create channel with icon if available
        let channel: BlogPostModelV3Channel = {
            // If we have a channel icon URL, create a full ChannelModel
            if let channelIconUrl = result.channelIconUrl {
                let channelIcon = ImageModel(
                    width: 512,
                    height: 512,
                    path: channelIconUrl,
                    childImages: nil
                )

                let channelModel = ChannelModel(
                    id: result.channelTitle.lowercased().replacingOccurrences(of: " ", with: ""),
                    creator: creatorId,
                    title: result.channelTitle,
                    urlname: result.channelTitle.lowercased().replacingOccurrences(of: " ", with: ""),
                    about: "",
                    order: nil,
                    cover: nil,
                    card: nil,
                    icon: channelIcon,
                    socialLinks: nil
                )

                return BlogPostModelV3Channel.typeChannelModel(channelModel)
            } else {
                // Fallback to string type if no icon URL available
                return BlogPostModelV3Channel.typeString(result.channelTitle)
            }
        }()

        // Create BlogPost
        return BlogPost(
            id: result.id,
            guid: result.id,
            title: result.title,
            text: "",
            type: .blogpost,
            channel: channel,
            tags: [],
            attachmentOrder: result.hasVideo ? [result.id] : [],
            metadata: metadata,
            releaseDate: result.releaseDate,
            likes: 0,
            dislikes: 0,
            score: 0,
            comments: 0,
            creator: creator,
            wasReleasedSilently: false,
            thumbnail: thumbnail,
            isAccessible: true,
            videoAttachments: result.hasVideo ? [result.id] : nil,
            audioAttachments: nil,
            pictureAttachments: nil,
            galleryAttachments: nil
        )
    }
}

