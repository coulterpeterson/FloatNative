//
//  TabCoordinator.swift
//  FloatNative
//
//  Manages tab selection and navigation state across the app
//

import SwiftUI

@MainActor
class TabCoordinator: ObservableObject {
    @Published var selectedTab: Int = 0
    @Published var path = NavigationPath()

    /// Navigate to a video player from anywhere in the app
    /// Used for PiP restoration and deep linking
    func navigateToVideo(_ post: BlogPost) {
        // Push the video onto the current tab's navigation stack
        path.append(post)
    }

    #if !os(tvOS)
    /// Tab index of Downloads on iPad, where it has a tab of its own. iPhone has
    /// Search in that slot and reaches Downloads through the account menu.
    static let iPadDownloadsTab = 3
    private static let iPhoneSearchTab = 3

    func showDownloads() {
        if UIDevice.current.userInterfaceIdiom == .pad {
            selectedTab = Self.iPadDownloadsTab
        } else {
            push(.downloads)
        }
    }

    func play(_ video: LocalVideo, replacingCurrent: Bool = false) {
        if replacingCurrent, !path.isEmpty {
            path.removeLast()
        }
        push(.downloadPlayer(video))
    }

    /// Search has its own navigation stack, so on that tab this goes through Home.
    private func push(_ destination: NavigationDestination) {
        guard selectedTab == Self.iPhoneSearchTab, UIDevice.current.userInterfaceIdiom != .pad else {
            path.append(destination)
            return
        }
        selectedTab = 0
        // Switching tabs clears the path, so wait for that to happen.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            self.path.append(destination)
        }
    }
    #endif

    /// Clear the navigation stack
    func popToRoot() {
        print("📍 TabCoordinator: Popping to root")
        path = NavigationPath()
    }
}
