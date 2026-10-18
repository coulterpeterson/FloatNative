//
//  ViewExtensions.swift
//  FloatNative
//
//  Reusable view modifiers and extensions
//

import SwiftUI

// MARK: - Opening the video player

/// Opens a post's player. iOS pushes it (NavigationLink + the screen's
/// `navigationDestination(for: BlogPost.self)`); tvOS sets `presented`, which
/// `tvOSPlayerCover(_:)` shows full screen. Pushed inside the tvOS TabView,
/// the hidden tab bar kept focus for seconds after the player appeared, and a
/// Menu press in that window suspended the app.
struct PostPlayerLink<Label: View>: View {
    let post: BlogPost
    @Binding var presented: BlogPost?
    @ViewBuilder let label: () -> Label

    var body: some View {
        #if os(tvOS)
        Button { presented = post } label: { label() }
        #else
        NavigationLink(value: post) { label() }
        #endif
    }
}

extension View {
    /// tvOS: present the player for `post` full screen. No-op on iOS, which
    /// pushes via `PostPlayerLink` instead.
    @ViewBuilder
    func tvOSPlayerCover(_ post: Binding<BlogPost?>) -> some View {
        #if os(tvOS)
        fullScreenCover(item: post) { VideoPlayerTvosView(post: $0) }
        #else
        self
        #endif
    }
}

// MARK: - Global Menu Modifier

extension View {
    /// Adds the global 3-dot menu to the navigation bar
    func globalMenu() -> some View {
        self.modifier(GlobalMenuModifier())
    }
}

struct GlobalMenuModifier: ViewModifier {
    @EnvironmentObject var tabCoordinator: TabCoordinator
    @State private var showSearch = false

    func body(content: Content) -> some View {
        content
            #if !os(tvOS)
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    // On iPad, show search button next to the menu
                    if UIDevice.current.userInterfaceIdiom == .pad {
                        Button {
                            showSearch = true
                        } label: {
                            Image(systemName: "magnifyingglass")
                                .foregroundColor(Color.adaptiveText)
                                .font(.title3)
                        }
                    }

                    Menu {
                        Button {
                            tabCoordinator.path.append(NavigationDestination.history)
                        } label: {
                            Label("Watch History", systemImage: "clock.fill")
                        }

                        Button {
                            tabCoordinator.path.append(NavigationDestination.settings)
                        } label: {
                            Label("Settings", systemImage: "gearshape.fill")
                        }
                    } label: {
                        Image(systemName: "ellipsis")
                            .foregroundColor(Color.adaptiveText)
                            .font(.title3)
                    }
                }
            }
            .sheet(isPresented: $showSearch) {
                SearchView()
            }
            #endif
    }
}
