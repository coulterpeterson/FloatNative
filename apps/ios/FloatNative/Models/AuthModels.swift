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
