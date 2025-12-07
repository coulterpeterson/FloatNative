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

    /// Clear the navigation stack
    func popToRoot() {
        print("📍 TabCoordinator: Popping to root")
        path = NavigationPath()
    }
}
