//
//  FloatNativeApp.swift
//  FloatNative
//
//  Created by Coulter Peterson on 2025-10-08.
//

import SwiftUI

#if !os(tvOS)
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // Reconnects to any downloads still running from a previous launch.
        _ = DownloadManager.shared
        DownloadProgressStore.shared.startMonitoring()
        return true
    }

    func application(_ application: UIApplication, handleEventsForBackgroundURLSession identifier: String, completionHandler: @escaping () -> Void) {
        DownloadManager.shared.backgroundEventsCompletion = completionHandler
    }
}
#endif

@main
struct FloatNativeApp: App {
    #if os(tvOS)
    init() {
        TVLog.installGlobalObservers()
    }
    #else
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    #endif

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
