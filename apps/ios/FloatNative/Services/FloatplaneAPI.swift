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
    private let authBaseURL = "https://auth.floatplane.com"

    // OAuth Configuration
    private let realm = "floatplane"
    #if os(tvOS)
    private let clientId = "floatnative"
    #else
    private let clientId = "floatnative"
    #endif
    // private let redirectUri = "floatnative://auth" // For future implementation

    // Endpoints
    private var tokenEndpoint: String { "/realms/\(realm)/protocol/openid-connect/token" }
    private var deviceAuthEndpoint: String { "/realms/\(realm)/protocol/openid-connect/auth/device" }
    private var authEndpoint: String { "/realms/\(realm)/protocol/openid-connect/auth" }

    // MARK: - Session

    private var session: URLSession
    private let cookieStorage: HTTPCookieStorage

    // MARK: - Authentication State

    @Published private(set) var isAuthenticated = false
    @Published private(set) var currentUser: User?
    @Published private(set) var currentUserDetails: UserSelfV3Response?
    @Published var autoReloginEnabled = true // User preference for auto re-login

    // OAuth State
    @Published private(set) var accessToken: String?
    private var refreshToken: String?
    private var tokenExpiry: Date?
    private var lastDPoPNonce: String? // Store latest server-provided nonce

    // Legacy Cookie (for backward compatibility during migration)
    private var authCookie: String? {
        didSet {
            // Only consider authenticated via cookie if no OAuth token
            if accessToken == nil {
                isAuthenticated = authCookie != nil
            }
        }
    }

    private var refreshTask: Task<Void, Error>?

    // MARK: - Initialization

    private init() {
        let config = URLSessionConfiguration.default
        config.httpCookieAcceptPolicy = .always
        config.httpShouldSetCookies = true
        self.cookieStorage = HTTPCookieStorage.shared
        self.session = URLSession(configuration: config)

        // Load saved settings
        loadOAuthTokens()
        loadAuthCookie() // Keep for legacy migration
        loadAutoReloginPreference()

        // Check authentication state
        checkAuthState()
    }

    private func checkAuthState() {
        // If we have a refresh token, we consider the user "potentially" authenticated.
        // The first API call will fail with 401 if access token is expired,
        // triggering the refresh flow automatically.
        if refreshToken != nil {
            isAuthenticated = true
        } else if let expiry = tokenExpiry, expiry > Date() {
            isAuthenticated = true
        } else if authCookie != nil {
            isAuthenticated = true
        } else {
            isAuthenticated = false
        }
    }

    private func loadOAuthTokens() {
        accessToken = KeychainManager.shared.getAccessToken()
        refreshToken = KeychainManager.shared.getRefreshToken()
        tokenExpiry = KeychainManager.shared.getTokenExpiry()
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

        // Clear OAuth state
        clearOAuthState()
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
        requiresAuth: Bool = false,
        retryCount: Int = 0
    ) async throws {
        guard let url = URL(string: baseURL + endpoint) else {
            throw FloatplaneAPIError.invalidURL
        }

        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = method
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.setValue("FloatNative/1.0 (iOS), CFNetwork", forHTTPHeaderField: "User-Agent")

        // Add auth headers
        if requiresAuth {
            if let accessToken = accessToken {
                
                // Add DPoP Proof
                if let dpopProof = try? DPoPManager.shared.generateProof(
                    httpMethod: method,
                    httpUrl: baseURL + endpoint,
                    accessToken: accessToken,
                    nonce: lastDPoPNonce
                ) {
                    urlRequest.setValue(dpopProof, forHTTPHeaderField: "DPoP")
                    // DPoP-bound tokens must use the DPoP scheme
                    urlRequest.setValue("DPoP \(accessToken)", forHTTPHeaderField: "Authorization")
                } else {
                    // Fallback to Bearer if DPoP generation fails (shouldn't happen)
                    urlRequest.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
                }
            } else if let authCookie = authCookie {
                // Fallback to legacy cookie
                urlRequest.setValue("sails.sid=\(authCookie)", forHTTPHeaderField: "Cookie")
            } else {
                throw FloatplaneAPIError.notAuthenticated
            }
        }

        // Encode body if present
        if let body = body {
            urlRequest.httpBody = try JSONEncoder().encode(body)
        }

        // Perform request
        let (_, response) = try await session.data(for: urlRequest)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw FloatplaneAPIError.invalidResponse
        }

        // Extract cookies from response
        extractCookie(from: httpResponse)

        // Create case-insensitive lookup for headers
        let headers = Dictionary(uniqueKeysWithValues: httpResponse.allHeaderFields.map {
            // Keys in allHeaderFields can be Any, usually String.
            (String(describing: $0.key).lowercased(), String(describing: $0.value))
        })
        
        // Capture DPoP-Nonce if present
        if let nonce = headers["dpop-nonce"] {
            self.lastDPoPNonce = nonce
        }
        
        // Handle HTTP errors
        guard (200...299).contains(httpResponse.statusCode) else {
                // Check for authentication errors (401 Unauthorized or 403 Forbidden)
            if httpResponse.statusCode == 401 || httpResponse.statusCode == 403 {


                // Check if error is due to DPoP Nonce mismatch (retry locally without refresh)
                if let wwwAuth = headers["www-authenticate"], 
                   wwwAuth.contains("use_dpop_nonce"),
                   retryCount < 3 {

                    return try await requestWithoutResponse(
                        endpoint: endpoint,
                        method: method,
                        body: body,
                        requiresAuth: requiresAuth,
                        retryCount: retryCount + 1
                    )
                }

                // Try OAuth Refresh first (Only for 401 Unauthorized)
                if httpResponse.statusCode == 401 && refreshToken != nil {
                     do {
                         try await ensureRefreshed()
                         return try await requestWithoutResponse(
                             endpoint: endpoint,
                             method: method,
                             body: body,
                             requiresAuth: requiresAuth
                         )
                     } catch {
                         throw error
                     }
                }
            }

            throw FloatplaneAPIError.httpError(statusCode: httpResponse.statusCode, message: nil)
        }
    }

    private func request<T: Decodable>(
        endpoint: String,
        method: String = "GET",
        body: Encodable? = nil,
        requiresAuth: Bool = false,
        retryCount: Int = 0
    ) async throws -> T {
        guard let url = URL(string: baseURL + endpoint) else {
            throw FloatplaneAPIError.invalidURL
        }

        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = method
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.setValue("FloatNative/1.0 (iOS), CFNetwork", forHTTPHeaderField: "User-Agent")

        // Add auth headers
        if requiresAuth {
            if let accessToken = accessToken {
                
                // Add DPoP Proof
                if let dpopProof = try? DPoPManager.shared.generateProof(
                    httpMethod: method,
                    httpUrl: baseURL + endpoint,
                    accessToken: accessToken,
                    nonce: lastDPoPNonce
                ) {
                    urlRequest.setValue(dpopProof, forHTTPHeaderField: "DPoP")
                    // DPoP-bound tokens must use the DPoP scheme
                    urlRequest.setValue("DPoP \(accessToken)", forHTTPHeaderField: "Authorization")
                } else {
                    // Fallback to Bearer if DPoP generation fails (shouldn't happen)
                    urlRequest.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
                }
            } else if let authCookie = authCookie {
                // Fallback to legacy cookie
                urlRequest.setValue("sails.sid=\(authCookie)", forHTTPHeaderField: "Cookie")
            } else {
                throw FloatplaneAPIError.notAuthenticated
            }
        }
        
        #if DEBUG
        if let auth = urlRequest.value(forHTTPHeaderField: "Authorization") {
            print("Auth: \(auth.prefix(20))...")
        } else {
            print("Auth: NONE")
        }
        if let dpop = urlRequest.value(forHTTPHeaderField: "DPoP") {
            print("DPoP Headers: \(dpop)")
        } else {
            print("DPoP Headers: MISSING")
        }
        print("-------------------------------")
        #endif

        // Encode body if present
        if let body = body {
            urlRequest.httpBody = try JSONEncoder().encode(body)
        }

        // Perform request
        let (data, response) = try await session.data(for: urlRequest)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw FloatplaneAPIError.invalidResponse
        }

        // Extract cookies from response
        extractCookie(from: httpResponse)

        // Create case-insensitive lookup for headers
        let headers = Dictionary(uniqueKeysWithValues: httpResponse.allHeaderFields.map {
            // Keys in allHeaderFields can be Any, usually String.
            (String(describing: $0.key).lowercased(), String(describing: $0.value))
        })
        
        // Capture DPoP-Nonce if present
        if let nonce = headers["dpop-nonce"] {
            self.lastDPoPNonce = nonce
        }
        
        // Handle HTTP errors
        guard (200...299).contains(httpResponse.statusCode) else {
                // Check for authentication errors (401 Unauthorized or 403 Forbidden)
            if httpResponse.statusCode == 401 || httpResponse.statusCode == 403 {


                // Check if error is due to DPoP Nonce mismatch (retry locally without refresh)
                if let wwwAuth = headers["www-authenticate"], 
                   wwwAuth.contains("use_dpop_nonce"),
                   retryCount < 3 {

                    return try await request(
                        endpoint: endpoint,
                        method: method,
                        body: body,
                        requiresAuth: requiresAuth,
                        retryCount: retryCount + 1
                    )
                }

                // Try OAuth Refresh first (Only for 401 Unauthorized)
                if httpResponse.statusCode == 401 && refreshToken != nil {
                     do {
                         try await ensureRefreshed()
                         return try await request(
                             endpoint: endpoint,
                             method: method,
                             body: body,
                             requiresAuth: requiresAuth
                         )
                     } catch {
                         throw error
                     }
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

    // MARK: - OAuth Helpers

    private func requestAuth<T: Decodable>(
        endpoint: String,
        method: String = "POST",
        body: [String: String],
        dpopProof: String? = nil
    ) async throws -> T {
        guard let url = URL(string: authBaseURL + endpoint) else {
            throw FloatplaneAPIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue("FloatNative/1.0 (iOS), CFNetwork", forHTTPHeaderField: "User-Agent")
        
        if let dpopProof = dpopProof {
            request.setValue(dpopProof, forHTTPHeaderField: "DPoP")
        }

        // Encode body params
        let bodyString = body.map { "\($0.key)=\($0.value)" }
            .joined(separator: "&")
        request.httpBody = bodyString.data(using: .utf8)

        #if DEBUG
        print("Body: \(bodyString)")
        if let dpop = request.value(forHTTPHeaderField: "DPoP") {
            print("DPoP Headers: \(dpop)")
        } else {
            print("DPoP Headers: MISSING")
        }
        print("--------------------------------")
        #endif

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw FloatplaneAPIError.invalidResponse
        }

        // Extract cookies (crucial for Hybrid DPoP/Cookie video playback)
        extractCookie(from: httpResponse)

        // Create case-insensitive lookup for headers
        let headers = Dictionary(uniqueKeysWithValues: httpResponse.allHeaderFields.map {
            // Keys in allHeaderFields can be Any, usually String.
            (String(describing: $0.key).lowercased(), String(describing: $0.value))
        })
        
        // Capture DPoP-Nonce if present
        if let nonce = headers["dpop-nonce"] {
            self.lastDPoPNonce = nonce
        }


        #if DEBUG
        print("--------- AUTH RESPONSE ---------")
        print("Status: \(httpResponse.statusCode)")
        print("Headers: \(httpResponse.allHeaderFields)")
        #endif

        guard (200...299).contains(httpResponse.statusCode) else {
            // Parse error response
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let error = json["error"] as? String {
                // Return descriptive error for polling (e.g., authorization_pending)
                throw FloatplaneAPIError.httpError(statusCode: httpResponse.statusCode, message: error)
            }
            throw FloatplaneAPIError.httpError(statusCode: httpResponse.statusCode, message: nil)
        }

        let decoder = JSONDecoder()
        return try decoder.decode(T.self, from: data)
    }

    private func handleOAuthResponse(_ response: OAuthTokenResponse) {
        self.accessToken = response.accessToken
        self.refreshToken = response.refreshToken
        self.tokenExpiry = Date().addingTimeInterval(TimeInterval(response.expiresIn))

        // Save to Keychain
        _ = KeychainManager.shared.saveOAuthTokens(
            accessToken: response.accessToken,
            refreshToken: response.refreshToken,
            expiresIn: response.expiresIn
        )

        self.isAuthenticated = true
        print("✅ OAuth login successful. Token expires in \(response.expiresIn)s")
    }

    // MARK: - AUTHENTICATION

    // MARK: OAuth Methods

    /// Start Device Authorization Flow (TV)
    func startDeviceAuth() async throws -> DeviceCodeResponse {
        let body = [
            "client_id": clientId,
            "scope": "openid offline_access"
        ]

        return try await requestAuth(endpoint: deviceAuthEndpoint, body: body)
    }

    /// Poll for token during Device Flow
    func pollDeviceToken(deviceCode: String) async throws -> OAuthTokenResponse {
        let body = [
            "grant_type": "urn:ietf:params:oauth:grant-type:device_code",
            "client_id": clientId,
            "device_code": deviceCode
        ]
        do {
            // Generate DPoP proof for the token endpoint
            let dpopProof = try DPoPManager.shared.generateProof(
                httpMethod: "POST",
                httpUrl: authBaseURL + tokenEndpoint,
                nonce: lastDPoPNonce
            )

            let response: OAuthTokenResponse = try await requestAuth(
                endpoint: tokenEndpoint,
                body: body,
                dpopProof: dpopProof
            )
            handleOAuthResponse(response)
            return response
        } catch {
            throw error
        }
    }

    /// Exchange Authorization Code for Token (iOS)
    func exchangeAuthCode(code: String, verifier: String, redirectUri: String? = nil) async throws {
        var body = [
            "grant_type": "authorization_code",
            "client_id": clientId,
            "code": code,
            "code_verifier": verifier
        ]
        
        if let redirectUri = redirectUri {
            body["redirect_uri"] = redirectUri
        }

        // Generate DPoP proof
        let dpopProof = try DPoPManager.shared.generateProof(
            httpMethod: "POST",
            httpUrl: authBaseURL + tokenEndpoint,
            nonce: lastDPoPNonce
        )

        let response: OAuthTokenResponse = try await requestAuth(
            endpoint: tokenEndpoint,
            body: body,
            dpopProof: dpopProof
        )
        handleOAuthResponse(response)

        // Fetch user info to populate currentUser
        _ = try? await getCurrentUser()
    }

    /// Refresh Access Token
    func refreshAccessToken() async throws {
        guard let refreshToken = refreshToken else {
            throw FloatplaneAPIError.notAuthenticated
        }

        let body = [
            "grant_type": "refresh_token",
            "client_id": clientId,
            "refresh_token": refreshToken
        ]

        do {
            // Generate DPoP proof for the token endpoint
            let dpopProof = try DPoPManager.shared.generateProof(
                httpMethod: "POST",
                httpUrl: authBaseURL + tokenEndpoint,
                nonce: lastDPoPNonce
            )

            let response: OAuthTokenResponse = try await requestAuth(
                endpoint: tokenEndpoint,
                body: body,
                dpopProof: dpopProof
            )
            handleOAuthResponse(response)
        } catch {
            // Check for cancellation (don't logout if task was simply cancelled)
            if error is CancellationError {
                throw error
            }
            let nsError = error as NSError
            if nsError.domain == NSURLErrorDomain && nsError.code == NSURLErrorCancelled {
                throw error
            }

            // If refresh fails (and not cancelled), clear tokens
            print("❌ Token refresh failed: \(error.localizedDescription)")
            clearOAuthState()
            throw error
        }
    }

    private func ensureRefreshed() async throws {
        if let task = refreshTask {
             return try await task.value
        }
        
        let task = Task {
             try await refreshAccessToken()
        }
        self.refreshTask = task
        
        do {
             try await task.value
             self.refreshTask = nil
        } catch {
             self.refreshTask = nil
             throw error
        }
    }

    private func clearOAuthState() {
        self.accessToken = nil
        self.refreshToken = nil
        self.tokenExpiry = nil
        KeychainManager.shared.clearOAuthTokens()
        self.isAuthenticated = false
    }

    // MARK: - LOGOUT

    /// Logout
    func logout() async throws {
        // Logout from Companion API (invalidate key)
        await CompanionAPI.shared.logout()

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
