//
//  MainTabView.swift
//  FloatNative
//
//  iOS 26 Liquid Glass 2-tab navigation with floating search
//

import SwiftUI

// MARK: - Navigation Destination

enum NavigationDestination: Hashable {
    case history
    case settings
}

struct MainTabView: View {
    @StateObject private var tabCoordinator = TabCoordinator()
    @StateObject private var playerManager = AVPlayerManager.shared

    var body: some View {
        #if os(tvOS)
        tvOSTabView
            .onChange(of: playerManager.shouldRestoreVideoPlayer) { oldValue, newValue in
                if newValue, let post = playerManager.currentPost {
                    handlePiPRestore(post: post)
                }
            }
        #else
        if #available(iOS 18, *) {
            modernTabView
                .onChange(of: playerManager.shouldRestoreVideoPlayer) { oldValue, newValue in
                    if newValue, let post = playerManager.currentPost {
                        handlePiPRestore(post: post)
                    }
                }
        } else {
            legacyTabView
                .onChange(of: playerManager.shouldRestoreVideoPlayer) { oldValue, newValue in
                    if newValue, let post = playerManager.currentPost {
                        handlePiPRestore(post: post)
                    }
                }
        }
        #endif
    }

    private func handlePiPRestore(post: BlogPost) {
        // Navigate to the video from PiP restoration
        tabCoordinator.navigateToVideo(post)
    }

    @available(iOS 18, *)
    private var modernTabView: some View {
        TabView(selection: $tabCoordinator.selectedTab) {
            Tab("Home", systemImage: "house.fill", value: 0) {
                NavigationStack(path: $tabCoordinator.path) {
                    VideoFeedView()
                        .navigationDestination(for: BlogPost.self) { post in
                            #if os(tvOS)
                            VideoPlayerTvosView(post: post)
                            #else
                            VideoPlayerView(post: post)
                            #endif
                        }
                        .navigationDestination(for: NavigationDestination.self) { destination in
                            switch destination {
                            case .history:
                                HistoryView()
                            case .settings:
                                SettingsView()
                            }
                        }
                }
            }

            Tab("Creators", systemImage: "person.2.fill", value: 1) {
                NavigationStack(path: $tabCoordinator.path) {
                    CreatorsView()
                        .navigationDestination(for: FeedFilter.self) { filter in
                            VideoFeedView(filter: filter)
                        }
                        .navigationDestination(for: BlogPost.self) { post in
                            #if os(tvOS)
                            VideoPlayerTvosView(post: post)
                            #else
                            VideoPlayerView(post: post)
                            #endif
                        }
                        .navigationDestination(for: NavigationDestination.self) { destination in
                            switch destination {
                            case .history:
                                HistoryView()
                            case .settings:
                                SettingsView()
                            }
                        }
                }
            }

            Tab("Playlists", systemImage: "list.bullet.rectangle", value: 2) {
                NavigationStack(path: $tabCoordinator.path) {
                    PlaylistsView()
                        .navigationDestination(for: BlogPost.self) { post in
                            #if os(tvOS)
                            VideoPlayerTvosView(post: post)
                            #else
                            VideoPlayerView(post: post)
                            #endif
                        }
                        .navigationDestination(for: NavigationDestination.self) { destination in
                            switch destination {
                            case .history:
                                HistoryView()
                            case .settings:
                                SettingsView()
                            }
                        }
                }
            }

            // Only show Search tab on iPhone (hidden on iPad)
            if UIDevice.current.userInterfaceIdiom != .pad {
                Tab("Search", systemImage: "magnifyingglass", value: 3, role: .search) {
                    SearchView()
                }
            }
        }
        .tint(Color.floatplaneBlue)
        .applyLiquidGlassTabBar()
        .environmentObject(tabCoordinator)
        .onChange(of: tabCoordinator.selectedTab) { oldTab, newTab in
            // Clear navigation path when switching tabs to prevent navigation state conflicts
            if oldTab != newTab {
                tabCoordinator.path = NavigationPath()
            }
        }
    }

    private var legacyTabView: some View {
        TabView(selection: $tabCoordinator.selectedTab) {
            NavigationStack(path: $tabCoordinator.path) {
                VideoFeedView()
                    .navigationDestination(for: BlogPost.self) { post in
                        VideoPlayerTvosView(post: post)
                    }
                    .navigationDestination(for: NavigationDestination.self) { destination in
                        switch destination {
                        case .history:
                            HistoryView()
                        case .settings:
                            SettingsView()
                        }
                    }
            }
            .tabItem {
                Label("Home", systemImage: "house.fill")
            }
            .tag(0)

            NavigationStack(path: $tabCoordinator.path) {
                CreatorsView()
                    .navigationDestination(for: FeedFilter.self) { filter in
                        VideoFeedView(filter: filter)
                    }
                    .navigationDestination(for: BlogPost.self) { post in
                        VideoPlayerTvosView(post: post)
                    }
                    .navigationDestination(for: NavigationDestination.self) { destination in
                        switch destination {
                        case .history:
                            HistoryView()
                        case .settings:
                            SettingsView()
                        }
                    }
            }
            .tabItem {
                Label("Creators", systemImage: "person.2.fill")
            }
            .tag(1)

            NavigationStack(path: $tabCoordinator.path) {
                PlaylistsView()
                    .navigationDestination(for: BlogPost.self) { post in
                        VideoPlayerTvosView(post: post)
                    }
                    .navigationDestination(for: NavigationDestination.self) { destination in
                        switch destination {
                        case .history:
                            HistoryView()
                        case .settings:
                            SettingsView()
                        }
                    }
            }
            .tabItem {
                Label("Playlists", systemImage: "list.bullet.rectangle")
            }
            .tag(2)

            // Only show Search tab on iPhone (hidden on iPad)
            if UIDevice.current.userInterfaceIdiom != .pad {
                NavigationStack {
                    SearchView()
                }
                .tabItem {
                    Label("Search", systemImage: "magnifyingglass")
                }
                .tag(3)
            }
        }
        .tint(Color.floatplaneBlue)
        .environmentObject(tabCoordinator)
        .onChange(of: tabCoordinator.selectedTab) { oldTab, newTab in
            // Clear navigation path when switching tabs to prevent navigation state conflicts
            if oldTab != newTab {
                tabCoordinator.path = NavigationPath()
            }
        }
    }

    // tvOS-specific tab view with 5 tabs (includes History and Settings)
    private var tvOSTabView: some View {
        TabView(selection: $tabCoordinator.selectedTab) {
            NavigationStack(path: $tabCoordinator.path) {
                VideoFeedView()
                    .navigationDestination(for: BlogPost.self) { post in
                        VideoPlayerTvosView(post: post)
                    }
                    .navigationDestination(for: NavigationDestination.self) { destination in
                        switch destination {
                        case .history:
                            HistoryView()
                        case .settings:
                            SettingsView()
                        }
                    }
            }
            .tabItem {
                Label("Home", systemImage: "house.fill")
            }
            .tag(0)

            NavigationStack(path: $tabCoordinator.path) {
                CreatorsView()
                    .navigationDestination(for: FeedFilter.self) { filter in
                        VideoFeedView(filter: filter)
                    }
                    .navigationDestination(for: BlogPost.self) { post in
                        VideoPlayerTvosView(post: post)
                    }
                    .navigationDestination(for: NavigationDestination.self) { destination in
                        switch destination {
                        case .history:
                            HistoryView()
                        case .settings:
                            SettingsView()
                        }
                    }
            }
            .tabItem {
                Label("Creators", systemImage: "person.2.fill")
            }
            .tag(1)

            NavigationStack(path: $tabCoordinator.path) {
                PlaylistsView()
                    .navigationDestination(for: BlogPost.self) { post in
                        VideoPlayerTvosView(post: post)
                    }
                    .navigationDestination(for: NavigationDestination.self) { destination in
                        switch destination {
                        case .history:
                            HistoryView()
                        case .settings:
                            SettingsView()
                        }
                    }
            }
            .tabItem {
                Label("Playlists", systemImage: "list.bullet.rectangle")
            }
            .tag(2)

            NavigationStack {
                SearchView()
            }
            .tabItem {
                Label("Search", systemImage: "magnifyingglass")
            }
            .tag(3)

            NavigationStack(path: $tabCoordinator.path) {
                HistoryView()
                    .navigationDestination(for: NavigationDestination.self) { destination in
                        switch destination {
                        case .history:
                            HistoryView()
                        case .settings:
                            SettingsView()
                        }
                    }
            }
            .tabItem {
                Label("History", systemImage: "clock.fill")
            }
            .tag(4)

            NavigationStack(path: $tabCoordinator.path) {
                SettingsView()
                    .navigationDestination(for: NavigationDestination.self) { destination in
                        switch destination {
                        case .history:
                            HistoryView()
                        case .settings:
                            SettingsView()
                        }
                    }
            }
            .tabItem {
                Label("Settings", systemImage: "gearshape.fill")
            }
            .tag(5)
        }
        .tint(Color.floatplaneBlue)
        .environmentObject(tabCoordinator)
        .onChange(of: tabCoordinator.selectedTab) { oldTab, newTab in
            // Clear navigation path when switching tabs to prevent navigation state conflicts
            if oldTab != newTab {
                tabCoordinator.path = NavigationPath()
            }
        }
    }
}

// iOS Liquid Glass Tab Bar Extensions (for future use)
extension View {
    func applyLiquidGlassTabBar() -> some View {
        self
    }
}

#Preview {
    MainTabView()
}
