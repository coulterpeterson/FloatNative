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
    /// Adds the global account menu to the navigation bar
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
                            Label("Search", systemImage: "magnifyingglass")
                                .font(.title3)
                        }
                        .tint(Color.adaptiveText)
                    }

                    Menu {
                        // iPad has a Downloads tab instead.
                        if UIDevice.current.userInterfaceIdiom != .pad {
                            Button {
                                tabCoordinator.showDownloads()
                            } label: {
                                Label("Downloads", systemImage: "arrow.down.circle.fill")
                            }
                        }

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
                        // Not an ellipsis: the system uses that for toolbar overflow,
                        // and on iPhone Duo this menu can sit right beside it.
                        Label("Account", systemImage: "person.crop.circle")
                            .font(.title3)
                    }
                    .tint(Color.adaptiveText)
                }
            }
            .sheet(isPresented: $showSearch) {
                SearchView()
            }
            #endif
    }
}
