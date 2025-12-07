//
//  CreatePlaylistView.swift
//  FloatNative
//
//  Sheet view for creating a new playlist
//

import SwiftUI

struct CreatePlaylistView: View {
    @State private var playlistName = ""
    @State private var isCreating = false
    @Environment(\.dismiss) private var dismiss

    let onCreate: (String) async -> Void

    var body: some View {
        NavigationView {
            Form {
                Section {
                    TextField("Playlist Name", text: $playlistName)
                        .autocorrectionDisabled()
                }
            }
            .navigationTitle("New Playlist")
            #if !os(tvOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                    .disabled(isCreating)
                }

                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task {
                            isCreating = true
                            await onCreate(playlistName)
                            dismiss()
                        }
                    } label: {
                        if isCreating {
                            ProgressView()
                                .tint(.floatplaneBlue)
                        } else {
                            Text("Create")
                        }
                    }
                    .disabled(playlistName.isEmpty || isCreating)
                }
            }
        }
    }
}

#Preview {
    CreatePlaylistView { name in
        print("Creating playlist: \(name)")
    }
}
