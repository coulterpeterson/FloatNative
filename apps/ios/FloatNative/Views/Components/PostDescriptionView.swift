//
//  PostDescriptionView.swift
//  FloatNative
//
//  A post's description, collapsed to three lines with Show more / Show less.
//  Timestamps in the text seek the current video.
//

import SwiftUI

struct PostDescriptionView: View {
    let description: AttributedString
    /// Length of the description as sent, which decides whether it collapses.
    let rawLength: Int

    @State private var isExpanded = false

    private var seekOrOpen: OpenURLAction {
        OpenURLAction { url in
            // Handle timestamp seeking
            if url.scheme == "floatplane", url.host == "seek",
               let secondsString = url.pathComponents.last,
               let seconds = Int(secondsString) {
                AVPlayerManager.shared.seek(to: TimeInterval(seconds))
                return .handled
            }
            // Let system handle other URLs
            return .systemAction
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Make description text tappable when collapsed
            Group {
                if !isExpanded && rawLength > 150 {
                    Button {
                        withAnimation {
                            isExpanded = true
                        }
                    } label: {
                        Text(description)
                            .font(.subheadline)
                            .foregroundColor(Color.adaptiveText)
                            .lineLimit(3)
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(PlainButtonStyle())
                } else {
                    Text(description)
                        .font(.subheadline)
                        .foregroundColor(Color.adaptiveText)
                        .lineLimit(isExpanded ? nil : 3)
                }
            }
            .environment(\.openURL, seekOrOpen)

            if rawLength > 150 {
                Button {
                    withAnimation {
                        isExpanded.toggle()
                    }
                } label: {
                    Text(isExpanded ? "Show less" : "Show more")
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(.floatplaneBlue)
                }
            }
        }
    }
}
