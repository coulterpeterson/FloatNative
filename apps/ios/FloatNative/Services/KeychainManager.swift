//
//  KeychainManager.swift
//  FloatNative
//
//  Secure storage for user credentials using iOS Keychain Services
//

import Foundation
import Security

class KeychainManager {
    static let shared = KeychainManager()

    private let service = "com.floatnative.credentials"
    private let usernameKey = "username"
    private let passwordKey = "password"
    private let apiKeyKey = "companion_api_key"
    private let authTokenKey = "floatplane_auth_token" // Legacy sails.sid
    private let accessTokenKey = "oauth_access_token"
    private let refreshTokenKey = "oauth_refresh_token"
    private let tokenExpiryKey = "oauth_token_expiry"

    private init() {}

    // MARK: - Credential Storage

    /// Store username and password securely in Keychain
    func saveCredentials(username: String, password: String) -> Bool {
        // Save username
        let usernameSuccess = saveItem(key: usernameKey, value: username)

        // Save password
        let passwordSuccess = saveItem(key: passwordKey, value: password)

        return usernameSuccess && passwordSuccess
    }

    /// Retrieve stored credentials from Keychain
    func getCredentials() -> (username: String, password: String)? {
        guard let username = getItem(key: usernameKey),
              let password = getItem(key: passwordKey) else {
            return nil
        }

        return (username, password)
    }

    /// Clear all stored credentials from Keychain
    func clearCredentials() {
        deleteItem(key: usernameKey)
        deleteItem(key: passwordKey)
    }

    /// Check if credentials are stored
    func hasStoredCredentials() -> Bool {
        return getItem(key: usernameKey) != nil && getItem(key: passwordKey) != nil
    }

    // MARK: - API Key Storage

    /// Store companion API key securely in Keychain
    func saveAPIKey(_ apiKey: String) -> Bool {
        return saveItem(key: apiKeyKey, value: apiKey)
    }

    /// Retrieve stored API key from Keychain
    func getAPIKey() -> String? {
        return getItem(key: apiKeyKey)
    }

    /// Check if API key is stored
    func hasAPIKey() -> Bool {
        return getItem(key: apiKeyKey) != nil
    }

    /// Clear stored API key from Keychain
    func clearAPIKey() {
        deleteItem(key: apiKeyKey)
    }

    // MARK: - Auth Token Storage (sails.sid cookie)

    /// Store Floatplane auth token (sails.sid) securely in Keychain
    func saveAuthToken(_ token: String) -> Bool {
        return saveItem(key: authTokenKey, value: token)
    }

    /// Retrieve stored auth token from Keychain
    func getAuthToken() -> String? {
        return getItem(key: authTokenKey)
    }

    /// Check if auth token is stored
    func hasAuthToken() -> Bool {
        return getItem(key: authTokenKey) != nil
    }

    /// Clear stored auth token from Keychain
    func clearAuthToken() {
        deleteItem(key: authTokenKey)
    }

    // MARK: - OAuth Token Storage

    func saveOAuthTokens(accessToken: String, refreshToken: String, expiresIn: Int) -> Bool {
        let expiryDate = Date().addingTimeInterval(TimeInterval(expiresIn))
        let expiryTimestamp = String(expiryDate.timeIntervalSince1970)

        let accessSuccess = saveItem(key: accessTokenKey, value: accessToken)
        let refreshSuccess = saveItem(key: refreshTokenKey, value: refreshToken)
        let expirySuccess = saveItem(key: tokenExpiryKey, value: expiryTimestamp)

        return accessSuccess && refreshSuccess && expirySuccess
    }

    func getAccessToken() -> String? {
        return getItem(key: accessTokenKey)
    }

    func getRefreshToken() -> String? {
        return getItem(key: refreshTokenKey)
    }

    func getTokenExpiry() -> Date? {
        guard let timestampString = getItem(key: tokenExpiryKey),
              let timestamp = TimeInterval(timestampString) else {
            return nil
        }
        return Date(timeIntervalSince1970: timestamp)
    }

    func clearOAuthTokens() {
        deleteItem(key: accessTokenKey)
        deleteItem(key: refreshTokenKey)
        deleteItem(key: tokenExpiryKey)
    }

    // MARK: - Private Keychain Operations

    private func saveItem(key: String, value: String) -> Bool {
        guard let data = value.data(using: .utf8) else { return false }

        // Delete existing item first (if any)
        deleteItem(key: key)

        // Create query for new item
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]

        let status = SecItemAdd(query as CFDictionary, nil)
        return status == errSecSuccess
    }

    private func getItem(key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess,
              let data = result as? Data,
              let value = String(data: data, encoding: .utf8) else {
            return nil
        }

        return value
    }

    private func deleteItem(key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]

        SecItemDelete(query as CFDictionary)
    }
}
