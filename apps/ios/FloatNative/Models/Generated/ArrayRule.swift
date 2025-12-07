//
// ArrayRule.swift
// FloatNative
//
// Validation rule for OpenAPI-generated models
//

import Foundation

/// Validation rule for array constraints (used by OpenAPI generator)
public struct ArrayRule {
    public let minItems: Int?
    public let maxItems: Int?
    public let uniqueItems: Bool

    public init(minItems: Int?, maxItems: Int?, uniqueItems: Bool) {
        self.minItems = minItems
        self.maxItems = maxItems
        self.uniqueItems = uniqueItems
    }
}
