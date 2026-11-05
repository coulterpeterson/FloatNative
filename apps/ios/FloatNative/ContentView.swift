//
//  ContentView.swift
//  FloatNative
//
//  Created by Coulter Peterson on 2025-10-08.
//

import SwiftUI

struct ContentView: View {
    @StateObject private var api = FloatplaneAPI.shared
    @ObservedObject private var themeManager = ThemeManager.shared
    @ObservedObject private var whatsNew = WhatsNewService.shared
    @State private var showLogin = false

    var body: some View {
        Group {
            if api.isAuthenticated {
                MainTabView()
                    .reviewPromptWhenDue()
            } else {
                LoginView()
            }
        }
        .preferredColorScheme(resolvedColorScheme)
        .onAppear {
            // Check if already authenticated
            if !api.isAuthenticated {
                showLogin = true
            }
            // Fire What's New if this is an upgraded launch (no-op on fresh
            // installs — see WhatsNewService).
            whatsNew.presentIfNeeded()
        }
        .sheet(isPresented: $whatsNew.isPresented) {
            WhatsNewSheet()
        }
    }

    private var resolvedColorScheme: ColorScheme? {
        switch themeManager.currentTheme {
        case .light:
            return .light
        case .dark:
            return .dark
        case .system:
            return nil
        }
    }
}

#Preview {
    ContentView()
}
