//
//  StreamOfflineOverlay.swift
//  FloatNative
//
//  Stream offline overlay shown when a livestream ends
//

import SwiftUI

struct StreamOfflineOverlay: View {
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            // Semi-transparent background
            Color.black.opacity(0.8)
                .ignoresSafeArea()

            VStack(spacing: 24) {
                // Icon
                Image(systemName: "antenna.radiowaves.left.and.right.slash")
                    .font(.system(size: 60))
                    .foregroundStyle(.white)

                // Title
                Text("Stream Offline")
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundStyle(.white)

                // Message
                Text("This livestream has ended")
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.8))
                    .multilineTextAlignment(.center)

                // Return button
                Button {
                    onDismiss()
                } label: {
                    Text("Return to Feed")
                        .font(.headline)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 32)
                        .padding(.vertical, 12)
                        .background(Color.floatplaneBlue)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                #if os(tvOS)
                .buttonStyle(.card)
                #endif
            }
            .padding()
        }
    }
}

#Preview {
    StreamOfflineOverlay(onDismiss: {})
}
