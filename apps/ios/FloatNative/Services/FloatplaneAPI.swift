//
//  FloatplaneAPI.swift
//  FloatNative
//
//  Complete Floatplane API Client
//  Created by Claude on 2025-10-08.
//

import Foundation

// MARK: - API Error

enum FloatplaneAPIError: LocalizedError {
    case invalidURL
    case invalidResponse
    case httpError(statusCode: Int, message: String?)
    case decodingError(Error)
    case notAuthenticated
    case networkError(Error)
    case malformedData(message: String?)
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
            return "Not authenticated. Please log in."
        case .networkError(let error):
            return "Network error: \(error.localizedDescription)"
        case .malformedData(let message):
            return "Received unexpected data: \(message ?? "Malformed response")"
        case .unknown:
            return "An unknown error occurred"
        }
    }
}

// MARK: - Error Response Model

struct FloatplaneErrorResponse: Codable {
    let errors: [ErrorDetail]?
    let message: String?

    struct ErrorDetail: Codable {
        let id: String?
        let message: String
        let data: [String: String]?
    }
}

// MARK: - Floatplane API Client

@MainActor
class FloatplaneAPI: ObservableObject {

    // MARK: - Singleton

    static let shared = FloatplaneAPI()

    // MARK: - Configuration

    private let baseURL = "https://www.floatplane.com"
    private let imageBaseURL = "https://pbs.floatplane.com"

    // MARK: - Session

    private var session: URLSession
    private let cookieStorage: HTTPCookieStorage

    // MARK: - Authentication State

    @Published private(set) var isAuthenticated = false
    @Published private(set) var currentUser: User?
    @Published private(set) var currentUserDetails: UserSelfV3Response?
    @Published var autoReloginEnabled = true // User preference for auto re-login

    private var authCookie: String? {
        didSet {
            isAuthenticated = authCookie != nil
        }
    }

    private var isRelogging = false // Prevent multiple simultaneous re-login attempts

    // MARK: - Initialization

    private init() {
        let config = URLSessionConfiguration.default
        config.httpCookieAcceptPolicy = .always
        config.httpShouldSetCookies = true
        self.cookieStorage = HTTPCookieStorage.shared
        self.session = URLSession(configuration: config)

        // Load saved settings
        loadAuthCookie()
        loadAutoReloginPreference()
    }

    // MARK: - Cookie Management

    private func loadAuthCookie() {
        // Try Keychain first (more secure), then fall back to UserDefaults
        let savedCookie = KeychainManager.shared.getAuthToken() ?? UserDefaults.standard.string(forKey: "sails.sid")

        if let savedCookie = savedCookie {
            self.authCookie = savedCookie
            // Restore cookie to storage
            if URL(string: baseURL) != nil {
                let cookie = HTTPCookie(properties: [
                    .domain: ".floatplane.com",
                    .path: "/",
                    .name: "sails.sid",
                    .value: savedCookie,
                    .secure: "TRUE",
                ])
                if let cookie = cookie {
                    cookieStorage.setCookie(cookie)
                }
            }
        }
    }

    private func saveAuthCookie(_ cookie: String) {
        self.authCookie = cookie
        // Save to both Keychain (secure) and UserDefaults (backward compatibility)
        _ = KeychainManager.shared.saveAuthToken(cookie)
        UserDefaults.standard.set(cookie, forKey: "sails.sid")

        // Restore cookie to storage (same as loadAuthCookie)
        if URL(string: baseURL) != nil {
            let httpCookie = HTTPCookie(properties: [
                .domain: ".floatplane.com",
                .path: "/",
                .name: "sails.sid",
                .value: cookie,
                .secure: "TRUE",
            ])
            if let httpCookie = httpCookie {
                cookieStorage.setCookie(httpCookie)
            }
        }
    }

    private func clearAuthCookie() {
        self.authCookie = nil
        // Clear from both Keychain and UserDefaults
        KeychainManager.shared.clearAuthToken()
        UserDefaults.standard.removeObject(forKey: "sails.sid")
        currentUser = nil
        currentUserDetails = nil

        // Clear cookies from storage
        if let cookies = cookieStorage.cookies {
            for cookie in cookies {
                cookieStorage.deleteCookie(cookie)
            }
        }
    }

    // MARK: - Auto Re-login Management

    private func loadAutoReloginPreference() {
        if UserDefaults.standard.object(forKey: "autoReloginEnabled") != nil {
            autoReloginEnabled = UserDefaults.standard.bool(forKey: "autoReloginEnabled")
        } else {
            autoReloginEnabled = true // Default to enabled
        }
    }

    func setAutoReloginEnabled(_ enabled: Bool) {
        autoReloginEnabled = enabled
        UserDefaults.standard.set(enabled, forKey: "autoReloginEnabled")
    }

    /// Attempt automatic re-login using stored credentials
    private func attemptAutoRelogin() async throws {
        // Prevent multiple simultaneous re-login attempts
        guard !isRelogging else { return }

        // Check if auto re-login is enabled
        guard autoReloginEnabled else {
            throw FloatplaneAPIError.notAuthenticated
        }

        // Get stored credentials
        guard let credentials = KeychainManager.shared.getCredentials() else {
            throw FloatplaneAPIError.notAuthenticated
        }

        isRelogging = true
        defer { isRelogging = false }

        // Attempt login with stored credentials
        do {
            let response = try await login(username: credentials.username, password: credentials.password)

            // Handle 2FA if needed
            if response.needs2FA {
                // Can't automatically handle 2FA, fail and require manual login
                throw FloatplaneAPIError.notAuthenticated
            }

            print("✅ Auto re-login successful")
        } catch {
            print("❌ Auto re-login failed: \(error.localizedDescription)")
            throw FloatplaneAPIError.notAuthenticated
        }
    }

    private func extractCookie(from response: HTTPURLResponse) {
        if let headerFields = response.allHeaderFields as? [String: String],
           let url = response.url {
            let cookies = HTTPCookie.cookies(withResponseHeaderFields: headerFields, for: url)
            for cookie in cookies where cookie.name == "sails.sid" {
                saveAuthCookie(cookie.value)
            }
        }
    }

    // MARK: - Network Request Helpers

    /// Request method for endpoints that don't return JSON (plain text responses)
    private func requestWithoutResponse(
        endpoint: String,
        method: String = "GET",
        body: Encodable? = nil,
        requiresAuth: Bool = false
    ) async throws {
        guard let url = URL(string: baseURL + endpoint) else {
            throw FloatplaneAPIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("FloatNative/1.0 (iOS), CFNetwork", forHTTPHeaderField: "User-Agent")

        // Add auth cookie if required
        if requiresAuth {
            guard let authCookie = authCookie else {
                throw FloatplaneAPIError.notAuthenticated
            }
            request.setValue("sails.sid=\(authCookie)", forHTTPHeaderField: "Cookie")
            #if DEBUG

            #endif
        }

        // Encode body if present
        if let body = body {
            request.httpBody = try JSONEncoder().encode(body)
        }

        // Perform request
        let (_, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw FloatplaneAPIError.invalidResponse
        }

        // Extract cookies from response
        extractCookie(from: httpResponse)

        // Handle HTTP errors
        guard (200...299).contains(httpResponse.statusCode) else {
            // Check for authentication errors (401 Unauthorized, 403 Forbidden)
            if httpResponse.statusCode == 401 || httpResponse.statusCode == 403 {
                // Try auto re-login if enabled and credentials are stored
                if autoReloginEnabled && KeychainManager.shared.hasStoredCredentials() && !isRelogging {
                    do {
                        try await attemptAutoRelogin()
                        // Retry the original request after successful re-login
                        // Note: This will recursively call this method, but with valid auth now
                        return try await requestWithoutResponse(
                            endpoint: endpoint,
                            method: method,
                            body: body,
                            requiresAuth: requiresAuth
                        )
                    } catch {
                        // Auto re-login failed, clear auth state
                        clearAuthCookie()
                    }
                } else {
                    // Auto re-login not available, clear auth state
                    clearAuthCookie()
                }
            }

            throw FloatplaneAPIError.httpError(statusCode: httpResponse.statusCode, message: nil)
        }
    }

    private func request<T: Decodable>(
        endpoint: String,
        method: String = "GET",
        body: Encodable? = nil,
        requiresAuth: Bool = false
    ) async throws -> T {
        guard let url = URL(string: baseURL + endpoint) else {
            throw FloatplaneAPIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("FloatNative/1.0 (iOS), CFNetwork", forHTTPHeaderField: "User-Agent")

        // Add auth cookie if required
        if requiresAuth {
            guard let authCookie = authCookie else {
                throw FloatplaneAPIError.notAuthenticated
            }
            request.setValue("sails.sid=\(authCookie)", forHTTPHeaderField: "Cookie")
            #if DEBUG

            #endif
        }

        // Encode body if present
        if let body = body {
            request.httpBody = try JSONEncoder().encode(body)
        }

        // Perform request
        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw FloatplaneAPIError.invalidResponse
        }

        // Extract cookies from response
        extractCookie(from: httpResponse)

        // Handle HTTP errors
        guard (200...299).contains(httpResponse.statusCode) else {
            // Check for authentication errors (401 Unauthorized, 403 Forbidden)
            if httpResponse.statusCode == 401 || httpResponse.statusCode == 403 {

                // Try auto re-login if enabled and credentials are stored
                if autoReloginEnabled && KeychainManager.shared.hasStoredCredentials() && !isRelogging {
                    do {
                        try await attemptAutoRelogin()
                        // Retry the original request after successful re-login
                        // Note: This will recursively call this method, but with valid auth now
                        return try await self.request(
                            endpoint: endpoint,
                            method: method,
                            body: body,
                            requiresAuth: requiresAuth
                        )
                    } catch {
                        // Auto re-login failed, clear auth state
                        clearAuthCookie()
                    }
                } else {
                    // Auto re-login not available, clear auth state
                    clearAuthCookie()
                }
            }

            let errorMessage = try? JSONDecoder().decode(FloatplaneErrorResponse.self, from: data)
            let message = errorMessage?.message ?? errorMessage?.errors?.first?.message
            throw FloatplaneAPIError.httpError(statusCode: httpResponse.statusCode, message: message)
        }

        // Decode response
        do {
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
            // Check if this is a malformed response (e.g., rate limit, unexpected structure)
            // Try to decode as a generic error response first
            if let errorResponse = try? JSONDecoder().decode(FloatplaneErrorResponse.self, from: data) {
                let message = errorResponse.message ?? errorResponse.errors?.first?.message
                #if DEBUG
                print("❌ API returned error structure for \(endpoint): \(message ?? "Unknown")")
                #endif
                throw FloatplaneAPIError.malformedData(message: message)
            }

            // Check if response looks like a rate limit or other unexpected JSON structure
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               (json.keys.contains("rateLimit") || json.keys.contains("error") || json.keys.contains("status")) {
                let message = (json["message"] as? String) ?? (json["error"] as? String) ?? "Unexpected response structure"
                #if DEBUG
                print("❌ API returned unexpected structure for \(endpoint): \(json)")
                #endif
                throw FloatplaneAPIError.malformedData(message: message)
            }

            // Log decoding errors for debugging
            #if DEBUG
            print("❌ Decoding Error for \(endpoint): \(error)")
            if let dataString = String(data: data, encoding: .utf8) {
                print("Response data: \(dataString)")
            }
            #endif
            throw FloatplaneAPIError.decodingError(error)
        }
    }

    // MARK: - Debug Helper

    private func prettyPrintJSON(_ data: Data) -> String? {
        guard let jsonObject = try? JSONSerialization.jsonObject(with: data),
              let prettyData = try? JSONSerialization.data(withJSONObject: jsonObject, options: [.prettyPrinted, .sortedKeys]),
              let prettyString = String(data: prettyData, encoding: .utf8) else {
            return nil
        }
        return prettyString
    }

    // MARK: - AUTHENTICATION

    /// Login with username and password
    func login(username: String, password: String, captchaToken: String? = nil) async throws -> LoginResponse {
        let loginRequest = LoginRequest(
            username: username,
            password: password,
            captchaToken: captchaToken
        )

        let response: LoginResponse = try await request(
            endpoint: "/api/v2/auth/login",
            method: "POST",
            body: loginRequest
        )

        if !response.needs2FA {
            currentUser = response.user
        }

        return response
    }

    /// Login with auth token (sails.sid cookie)
    func loginWithToken(_ token: String) async throws {
        // Save token to Keychain and UserDefaults
        _ = KeychainManager.shared.saveAuthToken(token)
        saveAuthCookie(token)

        // Validate token by fetching user info
        do {
            _ = try await getCurrentUser()
            print("✅ Token login successful")
        } catch {
            // Token is invalid, clear it
            clearAuthCookie()
            KeychainManager.shared.clearAuthToken()
            throw FloatplaneAPIError.notAuthenticated
        }
    }

    /// Complete 2FA login
    func verify2FA(token: String) async throws -> LoginResponse {
        let twoFactorRequest = TwoFactorRequest(token: token)

        let response: LoginResponse = try await request(
            endpoint: "/api/v2/auth/checkFor2faLogin",
            method: "POST",
            body: twoFactorRequest,
            requiresAuth: true
        )

        currentUser = response.user
        return response
    }

    /// Logout
    func logout() async throws {
        // Try to call logout endpoint, but don't fail if it errors
        do {
            try await requestWithoutResponse(
                endpoint: "/api/v2/auth/logout",
                method: "POST",
                requiresAuth: true
            )
            print("✅ Logout API call successful")
        } catch {
            // Ignore errors - endpoint may be deprecated (404) or network issues
            // We'll clear local state anyway
            print("⚠️ Logout API call failed (likely endpoint deprecated): \(error.localizedDescription)")
        }

        // Always clear local state, regardless of API call success
        clearAuthCookie()
        KeychainManager.shared.clearCredentials()
    }

    /// Get captcha info
    func getCaptchaInfo() async throws -> CaptchaInfo {
        try await request(endpoint: "/api/v3/auth/captcha/info")
    }

    // MARK: - USER

    /// Get current user info
    func getCurrentUser() async throws -> User {
        let userDetails: UserSelfV3Response = try await request(
            endpoint: "/api/v3/user/self",
            requiresAuth: true
        )
        currentUserDetails = userDetails
        // Also set currentUser with basic info for compatibility
        currentUser = UserModel(
            id: userDetails.id,
            username: userDetails.username,
            profileImage: userDetails.profileImage
        )
        return currentUser!
    }

    /// Get user activity feed
    func getUserActivity(userId: String) async throws -> UserActivity {
        try await request(
            endpoint: "/api/v3/user/activity?id=\(userId)",
            requiresAuth: true
        )
    }

    /// Get user external links
    func getUserLinks(userId: String) async throws -> UserLinks {
        try await request(
            endpoint: "/api/v3/user/links?id=\(userId)",
            requiresAuth: true
        )
    }

    /// Get user notification settings
    func getNotificationSettings() async throws -> [NotificationSetting] {
        try await request(
            endpoint: "/api/v3/user/notification/list",
            requiresAuth: true
        )
    }

    /// Update user notification setting
    func updateNotificationSetting(
        creator: String,
        property: String,
        newValue: Bool
    ) async throws -> Bool {
        let updateRequest = NotificationUpdateRequest(
            creator: creator,
            property: property,
            newValue: newValue
        )

        return try await request(
            endpoint: "/api/v3/user/notification/update",
            method: "POST",
            body: updateRequest,
            requiresAuth: true
        )
    }

    // MARK: - SUBSCRIPTIONS

    /// Get user's subscriptions
    func getSubscriptions() async throws -> [Subscription] {
        try await request(
            endpoint: "/api/v3/user/subscriptions",
            requiresAuth: true
        )
    }

    // MARK: - CREATORS

    /// Get creator info by ID
    func getCreator(id: String) async throws -> Creator {
        try await request(
            endpoint: "/api/v3/creator/info?id=\(id)",
            requiresAuth: true
        )
    }

    /// Get creator by URL name
    func getCreator(urlName: String) async throws -> [Creator] {
        let encodedName = urlName.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? urlName
        return try await request(
            endpoint: "/api/v3/creator/named?creatorURL[]=\(encodedName)",
            requiresAuth: true
        )
    }

    /// Search/list all creators
    func searchCreators(query: String = "") async throws -> [Creator] {
        let encodedQuery = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        return try await request(
            endpoint: "/api/v3/creator/list?search=\(encodedQuery)",
            requiresAuth: true
        )
    }

    /// Get creator channels
    func getCreatorChannels(creatorIds: [String]) async throws -> [Channel] {
        let idsParam = creatorIds.enumerated()
            .map { "ids[\($0.offset)]=\($0.element)" }
            .joined(separator: "&")

        return try await request(
            endpoint: "/api/v3/creator/channels/list?\(idsParam)",
            requiresAuth: true
        )
    }

    // MARK: - CONTENT

    /// Get blog posts from a single creator
    func getCreatorContent(
        creatorId: String,
        channelId: String? = nil,
        limit: Int = 20,
        fetchAfter: Int = 0,
        search: String? = nil,
        tags: [String]? = nil,
        hasVideo: Bool? = nil,
        hasAudio: Bool? = nil,
        hasPicture: Bool? = nil,
        hasText: Bool? = nil,
        sort: String = "DESC",
        fromDuration: Int? = nil,
        toDuration: Int? = nil,
        fromDate: Date? = nil,
        toDate: Date? = nil
    ) async throws -> [BlogPost] {
        var params = [
            "id=\(creatorId)",
            "limit=\(limit)",
            "fetchAfter=\(fetchAfter)",
            "sort=\(sort)"
        ]

        if let channelId = channelId {
            params.append("channel=\(channelId)")
        }

        if let search = search, !search.isEmpty {
            let encoded = search.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? search
            params.append("search=\(encoded)")
        }

        if let tags = tags, !tags.isEmpty {
            let tagsParam = tags.enumerated()
                .map { "tags[\($0.offset)]=\($0.element)" }
                .joined(separator: "&")
            params.append(tagsParam)
        }

        if let hasVideo = hasVideo {
            params.append("hasVideo=\(hasVideo)")
        }

        if let hasAudio = hasAudio {
            params.append("hasAudio=\(hasAudio)")
        }

        if let hasPicture = hasPicture {
            params.append("hasPicture=\(hasPicture)")
        }

        if let hasText = hasText {
            params.append("hasText=\(hasText)")
        }

        if let fromDuration = fromDuration {
            params.append("fromDuration=\(fromDuration)")
        }

        if let toDuration = toDuration {
            params.append("toDuration=\(toDuration)")
        }

        let endpoint = "/api/v3/content/creator?\(params.joined(separator: "&"))"
        return try await request(endpoint: endpoint, requiresAuth: true)
    }

    /// Get multi-creator home feed
    func getMultiCreatorFeed(
        creatorIds: [String],
        limit: Int = 20,
        fetchAfter: [FetchCursor]? = nil
    ) async throws -> CreatorListResponse {
        var params = ["limit=\(limit)"]

        let idsParam = creatorIds.enumerated()
            .map { "ids[\($0.offset)]=\($0.element)" }
            .joined(separator: "&")
        params.append(idsParam)

        if let fetchAfter = fetchAfter, !fetchAfter.isEmpty {
            let cursorParams = fetchAfter.enumerated().flatMap { index, cursor -> [String] in
                var cursorParts = [
                    "fetchAfter[\(index)][creatorId]=\(cursor.creatorId)",
                    "fetchAfter[\(index)][moreFetchable]=\(cursor.moreFetchable)"
                ]
                // Only include blogPostId if it's not nil
                if let blogPostId = cursor.blogPostId {
                    cursorParts.insert("fetchAfter[\(index)][blogPostId]=\(blogPostId)", at: 1)
                }
                return cursorParts
            }.joined(separator: "&")
            params.append(cursorParams)
        }

        let endpoint = "/api/v3/content/creator/list?\(params.joined(separator: "&"))"
        return try await request(endpoint: endpoint, requiresAuth: true)
    }

    /// Get single blog post
    func getBlogPost(id: String) async throws -> BlogPostDetailedWithInteraction {
        try await request(
            endpoint: "/api/v3/content/post?id=\(id)",
            requiresAuth: true
        )
    }

    /// Get related blog posts
    func getRelatedPosts(postId: String) async throws -> [BlogPost] {
        try await request(
            endpoint: "/api/v3/content/related?id=\(postId)",
            requiresAuth: true
        )
    }

    /// Get content tags for creators
    func getContentTags(creatorIds: [String]) async throws -> ContentTags {
        let idsParam = creatorIds.enumerated()
            .map { "creatorIds[\($0.offset)]=\($0.element)" }
            .joined(separator: "&")

        return try await request(
            endpoint: "/api/v3/content/tags?\(idsParam)",
            requiresAuth: true
        )
    }

    // MARK: - VIDEO & MEDIA

    /// Get video content details
    func getVideoContent(id: String) async throws -> VideoContent {
        try await request(
            endpoint: "/api/v3/content/video?id=\(id)",
            requiresAuth: true
        )
    }

    /// Get picture content
    func getPictureContent(id: String) async throws -> PictureContent {
        try await request(
            endpoint: "/api/v3/content/picture?id=\(id)",
            requiresAuth: true
        )
    }

    /// Get video delivery info (streaming URLs)
    func getDeliveryInfo(
        scenario: DeliveryScenario,
        entityId: String,
        outputKind: OutputKind = .hlsFmp4
    ) async throws -> DeliveryInfo {
        let endpoint = "/api/v3/delivery/info?scenario=\(scenario.rawValue)&entityId=\(entityId)&outputKind=\(outputKind.rawValue)"
        return try await request(endpoint: endpoint, requiresAuth: true)
    }

    /// Convenience method to get streaming URL for a video
    func getStreamingURL(videoId: String, preferredQuality: String? = nil) async throws -> String {
        let deliveryInfo = try await getDeliveryInfo(
            scenario: .onDemand,
            entityId: videoId,
            outputKind: .hlsFmp4
        )

        let variants = deliveryInfo.availableVariants()

        guard !variants.isEmpty else {
            throw FloatplaneAPIError.invalidResponse
        }

        // If preferred quality specified, find it
        if let preferredQuality = preferredQuality {
            if let preferred = variants.first(where: { $0.name.contains(preferredQuality) }) {
                return preferred.url
            }
        }

        // Default to highest quality
        return variants.first?.url ?? ""
    }

    /// Get live stream URL
    func getLiveStreamURL(liveStreamId: String) async throws -> String {
        let deliveryInfo = try await getDeliveryInfo(
            scenario: .live,
            entityId: liveStreamId,
            outputKind: .hlsFmp4
        )

        let variants = deliveryInfo.availableVariants()
        guard let url = variants.first?.url else {
            throw FloatplaneAPIError.invalidResponse
        }

        return url
    }

    // MARK: - ENGAGEMENT

    /// Like content
    func likeContent(contentType: String = "blogPost", id: String) async throws -> [String] {
        let request = ContentInteractionRequest(contentType: contentType, id: id)
        return try await self.request(
            endpoint: "/api/v3/content/like",
            method: "POST",
            body: request,
            requiresAuth: true
        )
    }

    /// Dislike content
    func dislikeContent(contentType: String = "blogPost", id: String) async throws -> [String] {
        let request = ContentInteractionRequest(contentType: contentType, id: id)
        return try await self.request(
            endpoint: "/api/v3/content/dislike",
            method: "POST",
            body: request,
            requiresAuth: true
        )
    }

    // MARK: - COMMENTS

    /// Get comments for a blog post
    func getComments(
        blogPostId: String,
        limit: Int = 20,
        fetchAfter: String? = nil
    ) async throws -> [Comment] {
        var params = [
            "blogPost=\(blogPostId)",
            "limit=\(limit)"
        ]

        if let fetchAfter = fetchAfter {
            params.append("fetchAfter=\(fetchAfter)")
        }

        let endpoint = "/api/v3/comment?\(params.joined(separator: "&"))"
        return try await request(endpoint: endpoint, requiresAuth: true)
    }

    /// Get comment replies
    func getCommentReplies(
        commentId: String,
        blogPostId: String,
        limit: Int = 20,
        lastReplyId: String
    ) async throws -> [Comment] {
        let params = [
            "comment=\(commentId)",
            "blogPost=\(blogPostId)",
            "limit=\(limit)",
            "rid=\(lastReplyId)"
        ].joined(separator: "&")

        return try await request(
            endpoint: "/api/v3/comment/replies?\(params)",
            requiresAuth: true
        )
    }

    /// Post a comment
    func postComment(blogPostId: String, text: String) async throws -> Comment {
        let commentRequest = PostCommentRequest(blogPost: blogPostId, text: text)
        return try await request(
            endpoint: "/api/v3/comment",
            method: "POST",
            body: commentRequest,
            requiresAuth: true
        )
    }

    /// Like a comment (returns interaction ID when liking, null when unliking)
    func likeComment(commentId: String, blogPostId: String) async throws -> String? {
        let likeRequest = CommentInteractionRequest(comment: commentId, blogPost: blogPostId)
        return try await request(
            endpoint: "/api/v3/comment/like",
            method: "POST",
            body: likeRequest,
            requiresAuth: true
        )
    }

    /// Dislike a comment (returns interaction ID when disliking, null when undisliking)
    func dislikeComment(commentId: String, blogPostId: String) async throws -> String? {
        let dislikeRequest = CommentInteractionRequest(comment: commentId, blogPost: blogPostId)
        return try await request(
            endpoint: "/api/v3/comment/dislike",
            method: "POST",
            body: dislikeRequest,
            requiresAuth: true
        )
    }

    // MARK: - PROGRESS TRACKING

    /// Get progress for multiple posts
    func getProgress(postIds: [String], contentType: String = "blogPost") async throws -> [ProgressResponse] {
        let idsParam = postIds.enumerated()
            .map { "ids[\($0.offset)]=\($0.element)" }
            .joined(separator: "&")

        let endpoint = "/api/v3/content/progress?\(idsParam)&contentType=\(contentType)"
        return try await request(endpoint: endpoint, requiresAuth: true)
    }

    /// Update video progress
    /// Update video progress
    func updateProgress(
        videoId: String,
        contentType: String = "video",
        progress: Int
    ) async throws {
        let updateRequest = UpdateProgressRequest(
            id: videoId,
            contentType: contentType,
            progress: progress
        )

        try await requestWithoutResponse(
            endpoint: "/api/v3/content/progress",
            method: "POST",
            body: updateRequest,
            requiresAuth: true
        )
    }

    // MARK: - WATCH HISTORY

    /// Get watch history from server
    func getWatchHistory(offset: Int = 0) async throws -> [WatchHistoryResponse] {
        return try await request(
            endpoint: "/api/v3/content/history?offset=\(offset)",
            method: "GET",
            requiresAuth: true
        )
    }
}
