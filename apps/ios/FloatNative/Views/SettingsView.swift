//
//  SettingsView.swift
//  FloatNative
//
//  User settings and account management
//

import SwiftUI

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var api = FloatplaneAPI.shared
    @ObservedObject private var themeManager = ThemeManager.shared
    @State private var showLogoutConfirmation = false

    // Enhanced LTT Search
    @AppStorage("enhancedLttSearchEnabled") private var enhancedLttSearchEnabled = false
    @State private var isLttOnlySubscriber = false
    @State private var isCheckingSubscriptions = true

    var body: some View {
        ZStack {
            Color.adaptiveBackground
                .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 32) {
                    // User Profile Section
                    if let user = api.currentUserDetails {
                        VStack(spacing: 16) {
                            // Profile Image
                            CachedAsyncImage(url: user.profileImage.fullURL) { image in
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Circle()
                                    .fill(Color.floatplaneGray.opacity(0.3))
                                    .overlay(
                                        Image(systemName: "person.fill")
                                            .font(.system(size: 40))
                                            .foregroundColor(.floatplaneGray)
                                    )
                            }
                            .frame(width: 100, height: 100)
                            .clipShape(Circle())

                            // Username
                            Text(user.username)
                                .font(.title2)
                                .fontWeight(.bold)
                                .foregroundColor(Color.adaptiveText)

                            // Email
                            Text(user.email)
                                .font(.subheadline)
                                .foregroundColor(Color.adaptiveSecondaryText)
                        }
                        .padding(.top, 32)
                    }

                    // Appearance Section
                    #if !os(tvOS)
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Appearance")
                            .font(.headline)
                            .foregroundColor(Color.adaptiveText)
                            .padding(.horizontal)

                        // Theme Selector Cards
                        HStack(spacing: 12) {
                            ForEach(ThemeMode.allCases, id: \.self) { mode in
                                ThemeCard(
                                    mode: mode,
                                    isSelected: themeManager.currentTheme == mode
                                ) {
                                    withAnimation(.spring(response: 0.3)) {
                                        themeManager.currentTheme = mode
                                    }
                                }
                            }
                        }
                        .padding(.horizontal)
                    }
                    .padding(.bottom, 24)
                    #endif

                    // Authentication Section
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Authentication")
                            .font(.headline)
                            .foregroundColor(Color.adaptiveText)
                            .padding(.horizontal)

                        // Auto Re-login Toggle
                        VStack(spacing: 0) {
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Auto Re-login")
                                        .font(.body)
                                        .foregroundColor(Color.adaptiveText)

                                    Text("Automatically log back in if your session expires")
                                        .font(.caption)
                                        .foregroundColor(Color.adaptiveSecondaryText)
                                }

                                Spacer()

                                Toggle("", isOn: $api.autoReloginEnabled)
                                    .labelsHidden()
                                    .onChange(of: api.autoReloginEnabled) { _, newValue in
                                        api.setAutoReloginEnabled(newValue)

                                        // Clear stored credentials if disabled
                                        if !newValue {
                                            KeychainManager.shared.clearCredentials()
                                        }
                                    }
                            }
                            .padding()
                            .background(Color.adaptiveSecondaryBackground)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                        .padding(.horizontal)
                    }
                    .padding(.bottom, 24)

                    // Search Section
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Search")
                            .font(.headline)
                            .foregroundColor(Color.adaptiveText)
                            .padding(.horizontal)

                        // Enhanced LTT Search Toggle
                        VStack(spacing: 0) {
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Use Enhanced LTT Search")
                                        .font(.body)
                                        .foregroundColor(Color.adaptiveText)

                                    if isCheckingSubscriptions {
                                        HStack(spacing: 6) {
                                            ProgressView()
                                                .scaleEffect(0.7)
                                            Text("Checking subscriptions...")
                                                .font(.caption)
                                                .foregroundColor(Color.adaptiveSecondaryText)
                                        }
                                    } else if isLttOnlySubscriber {
                                        Text("Only applies if your only Floatplane subscription is to LTT and no other creators")
                                            .font(.caption)
                                            .foregroundColor(Color.adaptiveSecondaryText)
                                    } else {
                                        Text("Not available: You are subscribed to multiple creators or not subscribed to LTT")
                                            .font(.caption)
                                            .foregroundColor(.orange)
                                    }
                                }

                                Spacer()

                                Toggle("", isOn: $enhancedLttSearchEnabled)
                                    .labelsHidden()
                                    .disabled(!isLttOnlySubscriber)
                            }
                            .padding()
                            .background(Color.adaptiveSecondaryBackground)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                        .padding(.horizontal)
                    }
                    .padding(.bottom, 24)

                    // Send Feedback Button
                    #if !os(tvOS)
                    Link(destination: URL(string: "mailto:contact@maplespace.ca")!) {
                        HStack {
                            Image(systemName: "envelope.fill")
                                .font(.title3)

                            Text("Send Feedback")
                                .font(.body)
                                .fontWeight(.semibold)
                        }
                        .foregroundColor(.floatplaneBlue)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                    }
                    .liquidGlass(tint: .floatplaneBlue, opacity: 0.6)
                    .padding(.horizontal)
                    #endif

                    // YouTube Subscribe Button
                    #if !os(tvOS)
                    Link(destination: URL(string: "https://www.youtube.com/@CoulterPeterson?sub_confirmation=1")!) {
                        HStack {
                            Image(systemName: "play.rectangle.fill")
                                .font(.title3)

                            Text("Say Thanks By Subscribing :)")
                                .font(.body)
                                .fontWeight(.semibold)
                        }
                        .foregroundColor(.yellow)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                    }
                    .liquidGlass(tint: .yellow, opacity: 0.6)
                    .padding(.horizontal)
                    #endif

                    // tvOS-only YouTube Subscribe Text
                    #if os(tvOS)
                    HStack(spacing: 12) {
                        Image(systemName: "play.rectangle.fill")
                            .font(.title2)
                            .foregroundColor(.red)

                        Text("Say thanks by subscribing to the Coulter Peterson channel :)")
                            .font(.body)
                            .foregroundColor(Color.adaptiveText)

                        Spacer()
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 8)
                    #endif

                    // Logout Button
                    Button {
                        showLogoutConfirmation = true
                    } label: {
                        HStack {
                            Image(systemName: "arrow.right.square.fill")
                                .font(.title3)

                            Text("Logout")
                                .font(.body)
                                .fontWeight(.semibold)
                        }
                        .foregroundColor(.red)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                    }
                    #if os(tvOS)
                    .buttonStyle(.card)
                    .padding(.horizontal, 40)  // Match tvOS safe zones
                    #else
                    .liquidGlass(tint: .red, opacity: 0.6)
                    .padding(.horizontal)
                    #endif
                    .padding(.bottom, 32)
                }
            }
        }
        #if os(tvOS)
        .navigationTitle("")
        #else
        .navigationTitle("Settings")
        #endif
        .toolbarBackground(.visible, for: .navigationBar)
        .globalMenu()
        .task {
            // Fetch current user info when view appears
            if api.currentUserDetails == nil {
                _ = try? await api.getCurrentUser()
            }

            // Check if user is LTT-only subscriber
            await checkLttOnlySubscription()
        }
        .alert("Logout", isPresented: $showLogoutConfirmation) {
            Button("Cancel", role: .cancel) {}
            Button("Logout", role: .destructive) {
                Task {
                    await logout()
                }
            }
        } message: {
            Text("Are you sure you want to logout?")
        }
    }

    private func logout() async {
        do {
            try await api.logout()
            // Clear stored credentials from Keychain on explicit logout
            KeychainManager.shared.clearCredentials()
            // No need to dismiss - ContentView will automatically switch to LoginView
            // when api.isAuthenticated becomes false
        } catch {
            // Handle error (could show alert)
            print("Logout error: \(error.localizedDescription)")
        }
    }

    private func checkLttOnlySubscription() async {
        let LTT_CREATOR_ID = "59f94c0bdd241b70349eb72b"

        do {
            let subscriptions = try await api.getSubscriptions()

            await MainActor.run {
                // Check if user is ONLY subscribed to LTT
                isLttOnlySubscriber = subscriptions.count == 1 &&
                                     subscriptions.first?.creator == LTT_CREATOR_ID

                // Mark that we've checked the subscription status (don't auto-enable anymore)
                if !UserDefaults.standard.bool(forKey: "enhancedLttSearchChecked") {
                    UserDefaults.standard.set(true, forKey: "enhancedLttSearchChecked")
                }

                isCheckingSubscriptions = false
            }
        } catch {
            await MainActor.run {
                // If we fail to check, disable the feature
                isLttOnlySubscriber = false
                isCheckingSubscriptions = false
                print("Failed to check LTT subscription status: \(error)")
            }
        }
    }
}

// MARK: - Theme Card

struct ThemeCard: View {
    let mode: ThemeMode
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 12) {
                // Icon
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.floatplaneGray.opacity(0.2))
                        .frame(height: 80)

                    Image(systemName: mode.icon)
                        .font(.system(size: 32))
                        .foregroundColor(isSelected ? .floatplaneBlue : Color.adaptiveText)
                }

                // Label
                Text(mode.displayName)
                    .font(.caption)
                    .fontWeight(isSelected ? .semibold : .regular)
                    .foregroundColor(isSelected ? .floatplaneBlue : Color.adaptiveText)
            }
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(isSelected ? Color.floatplaneBlue : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

#Preview {
    NavigationStack {
        SettingsView()
    }
}
