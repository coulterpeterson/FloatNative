//
//  LiveVideoCard.swift
//  FloatNative
//
//  Created for FloatNative
//

import SwiftUI

struct LiveVideoCard: View {
    let creator: Creator
    
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Thumbnail
            Color.clear
                #if os(tvOS)
                .aspectRatio(16/9, contentMode: .fill)
                #else
                .aspectRatio(16/9, contentMode: .fit)
                #endif
                .overlay(
                    ZStack(alignment: .bottomTrailing) {
                        if let thumbnail = creator.liveStream?.thumbnail {
                            CachedAsyncImage_Phase(url: thumbnail.fullURL) { phase in
                                switch phase {
                                case .success(let image):
                                    image
                                        .resizable()
                                        .scaledToFill()
                                default:
                                    Rectangle()
                                        .fill(Color.floatplaneGray.opacity(0.3))
                                }
                            }
                        } else {
                            Rectangle()
                                .fill(Color.floatplaneGray.opacity(0.3))
                                .overlay(
                                    Image(systemName: "play.tv.fill")
                                        .font(.system(size: 40))
                                        .foregroundColor(.white.opacity(0.3))
                                )
                        }
                    }
                )
                .overlay(
                    // LIVE indicator (top-left) - Stronger visual for the dedicated live card
                    VStack {
                        HStack {
                            HStack(spacing: 6) {
                                Circle()
                                    .fill(.red)
                                    .frame(width: 8, height: 8)
                                    .opacity(1.0) // We'll add pulsing if we can access the modifier, otherwise just static red dot
                                Text("LIVE")
                                    .font(.caption)
                                    .fontWeight(.bold)
                            }
                            .foregroundStyle(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.red) // Solid red background for visibility
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                            .shadow(radius: 2)
                            .padding(8)
                            
                            Spacer()
                        }
                        Spacer()
                    }
                )
                .clipShape(RoundedRectangle(cornerRadius: 12))
            
            // Info
            VStack(alignment: .leading, spacing: 4) {
                // Title
                Text(creator.liveStream?.title ?? "\(creator.title) is Live")
                    .font(.body) // Slightly larger than subheadline
                    .fontWeight(.semibold)
                    .foregroundStyle(Color.adaptiveText)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                
                // Creator
                HStack(spacing: 6) {
                    if let icon = creator.icon.fullURL {
                        CachedAsyncImage(url: icon) { image in
                            image.resizable().aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Circle().fill(Color.gray.opacity(0.3))
                        }
                        .frame(width: 20, height: 20)
                        .clipShape(Circle())
                    }
                    
                    Text(creator.title)
                        .font(.caption)
                        .foregroundStyle(Color.adaptiveSecondaryText)
                }
            }
            .padding(.top, 12)
        }
        .contentShape(Rectangle()) // Ensure tap target is good
    }
}
