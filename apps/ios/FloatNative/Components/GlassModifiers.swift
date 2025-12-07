//
//  GlassModifiers.swift
//  FloatNative
//
//  Liquid glass UI components and modifiers
//  Created by Claude on 2025-10-08.
//

import SwiftUI

// MARK: - Glass Material Styles

enum GlassMaterial {
    case thin
    case regular
    case thick
    case ultraThin

    var blurRadius: CGFloat {
        switch self {
        case .ultraThin: return 5
        case .thin: return 10
        case .regular: return 20
        case .thick: return 30
        }
    }

    var opacity: Double {
        switch self {
        case .ultraThin: return 0.5
        case .thin: return 0.6
        case .regular: return 0.7
        case .thick: return 0.85
        }
    }
}

// MARK: - Glass Background Modifier

struct GlassBackgroundModifier: ViewModifier {
    let material: GlassMaterial
    let tint: Color
    let cornerRadius: CGFloat

    func body(content: Content) -> some View {
        content
            .background(
                ZStack {
                    // Base blur layer
                    RoundedRectangle(cornerRadius: cornerRadius)
                        .fill(tint.opacity(material.opacity * 0.3))
                        .background(.ultraThinMaterial)

                    // Gradient overlay for depth
                    RoundedRectangle(cornerRadius: cornerRadius)
                        .fill(
                            LinearGradient(
                                colors: [
                                    tint.opacity(0.2),
                                    tint.opacity(0.05)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )

                    // Subtle border
                    RoundedRectangle(cornerRadius: cornerRadius)
                        .strokeBorder(
                            LinearGradient(
                                colors: [
                                    Color.white.opacity(0.3),
                                    Color.white.opacity(0.1)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1
                        )
                }
            )
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
    }
}

// MARK: - Frosted Glass Card

struct FrostedGlassCard<Content: View>: View {
    let content: Content
    let material: GlassMaterial
    let tint: Color
    let cornerRadius: CGFloat

    init(
        material: GlassMaterial = .regular,
        tint: Color = .blue,
        cornerRadius: CGFloat = 20,
        @ViewBuilder content: () -> Content
    ) {
        self.material = material
        self.tint = tint
        self.cornerRadius = cornerRadius
        self.content = content()
    }

    var body: some View {
        content
            .modifier(GlassBackgroundModifier(
                material: material,
                tint: tint,
                cornerRadius: cornerRadius
            ))
    }
}

// MARK: - Liquid Button

struct LiquidButton: View {
    let title: String
    let icon: String?
    let action: () -> Void

    @State private var isPressed = false

    init(_ title: String, icon: String? = nil, action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon = icon {
                    Image(systemName: icon)
                        .font(.system(size: 16, weight: .semibold))
                }
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 24)
            .padding(.vertical, 12)
            .background(
                ZStack {
                    // Gradient background
                    RoundedRectangle(cornerRadius: 16)
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color.blue.opacity(0.8),
                                    Color.blue.opacity(0.6)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )

                    // Shimmer effect
                    RoundedRectangle(cornerRadius: 16)
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color.white.opacity(0.3),
                                    Color.clear,
                                    Color.white.opacity(0.3)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .blur(radius: 5)
                }
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.white.opacity(0.3), lineWidth: 1)
            )
            .scaleEffect(isPressed ? 0.95 : 1.0)
            .animation(.spring(response: 0.3, dampingFraction: 0.6), value: isPressed)
        }
        .buttonStyle(PlainButtonStyle())
        #if !os(tvOS)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in isPressed = true }
                .onEnded { _ in isPressed = false }
        )
        #endif
    }
}

// MARK: - Glass Progress Bar

struct GlassProgressBar: View {
    let progress: Double
    let height: CGFloat

    init(progress: Double, height: CGFloat = 4) {
        self.progress = progress
        self.height = height
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                // Background track
                RoundedRectangle(cornerRadius: height / 2)
                    .fill(Color.white.opacity(0.2))
                    .frame(height: height)

                // Progress fill with gradient
                RoundedRectangle(cornerRadius: height / 2)
                    .fill(
                        LinearGradient(
                            colors: [
                                Color.blue,
                                Color.purple
                            ],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .frame(width: geometry.size.width * progress, height: height)
                    .shadow(color: Color.blue.opacity(0.5), radius: 4, x: 0, y: 2)
            }
        }
        .frame(height: height)
    }
}

// MARK: - Morphing Container

struct MorphingContainer<Content: View>: View {
    let content: Content
    @State private var morphPhase: CGFloat = 0

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
            .background(
                Canvas { context, size in
                    let path = createMorphingPath(in: size, phase: morphPhase)
                    context.fill(
                        path,
                        with: .linearGradient(
                            Gradient(colors: [
                                Color.blue.opacity(0.3),
                                Color.purple.opacity(0.3)
                            ]),
                            startPoint: .zero,
                            endPoint: CGPoint(x: size.width, y: size.height)
                        )
                    )
                }
            )
            .onAppear {
                withAnimation(.easeInOut(duration: 3).repeatForever(autoreverses: true)) {
                    morphPhase = 1
                }
            }
    }

    private func createMorphingPath(in size: CGSize, phase: CGFloat) -> Path {
        var path = Path()

        let amplitude: CGFloat = 10
        let frequency: CGFloat = 2

        path.move(to: .zero)

        for x in stride(from: 0, through: size.width, by: 1) {
            let relativeX = x / size.width
            let sine = sin((relativeX + phase) * .pi * frequency)
            let y = amplitude * sine
            path.addLine(to: CGPoint(x: x, y: y))
        }

        path.addLine(to: CGPoint(x: size.width, y: size.height))
        path.addLine(to: CGPoint(x: 0, y: size.height))
        path.closeSubpath()

        return path
    }
}

// MARK: - Shimmer Effect

struct ShimmerEffect: ViewModifier {
    @State private var phase: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .overlay(
                LinearGradient(
                    gradient: Gradient(colors: [
                        .clear,
                        Color.white.opacity(0.3),
                        .clear
                    ]),
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .blur(radius: 5)
                .offset(x: -200 + (phase * 400))
                .mask(content)
            )
            .onAppear {
                withAnimation(.linear(duration: 2).repeatForever(autoreverses: false)) {
                    phase = 1
                }
            }
    }
}

// MARK: - View Extensions

extension View {
    func glassBackground(
        material: GlassMaterial = .regular,
        tint: Color = .blue,
        cornerRadius: CGFloat = 20
    ) -> some View {
        self.modifier(GlassBackgroundModifier(
            material: material,
            tint: tint,
            cornerRadius: cornerRadius
        ))
    }

    func shimmer() -> some View {
        self.modifier(ShimmerEffect())
    }

    /// Smooth scale animation on tap
    func bounceOnTap() -> some View {
        self.buttonStyle(BounceButtonStyle())
    }
}

// MARK: - Bounce Button Style

struct BounceButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.95 : 1.0)
            .animation(.spring(response: 0.3, dampingFraction: 0.6), value: configuration.isPressed)
    }
}

// MARK: - Glass Blur View

struct GlassBlurView: View {
    let intensity: Double

    init(intensity: Double = 0.7) {
        self.intensity = intensity
    }

    var body: some View {
        ZStack {
            Color.white.opacity(0.1 * intensity)

            Rectangle()
                .fill(.ultraThinMaterial)
                .opacity(intensity)
        }
    }
}

// MARK: - Gradient Overlay

struct GradientOverlay: View {
    let colors: [Color]
    let startPoint: UnitPoint
    let endPoint: UnitPoint

    init(
        colors: [Color] = [.blue, .purple],
        startPoint: UnitPoint = .topLeading,
        endPoint: UnitPoint = .bottomTrailing
    ) {
        self.colors = colors
        self.startPoint = startPoint
        self.endPoint = endPoint
    }

    var body: some View {
        LinearGradient(
            colors: colors,
            startPoint: startPoint,
            endPoint: endPoint
        )
    }
}

// MARK: - Floating Action Button

struct FloatingActionButton: View {
    let icon: String
    let action: () -> Void

    @State private var isPressed = false

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 24, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 60, height: 60)
                .background(
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color.blue,
                                    Color.purple
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .shadow(color: Color.blue.opacity(0.5), radius: 10, x: 0, y: 5)
                )
                .overlay(
                    Circle()
                        .strokeBorder(Color.white.opacity(0.3), lineWidth: 2)
                )
                .scaleEffect(isPressed ? 0.9 : 1.0)
                .animation(.spring(response: 0.3, dampingFraction: 0.6), value: isPressed)
        }
        .buttonStyle(PlainButtonStyle())
        #if !os(tvOS)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in isPressed = true }
                .onEnded { _ in isPressed = false }
        )
        #endif
    }
}
