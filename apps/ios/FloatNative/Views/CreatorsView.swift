//
//  CreatorsView.swift
//  FloatNative
//
//  Subscribed creators/channels list
//

import SwiftUI

struct CreatorsView: View {
    @StateObject private var viewModel = CreatorsViewModel()
    @State private var selectedFilter: FeedFilter?

    var body: some View {
        ZStack {
            // Background
            Color.adaptiveBackground
                .ignoresSafeArea()

            if viewModel.creators.isEmpty && !viewModel.isLoading {
                emptyState
            } else {
                List {
                    ForEach(viewModel.creators) { creator in
                        CreatorSection(creator: creator, selectedFilter: $selectedFilter)
                            .listRowBackground(Color.clear)
                            #if !os(tvOS)
                            .listRowSeparator(.hidden)
                            #endif
                            #if os(tvOS)
                            .listRowInsets(EdgeInsets(top: 8, leading: 40, bottom: 8, trailing: 40))
                            #endif
                    }
                }
                .listStyle(.plain)
                #if os(tvOS)
                .focusSection()
                #else
                .scrollContentBackground(.hidden)
                #endif
                .refreshable {
                    await viewModel.loadCreators()
                }
            }

            // Loading state
            if viewModel.isLoading {
                ProgressView()
                    .tint(.floatplaneBlue)
                    .scaleEffect(1.5)
            }

            // Error state
            if let error = viewModel.errorMessage {
                VStack(spacing: 16) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.system(size: 50))
                        .foregroundStyle(.red)

                    Text(error)
                        .foregroundStyle(Color.adaptiveText)
                        .multilineTextAlignment(.center)

                    Button("Retry") {
                        Task {
                            await viewModel.loadCreators()
                        }
                    }
                    .buttonStyle(LiquidGlassButtonStyle(tint: .floatplaneBlue))
                }
                .padding()
            }
        }
        #if os(tvOS)
        .navigationTitle("")
        #else
        .navigationTitle("Creators")
        #endif
        .toolbarBackground(.visible, for: .navigationBar)
        .navigationDestination(item: $selectedFilter) { filter in
            VideoFeedView(filter: filter)
        }
        .navigationDestination(for: BlogPost.self) { post in
            #if os(tvOS)
            VideoPlayerTvosView(post: post)
            #else
            VideoPlayerView(post: post)
            #endif
        }
        .globalMenu()
        .task {
            await viewModel.loadCreators()
        }
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "person.2.fill")
                .font(.system(size: 60))
                .foregroundStyle(Color.floatplaneGray)

            Text("No subscriptions")
                .font(.title3)
                .fontWeight(.medium)
                .foregroundStyle(Color.adaptiveText)

            Text("Subscribe to creators to see their content")
                .font(.subheadline)
                .foregroundStyle(Color.adaptiveSecondaryText)
                .multilineTextAlignment(.center)
        }
        .padding()
    }
}

// MARK: - Creator Section

struct CreatorSection: View {
    let creator: Creator
    @Binding var selectedFilter: FeedFilter?
    @State private var isExpanded = true

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Creator Row (Tappable with Navigation)
            HStack(spacing: 16) {
                // Creator Avatar
                CachedAsyncImage(url: creator.icon.fullURL) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } placeholder: {
                    Circle()
                        .fill(Color.floatplaneGray.opacity(0.3))
                }
                #if os(tvOS)
                .frame(width: 80, height: 80)
                #else
                .frame(width: 56, height: 56)
                #endif
                .clipShape(Circle())

                VStack(alignment: .leading, spacing: 4) {
                    // Creator Name
                    Text(creator.title)
                        #if os(tvOS)
                        .font(.title3)
                        #else
                        .font(.body)
                        #endif
                        .fontWeight(.medium)
                        .foregroundColor(Color.adaptiveText)

                    // Subscription status
                    HStack(spacing: 4) {
                        Circle()
                            .fill(Color.green)
                            #if os(tvOS)
                            .frame(width: 10, height: 10)
                            #else
                            .frame(width: 8, height: 8)
                            #endif

                        Text("\(creator.channels.count) channel\(creator.channels.count == 1 ? "" : "s")")
                            #if os(tvOS)
                            .font(.body)
                            #else
                            .font(.caption)
                            #endif
                            .foregroundColor(Color.adaptiveSecondaryText)
                    }
                }

                Spacer()

                // Expand/Collapse button
                if !creator.channels.isEmpty {
                    Button {
                        withAnimation {
                            isExpanded.toggle()
                        }
                    } label: {
                        Image(systemName: isExpanded ? "chevron.down" : "chevron.right")
                            .foregroundColor(.floatplaneGray)
                            #if os(tvOS)
                            .font(.title3)
                            #else
                            .font(.caption)
                            #endif
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            #if os(tvOS)
            .padding(.vertical, 16)
            .padding(.horizontal, 20)
            #else
            .padding(.vertical, 8)
            #endif
            .contentShape(Rectangle())

            // Channel List (Expandable)
            if isExpanded && !creator.channels.isEmpty {
                VStack(spacing: 8) {
                    // "View All" row for creator filter
                    Button {
                        selectedFilter = FeedFilter.creator(id: creator.id, name: creator.title, icon: creator.icon)
                    } label: {
                        HStack(spacing: 12) {
                            // Icon
                            Image(systemName: "square.grid.2x2.fill")
                                .foregroundColor(.floatplaneBlue)
                                #if os(tvOS)
                                .font(.title2)
                                .frame(width: 56, height: 56)
                                #else
                                .frame(width: 36, height: 36)
                                #endif

                            // Text
                            Text("View All Videos")
                                #if os(tvOS)
                                .font(.body)
                                #else
                                .font(.subheadline)
                                #endif
                                .fontWeight(.medium)
                                .foregroundColor(.floatplaneBlue)

                            Spacer()

                            // Chevron
                            Image(systemName: "chevron.right")
                                .foregroundColor(.floatplaneGray)
                                #if os(tvOS)
                                .font(.body)
                                #else
                                .font(.caption2)
                                #endif
                        }
                        #if os(tvOS)
                        .padding(.vertical, 12)
                        .padding(.horizontal, 16)
                        #else
                        .padding(.vertical, 6)
                        #endif
                        .contentShape(Rectangle())
                    }
                    #if os(tvOS)
                    .buttonStyle(.card)
                    #else
                    .buttonStyle(PlainButtonStyle())
                    #endif

                    // Individual channels
                    ForEach(creator.channels) { channel in
                        ChannelRow(channel: channel, creatorId: creator.id, selectedFilter: $selectedFilter)
                    }
                }
                .padding(.leading, 40)
                .padding(.top, 8)
            }
        }
    }
}

// MARK: - Channel Row

struct ChannelRow: View {
    let channel: Channel
    let creatorId: String
    @Binding var selectedFilter: FeedFilter?

    var body: some View {
        Button {
            selectedFilter = FeedFilter.channel(id: channel.id, name: channel.title, icon: channel.icon, creatorId: creatorId)
        } label: {
            HStack(spacing: 12) {
                // Channel Icon
                CachedAsyncImage(url: channel.icon.fullURL) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } placeholder: {
                    Circle()
                        .fill(Color.floatplaneGray.opacity(0.3))
                }
                #if os(tvOS)
                .frame(width: 56, height: 56)
                #else
                .frame(width: 36, height: 36)
                #endif
                .clipShape(Circle())

                // Channel Name
                Text(channel.title)
                    #if os(tvOS)
                    .font(.body)
                    #else
                    .font(.subheadline)
                    #endif
                    .foregroundColor(Color.adaptiveText)

                Spacer()

                // Chevron
                Image(systemName: "chevron.right")
                    .foregroundColor(.floatplaneGray)
                    #if os(tvOS)
                    .font(.body)
                    #else
                    .font(.caption2)
                    #endif
            }
            #if os(tvOS)
            .padding(.vertical, 12)
            .padding(.horizontal, 16)
            #else
            .padding(.vertical, 6)
            #endif
            .contentShape(Rectangle())
        }
        #if os(tvOS)
        .buttonStyle(.card)
        #else
        .buttonStyle(PlainButtonStyle())
        #endif
    }
}

// MARK: - Creators View Model

@MainActor
class CreatorsViewModel: ObservableObject {
    @Published var subscriptions: [Subscription] = []
    @Published var creators: [Creator] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let api = FloatplaneAPI.shared

    func loadCreators() async {
        guard !isLoading else { return }

        isLoading = true
        errorMessage = nil

        do {
            // Get subscriptions
            subscriptions = try await api.getSubscriptions()

            // Fetch full creator details for each subscription
            var loadedCreators: [Creator] = []
            for subscription in subscriptions {
                do {
                    let creator = try await api.getCreator(id: subscription.creator)
                    loadedCreators.append(creator)
                } catch {
                    print("⚠️ Failed to load creator \(subscription.creator): \(error)")
                }
            }

            creators = loadedCreators
            isLoading = false
        } catch {
            errorMessage = "Failed to load creators: \(error.localizedDescription)"
            isLoading = false
        }
    }
}

#Preview {
    NavigationStack {
        CreatorsView()
    }
}
