//
//  WatchHistoryManager.swift
//  FloatNative
//
//  Local watch history tracking
//

import Foundation

// MARK: - Watch History Item

struct WatchHistoryItem: Codable {
    let postId: String
    let videoId: String?
    let watchedAt: Date

    enum CodingKeys: String, CodingKey {
        case postId
        case videoId
        case watchedAt
    }
}

// MARK: - Watch History Manager

@MainActor
class WatchHistoryManager: ObservableObject {
    static let shared = WatchHistoryManager()

    private let userDefaultsKey = "watchHistory"
    private let maxHistoryItems = 100

    @Published private(set) var history: [WatchHistoryItem] = []

    private init() {
        loadHistory()
    }

    // MARK: - Public Methods

    /// Add a video to watch history
    func addToHistory(postId: String, videoId: String?) {


        // Remove existing entry for this post if it exists
        history.removeAll { $0.postId == postId }

        // Add new entry at the beginning
        let item = WatchHistoryItem(
            postId: postId,
            videoId: videoId,
            watchedAt: Date()
        )
        history.insert(item, at: 0)

        // Limit history size
        if history.count > maxHistoryItems {
            history = Array(history.prefix(maxHistoryItems))
        }


        saveHistory()
    }

    /// Get all history items
    func getHistory() -> [WatchHistoryItem] {
        return history
    }

    /// Clear all watch history
    func clearHistory() {
        history = []
        saveHistory()
    }

    /// Remove specific item from history
    func removeFromHistory(postId: String) {
        history.removeAll { $0.postId == postId }
        saveHistory()
    }

    // MARK: - Private Methods

    private func loadHistory() {

        guard let data = UserDefaults.standard.data(forKey: userDefaultsKey) else {

            history = []
            return
        }

        do {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            history = try decoder.decode([WatchHistoryItem].self, from: data)

        } catch {
            print("⚠️ Failed to load watch history: \(error)")
            history = []
        }
    }

    private func saveHistory() {

        do {
            let encoder = JSONEncoder()
            encoder.dateEncodingStrategy = .iso8601
            let data = try encoder.encode(history)
            UserDefaults.standard.set(data, forKey: userDefaultsKey)

        } catch {
            print("⚠️ Failed to save watch history: \(error)")
        }
    }
}
