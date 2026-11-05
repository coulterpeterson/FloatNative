//
//  WhatsNewService.swift
//  FloatNative
//
//  Loads the bundled `whats-new.json`, decides whether to show the popup
//  for this launch, and persists the last-seen version so it doesn't fire
//  again until the next release.
//
//  Design notes (per #42):
//  - Fresh installs are suppressed — we set lastSeen = current on first
//    launch so the popup only ever fires after an *upgrade*.
//  - Skipped versions don't accumulate — we always show only the latest.
//  - Settings → "What's New in this version" re-opens the sheet on demand
//    without mutating lastSeen.
//  - iOS/iPadOS and tvOS have separate content files, so a release can skip
//    the popup on a platform it didn't change: leave that platform's file
//    (and its version) alone.
//

import Foundation
import SwiftUI

struct WhatsNewContent: Codable, Equatable {
    let version: String
    let title: String
    /// Optional. When present, controls the value written to
    /// `lastSeenWhatsNewVersion` on a first-ever launch. Use this to make
    /// the popup fire even for fresh installs of a specific release — set
    /// it to the *previous* release's version (e.g. "1.6" while shipping
    /// 1.7), and the version-comparison below will see a mismatch and
    /// show the popup once. Omit it for normal "suppress on fresh
    /// install" behavior.
    let firstLaunchSeed: String?
    let items: [Item]

    struct Item: Codable, Equatable, Identifiable {
        let icon: String
        let title: String
        let body: String
        var id: String { title }
    }
}

@MainActor
final class WhatsNewService: ObservableObject {
    static let shared = WhatsNewService()

    private static let lastSeenVersionKey = "lastSeenWhatsNewVersion"
    private static let firstLaunchKey = "whatsNewFirstLaunchSeeded"

    /// Bundled content, decoded once. `nil` means the JSON isn't present
    /// or didn't parse — we just no-op the feature in that case.
    let content: WhatsNewContent?

    @Published var isPresented = false

    private init() {
        self.content = Self.loadBundledContent()
    }

    /// Call once from the app's root `.onAppear`. Sets `isPresented = true`
    /// if there's something new to show. First-launch users are suppressed
    /// (we treat the bundled version as "already seen").
    func presentIfNeeded() {
        guard let content else { return }
        let defaults = UserDefaults.standard

        if !defaults.bool(forKey: Self.firstLaunchKey) {
            // First launch ever. Seed `lastSeen` with the JSON's
            // `firstLaunchSeed` (if specified) or the current version (the
            // default). When the seed is an *earlier* version, the
            // comparison below will fire the popup even for fresh
            // installs — useful for the release that *introduces* the
            // What's New feature itself (otherwise nobody would ever see
            // it for that release).
            defaults.set(true, forKey: Self.firstLaunchKey)
            defaults.set(content.firstLaunchSeed ?? content.version, forKey: Self.lastSeenVersionKey)
        }

        let lastSeen = defaults.string(forKey: Self.lastSeenVersionKey)
        if lastSeen != content.version {
            isPresented = true
        }
    }

    /// Settings-triggered re-open. Doesn't touch lastSeen.
    func presentManually() {
        guard content != nil else { return }
        isPresented = true
    }

    /// Called by WhatsNewSheet's Close button.
    func dismiss() {
        if let content {
            UserDefaults.standard.set(content.version, forKey: Self.lastSeenVersionKey)
        }
        isPresented = false
    }

    // MARK: - Bundle loading

    #if os(tvOS)
    private static let contentFile = "whats-new"
    #else
    private static let contentFile = "whats-new-ios"
    #endif

    private static func loadBundledContent() -> WhatsNewContent? {
        guard let url = Bundle.main.url(forResource: contentFile, withExtension: "json") else {
            print("⚠️ [WhatsNew] \(contentFile).json not found in bundle")
            return nil
        }
        do {
            let data = try Data(contentsOf: url)
            return try JSONDecoder().decode(WhatsNewContent.self, from: data)
        } catch {
            print("⚠️ [WhatsNew] failed to decode \(contentFile).json: \(error)")
            return nil
        }
    }
}
