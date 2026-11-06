//
//  PlayerPageLayout.swift
//  FloatNative
//
//  How a player page uses the space its window has. A window can change size at any
//  moment (unfolding an iPhone Duo, resizing on iPad), so this goes by that space and
//  not by which device it's on.
//

import SwiftUI

/// Player above its details, or beside them with a second column when there's room.
struct PlayerPageLayout<Player: View, Details: View, Side: View, Footer: View>: View {
    /// Files added through the Files app have no `side` to show.
    var hasSide = true
    /// Keeps the end of `side` clear of `footer`.
    var sideBottomPadding: CGFloat = 0
    @ViewBuilder var player: Player
    @ViewBuilder var details: Details
    @ViewBuilder var side: Side
    /// Floats over the bottom of `side`, or of the whole page when stacked.
    @ViewBuilder var footer: Footer

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.verticalSizeClass) private var verticalSizeClass

    var body: some View {
        GeometryReader { geometry in
            let sideBySide = PlayerPageArrangement.isSideBySide(
                size: geometry.size,
                horizontal: horizontalSizeClass,
                vertical: verticalSizeClass
            )
            let foldX = Fold.x(in: geometry)
            let playerColumnWidth = PlayerPageArrangement.playerColumnWidth(
                totalWidth: geometry.size.width,
                foldX: foldX
            )
            let layout = sideBySide
                ? AnyLayout(HStackLayout(alignment: .top, spacing: 0))
                : AnyLayout(VStackLayout(spacing: 0))
            // With nothing else for the second column, the details go there.
            let detailsUnderPlayer = sideBySide && hasSide

            // The player keeps its place in both layouts, so unfolding the phone
            // doesn't rebuild it and interrupt the video.
            layout {
                VStack(spacing: 0) {
                    // Takes its full size first, and whatever's under it gets the rest.
                    player
                        .layoutPriority(1)

                    if detailsUnderPlayer {
                        ScrollView {
                            column { details }
                        }
                    }
                }
                .frame(width: sideBySide ? playerColumnWidth : nil)
                .layoutPriority(1)

                ScrollView {
                    column {
                        if !detailsUnderPlayer {
                            details

                            if hasSide {
                                Divider()
                                    .background(Color.floatplaneGray.opacity(0.3))
                            }
                        }

                        if hasSide {
                            side
                        }
                    }
                    .padding(.bottom, sideBottomPadding)
                }
                .overlay(alignment: .leading) {
                    if sideBySide {
                        // In an HStack, a Divider is vertical.
                        HStack(spacing: 0) {
                            Divider()
                        }
                        .ignoresSafeArea(edges: .bottom)
                    }
                }
            }
            .overlay(alignment: sideBySide ? .bottomTrailing : .bottom) {
                footer
                    .frame(width: sideBySide ? geometry.size.width - playerColumnWidth : geometry.size.width)
            }
            .animation(.smooth, value: foldX)
        }
    }

    private func column<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            content()
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

extension PlayerPageLayout where Footer == EmptyView {
    init(
        hasSide: Bool = true,
        @ViewBuilder player: () -> Player,
        @ViewBuilder details: () -> Details,
        @ViewBuilder side: () -> Side
    ) {
        self.hasSide = hasSide
        self.player = player()
        self.details = details()
        self.side = side()
        self.footer = EmptyView()
    }
}

enum PlayerPageArrangement {
    /// A large window that's wider than it's tall: an open iPhone Duo or a landscape iPad.
    /// A phone in landscape is too short for this, and goes fullscreen instead.
    static func isSideBySide(
        size: CGSize,
        horizontal: UserInterfaceSizeClass?,
        vertical: UserInterfaceSizeClass?
    ) -> Bool {
        #if os(tvOS)
        return false
        #else
        return horizontal == .regular && vertical == .regular && size.width > size.height
        #endif
    }

    /// Leaves the player large enough to watch, and a column wide enough to read.
    private static let playerColumnFraction: CGFloat = 0.62

    /// Where the player column ends. On a partly folded iPhone Duo that's the fold, as
    /// content across it is hard to see.
    static func playerColumnWidth(totalWidth: CGFloat, foldX: CGFloat?) -> CGFloat {
        (Fold.usableX(foldX, totalWidth: totalWidth) ?? totalWidth * playerColumnFraction).rounded()
    }
}

/// Keeps content off the fold of a partly folded iPhone Duo, where it's hard to see.
enum Fold {
    /// The middle of an active fold that runs top to bottom through this view.
    static func x(in geometry: GeometryProxy) -> CGFloat? {
        // The fold API is in the iOS 27.1 SDK, which comes with Swift 6.4 in Xcode 27.1.
        #if compiler(>=6.4) && os(iOS)
        if #available(iOS 27.1, *) {
            return geometry.reservedRegions(kind: .division)
                .map(\.frame)
                .first { $0.height > $0.width }?
                .midX
        }
        #endif
        return nil
    }

    /// The fold, unless it's so far to one side that working around it would leave
    /// something too squeezed to use. That also rules out a position left over from
    /// another size partway through a fold or rotation, which can be well off centre.
    static func usableX(_ foldX: CGFloat?, totalWidth: CGFloat) -> CGFloat? {
        guard let foldX, foldX > totalWidth * 0.35, foldX < totalWidth * 0.65 else { return nil }
        return foldX
    }

    /// How much to narrow evenly padded columns to centre them on the fold, which puts
    /// the gap between two columns on it. Positive narrows them from the leading edge,
    /// and negative from the trailing edge. `leadingIndent` is any more the columns are
    /// already narrowed by from the leading edge.
    static func columnsInset(totalWidth: CGFloat, foldX: CGFloat?, leadingIndent: CGFloat = 0) -> CGFloat {
        guard let foldX = usableX(foldX, totalWidth: totalWidth) else { return 0 }
        return (foldX * 2 - totalWidth - leadingIndent).rounded()
    }
}

extension View {
    /// Puts the gap between two columns on the fold, by narrowing what's in a scroll view.
    /// Goes on the scroll view, with a grid in it that's evenly padded apart from any
    /// `leadingIndent`. The inner screen is off centre in its window, as the bars take
    /// up one side.
    @ViewBuilder
    func columnsAvoidingFold(leadingIndent: CGFloat = 0) -> some View {
        #if os(tvOS)
        self
        #else
        modifier(FoldAlignedColumns(leadingIndent: leadingIndent))
        #endif
    }

    /// Centres a message on the leading side of the fold instead of across it, and keeps
    /// its lines short enough to read. Goes on a view that would fill the screen.
    @ViewBuilder
    func messageAvoidingFold() -> some View {
        #if os(tvOS)
        self
        #else
        modifier(FoldAvoidingMessage())
        #endif
    }
}

#if !os(tvOS)
private struct FoldAlignedColumns: ViewModifier {
    let leadingIndent: CGFloat

    func body(content: Content) -> some View {
        // Worked out from the current size on every layout. A saved copy could stick on
        // a size from partway through a fold or rotation, with nothing to correct it.
        GeometryReader { geometry in
            let inset = Fold.columnsInset(
                totalWidth: geometry.size.width,
                foldX: Fold.x(in: geometry),
                leadingIndent: leadingIndent
            )

            content
                .contentMargins(.leading, max(inset, 0), for: .scrollContent)
                .contentMargins(.trailing, max(-inset, 0), for: .scrollContent)
                .animation(.smooth, value: inset)
        }
    }
}

private struct FoldAvoidingMessage: ViewModifier {
    private let maximumWidth: CGFloat = 440

    func body(content: Content) -> some View {
        GeometryReader { geometry in
            let foldX = Fold.usableX(Fold.x(in: geometry), totalWidth: geometry.size.width)

            content
                .frame(maxWidth: maximumWidth)
                .frame(width: foldX ?? geometry.size.width, height: geometry.size.height)
                .animation(.smooth, value: foldX)
        }
    }
}
#endif

/// Stands in for the thumbnail of a post that doesn't have one, as a text post may not.
struct MissingThumbnail: View {
    var body: some View {
        Rectangle()
            .fill(Color.floatplaneGray.opacity(0.3))
            .overlay {
                Image(systemName: "text.alignleft")
                    .font(.title)
                    .foregroundStyle(Color.adaptiveSecondaryText)
            }
    }
}

#if !os(tvOS)
/// Goes fullscreen. A phone or iPad rotates to do it, but an open iPhone Duo's screen
/// doesn't rotate with the device, so there it says what it actually does.
struct PlayerFullScreenButton: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.verticalSizeClass) private var verticalSizeClass

    private var rotates: Bool {
        AVPlayerManager.rotationControlsFullScreen(
            idiom: UIDevice.current.userInterfaceIdiom,
            horizontal: UIUserInterfaceSizeClass(horizontalSizeClass),
            vertical: UIUserInterfaceSizeClass(verticalSizeClass)
        )
    }

    var body: some View {
        Button {
            AVPlayerManager.shared.forceLandscape()
        } label: {
            Image(systemName: rotates ? "rectangle.landscape.rotate" : "arrow.up.left.and.arrow.down.right")
                .font(.title2)
                .foregroundColor(Color.adaptiveText)
        }
        .accessibilityLabel(rotates ? "Force landscape" : "Full screen")
    }
}
#endif

enum AdaptiveGrid {
    /// The narrowest a video card can be. An open iPhone Duo gets two columns whichever
    /// way it's held or however far it's open, so its cards don't reflow as the hinge
    /// moves, with one edge-to-edge column on any phone screen held upright.
    static let minimumColumnWidth: CGFloat = 280

    static var columns: [GridItem] {
        [GridItem(.adaptive(minimum: minimumColumnWidth), spacing: 16)]
    }

    /// The narrowest a row of a video and its details can be, as it needs more room than
    /// a card. Low enough for a row on each side of a partly folded iPhone Duo.
    static let minimumRowWidth: CGFloat = 340

    static var rowColumns: [GridItem] {
        [GridItem(.adaptive(minimum: minimumRowWidth), spacing: 0, alignment: .top)]
    }
}
