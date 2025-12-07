//
//  LiquidGlassStyles.swift
//  FloatNative
//
//  iOS 26 Liquid Glass reusable styles and modifiers
//

import SwiftUI

// MARK: - Glass Effect Modifiers

extension View {
    /// Applies a basic liquid glass effect using ultraThinMaterial
    func liquidGlass() -> some View {
        self
            .background(.ultraThinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    /// Applies a tinted liquid glass effect
    func liquidGlass(tint: Color, opacity: Double = 0.8) -> some View {
        self
            .background(.ultraThinMaterial)
            .background(tint.opacity(opacity * 0.3))
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    /// Applies an interactive liquid glass effect
    func liquidGlassInteractive(tint: Color = .blue, opacity: Double = 0.8) -> some View {
        self
            .background(.ultraThinMaterial)
            .background(tint.opacity(opacity * 0.3))
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - Button Styles

struct LiquidGlassButtonStyle: ButtonStyle {
    var tint: Color = .blue
    var opacity: Double = 0.8

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .liquidGlassInteractive(tint: tint, opacity: opacity)
            .scaleEffect(configuration.isPressed ? 0.95 : 1.0)
            .animation(.easeInOut(duration: 0.1), value: configuration.isPressed)
    }
}

struct LiquidGlassProminentButtonStyle: ButtonStyle {
    var tint: Color = .blue

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(tint)
            .foregroundColor(.white)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .scaleEffect(configuration.isPressed ? 0.95 : 1.0)
            .animation(.easeInOut(duration: 0.1), value: configuration.isPressed)
    }
}

// MARK: - Glass Container

struct GlassContainer<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
    }
}

// MARK: - Floatplane Brand Colors

extension Color {
    static let floatplaneBlue = Color(red: 0.0, green: 0.59, blue: 1.0) // #0096FF
    static let floatplaneDark = Color(red: 0.11, green: 0.11, blue: 0.11) // #1C1C1C
    static let floatplaneLight = Color(red: 0.95, green: 0.95, blue: 0.97) // #F2F2F7 (iOS light background)
    static let floatplaneGray = Color(red: 0.58, green: 0.58, blue: 0.58) // #949494

    // MARK: - Adaptive Colors (theme-aware)

    /// Background color that adapts to light/dark mode
    static var adaptiveBackground: Color {
        #if os(tvOS)
        Color.black
        #else
        Color(uiColor: .systemBackground)
        #endif
    }

    /// Primary text color that adapts to light/dark mode
    static var adaptiveText: Color {
        Color(uiColor: .label)
    }

    /// Secondary text color that adapts to light/dark mode
    static var adaptiveSecondaryText: Color {
        Color(uiColor: .secondaryLabel)
    }

    /// Tertiary/gray text color that adapts to light/dark mode
    static var adaptiveTertiaryText: Color {
        Color(uiColor: .tertiaryLabel)
    }

    /// Secondary background color (for cards, sections) that adapts to light/dark mode
    static var adaptiveSecondaryBackground: Color {
        #if os(tvOS)
        Color.gray.opacity(0.15)
        #else
        Color(uiColor: .secondarySystemBackground)
        #endif
    }

    /// Tertiary background color (for grouped content) that adapts to light/dark mode
    static var adaptiveTertiaryBackground: Color {
        #if os(tvOS)
        Color.gray.opacity(0.1)
        #else
        Color(uiColor: .tertiarySystemBackground)
        #endif
    }
}

// MARK: - Preview Helpers

#Preview("Liquid Glass Buttons") {
    VStack(spacing: 20) {
        Button("Regular Glass Button") {
            print("Tapped")
        }
        .buttonStyle(LiquidGlassButtonStyle())

        Button("Prominent Glass Button") {
            print("Tapped")
        }
        .buttonStyle(LiquidGlassProminentButtonStyle(tint: .floatplaneBlue))

        Button {
            print("Tapped")
        } label: {
            HStack {
                Image(systemName: "play.fill")
                Text("Play Video")
            }
        }
        .buttonStyle(LiquidGlassButtonStyle(tint: .floatplaneBlue))
    }
    .padding()
    .background(Color.floatplaneDark)
}
