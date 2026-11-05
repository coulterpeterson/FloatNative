//
//  ReviewPromptTests.swift
//  FloatNativeTests
//

import Testing
import Foundation
@testable import FloatNative

@MainActor
struct ReviewPromptTests {
    /// A prompt whose clock the test controls, starting at day zero.
    private final class Clock {
        var date = Date(timeIntervalSince1970: 1_800_000_000)
        func advance(days: Int) { date += Double(days) * 86_400 }
    }

    private func makePrompt(_ clock: Clock) -> ReviewPrompt {
        let name = "ReviewPromptTests-\(UUID().uuidString)"
        return ReviewPrompt(defaults: UserDefaults(suiteName: name)!, now: { clock.date })
    }

    private func finishVideos(_ count: Int, on prompt: ReviewPrompt) {
        for _ in 0..<count { prompt.recordVideoFinished(watchedTo: 600) }
    }

    @Test func needsBothEnoughVideosAndEnoughDays() {
        let clock = Clock()
        let prompt = makePrompt(clock)

        finishVideos(10, on: prompt)
        #expect(!prompt.isDue) // Plenty of videos, but on day one.

        clock.advance(days: 7)
        finishVideos(1, on: prompt)
        #expect(prompt.isDue)
    }

    @Test func enoughDaysAloneIsNotEnough() {
        let clock = Clock()
        let prompt = makePrompt(clock)

        clock.advance(days: 30)
        finishVideos(4, on: prompt)
        #expect(!prompt.isDue)

        finishVideos(1, on: prompt)
        #expect(prompt.isDue)
    }

    @Test func videosBarelyStartedDoNotCount() {
        let clock = Clock()
        let prompt = makePrompt(clock)
        clock.advance(days: 30)

        for _ in 0..<20 { prompt.recordVideoFinished(watchedTo: 119) }
        #expect(!prompt.isDue)
    }

    @Test func waitsBetweenPromptsAndNeedsMoreVideos() {
        let clock = Clock()
        let prompt = makePrompt(clock)
        clock.advance(days: 7)
        finishVideos(5, on: prompt)
        prompt.markPrompted()
        #expect(!prompt.isDue)

        // Enough videos again, but too soon.
        clock.advance(days: 119)
        finishVideos(5, on: prompt)
        #expect(!prompt.isDue)

        clock.advance(days: 1)
        finishVideos(1, on: prompt)
        #expect(prompt.isDue)
    }

    @Test func skippedMomentTriesAgainAtTheNextVideo() {
        let clock = Clock()
        let prompt = makePrompt(clock)
        clock.advance(days: 7)
        finishVideos(5, on: prompt)

        prompt.skipForNow()
        #expect(!prompt.isDue)

        finishVideos(1, on: prompt)
        #expect(prompt.isDue)
    }
}
