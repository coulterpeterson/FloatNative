import Foundation

/// One cue from a WebVTT file. `payload` is everything after the timestamp
/// line — cue identifier (rare), settings, and text — joined by newlines.
/// Kept as a single string so the overlay renders exactly what Floatplane
/// authored, including line breaks.
struct VTTCue: Equatable {
    let startSec: Double
    let endSec: Double
    let payload: String
}

/// WebVTT parser tuned for Floatplane's HTML5-style cues. Doesn't try to
/// be a full WebVTT spec implementation — just enough to extract cues from
/// real Floatplane WebVTT files (auto-generated captions and uploaded SDH).
enum VTTCueParser {

    static func parse(_ raw: Data) -> [VTTCue] {
        guard let body = String(data: raw, encoding: .utf8) else { return [] }
        return parse(body)
    }

    static func parse(_ source: String) -> [VTTCue] {
        let normalized = source.replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
        // Cues are blank-line-separated blocks. The first block is the WEBVTT
        // header; subsequent blocks may be NOTE / STYLE / REGION or a cue.
        let blocks = normalized.components(separatedBy: "\n\n")
        var cues: [VTTCue] = []
        for (i, rawBlock) in blocks.enumerated() {
            if i == 0 { continue }
            let trimmed = rawBlock.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty { continue }
            if trimmed.hasPrefix("NOTE") || trimmed.hasPrefix("STYLE") || trimmed.hasPrefix("REGION") {
                continue
            }
            let lines = trimmed.components(separatedBy: "\n")
            guard let tsIdx = lines.firstIndex(where: { $0.contains("-->") }) else { continue }
            guard let (start, end) = parseTimestampLine(lines[tsIdx]) else { continue }
            let payloadLines = lines.suffix(from: tsIdx + 1)
            let payload = payloadLines.joined(separator: "\n")
            cues.append(VTTCue(startSec: start, endSec: end, payload: payload))
        }
        return cues
    }

    /// Active cues at `time` are those whose half-open range [start, end)
    /// contains the time. Floatplane authoring overlaps are rare but allowed.
    static func activeCues(_ cues: [VTTCue], at time: Double) -> [VTTCue] {
        cues.filter { $0.startSec <= time && time < $0.endSec }
    }

    // MARK: - Internal

    private static func parseTimestampLine(_ line: String) -> (Double, Double)? {
        let parts = line.components(separatedBy: "-->")
        guard parts.count >= 2 else { return nil }
        let leftStr = parts[0].trimmingCharacters(in: .whitespaces)
        // Right side may include cue settings (e.g. " line:80%"). Take only
        // the timestamp; settings have no whitespace inside them.
        let rightStr = parts[1]
            .trimmingCharacters(in: .whitespaces)
            .components(separatedBy: " ").first ?? ""
        guard let start = parseTimestamp(leftStr),
              let end = parseTimestamp(rightStr)
        else { return nil }
        return (start, end)
    }

    private static func parseTimestamp(_ str: String) -> Double? {
        // HH:MM:SS.mmm or MM:SS.mmm
        let dotParts = str.components(separatedBy: ".")
        guard dotParts.count == 2, let ms = Double(dotParts[1]) else { return nil }
        let colonParts = dotParts[0].components(separatedBy: ":")
        let nums = colonParts.compactMap { Double($0) }
        guard nums.count == colonParts.count, !nums.isEmpty else { return nil }
        var total: Double = 0
        if nums.count == 3 { total = nums[0] * 3600 + nums[1] * 60 + nums[2] }
        else if nums.count == 2 { total = nums[0] * 60 + nums[1] }
        else { total = nums[0] }
        return total + ms / 1000.0
    }
}

/// Fetch the first available WebVTT track from a list of Floatplane text
/// tracks and parse it into cues. Returns an empty array on any failure —
/// captions are nice-to-have and shouldn't block playback. Picks the first
/// track for now; we can layer language selection on later.
func fetchCaptionCues(
    from tracks: [ContentVideoV3ResponseTextTracksInner],
    session: URLSession = .shared
) async -> [VTTCue] {
    guard let file = await fetchCaptionFile(from: tracks, session: session) else { return [] }
    return VTTCueParser.parse(file)
}

/// The raw WebVTT of the first track that has any cues.
func fetchCaptionFile(
    from tracks: [ContentVideoV3ResponseTextTracksInner],
    session: URLSession = .shared
) async -> Data? {
    for track in tracks {
        guard let url = URL(string: track.src) else { continue }
        do {
            let (data, response) = try await session.data(from: url)
            if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
                continue
            }
            if !VTTCueParser.parse(data).isEmpty { return data }
        } catch {
            continue
        }
    }
    return nil
}
