//
// JSONEncodable.swift
// FloatNative
//
// Protocol for OpenAPI-generated models
//

import Foundation

/// Protocol for types that can be encoded to JSON
public protocol JSONEncodable: Encodable {
}

/// Default implementation - all Encodable types conform
extension JSONEncodable {
    public func encodeToJSON() -> Data? {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return try? encoder.encode(self)
    }
}
