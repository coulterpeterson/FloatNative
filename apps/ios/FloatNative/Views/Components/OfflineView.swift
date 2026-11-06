//
//  OfflineView.swift
//  FloatNative
//
//  What a screen that needs the internet shows when there's no connection and
//  nothing loaded: a way to the videos that do work, not an error.
//

import SwiftUI

extension View {
    /// Shows the offline page in place of this screen while there's no connection
    /// and `hasContent` is false, then calls `onReconnect` when the connection
    /// returns. Content already on screen stays usable. Does nothing on tvOS.
    ///
    /// - Parameters:
    /// - Parameter postId: The post this screen is for, to offer its download if there is one.
    @ViewBuilder
    func offlineFallback(
        hasContent: Bool,
        postId: String? = nil,
        onReconnect: @escaping () async -> Void
    ) -> some View {
        #if os(tvOS)
        self
        #else
        modifier(OfflineFallback(
            hasContent: hasContent,
            postId: postId,
            onReconnect: onReconnect
        ))
        #endif
    }
}

#if !os(tvOS)
extension EnvironmentValues {
    /// Closes Search for a screen inside it. Search has its own navigation stack, and
    /// on iPad it's a sheet, so the offline page has to leave it to go anywhere.
    @Entry var leaveSearch: (() -> Void)? = nil
}

private struct OfflineFallback: ViewModifier {
    let hasContent: Bool
    let postId: String?
    let onReconnect: () async -> Void

    @ObservedObject private var network = NetworkMonitor.shared

    func body(content: Content) -> some View {
        content
            // Fills the screen so the offline page does too, whatever this screen's size.
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .overlay {
                if !network.isOnline && !hasContent {
                    OfflineView(postId: postId)
                }
            }
            .onChange(of: network.isOnline) { _, isOnline in
                guard isOnline, !hasContent else { return }
                Task { await onReconnect() }
            }
    }
}

struct OfflineView: View {
    var postId: String? = nil

    @ObservedObject private var library = DownloadLibrary.shared
    @EnvironmentObject private var tabCoordinator: TabCoordinator
    @Environment(\.leaveSearch) private var leaveSearch

    var body: some View {
        ZStack {
            Color.adaptiveBackground
                .ignoresSafeArea()

            VStack(spacing: 20) {
                Image(systemName: "wifi.slash")
                    .font(.system(size: 40, weight: .medium))
                    .foregroundStyle(Color.floatplaneBlue)
                    .frame(width: 96, height: 96)
                    .background(Color.floatplaneBlue.opacity(0.15), in: Circle())

                VStack(spacing: 8) {
                    Text("You're Offline")
                        .font(.title2)
                        .fontWeight(.semibold)
                        .foregroundStyle(Color.adaptiveText)

                    Text(message)
                        .font(.subheadline)
                        .foregroundStyle(Color.adaptiveSecondaryText)
                        .multilineTextAlignment(.center)
                }

                VStack(spacing: 12) {
                    if let downloadedVideo {
                        Button {
                            leaveSearch?()
                            // Back shouldn't return to a player with nothing to show.
                            // In Search that screen isn't on the path, so there it stays.
                            tabCoordinator.play(downloadedVideo, replacingCurrent: leaveSearch == nil)
                        } label: {
                            buttonLabel("Play Downloaded Video", systemImage: "play.fill")
                        }
                        .buttonStyle(.borderedProminent)

                        Button {
                            goToDownloads()
                        } label: {
                            buttonLabel("Go to Downloads", systemImage: "arrow.down.circle.fill")
                        }
                        .buttonStyle(.bordered)
                    } else {
                        Button {
                            goToDownloads()
                        } label: {
                            buttonLabel("Go to Downloads", systemImage: "arrow.down.circle.fill")
                        }
                        .buttonStyle(.borderedProminent)
                    }
                }
                .tint(.floatplaneBlue)
                .buttonBorderShape(.capsule)
                .controlSize(.large)
            }
            .frame(maxWidth: 340)
            .padding(24)
            .messageAvoidingFold()
        }
    }

    private func buttonLabel(_ title: String, systemImage: String) -> some View {
        Label(title, systemImage: systemImage)
            .fontWeight(.semibold)
            .frame(maxWidth: .infinity)
    }

    private func goToDownloads() {
        leaveSearch?()
        tabCoordinator.showDownloads()
    }

    private var downloadedVideo: LocalVideo? {
        guard let postId else { return nil }
        return library.videos.first { $0.metadata?.postId == postId }
    }

    private var message: String {
        if downloadedVideo != nil {
            return "You've downloaded this video, so you can watch it without a connection."
        }
        switch library.videos.count {
        case 0:
            return "This page will load when you're back online. Videos you download will be ready to watch anytime."
        case 1:
            return "This page will load when you're back online. In the meantime, you have 1 downloaded video ready to watch."
        case let count:
            return "This page will load when you're back online. In the meantime, you have \(count) downloaded videos ready to watch."
        }
    }
}
#endif
