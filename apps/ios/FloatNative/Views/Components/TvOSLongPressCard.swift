//
//  TvOSLongPressCard.swift
//  FloatNative
//
//  UIKit-based wrapper for tvOS that detects long-press gestures
//  Uses UIHostingConfiguration (tvOS 16+) for proper SwiftUI embedding
//

import SwiftUI
import UIKit

#if os(tvOS)
// Custom UIButton that handles tvOS focus with visual feedback
class FocusableCardButton: UIButton {
    override func didUpdateFocus(in context: UIFocusUpdateContext, with coordinator: UIFocusAnimationCoordinator) {
        super.didUpdateFocus(in: context, with: coordinator)

        coordinator.addCoordinatedAnimations {
            if self.isFocused {
                // Scale up and add shadow when focused (mimics .card button style)
                self.transform = CGAffineTransform(scaleX: 1.05, y: 1.05)
                self.layer.shadowColor = UIColor.white.cgColor
                self.layer.shadowRadius = 15
                self.layer.shadowOpacity = 0.8
                self.layer.shadowOffset = CGSize(width: 0, height: 5)
            } else {
                // Reset when not focused
                self.transform = .identity
                self.layer.shadowOpacity = 0
            }
        }
    }
}

struct TvOSLongPressCard<Content: View>: UIViewRepresentable {
    let content: Content
    let width: CGFloat
    let progress: Double?
    let onTap: () -> Void
    let onLongPress: () -> Void

    init(width: CGFloat, progress: Double?, onTap: @escaping () -> Void, onLongPress: @escaping () -> Void, @ViewBuilder content: () -> Content) {
        self.width = width
        self.progress = progress
        self.onTap = onTap
        self.onLongPress = onLongPress
        self.content = content()
    }

    func makeUIView(context: Context) -> FocusableCardButton {
        // Calculate height based on 16:9 aspect ratio
        let height = width / (16.0 / 9.0)

        // Use UIHostingConfiguration to create a content view
        // This properly handles all view controller lifecycle for SwiftUI content
        let configuration = UIHostingConfiguration {
            content
        }
        .margins(.all, 0) // Remove default margins

        // Create the hosting content view
        let contentView = configuration.makeContentView()
        contentView.translatesAutoresizingMaskIntoConstraints = false

        // Create FocusableCardButton as container (handles focus visual feedback)
        let button = FocusableCardButton(type: .custom)
        button.backgroundColor = .clear
        button.clipsToBounds = false // Allow shadow to show

        // Add the hosting content view
        button.addSubview(contentView)

        // Create and store size constraints for animation
        let widthConstraint = button.widthAnchor.constraint(equalToConstant: width)
        let heightConstraint = button.heightAnchor.constraint(equalToConstant: height)

        // Set explicit size constraints
        button.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            widthConstraint,
            heightConstraint,
            contentView.topAnchor.constraint(equalTo: button.topAnchor),
            contentView.leadingAnchor.constraint(equalTo: button.leadingAnchor),
            contentView.trailingAnchor.constraint(equalTo: button.trailingAnchor),
            contentView.bottomAnchor.constraint(equalTo: button.bottomAnchor)
        ])

        // Regular tap - triggered by Select button on Apple TV remote
        button.addTarget(context.coordinator, action: #selector(Coordinator.handleTap), for: .primaryActionTriggered)

        // Long press gesture recognizer - works on tvOS!
        let longPress = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleLongPress))
        longPress.minimumPressDuration = 0.5 // 500ms hold
        longPress.allowedPressTypes = [NSNumber(value: UIPress.PressType.select.rawValue)]
        button.addGestureRecognizer(longPress)

        // Store content view, constraints, and initial width in coordinator for updates
        context.coordinator.contentView = contentView
        context.coordinator.widthConstraint = widthConstraint
        context.coordinator.heightConstraint = heightConstraint
        context.coordinator.lastWidth = width
        context.coordinator.lastProgress = progress

        return button
    }

    func updateUIView(_ uiView: FocusableCardButton, context: Context) {
        let height = width / (16.0 / 9.0)

        // Check if width or progress has changed
        let widthChanged = context.coordinator.lastWidth != width
        let progressChanged = context.coordinator.lastProgress != progress

        if widthChanged {
            // Animate constraint changes instead of rebuilding the view
            // This prevents the flash by keeping the same content view with cached images
            if let widthConstraint = context.coordinator.widthConstraint,
               let heightConstraint = context.coordinator.heightConstraint {

                // Update constraints with animation matching the sidebar transition
                // Uses 0.3s easeInOut to match VideoFeedView sidebar animation
                UIView.animate(withDuration: 0.3, delay: 0, options: .curveEaseInOut) {
                    widthConstraint.constant = width
                    heightConstraint.constant = height
                    uiView.layoutIfNeeded()
                }
            }

            // Update stored width for next comparison
            context.coordinator.lastWidth = width
        }
        
        if progressChanged {
            // Rebuild content if progress changed
            // This is necessary because UIHostingConfiguration doesn't automatically update
            // when captured SwiftUI state changes unless we explicitly tell it to
            let configuration = UIHostingConfiguration {
                content
            }
            .margins(.all, 0)

            if let oldContentView = context.coordinator.contentView {
                let newContentView = configuration.makeContentView()
                newContentView.translatesAutoresizingMaskIntoConstraints = false

                oldContentView.removeFromSuperview()
                uiView.addSubview(newContentView)

                NSLayoutConstraint.activate([
                    newContentView.topAnchor.constraint(equalTo: uiView.topAnchor),
                    newContentView.leadingAnchor.constraint(equalTo: uiView.leadingAnchor),
                    newContentView.trailingAnchor.constraint(equalTo: uiView.trailingAnchor),
                    newContentView.bottomAnchor.constraint(equalTo: uiView.bottomAnchor)
                ])

                context.coordinator.contentView = newContentView
            }
            context.coordinator.lastProgress = progress
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(onTap: onTap, onLongPress: onLongPress)
    }

    class Coordinator: NSObject {
        let onTap: () -> Void
        let onLongPress: () -> Void
        var contentView: UIView?
        var widthConstraint: NSLayoutConstraint?
        var heightConstraint: NSLayoutConstraint?
        var lastWidth: CGFloat?
        var lastProgress: Double?

        init(onTap: @escaping () -> Void, onLongPress: @escaping () -> Void) {
            self.onTap = onTap
            self.onLongPress = onLongPress
        }

        @objc func handleTap() {
            onTap()
        }

        @objc func handleLongPress(gesture: UILongPressGestureRecognizer) {
            // Only trigger once when gesture begins
            if gesture.state == .began {
                onLongPress()
            }
        }
    }
}
#endif
