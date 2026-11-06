//
//  NetworkMonitor.swift
//  FloatNative
//
//  Whether the device has a connection, and how to tell an offline failure from
//  a real one. Being offline is an expected way to use the app now that videos
//  can be downloaded, so screens treat it differently from an error.
//

import Combine
import Foundation
import Network

extension Error {
    /// True when a request failed only because there's no connection. Always
    /// false on tvOS, which has no downloads to offer instead of an error.
    var isOffline: Bool {
        #if os(tvOS)
        return false
        #else
        switch self as Error {
        case let error as URLError:
            return [.notConnectedToInternet, .dataNotAllowed, .internationalRoamingOff].contains(error.code)
        case FloatplaneAPIError.networkError(let underlying):
            return underlying.isOffline
        case CompanionAPIError.networkError(let underlying):
            return underlying.isOffline
        default:
            return false
        }
        #endif
    }
}

#if !os(tvOS)
@MainActor
final class NetworkMonitor: ObservableObject {
    static let shared = NetworkMonitor()

    @Published private(set) var isOnline = true

    private let monitor = NWPathMonitor()

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let online = path.status == .satisfied
            Task { @MainActor in
                guard let self, self.isOnline != online else { return }
                self.isOnline = online
            }
        }
        monitor.start(queue: DispatchQueue(label: "com.coulterpeterson.floatnative.network-monitor"))
    }
}
#endif
