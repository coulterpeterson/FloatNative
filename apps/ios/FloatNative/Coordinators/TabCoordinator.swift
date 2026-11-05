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
    /// Search in that slot and reaches Downloads through the 3-dot menu.
    static let iPadDownloadsTab = 3

    func showDownloads() {
        if UIDevice.current.userInterfaceIdiom == .pad {
            selectedTab = Self.iPadDownloadsTab
        } else {
            path.append(NavigationDestination.downloads)
        }
    }
    #endif

    /// Clear the navigation stack
    func popToRoot() {
        print("📍 TabCoordinator: Popping to root")
        path = NavigationPath()
    }
}
