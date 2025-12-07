//
//  ThemeManager.swift
//  FloatNative
//
//  Theme management with persistence
//

import SwiftUI

// MARK: - Theme Mode

enum ThemeMode: String, CaseIterable, Hashable {
    case light
    case dark
    case system

    var displayName: String {
        switch self {
        case .light: return "Light"
        case .dark: return "Dark"
        case .system: return "System"
        }
    }

    var icon: String {
        switch self {
        case .light: return "sun.max.fill"
        case .dark: return "moon.fill"
        case .system: return "circle.lefthalf.filled"
        }
    }
}

// MARK: - Theme Manager

class ThemeManager: ObservableObject {
    static let shared = ThemeManager()

    @AppStorage("appTheme") var currentTheme: ThemeMode = .dark

    private init() {}
}
