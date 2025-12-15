//
//  DPoPManager.swift
//  FloatNative
//
//  Created by Claude on 2024-03-22.
//

import Foundation
import CryptoKit

enum DPoPError: Error {
    case keyGenerationFailed
    case signingFailed
    case invalidKeyData
}

class DPoPManager {
    static let shared = DPoPManager()
    
    private init() {}
    
    // MARK: - Key Management
    
    /// Get existing key or generate a new one
    func getOrGenerateKey() throws -> P256.Signing.PrivateKey {
        if let keyData = KeychainManager.shared.getDPoPKey() {
            // Updated to handle both generic password (String) and raw data if possible,
            // but our KeychainManager was updated to store Data.
            // Assuming KeychainManager APIs are updated to return Data as implemented previously.
            return try P256.Signing.PrivateKey(rawRepresentation: keyData)
        } else {
            let key = P256.Signing.PrivateKey()
            if KeychainManager.shared.saveDPoPKey(key.rawRepresentation) {
                return key
            } else {
                throw DPoPError.keyGenerationFailed
            }
        }
    }
    
    // MARK: - Proof Generation
    
    struct DPoPClaims: Encodable {
        let aud: String?    // Optional audience
        let iat: Int        // Issued At
        let jti: String     // Unique ID
        let htm: String     // HTTP Method
        let htu: String     // HTTP URL
        let ath: String?    // Access Token Hash (optional)
        let nonce: String?  // Server-provided nonce (optional)
    }
    
    struct JWKHeader: Encodable {
        let typ: String = "dpop+jwt"
        let alg: String = "ES256"
        let jwk: [String: String]
    }
    
    func generateProof(
        httpMethod: String,
        httpUrl: String,
        accessToken: String? = nil,
        nonce: String? = nil
    ) throws -> String {
        let privateKey = try getOrGenerateKey()
        let publicKey = privateKey.publicKey
        
        // 1. Create JWK Header
        // P-256 coordinates (x, y) need to be extracted.
        // Swift CryptoKit doesn't easily export to JWK map, doing it manually from raw representation or x963.
        // Basic JWK for ES256/P-256
        let x963 = publicKey.x963Representation
        // P-256 X9.63 format: 04 || X (32 bytes) || Y (32 bytes)
        let x = x963.subdata(in: 1..<33)
        let y = x963.subdata(in: 33..<65)
        
        let jwk: [String: String] = [
            "kty": "EC",
            "crv": "P-256",
            "x": base64UrlEncode(x),
            "y": base64UrlEncode(y)
        ]
        
        let header = JWKHeader(jwk: jwk)
        
        // 2. Create Payload (Claims)
        // Strip query params from HTU as per spec
        var htu = httpUrl
        if let url = URL(string: httpUrl), var components = URLComponents(url: url, resolvingAgainstBaseURL: true) {
            components.query = nil
            htu = components.string ?? httpUrl
        }
        
        var ath: String? = nil
        if let accessToken = accessToken {
            // ath: Base64url-encoded SHA-256 hash of the ASCII encoding of the access token value
            if let tokenData = accessToken.data(using: .ascii) {
                let hash = SHA256.hash(data: tokenData)
                ath = base64UrlEncode(Data(hash))
            }
        }
        
        let claims = DPoPClaims(
            aud: nil,
            iat: Int(Date().timeIntervalSince1970),
            jti: UUID().uuidString,
            htm: httpMethod,
            htu: htu,
            ath: ath,
            nonce: nonce
        )
        
        // 3. Encode and Sign
        let encoder = JSONEncoder()
        encoder.outputFormatting = .withoutEscapingSlashes // Crucial for URL safety in some backends
        
        let headerData = try encoder.encode(header)
        let payloadData = try encoder.encode(claims)
        
        let headerB64 = base64UrlEncode(headerData)
        let payloadB64 = base64UrlEncode(payloadData)
        
        let toSign = "\(headerB64).\(payloadB64)"
        guard let toSignData = toSign.data(using: .utf8) else { throw DPoPError.signingFailed }
        
        let signature = try privateKey.signature(for: toSignData)
        let signatureB64 = base64UrlEncode(signature.rawRepresentation)
        
        return "\(toSign).\(signatureB64)"
    }
    
    // MARK: - Helper
    
    private func base64UrlEncode(_ data: Data) -> String {
        return data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
