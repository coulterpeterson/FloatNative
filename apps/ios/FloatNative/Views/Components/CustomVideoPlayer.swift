//
//  CustomVideoPlayer.swift
//  FloatNative
//
//  Custom video player with native Picture-in-Picture support
//  Uses AVPlayerViewController wrapped in UIViewControllerRepresentable
//

import SwiftUI
import AVKit

#if os(tvOS)
/// Custom AVPlayerViewController subclass that manages transport bar items efficiently
/// Prevents pulsing animation by avoiding unnecessary transport bar regeneration
class CustomAVPlayerViewController: AVPlayerViewController {
    private var likeAction: UIAction?
    private var dislikeAction: UIAction?
    private var likeHandler: (() async -> Void)?
    private var dislikeHandler: (() async -> Void)?

    // MARK: - Remote diagnostics
    //
    // Every Siri Remote press that reaches the player lands here first.
    // These overrides log and then defer to `super` — they never consume a
    // press — so the trace shows exactly what the app received and when.
    // A press logged with no matching focus/transport change afterwards
    // means the event was delivered and ignored; a *missing* press log
    // (with the user insisting they pressed the button) means the event
    // never made it out of the system, which the MainThreadWatchdog hang
    // reports will corroborate.

    override func pressesBegan(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
        TVLog.press("Began", presses, source: "AVPlayerVC")
        super.pressesBegan(presses, with: event)
    }

    override func pressesEnded(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
        TVLog.press("Ended", presses, source: "AVPlayerVC")
        super.pressesEnded(presses, with: event)
    }

    override func pressesCancelled(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
        TVLog.press("Cancelled", presses, source: "AVPlayerVC")
        super.pressesCancelled(presses, with: event)
    }

    override func didUpdateFocus(in context: UIFocusUpdateContext, with coordinator: UIFocusAnimationCoordinator) {
        super.didUpdateFocus(in: context, with: coordinator)
        let from = context.previouslyFocusedItem.map { String(describing: type(of: $0)) } ?? "nil"
        let to = context.nextFocusedItem.map { String(describing: type(of: $0)) } ?? "nil"
        TVLog.log(.focus, "focus \(from) -> \(to) heading=\(context.focusHeading)")
    }

    /// Update the like button title and state without recreating the entire transport bar
    func updateLikeButton(count: Int, isLiked: Bool) {
        guard let likeAction = likeAction else { return }
        var items = transportBarCustomMenuItems

        // Create updated action with new title and icon
        let newAction = UIAction(
            title: "Like (\(count))",
            image: UIImage(systemName: isLiked ? "hand.thumbsup.fill" : "hand.thumbsup")
        ) { [weak self] _ in
            guard let self = self, let handler = self.likeHandler else { return }
            Task {
                await handler()
            }
        }

        // Replace the old action in the menu items array
        if let index = items.firstIndex(where: { $0 === likeAction }) {
            items[index] = newAction
            self.likeAction = newAction
            transportBarCustomMenuItems = items
        }
    }

    /// Update the dislike button title and state without recreating the entire transport bar
    func updateDislikeButton(count: Int, isDisliked: Bool) {
        guard let dislikeAction = dislikeAction else { return }
        var items = transportBarCustomMenuItems

        // Create updated action with new title and icon
        let newAction = UIAction(
            title: "Dislike (\(count))",
            image: UIImage(systemName: isDisliked ? "hand.thumbsdown.fill" : "hand.thumbsdown")
        ) { [weak self] _ in
            guard let self = self, let handler = self.dislikeHandler else { return }
            Task {
                await handler()
            }
        }

        // Replace the old action in the menu items array
        if let index = items.firstIndex(where: { $0 === dislikeAction }) {
            items[index] = newAction
            self.dislikeAction = newAction
            transportBarCustomMenuItems = items
        }
    }

    /// Set the initial transport bar items (call once during setup)
    func setInitialTransportBarItems(
        likeCount: Int,
        dislikeCount: Int,
        isLiked: Bool,
        isDisliked: Bool,
        onLike: @escaping () async -> Void,
        onDislike: @escaping () async -> Void,
        onDescription: @escaping () -> Void,
        onComments: @escaping () -> Void,
        qualityMenu: UIMenu
    ) {
        self.likeHandler = onLike
        self.dislikeHandler = onDislike

        // Create like action
        let likeAction = UIAction(
            title: "Like (\(likeCount))",
            image: UIImage(systemName: isLiked ? "hand.thumbsup.fill" : "hand.thumbsup")
        ) { _ in
            Task {
                await onLike()
            }
        }
        self.likeAction = likeAction

        // Create dislike action
        let dislikeAction = UIAction(
            title: "Dislike (\(dislikeCount))",
            image: UIImage(systemName: isDisliked ? "hand.thumbsdown.fill" : "hand.thumbsdown")
        ) { _ in
            Task {
                await onDislike()
            }
        }
        self.dislikeAction = dislikeAction

        // Create description action
        let descriptionAction = UIAction(
            title: "Description",
            image: UIImage(systemName: "doc.text")
        ) { _ in
            onDescription()
        }

        // Create comments action
        let commentsAction = UIAction(
            title: "Comments",
            image: UIImage(systemName: "bubble.left.and.bubble.right")
        ) { _ in
            onComments()
        }

        // Set all items once
        transportBarCustomMenuItems = [
            likeAction,
            dislikeAction,
            descriptionAction,
            commentsAction,
            qualityMenu
        ]
    }
}
#endif

/// A custom video player that supports Picture-in-Picture
/// SwiftUI's VideoPlayer doesn't support PiP, so we use AVPlayerViewController
struct CustomVideoPlayer: UIViewControllerRepresentable {
    let player: AVPlayer
    var showsPlaybackControls: Bool = true

    // tvOS-specific custom transport bar items
    #if os(tvOS)
    var customMenuItems: [UIMenuElement] = []
    var contextualActions: [UIAction] = []
    #endif

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        // Check if we should reuse existing playerViewController
        // Reuse during: PiP restoration, fullscreen transitions, and layout changes (like rotation)
        // This prevents black screen issues when AVPlayerViewController changes presentation mode
        if let existingController = AVPlayerManager.shared.playerViewController,
           existingController.player === player,
           let existingDelegate = AVPlayerManager.shared.playerViewControllerDelegate {

            // Reuse the existing delegate (Coordinator) to maintain callback connection
            existingController.delegate = existingDelegate as? AVPlayerViewControllerDelegate
            existingController.showsPlaybackControls = showsPlaybackControls
            return existingController
        }


        #if os(tvOS)
        let controller = CustomAVPlayerViewController()
        #else
        let controller = AVPlayerViewController()
        #endif

        controller.player = player
        controller.showsPlaybackControls = showsPlaybackControls
        controller.allowsPictureInPicturePlayback = true
        controller.delegate = context.coordinator

        #if os(tvOS)
        // Apply tvOS-specific customizations
        if !customMenuItems.isEmpty {

            controller.transportBarCustomMenuItems = customMenuItems
        } else {
            print("⚠️ CustomVideoPlayer: customMenuItems is EMPTY - no transport bar items will be applied!")
        }
        if !contextualActions.isEmpty {
            controller.contextualActions = contextualActions
        }
        // GH #11: previously suppressed subtitles entirely via
        // `allowedSubtitleOptionLanguages = [""]`. With captions wiring on
        // the asset (via AVMediaSelectionGroup once the synthetic-master
        // path is re-enabled), the tvOS swipe-down Info panel surfaces a
        // proper Subtitles submenu — so we don't restrict the language
        // list. AVPlayer hides the entry on its own when the asset
        // exposes no legible tracks, so this is safe before captions ship.
        #endif

        // Store BOTH controller AND coordinator in AVPlayerManager to keep alive during PiP
        AVPlayerManager.shared.playerViewController = controller
        AVPlayerManager.shared.playerViewControllerDelegate = context.coordinator

        // GH #11: side-loaded captions. AVPlayerViewController has a built-in
        // contentOverlayView that sits *above* the video surface and *below*
        // the playback controls, which is exactly where subtitles belong.
        // The hosted SwiftUI view observes AVPlayerManager and re-renders
        // when the active cue changes.
        attachCaptionsOverlay(to: controller)

        return controller
    }

    /// Mount a SwiftUI captions overlay onto the AVPlayerViewController's
    /// content overlay so it sits above the video but under the controls.
    private func attachCaptionsOverlay(to controller: AVPlayerViewController) {
        guard let overlayHost = controller.contentOverlayView else { return }
        let hosting = UIHostingController(rootView: CaptionsOverlay(playerManager: AVPlayerManager.shared))
        hosting.view.backgroundColor = .clear
        hosting.view.translatesAutoresizingMaskIntoConstraints = false
        // Don't let the hosting view intercept the player's touch / focus
        // handling — the overlay itself already sets allowsHitTesting(false).
        hosting.view.isUserInteractionEnabled = false

        overlayHost.addSubview(hosting.view)
        NSLayoutConstraint.activate([
            hosting.view.leadingAnchor.constraint(equalTo: overlayHost.leadingAnchor),
            hosting.view.trailingAnchor.constraint(equalTo: overlayHost.trailingAnchor),
            hosting.view.topAnchor.constraint(equalTo: overlayHost.topAnchor),
            hosting.view.bottomAnchor.constraint(equalTo: overlayHost.bottomAnchor),
        ])
    }

    func updateUIViewController(_ uiViewController: AVPlayerViewController, context: Context) {
        // Update player if it changed
        if uiViewController.player !== player {
            uiViewController.player = player
        }

        // Update playback controls visibility
        uiViewController.showsPlaybackControls = showsPlaybackControls

        #if os(tvOS)
        // This runs on every body evaluation of the host view, and the host
        // observes AVPlayerManager — whose `currentTime` republishes twice a
        // second during playback. Count the calls so the log shows how much
        // churn the player is actually under, but only touch AVKit when
        // something genuinely changed: reassigning `transportBarCustomMenuItems`
        // rebuilds the bar and resets focus.
        context.coordinator.updateCount += 1
        if context.coordinator.updateCount % 20 == 0 {
            TVLog.log(.ui, "updateUIViewController called \(context.coordinator.updateCount)x, transportBar reassigned \(context.coordinator.transportBarAssignments)x")
        }

        // Compare by object identity. UIAction/UIMenu are NSObject subclasses
        // whose `hashValue` is identity-based anyway, so `===` says the same
        // thing without pretending the comparison is value-based.
        let itemsChanged = !context.coordinator.lastCustomMenuItems.elementsEqual(customMenuItems, by: { $0 === $1 })

        if itemsChanged {
            uiViewController.transportBarCustomMenuItems = customMenuItems
            context.coordinator.lastCustomMenuItems = customMenuItems
            context.coordinator.transportBarAssignments += 1
            TVLog.log(.transport, "transportBarCustomMenuItems reassigned (\(customMenuItems.count) items, assignment #\(context.coordinator.transportBarAssignments))")
        }

        // Contextual actions are time-based, not state-based. Assigning them
        // unconditionally on every update poked AVKit twice a second for no
        // reason, so skip when nothing moved.
        if !uiViewController.contextualActions.elementsEqual(contextualActions, by: { $0 === $1 }) {
            uiViewController.contextualActions = contextualActions
        }
        #endif
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    class Coordinator: NSObject, AVPlayerViewControllerDelegate {
        var parent: CustomVideoPlayer
        var lastCustomMenuItems: [UIMenuElement] = []
        var updateCount: Int = 0
        var transportBarAssignments: Int = 0

        init(_ parent: CustomVideoPlayer) {
            self.parent = parent

        }

        #if os(tvOS)
        // MARK: - Transport bar visibility
        //
        // The only public signal for "are the playback controls on screen
        // right now". We mirror it onto AVPlayerManager so the SwiftUI layer
        // can reason about what a Menu press means, and log the transitions
        // because they're the thing a Menu press is supposed to change.
        func playerViewController(
            _ playerViewController: AVPlayerViewController,
            willTransitionToVisibilityOfTransportBar visible: Bool,
            with coordinator: any AVPlayerViewControllerAnimationCoordinator
        ) {
            MainActor.assumeIsolated {
                AVPlayerManager.shared.isTransportBarVisible = visible
            }
            TVLog.log(.transport, "transport bar -> \(visible ? "VISIBLE" : "hidden")")
        }
        #endif

        // Prevent the player view controller from being dismissed when PiP starts
        // This keeps the VideoPlayerView alive in the navigation stack so it can be restored later
        func playerViewControllerShouldAutomaticallyDismissAtPictureInPictureStart(_ playerViewController: AVPlayerViewController) -> Bool {
            return false  // Keep the view alive during PiP
        }

        func playerViewControllerWillStartPictureInPicture(_ playerViewController: AVPlayerViewController) {
            print("🎬 PiP: Starting Picture in Picture")
            Task { @MainActor in
                AVPlayerManager.shared.isPIPActive = true
                AVPlayerManager.shared.hasPIPSession = true
            }
        }

        func playerViewControllerDidStartPictureInPicture(_ playerViewController: AVPlayerViewController) {
            // PiP started successfully
        }

        func playerViewControllerWillStopPictureInPicture(_ playerViewController: AVPlayerViewController) {
            // PiP about to stop
        }

        func playerViewControllerDidStopPictureInPicture(_ playerViewController: AVPlayerViewController) {
            print("🎬 PiP: Stopped Picture in Picture")
            Task { @MainActor in
                AVPlayerManager.shared.isPIPActive = false
                AVPlayerManager.shared.shouldRestoreVideoPlayer = false
                // Keep stored references for potential re-entry to PiP
                // Cleanup happens only when loading a different video
            }
        }

        func playerViewController(
            _ playerViewController: AVPlayerViewController,
            restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void
        ) {
            print("🎬 PiP: Restoring user interface")

            // Signal to restore video player UI when user taps PiP window
            Task { @MainActor in
                AVPlayerManager.shared.shouldRestoreVideoPlayer = true

                // Give the navigation system time to respond
                try? await Task.sleep(nanoseconds: 100_000_000) // 0.1 seconds

                completionHandler(true)
            }
        }

        func playerViewController(_ playerViewController: AVPlayerViewController, failedToStartPictureInPictureWithError error: Error) {
            print("🎬 PiP: Failed to start - \(error.localizedDescription)")
        }

        #if !os(tvOS)
        // MARK: - Fullscreen transitions

        // Flip the flag ON at willBegin and KEEP it true until the user
        // actually leaves fullscreen — SwiftUI's `.onDisappear` on the host
        // VideoPlayerView fires *after* the enter-transition animation
        // completes (confirmed by logs), so clearing the flag at "enter
        // complete" was too early and let the GH #40 teardown nuke the
        // player mid-fullscreen → black screen + PlayerRemoteXPC -12860.
        func playerViewController(
            _ playerViewController: AVPlayerViewController,
            willBeginFullScreenPresentationWithAnimationCoordinator coordinator: UIViewControllerTransitionCoordinator
        ) {
            MainActor.assumeIsolated {
                AVPlayerManager.shared.isInFullScreenTransition = true
            }
            // Intentionally NOT clearing on enter-complete — the flag must
            // stay true for the whole fullscreen session.
        }

        // AVPlayerViewController often pauses the player when leaving
        // fullscreen. If the user was playing when they exited, restart
        // playback after the transition. Also force the device back to
        // portrait so the AVPlayerViewController's built-in close button
        // acts as a "back to portrait" toggle when system rotation lock is
        // engaged (otherwise the inline view would render rotated).
        func playerViewController(
            _ playerViewController: AVPlayerViewController,
            willEndFullScreenPresentationWithAnimationCoordinator coordinator: UIViewControllerTransitionCoordinator
        ) {
            // Capture state synchronously — we're already on the main thread
            // when UIKit fires this delegate, but the compiler needs us to
            // tell it explicitly that AVPlayerManager touches are main-isolated.
            let wasPlaying = MainActor.assumeIsolated { AVPlayerManager.shared.isPlaying }

            coordinator.animate(alongsideTransition: nil) { _ in
                MainActor.assumeIsolated {
                    if wasPlaying {
                        AVPlayerManager.shared.play()
                    }
                    AVPlayerManager.shared.forcePortrait()
                    AVPlayerManager.shared.isInFullScreenTransition = false
                }
            }
        }
        #endif
    }
}
