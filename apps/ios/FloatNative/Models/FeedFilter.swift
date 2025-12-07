//
//  FeedFilter.swift
//  FloatNative
//
//  Filter model for VideoFeedView
//

import Foundation

enum FeedFilter: Equatable, Hashable {
    case all
    case creator(id: String, name: String, icon: ImageModel)
    case channel(id: String, name: String, icon: ImageModel, creatorId: String)

    var isFiltered: Bool {
        switch self {
        case .all:
            return false
        case .creator, .channel:
            return true
        }
    }

    var displayName: String {
        switch self {
        case .all:
            return "All Videos"
        case .creator(_, let name, _):
            return name
        case .channel(_, let name, _, _):
            return name
        }
    }

    var icon: ImageModel? {
        switch self {
        case .all:
            return nil
        case .creator(_, _, let icon):
            return icon
        case .channel(_, _, let icon, _):
            return icon
        }
    }
}
