//
//  FloatNativeApp.swift
//  FloatNative
//
//  Created by Coulter Peterson on 2025-10-08.
//

import SwiftUI

@main
struct FloatNativeApp: App {
    #if os(tvOS)
    init() {
        TVLog.installGlobalObservers()
    }
    #endif

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
