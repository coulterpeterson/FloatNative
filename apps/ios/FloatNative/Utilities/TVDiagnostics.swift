//
//  TVDiagnostics.swift
//  FloatNative
//
//  Focused instrumentation for the tvOS player. Two problems motivated it:
//
//    1. The Siri Remote's Menu (back) button dismissed the whole player
//       instead of first collapsing the AVPlayerViewController transport
//       bar — see VideoPlayerTvosView's exit-command handling.
//    2. Remote presses occasionally appear to do nothing for several
//       seconds at a time.
//
//  For (2) the useful signal is *where* the press dies. `TVLog.press`
//  records every UIPress that reaches the player view controller, and
//  `MainThreadWatchdog` records main-run-loop stalls independently of the
//  main thread itself — so a gap in press logs that lines up with a hang
//  report means the app froze, while presses that log but produce no
//  focus/transport change means the press was delivered and ignored.
//
//  Everything here is print-based (visible in the Xcode console during a
//  device debug session, greppable by the `[FNTV]` prefix) and, for the
//  notable events only, mirrored into DebugLogManager so it is also
//  readable in-app via Settings → Debug Log.
//

import Foundation
import os

#if canImport(UIKit)
import UIKit
#endif

// MARK: - TVLog

enum TVLog {

    enum Cat: String {
        case remote      // raw UIPress traffic
        case focus       // focus engine movement
        case transport   // AVPlayerViewController transport bar
        case exitCmd     // Menu/back handling
        case player      // playback + loading state
        case ui          // SwiftUI body / representable churn
        case hang        // main-thread stalls
        case playlist    // playlist membership / bookmark badges
    }

    /// Master switch. On by default in debug builds so a device debug
    /// session is instrumented without any extra setup; off in release so
    /// shipping builds don't pay for the logging.
    #if DEBUG
    nonisolated(unsafe) static var isEnabled = true
    #else
    nonisolated(unsafe) static var isEnabled = false
    #endif

    /// Monotonic clock so timestamps stay comparable across a stall (wall
    /// clock can jump; uptime cannot).
    private static let startUptime = ProcessInfo.processInfo.systemUptime

    static var elapsed: Double {
        ProcessInfo.processInfo.systemUptime - startUptime
    }

    private static let lock = OSAllocatedUnfairLock()

    /// - Parameter notable: also record into the in-app Debug Log ring
    ///   buffer. Keep this false for anything high-frequency; the buffer is
    ///   only 200 entries and publishing hops to the main queue.
    static func log(_ cat: Cat, _ message: String, detail: String? = nil, notable: Bool = false) {
        guard isEnabled else { return }
        let stamp = String(format: "%9.3f", elapsed)
        let line = "[FNTV \(stamp)] [\(cat.rawValue)] \(message)"
        // print only: Xcode's console shows both stdout and os_log, so also
        // logging through os.Logger printed every line twice. stdout is what
        // `devicectl ... --console` captures from a device.
        print(line)
        if notable {
            DebugLogManager.shared.append(
                .init(category: .player, message: "[\(cat.rawValue)] \(message)", detail: detail)
            )
        }
    }
}

// MARK: - UIPress description helpers

#if os(tvOS)
extension UIPress.PressType {
    var fnName: String {
        switch self {
        case .upArrow: return "up"
        case .downArrow: return "down"
        case .leftArrow: return "left"
        case .rightArrow: return "right"
        case .select: return "select"
        case .menu: return "MENU"
        case .playPause: return "playPause"
        case .pageUp: return "pageUp"
        case .pageDown: return "pageDown"
        case .tvRemoteOneTwoThree: return "123"
        case .tvRemoteFourColors: return "colors"
        @unknown default: return "raw(\(rawValue))"
        }
    }
}

extension TVLog {
    private static var globalObservers: [NSObjectProtocol] = []

    /// App-wide observers: every focus move (including ones that never touch
    /// the player, e.g. the tab bar) and app foreground/background changes.
    /// The "Back exits the whole app" report needs both — tvOS suspends the
    /// app when a Menu press reaches the root, which shows up here as
    /// `app -> resignActive` right after the press.
    static func installGlobalObservers() {
        guard isEnabled, globalObservers.isEmpty else { return }
        let center = NotificationCenter.default
        globalObservers.append(center.addObserver(forName: UIFocusSystem.didUpdateNotification, object: nil, queue: .main) { note in
            let ctx = note.userInfo?[UIFocusSystem.focusUpdateContextUserInfoKey] as? UIFocusUpdateContext
            let name: (UIFocusItem?) -> String = { $0.map { String(describing: type(of: $0)) } ?? "nil" }
            log(.focus, "[global] \(name(ctx?.previouslyFocusedItem)) -> \(name(ctx?.nextFocusedItem))")
        })
        let lifecycle: [(Notification.Name, String)] = [
            (UIApplication.willResignActiveNotification, "resignActive"),
            (UIApplication.didEnterBackgroundNotification, "didEnterBackground"),
            (UIApplication.willEnterForegroundNotification, "willEnterForeground"),
            (UIApplication.didBecomeActiveNotification, "didBecomeActive"),
        ]
        for (name, label) in lifecycle {
            globalObservers.append(center.addObserver(forName: name, object: nil, queue: .main) { _ in
                log(.ui, "app -> \(label)", notable: true)
            })
        }
    }

    /// Log a raw remote press. `phase` is began/ended/cancelled/changed.
    static func press(_ phase: String, _ presses: Set<UIPress>, source: String) {
        guard isEnabled else { return }
        for press in presses {
            let responder = press.responder.map { String(describing: type(of: $0)) } ?? "nil"
            log(.remote, "\(source) presses\(phase) type=\(press.type.fnName) firstResponder=\(responder)")
        }
    }
}
#endif

// MARK: - MainThreadWatchdog

/// Detects main-run-loop stalls from a background queue.
///
/// A timer on a private queue stamps `lastPing`, hops to the main queue,
/// and the main queue stamps `lastPong`. If `lastPing - lastPong` exceeds
/// `threshold` the main thread has been busy (or blocked) for that long,
/// which is exactly the "the remote did nothing for ten seconds" symptom.
/// Reporting happens off the main thread, so the report still lands *while*
/// the app is frozen rather than after it recovers.
final class MainThreadWatchdog {

    static let shared = MainThreadWatchdog()

    // High QoS on purpose: the first device run showed the timer itself
    // getting starved during a stall (its "stalled for" and "recovered after"
    // numbers disagreed), which makes the report late and undercounts.
    private let queue = DispatchQueue(label: "com.coulterpeterson.floatnative.watchdog", qos: .userInteractive)
    private var timer: DispatchSourceTimer?
    private let lock = OSAllocatedUnfairLock()

    /// Both are systemUptime values; guarded by `lock`.
    private var lastPingIssued: Double = 0
    private var lastPongReceived: Double = 0
    private var pendingPing = false
    private var reportedForCurrentStall = false

    /// Sample period. Cheap — one empty main-queue block per tick.
    private let interval: TimeInterval = 0.25
    /// Anything above this is a user-visible stutter worth a line.
    private let threshold: TimeInterval = 0.75

    private init() {}

    func start() {
        guard TVLog.isEnabled else { return }
        lock.lock()
        defer { lock.unlock() }
        guard timer == nil else { return }

        let now = ProcessInfo.processInfo.systemUptime
        lastPingIssued = now
        lastPongReceived = now

        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now() + interval, repeating: interval)
        timer.setEventHandler { [weak self] in self?.tick() }
        timer.resume()
        self.timer = timer
        TVLog.log(.hang, "watchdog started (interval=\(interval)s threshold=\(threshold)s)")
    }

    func stop() {
        lock.lock()
        let t = timer
        timer = nil
        lock.unlock()
        t?.cancel()
        TVLog.log(.hang, "watchdog stopped")
    }

    private func tick() {
        let now = ProcessInfo.processInfo.systemUptime

        lock.lock()
        let outstanding = pendingPing
        let stalledFor = now - lastPongReceived
        let shouldReport = outstanding && stalledFor >= threshold && !reportedForCurrentStall
        if shouldReport { reportedForCurrentStall = true }
        if !outstanding {
            pendingPing = true
            lastPingIssued = now
        }
        lock.unlock()

        if shouldReport {
            // Deliberately logged from the watchdog queue so it appears in
            // the console during the freeze, not after it clears.
            TVLog.log(.hang,
                      String(format: "MAIN THREAD STALLED for %.2fs (and counting)", stalledFor),
                      notable: true)
        }

        guard !outstanding else { return }

        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            let pong = ProcessInfo.processInfo.systemUptime
            self.lock.lock()
            // Time since the main thread last answered — the full window in
            // which the UI could not have reacted to anything.
            let unresponsiveFor = pong - self.lastPongReceived
            let wasReported = self.reportedForCurrentStall
            self.lastPongReceived = pong
            self.pendingPing = false
            self.reportedForCurrentStall = false
            self.lock.unlock()

            if wasReported {
                TVLog.log(.hang,
                          String(format: "main thread recovered — unresponsive for %.2fs total", unresponsiveFor),
                          notable: true)
            } else if unresponsiveFor >= self.threshold + self.interval {
                TVLog.log(.hang, String(format: "main thread blocked %.2fs", unresponsiveFor), notable: true)
            }
        }
    }
}
