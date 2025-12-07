//
//  SharedVideoComponents.swift
//  FloatNative
//
//  Shared components for video cards and creator displays
//

import SwiftUI

// MARK: - Video Feed Card

struct VideoFeedCard: View {
    let post: BlogPost
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 0) {
                // Thumbnail with duration
                ZStack(alignment: .bottomTrailing) {
                    if let thumbnail = post.thumbnail {
                        CachedAsyncImage_Phase(url: thumbnail.fullURL) { phase in
                            switch phase {
                            case .success(let image):
                                image
                                    .resizable()
                                    .aspectRatio(16/9, contentMode: .fill)
                                    .clipped()
                            default:
                                Rectangle()
                                    .fill(Color.floatplaneGray.opacity(0.3))
                                    .aspectRatio(16/9, contentMode: .fit)
                            }
                        }
                    }

                    // Duration badge
                    if post.metadata.hasVideo {
                        Text(formatDuration(post.metadata.videoDuration))
                            .font(.caption2)
                            .fontWeight(.bold)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(Color.black.opacity(0.8))
                            .clipShape(RoundedRectangle(cornerRadius: 4))
                            .padding(8)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 12))

                // Video info
                HStack(alignment: .top, spacing: 12) {
                    // Creator avatar
                    if let icon = post.creator.icon.fullURL {
                        CachedAsyncImage(url: icon) { image in
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Circle()
                                .fill(Color.floatplaneGray.opacity(0.3))
                        }
                        .frame(width: 36, height: 36)
                        .clipShape(Circle())
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        // Title
                        Text(post.title)
                            .font(.subheadline)
                            .fontWeight(.medium)
                            .foregroundStyle(Color.adaptiveText)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)

                        // Creator and metadata
                        HStack(spacing: 4) {
                            Text(post.creator.title)
                            Text("•")
                            Text(formatDate(post.releaseDate))
                        }
                        .font(.caption)
                        .foregroundStyle(Color.adaptiveSecondaryText)
                    }

                    Spacer()

                    // More button
                    Button {
                        // TODO: Show more options
                    } label: {
                        Image(systemName: "ellipsis")
                            .foregroundColor(Color.adaptiveText)
                            .frame(width: 24, height: 24)
                    }
                }
                .padding(.top, 12)
            }
        }
        .buttonStyle(PlainButtonStyle())
    }

    private func formatDuration(_ seconds: Double) -> String {
        let totalSeconds = Int(seconds)
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        let secs = totalSeconds % 60

        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, secs)
        } else {
            return String(format: "%d:%02d", minutes, secs)
        }
    }

    private func formatDate(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}

// MARK: - Compact History Card

struct CompactHistoryCard: View {
    let post: BlogPost
    let progress: Double // 0.0 to 1.0
    let watchedAt: Date

    var body: some View {
        HStack(spacing: 12) {
            // Thumbnail with progress bar
            ZStack(alignment: .bottomLeading) {
                if let thumbnail = post.thumbnail {
                    CachedAsyncImage_Phase(url: thumbnail.fullURL) { phase in
                        switch phase {
                        case .success(let image):
                            image
                                .resizable()
                                .aspectRatio(16/9, contentMode: .fill)
                                .frame(width: 168, height: 94)
                                .clipped()
                        default:
                            Rectangle()
                                .fill(Color.floatplaneGray.opacity(0.3))
                                .frame(width: 168, height: 94)
                        }
                    }
                }

                // Duration badge
                if post.metadata.hasVideo {
                    VStack {
                        Spacer()
                        HStack {
                            Spacer()
                            Text(formatDuration(post.metadata.videoDuration))
                                .font(.caption2)
                                .fontWeight(.semibold)
                                .foregroundStyle(.white)
                                .padding(.horizontal, 4)
                                .padding(.vertical, 2)
                                .background(Color.black.opacity(0.8))
                                .clipShape(RoundedRectangle(cornerRadius: 2))
                                .padding([.trailing, .bottom], 4)
                        }
                    }
                }

                // Progress bar
                if progress > 0 {
                    VStack {
                        Spacer()
                        GeometryReader { geometry in
                            ZStack(alignment: .leading) {
                                // Background
                                Rectangle()
                                    .fill(Color.white.opacity(0.3))
                                    .frame(height: 3)

                                // Progress
                                Rectangle()
                                    .fill(Color.red)
                                    .frame(width: geometry.size.width * progress, height: 3)
                            }
                        }
                        .frame(height: 3)
                    }
                }
            }
            .frame(width: 168, height: 94)
            .clipShape(RoundedRectangle(cornerRadius: 8))

            // Video info
            VStack(alignment: .leading, spacing: 4) {
                // Title
                Text(post.title)
                    .font(.body)
                    .fontWeight(.medium)
                    .foregroundStyle(Color.adaptiveText)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)

                // Channel/Creator name
                if let channel = post.channel.channelObject {
                    Text(channel.title)
                        .font(.caption)
                        .foregroundStyle(Color.adaptiveSecondaryText)
                } else {
                    Text(post.creator.title)
                        .font(.caption)
                        .foregroundStyle(Color.adaptiveSecondaryText)
                }

                // Watched time
                Text(formatWatchedTime(watchedAt))
                    .font(.caption)
                    .foregroundStyle(Color.adaptiveSecondaryText)
            }

            Spacer()
        }
    }

    private func formatDuration(_ seconds: Double) -> String {
        let totalSeconds = Int(seconds)
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        let secs = totalSeconds % 60

        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, secs)
        } else {
            return String(format: "%d:%02d", minutes, secs)
        }
    }

    private func formatWatchedTime(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}

// MARK: - Creator Card

struct CreatorCard: View {
    let subscription: Subscription

    var body: some View {
        VStack(spacing: 8) {
            // Creator Avatar
            Circle()
                .fill(Color.floatplaneGray.opacity(0.3))
                .frame(width: 60, height: 60)
                .overlay(
                    Image(systemName: "person.fill")
                        .foregroundColor(.floatplaneGray)
                )

            // Creator Name
            Text(subscription.creator)
                .font(.caption2)
                .foregroundColor(Color.adaptiveText)
                .lineLimit(1)
        }
        .frame(width: 80)
    }
}
