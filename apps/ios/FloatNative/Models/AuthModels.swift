//
//  AuthModels.swift
//  FloatNative
//
//  Created by Claude on 2025-10-08.
//

import Foundation

// MARK: - Login

struct LoginRequest: Codable {
    let username: String
    let password: String
    let captchaToken: String?
}

// Using OpenAPI-generated models
typealias LoginResponse = AuthLoginV2Response
typealias User = UserModel  // Basic user info (id, username, profileImage)

struct TwoFactorRequest: Codable {
    let token: String
}

// MARK: - Captcha Info

struct CaptchaInfo: Codable {
    let v2: CaptchaKeys
    let v3: CaptchaKeys
}

struct CaptchaKeys: Codable {
    let android: String
    let ios: String
    let web: String
}

// MARK: - User Activity

struct UserActivity: Codable {
    let activity: [ActivityItem]
}

struct ActivityItem: Codable {
    let time: Date
    let comment: Comment?
    let post: BlogPost?
}

// MARK: - User Links

struct UserLinks: Codable {
    let twitter: SocialLink?
    let youtube: SocialLink?
    let instagram: SocialLink?
    let website: SocialLink?
    let facebook: SocialLink?
    let twitch: SocialLink?
}

struct SocialLink: Codable {
    let value: String
    let type: SocialLinkType?
}

struct SocialLinkType: Codable {
    let id: String
    let name: String
}

// MARK: - Notification Settings

struct NotificationSetting: Codable {
    let creator: String
    let property: String
    let newContent: NotificationChannel?
    let contentEmail: NotificationChannel?
}

struct NotificationChannel: Codable {
    let enabled: Bool
}


struct NotificationUpdateRequest: Codable {
    let creator: String
    let property: String
    let newValue: Bool
}

// MARK: - OAuth

struct OAuthTokenResponse: Codable {
    let accessToken: String
    let expiresIn: Int
    let refreshExpiresIn: Int
    let refreshToken: String
    let tokenType: String
    let notBeforePolicy: Int
    let sessionState: String
    let scope: String

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case expiresIn = "expires_in"
        case refreshExpiresIn = "refresh_expires_in"
        case refreshToken = "refresh_token"
        case tokenType = "token_type"
        case notBeforePolicy = "not-before-policy"
        case sessionState = "session_state"
        case scope
    }
}

struct DeviceCodeResponse: Codable {
    let deviceCode: String
    let userCode: String
    let verificationUri: String
    let verificationUriComplete: String
    let expiresIn: Int
    let interval: Int

    enum CodingKeys: String, CodingKey {
        case deviceCode = "device_code"
        case userCode = "user_code"
        case verificationUri = "verification_uri"
        case verificationUriComplete = "verification_uri_complete"
        case expiresIn = "expires_in"
        case interval
    }
}
