//
//  CachedAsyncImage.swift
//  FloatNative
//
//  Drop-in replacement for AsyncImage with disk caching support
//  Uses ImageCacheManager's cached URLSession for 1-hour persistence
//

import SwiftUI

/// Cached async image loader using URLCache for disk persistence
struct CachedAsyncImage<Content: View, Placeholder: View>: View {
    let url: URL?
    let content: (Image) -> Content
    let placeholder: () -> Placeholder

    @State private var image: UIImage?
    @State private var isLoading = false

    init(
        url: URL?,
        @ViewBuilder content: @escaping (Image) -> Content,
        @ViewBuilder placeholder: @escaping () -> Placeholder
    ) {
        self.url = url
        self.content = content
        self.placeholder = placeholder
    }

    var body: some View {
        Group {
            if let image = image {
                content(Image(uiImage: image))
            } else {
                placeholder()
                    .onAppear {
                        loadImage()
                    }
            }
        }
    }

    private func loadImage() {
        guard let url = url, !isLoading else { return }

        isLoading = true

        Task {
            do {
                let (data, response) = try await ImageCacheManager.shared.session.data(from: url)

                // Verify response is successful
                guard let httpResponse = response as? HTTPURLResponse,
                      (200...299).contains(httpResponse.statusCode),
                      let uiImage = UIImage(data: data) else {
                    isLoading = false
                    return
                }

                // Update on main thread
                await MainActor.run {
                    self.image = uiImage
                    self.isLoading = false
                }
            } catch {
                print("⚠️ Failed to load image from \(url): \(error)")
                await MainActor.run {
                    self.isLoading = false
                }
            }
        }
    }
}

// MARK: - Convenience Initializers

extension CachedAsyncImage where Content == Image, Placeholder == Color {
    /// Simple initializer with default placeholder
    init(url: URL?) {
        self.init(
            url: url,
            content: { image in image },
            placeholder: { Color.gray.opacity(0.3) }
        )
    }
}

extension CachedAsyncImage where Placeholder == EmptyView {
    /// Initializer with content transformation only
    init(
        url: URL?,
        @ViewBuilder content: @escaping (Image) -> Content
    ) {
        self.init(
            url: url,
            content: content,
            placeholder: { EmptyView() }
        )
    }
}

// MARK: - Phase-based API (mimics AsyncImage)

struct CachedAsyncImage_Phase<Content: View>: View {
    let url: URL?
    let content: (AsyncImagePhase) -> Content

    @State private var phase: AsyncImagePhase = .empty
    @State private var isLoading = false

    init(url: URL?, @ViewBuilder content: @escaping (AsyncImagePhase) -> Content) {
        self.url = url
        self.content = content
    }

    var body: some View {
        content(phase)
            .onAppear {
                loadImage()
            }
    }

    private func loadImage() {
        guard let url = url, !isLoading else {
            return
        }

        isLoading = true

        Task {
            do {
                let (data, response) = try await ImageCacheManager.shared.session.data(from: url)

                guard let httpResponse = response as? HTTPURLResponse,
                      (200...299).contains(httpResponse.statusCode),
                      let uiImage = UIImage(data: data) else {
                    await MainActor.run {
                        self.phase = .failure(URLError(.badServerResponse))
                        self.isLoading = false
                    }
                    return
                }

                await MainActor.run {
                    self.phase = .success(Image(uiImage: uiImage))
                    self.isLoading = false
                }
            } catch {
                await MainActor.run {
                    self.phase = .failure(error)
                    self.isLoading = false
                }
            }
        }
    }
}
