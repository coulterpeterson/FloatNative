//
//  ReviewPrompt.swift
//  FloatNative
//
//  Decides when to show the system "Enjoying FloatNative?" star rating dialog.
//  App Store rules require that dialog rather than a custom prompt. iOS also
//  caps it at three times a year and may choose not to show it when asked.
//  It never appears in TestFlight; development builds always show it.
//

import SwiftUI

#if !os(tvOS)
import StoreKit

@MainActor
final class ReviewPrompt: ObservableObject {
    static let shared = ReviewPrompt()

    /// Leaving a video only counts once it got this far in.
    static let minimumWatchedSeconds = 120.0
    static let videosBeforePrompt = 5
    static let daysBeforeFirstPrompt = 7
    static let daysBetweenPrompts = 120

    private enum Key {
        static let firstSeen = "reviewPromptFirstSeen"
        static let watchedVideos = "reviewPromptWatchedVideos"
        static let lastPrompt = "reviewPromptLastPrompt"
    }

    /// True when the dialog should show at the next calm moment.
    @Published private(set) var isDue = false

    private let defaults: UserDefaults
    private let now: () -> Date

    init(defaults: UserDefaults = .standard, now: @escaping () -> Date = Date.init) {
        self.defaults = defaults
        self.now = now
        if defaults.object(forKey: Key.firstSeen) == nil {
            defaults.set(now(), forKey: Key.firstSeen)
        }
    }

    /// Call when the user finishes with a video, streamed or downloaded.
    func recordVideoFinished(watchedTo seconds: Double) {
        guard seconds >= Self.minimumWatchedSeconds else { return }
        defaults.set(defaults.integer(forKey: Key.watchedVideos) + 1, forKey: Key.watchedVideos)
        isDue = isEligible
    }

    /// Starts the wait over, whether or not iOS actually showed the dialog.
    /// There's no way to tell which.
    func markPrompted() {
        defaults.set(now(), forKey: Key.lastPrompt)
        defaults.set(0, forKey: Key.watchedVideos)
        isDue = false
    }

    /// The moment passed (another video started), so wait for the next one.
    func skipForNow() {
        isDue = false
    }

    private var isEligible: Bool {
        #if DEBUG
        // To see the dialog on a device without waiting a week. It still needs
        // a video watched past minimumWatchedSeconds, then leaving it.
        if ProcessInfo.processInfo.environment["FLOATNATIVE_FORCE_REVIEW_PROMPT"] == "1" {
            return true
        }
        #endif

        guard defaults.integer(forKey: Key.watchedVideos) >= Self.videosBeforePrompt,
              let firstSeen = defaults.object(forKey: Key.firstSeen) as? Date,
              days(since: firstSeen) >= Self.daysBeforeFirstPrompt else { return false }

        guard let lastPrompt = defaults.object(forKey: Key.lastPrompt) as? Date else { return true }
        return days(since: lastPrompt) >= Self.daysBetweenPrompts
    }

    private func days(since date: Date) -> Int {
        Int(now().timeIntervalSince(date) / 86_400)
    }
}

private struct ReviewPromptModifier: ViewModifier {
    @ObservedObject private var prompt = ReviewPrompt.shared
    @Environment(\.requestReview) private var requestReview

    func body(content: Content) -> some View {
        content.onChange(of: prompt.isDue) { _, isDue in
            guard isDue else { return }
            Task {
                // Let the user land back on the page they returned to first.
                try? await Task.sleep(nanoseconds: 1_500_000_000)
                // Never interrupt playback, including picture in picture.
                guard AVPlayerManager.shared.player == nil else {
                    prompt.skipForNow()
                    return
                }
                requestReview()
                prompt.markPrompted()
            }
        }
    }
}
#endif

extension View {
    /// Shows the system rating dialog when ReviewPrompt says it's time.
    @ViewBuilder
    func reviewPromptWhenDue() -> some View {
        #if os(tvOS)
        self
        #else
        modifier(ReviewPromptModifier())
        #endif
    }
}
