//
//  MediaModels.swift
//  FloatNative
//
//  Created by Claude on 2025-10-08.
//
//  Note: Using OpenAPI-generated models for DeliveryInfo structures
//

import Foundation

// MARK: - Image Models
// Using OpenAPI-generated ImageModel

typealias ImageData = ImageModel
typealias ChildImage = ChildImageModel

extension ImageModel {
    var fullURL: URL? {
        // Handle both relative paths and full URLs
        if path.starts(with: "http://") || path.starts(with: "https://") {
            return URL(string: path)
        }
        return URL(string: "https://pbs.floatplane.com\(path)")
    }

    func childImageURL(at index: Int) -> URL? {
        guard let childImages = childImages, index < childImages.count else {
            return nil
        }
        let childPath = childImages[index].path
        // Handle both relative paths and full URLs
        if childPath.starts(with: "http://") || childPath.starts(with: "https://") {
            return URL(string: childPath)
        }
        return URL(string: "https://pbs.floatplane.com\(childPath)")
    }

    func bestThumbnail(targetWidth: Int) -> URL? {
        guard let childImages = childImages else {
            return fullURL
        }

        let sorted = childImages.sorted { abs($0.width - targetWidth) < abs($1.width - targetWidth) }
        if let best = sorted.first {
            let childPath = best.path
            // Handle both relative paths and full URLs
            if childPath.starts(with: "http://") || childPath.starts(with: "https://") {
                return URL(string: childPath)
            }
            return URL(string: "https://pbs.floatplane.com\(childPath)")
        }

        return fullURL
    }
}

// MARK: - Video Delivery
// Using OpenAPI-generated models from FloatNative/Models/Generated/
// These are type aliases to maintain compatibility with existing code

typealias DeliveryInfo = CdnDeliveryV3Response
typealias DeliveryGroup = CdnDeliveryV3Group
typealias DeliveryOrigin = CdnDeliveryV3Origin
typealias DeliveryVariant = CdnDeliveryV3Variant
typealias VariantMeta = CdnDeliveryV3Meta
typealias VideoMeta = CdnDeliveryV3MetaVideo
typealias AudioMeta = CdnDeliveryV3MetaAudio
typealias BitrateInfo = CdnDeliveryV3MediaBitrateInfoBitrate

// MARK: - Delivery Helper

extension DeliveryInfo {
    /// Get all available quality variants sorted by resolution (highest first)
    func availableVariants() -> [QualityVariant] {
        guard let group = groups.first else { return [] }

        let originURL = group.origins?.first?.url ?? ""
        let variants = group.variants ?? []

        return variants
            .filter { ($0.enabled ?? false) && !($0.hidden ?? false) }
            .map { variant in
                let fullURL: String
                if variant.url.starts(with: "http") {
                    fullURL = variant.url
                } else {
                    fullURL = originURL + variant.url
                }

                let videoMeta = variant.meta?.video
                let resolution: String
                if let width = videoMeta?.width, let height = videoMeta?.height {
                    resolution = "\(width)x\(height)"
                } else {
                    resolution = variant.label
                }

                return QualityVariant(
                    name: variant.name,
                    label: variant.label,
                    url: fullURL,
                    resolution: resolution,
                    width: videoMeta?.width ?? 0,
                    height: videoMeta?.height ?? 0,
                    order: Int(variant.order ?? 0)
                )
            }
            .sorted { $0.height > $1.height }
    }
}

struct QualityVariant: Identifiable, Equatable {
    let id = UUID()
    let name: String
    let label: String
    let url: String
    let resolution: String
    let width: Int
    let height: Int
    let order: Int
}

// MARK: - Delivery Scenario

enum DeliveryScenario: String {
    case onDemand
    case download
    case live
}

enum OutputKind: String {
    case hlsMpegts = "hls.mpegts"
    case hlsFmp4 = "hls.fmp4"
    case dashMpegts = "dash.mpegts"
    case dashM4s = "dash.m4s"
    case flat
}
