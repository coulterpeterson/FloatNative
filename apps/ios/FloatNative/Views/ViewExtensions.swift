//
//  ViewExtensions.swift
//  FloatNative
//
//  Reusable view modifiers and extensions
//

import SwiftUI

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
