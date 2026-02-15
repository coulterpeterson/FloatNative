//
//  VideoResourceLoader.swift
//  FloatNative
//
//  Created by Claude on 2024-03-22.
//

import AVFoundation
import Foundation

class VideoResourceLoader: NSObject, AVAssetResourceLoaderDelegate {
    
    private let session: URLSession
    private let customScheme = "floatnative"
    
    override init() {
        let config = URLSessionConfiguration.default
        self.session = URLSession(configuration: config)
        super.init()
    }
    
    // MARK: - Delegate Methods
    
    func resourceLoader(
        _ resourceLoader: AVAssetResourceLoader,
        shouldWaitForLoadingOfRequestedResource loadingRequest: AVAssetResourceLoadingRequest
    ) -> Bool {
        guard let url = loadingRequest.request.url else { return false }
        
        // Handle custom scheme requests
        if url.scheme == customScheme {
            handleCustomSchemeRequest(loadingRequest)
            return true
        }
        
        return false
    }
    
    // MARK: - Handlers
    
    private func handleCustomSchemeRequest(_ loadingRequest: AVAssetResourceLoadingRequest) {
        guard let url = loadingRequest.request.url,
              var components = URLComponents(url: url, resolvingAgainstBaseURL: true) else {
            loadingRequest.finishLoading(with: URLError(.badURL))
            return
        }
        
        // Switch scheme back to https
        components.scheme = "https"
        guard let realURL = components.url else {
            loadingRequest.finishLoading(with: URLError(.badURL))
            return
        }
        
        Task {
            do {
                // Check if this is a Master Playlist or Variant Playlist (m3u8)
                if realURL.pathExtension.caseInsensitiveCompare("m3u8") == .orderedSame {
                    try await handleManifestRequest(loadingRequest, realURL: realURL)
                } 
                // Check if this is a Key
                else if realURL.absoluteString.contains("key") || realURL.pathExtension == "key" {
                     try await handleKeyRequest(loadingRequest, realURL: realURL)
                }
                // Fallback (shouldn't happen with our rewrite logic, but handle gracefully)
                else {
                    try await handleGenericRequest(loadingRequest, realURL: realURL)
                }
            } catch {
                loadingRequest.finishLoading(with: error)
            }
        }
    }
    
    // MARK: - Manifest Handling
    
    private func handleManifestRequest(_ loadingRequest: AVAssetResourceLoadingRequest, realURL: URL) async throws {
        
        // 1. Download Manifest (using DPoP if needed, assuming the manifest endpoint is protected)
        let data = try await fetchWithDPoP(url: realURL)
        
        guard let manifestString = String(data: data, encoding: .utf8) else {
            throw URLError(.cannotDecodeContentData)
        }
        
        // 2. Rewrite Manifest
        // Base URL for resolving relative paths
        let baseURL = realURL.deletingLastPathComponent()
        
        var newLines: [String] = []
        
        manifestString.enumerateLines { line, _ in
            var processedLine = line
            
            // A. Rewrite KEY URIs to force interception
            // Format: #EXT-X-KEY:METHOD=AES-128,URI="https://..."
            if line.contains("#EXT-X-KEY") {
                if let range = line.range(of: "URI=\"") {
                     let rest = line[range.upperBound...]
                     if let endQuote = rest.firstIndex(of: "\"") {
                         let keyUriString = String(rest[..<endQuote])
                         // If it's already absolute http/s, replace scheme.
                         // If relative, make absolute first, then replace scheme.
                         if let keyURL = URL(string: keyUriString, relativeTo: baseURL) {
                             var keyComponents = URLComponents(url: keyURL, resolvingAgainstBaseURL: true)
                             keyComponents?.scheme = self.customScheme
                             if let newKeyUri = keyComponents?.string {
                                 processedLine = line.replacingOccurrences(of: keyUriString, with: newKeyUri)
                             }
                         }
                     }
                }
            }
            // B. Rewrite Segment URLs to Absolute HTTPS (to bypass interception)
            // Lines that are not tags (#) and not empty are URIs
            else if !line.hasPrefix("#") && !line.isEmpty {
                 if let segmentURL = URL(string: line, relativeTo: baseURL) {
                     // Ensure scheme is http/https
                     if segmentURL.scheme == "http" || segmentURL.scheme == "https" {
                         processedLine = segmentURL.absoluteString
                     }
                 }
            }
            
            newLines.append(processedLine)
        }
        
        let modifiedManifest = newLines.joined(separator: "\n")
        guard let modifiedData = modifiedManifest.data(using: .utf8) else {
            throw URLError(.cannotDecodeContentData)
        }
        
        // 3. Return Data
        loadingRequest.dataRequest?.respond(with: modifiedData)
        loadingRequest.finishLoading()
    }
    
    // MARK: - Key Handling
    
    private func handleKeyRequest(_ loadingRequest: AVAssetResourceLoadingRequest, realURL: URL) async throws {
        
        // Fetch with DPoP (This is what we came here for!)
        let data = try await fetchWithDPoP(url: realURL)
        
        loadingRequest.dataRequest?.respond(with: data)
        loadingRequest.finishLoading()
    }
    
    private func handleGenericRequest(_ loadingRequest: AVAssetResourceLoadingRequest, realURL: URL) async throws {
        // Just fetch and return
        let data = try await fetchWithDPoP(url: realURL)
        loadingRequest.dataRequest?.respond(with: data)
        loadingRequest.finishLoading()
    }
    
    // MARK: - Helpers
    
    private func fetchWithDPoP(url: URL) async throws -> Data {
        let accessToken = await FloatplaneAPI.shared.accessToken
        
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        
        // Add DPoP
        if let token = accessToken {
             if let dpopProof = try? DPoPManager.shared.generateProof(
                httpMethod: "GET",
                httpUrl: url.absoluteString,
                accessToken: token
             ) {
                 request.setValue(dpopProof, forHTTPHeaderField: "DPoP")
                 request.setValue("DPoP \(token)", forHTTPHeaderField: "Authorization")
             } else {
                 // Fallback
                 request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
             }
        }
        
        let (data, response) = try await session.data(for: request)
        
        if let httpResponse = response as? HTTPURLResponse, !(200...299).contains(httpResponse.statusCode) {
            throw URLError(.badServerResponse)
        }
        
        return data
    }
}
