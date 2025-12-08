import Foundation

extension BlogPostModelV3 {
    /// Helper to identify if a post is a livestream.
    /// Since the Generated ModelType enum might not contain .livestream,
    /// we use heuristics (video duration == 0) or other metadata to detect it.
    var isLivestream: Bool {
        // Heuristic: Livestreams have video but 0 duration.
        // We also check if the creator has an active livestream model if possible,
        // but primarily rely on the post metadata.
        return metadata.hasVideo && metadata.videoDuration == 0
    }
}
