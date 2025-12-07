//
//  ImageCacheManager.swift
//  FloatNative
//
//  Manages URL-based image caching with disk persistence
//  1-hour cache TTL to reduce CDN bandwidth and rate limiting
//

import Foundation

class ImageCacheManager {

    // MARK: - Singleton

    static let shared = ImageCacheManager()

    // MARK: - Cached URLSession

    private(set) var session: URLSession

    // MARK: - Initialization

    private init() {
        // Configure URLCache with disk persistence
        let memoryCapacity = 50 * 1024 * 1024  // 50 MB memory cache
        let diskCapacity = 200 * 1024 * 1024   // 200 MB disk cache

        let cache = URLCache(
            memoryCapacity: memoryCapacity,
            diskCapacity: diskCapacity,
            diskPath: "image_cache"
        )

        // Configure session with caching
        let config = URLSessionConfiguration.default
        config.urlCache = cache
        config.requestCachePolicy = .returnCacheDataElseLoad

        // Set cache control headers for 1 hour TTL
        config.httpAdditionalHeaders = [
            "Cache-Control": "max-age=3600" // 1 hour
        ]

        self.session = URLSession(configuration: config)


        print("   💾 Memory cache: \(memoryCapacity / 1024 / 1024) MB")
        print("   💿 Disk cache: \(diskCapacity / 1024 / 1024) MB")
    }

    // MARK: - Cache Management

    /// Clear all cached images
    func clearCache() {
        session.configuration.urlCache?.removeAllCachedResponses()
        print("🗑️ Image cache cleared")
    }

    /// Get current cache usage statistics
    func getCacheStats() -> (memoryUsage: Int, diskUsage: Int) {
        guard let cache = session.configuration.urlCache else {
            return (0, 0)
        }
        return (cache.currentMemoryUsage, cache.currentDiskUsage)
    }

    /// Print cache statistics to console
    func printCacheStats() {
        let stats = getCacheStats()
        print("📊 Image Cache Stats:")
        print("   💾 Memory: \(stats.memoryUsage / 1024 / 1024) MB")
        print("   💿 Disk: \(stats.diskUsage / 1024 / 1024) MB")
    }
}
