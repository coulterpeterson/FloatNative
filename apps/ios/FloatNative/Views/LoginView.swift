//
//  LoginView.swift
//  FloatNative
//
//  Login screen with liquid glass design
//  Created by Claude on 2025-10-08.
//

import SwiftUI
import AuthenticationServices
import CryptoKit

enum LoginMode {
    case qrCode
    case token
    case password
}

struct LoginView: View {
    @StateObject private var viewModel = LoginViewModel()
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    #if os(tvOS)
    @State private var loginMode: LoginMode = .qrCode
    #else
    @State private var loginMode: LoginMode = .token
    #endif

    var body: some View {
        ZStack {
            // Adaptive background
            if colorScheme == .dark {
                Color(red: 0.15, green: 0.15, blue: 0.15)
                    .ignoresSafeArea()
            } else {
                LinearGradient(
                    colors: [
                        Color(red: 0.95, green: 0.95, blue: 0.97),
                        Color(red: 0.90, green: 0.92, blue: 0.95),
                        Color(red: 0.85, green: 0.90, blue: 0.95)
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
            }

            // Floating orbs for depth
            GeometryReader { geometry in
                Circle()
                    .fill(colorScheme == .dark ? Color.blue.opacity(0.3) : Color.blue.opacity(0.15))
                    .blur(radius: 50)
                    .frame(width: 200, height: 200)
                    .offset(x: geometry.size.width * 0.1, y: geometry.size.height * 0.2)

                Circle()
                    .fill(colorScheme == .dark ? Color.purple.opacity(0.3) : Color.purple.opacity(0.15))
                    .blur(radius: 50)
                    .frame(width: 150, height: 150)
                    .offset(x: geometry.size.width * 0.7, y: geometry.size.height * 0.6)
            }

            ScrollView {
                VStack(spacing: 0) {
                    Spacer()
                        .frame(height: 60)

                    // Logo and title
                    VStack(spacing: 8) {
                        Image("Logo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 100, height: 100)
                            .clipShape(RoundedRectangle(cornerRadius: 20))
                            .shadow(color: .blue.opacity(0.5), radius: 20)

                        Text("FloatNative")
                            .font(.system(size: 40, weight: .bold))
                            .foregroundStyle(colorScheme == .dark ? .white : .primary)

                        Text("Sign in to continue")
                            .font(.subheadline)
                            .foregroundStyle(colorScheme == .dark ? .white.opacity(0.7) : .secondary)
                    }

                    // Login tabs
                    #if os(tvOS)
                        // tvOS: Device Flow only
                        VStack(spacing: 2) {
                            qrCodeLoginForm
                        }
                        .padding(.top, 20)
                        #else
                        // iOS: OAuth Login
                        VStack(spacing: 32) {
                            Text("Welcome to FloatNative")
                                .font(.title2)
                                .fontWeight(.bold)
                                .foregroundStyle(colorScheme == .dark ? .white : .primary)

                            // Main Login Button
                            Button {
                                Task {
                                    await viewModel.startIOSAuth()
                                }
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: "person.circle.fill")
                                        .font(.title3)
                                    Text("Log in with Floatplane")
                                        .fontWeight(.semibold)
                                }
                                .padding(.vertical, 8)
                                .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.borderedProminent)
                            .controlSize(.large)
                            .tint(.blue)
                            .shadow(color: .blue.opacity(0.3), radius: 10, x: 0, y: 5)

                            // Legacy Token Option (Hidden/Small)
                            /*
                            Button("Use Legacy Token") {
                                loginMode = .token
                            }
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            */
                        }
                        .padding(.horizontal, 32)
                        .padding(.vertical, 40)
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24))
                        .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: 5)
                        .padding(.horizontal)
                        .padding(.top, 40)
                        #endif
                    }

                    Spacer()
                }
                .padding()

            // Loading overlay
            if viewModel.isLoading {
                Color.black.opacity(0.5)
                    .ignoresSafeArea()

                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    .scaleEffect(1.5)
            }
        }
        .alert("Error", isPresented: .constant(viewModel.errorMessage != nil)) {
            Button("OK") {
                viewModel.errorMessage = nil
            }
        } message: {
            if let error = viewModel.errorMessage {
                Text(error)
            }
        }
        .onChange(of: viewModel.isAuthenticated) { _, newValue in
            if newValue {
                dismiss()
            }
        }
    }

    // MARK: - QR Code Login Form (Device Flow)

    #if os(tvOS)
    private var qrCodeLoginForm: some View {
        VStack(spacing: 2) {
            // Tab indicator
            Text("Log in with Floatplane")
                .font(.title3)
                .fontWeight(.semibold)
                .foregroundStyle(colorScheme == .dark ? .white : .primary)
                .padding(.bottom, 8)

            // Instructions
            if let session = viewModel.deviceCodeSession {
                VStack(spacing: 4) {
                    Text("Scan the QR code")
                        .font(.headline)
                        .foregroundStyle(colorScheme == .dark ? .white : .primary)
                }
                .multilineTextAlignment(.center)
            } else {
                 Text("Generating login code...")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            // QR Code & User Code
            if let session = viewModel.deviceCodeSession {
                VStack(spacing: 16) {
                    // QR Code
                    QRCodeView(url: session.verificationUriComplete, size: 300)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .shadow(radius: 5)


                    Text(session.userCode)
                        .font(.system(size: 40, weight: .bold, design: .monospaced))
                        .kerning(5) // Spacing between characters
                        .foregroundStyle(colorScheme == .dark ? .white : .black)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                        .background(colorScheme == .dark ? Color.white.opacity(0.1) : Color.black.opacity(0.05))
                        .clipShape(RoundedRectangle(cornerRadius: 8))

                    // Status message
                    HStack(spacing: 8) {
                        if viewModel.isPollingQRCode {
                            ProgressView()
                                .scaleEffect(0.8)
                        }
                        Text(viewModel.qrStatusMessage)
                            .font(.subheadline)
                            .foregroundStyle(colorScheme == .dark ? .white.opacity(0.8) : .secondary)
                    }


                }
            } else {
                // Loading state
                VStack(spacing: 20) {
                    ProgressView()
                        .scaleEffect(1.5)
                        .frame(width: 300, height: 300)
                    
                    Text("Connecting to Floatplane...")
                        .foregroundStyle(.secondary)
                }
            }

            // Refresh button (if expired)
            if let expiresAt = viewModel.qrExpiresAt,
               expiresAt.timeIntervalSinceNow < 0 {
                Button {
                    Task {
                        await viewModel.startTVAuth()
                    }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "arrow.clockwise")
                        Text("Get New Code")
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.card)
                .controlSize(.large)
            }
        }
        .padding(.horizontal, 32)
        .padding(.vertical, 24)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24))
        .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: 5)
        .padding(.horizontal)
        .frame(maxWidth: 800)
        .onAppear {
            Task {
                await viewModel.startTVAuth()
            }
        }
        .onDisappear {
            viewModel.stopQRCodePolling()
        }
    }
    #endif


}

// MARK: - View Model

@MainActor
class LoginViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var isAuthenticated = false

    // QR Code authentication (Device Flow)
    @Published var deviceCodeSession: DeviceCodeResponse?
    @Published var qrStatusMessage = "Waiting for scan..."
    @Published var isPollingQRCode = false
    @Published var qrExpiresAt: Date?

    private let api = FloatplaneAPI.shared
    private var pollingTask: Task<Void, Never>?

    // MARK: - TV Device Auth Flow

    func startTVAuth() async {
        #if os(tvOS)
        deviceCodeSession = nil
        qrStatusMessage = "Generating QR code..."
        errorMessage = nil

        do {
            // 1. Start Device Auth
            let session = try await api.startDeviceAuth()
            
            deviceCodeSession = session
            qrExpiresAt = Date().addingTimeInterval(TimeInterval(session.expiresIn))
            qrStatusMessage = ""
            
            // 2. Start Polling
            startDevicePolling(deviceCode: session.deviceCode, interval: session.interval)
        } catch {
            errorMessage = "Failed to generate QR code: \(error.localizedDescription)"
            qrStatusMessage = "Failed to generate QR code"
        }
        #endif
    }

    private func startDevicePolling(deviceCode: String, interval: Int) {
        stopQRCodePolling()
        isPollingQRCode = true
        
        pollingTask = Task {
            while !Task.isCancelled {
                do {
                    // Poll for token
                    _ = try await api.pollDeviceToken(deviceCode: deviceCode)
                    
                    // Success!
                    await MainActor.run {
                        self.isAuthenticated = true
                        self.isPollingQRCode = false
                        self.qrStatusMessage = "Login successful!"
                    }
                    return
                    
                } catch let error as FloatplaneAPIError {
                    // Handle specific OAuth errors
                    if case .httpError(_, let message) = error {
                        if message == "authorization_pending" {
                            // Continue polling
                        } else if message == "slow_down" {
                            // Increase polling interval
                            try? await Task.sleep(nanoseconds: UInt64((interval + 5) * 1_000_000_000))
                            continue
                        } else if message == "expired_token" {
                            await MainActor.run {
                                self.qrStatusMessage = "Code expired"
                                self.isPollingQRCode = false
                            }
                            return
                        } else {
                            // Other error
                            await MainActor.run {
                                self.errorMessage = "Login failed: \(message ?? "Unknown error")"
                                self.isPollingQRCode = false
                            }
                            return
                        }
                    } else if case .notAuthenticated = error {
                         // Should not happen during polling specifically unless endpoints invalid
                    } 
                    
                    // Default polling interval
                    try? await Task.sleep(nanoseconds: UInt64(interval * 1_000_000_000))
                    
                } catch {
                    // Generic error, retry
                    try? await Task.sleep(nanoseconds: UInt64(interval * 1_000_000_000))
                }
            }
        }
    }

    func stopQRCodePolling() {
        pollingTask?.cancel()
        pollingTask = nil
        isPollingQRCode = false
    }

    // MARK: - iOS OAuth Flow

    func startIOSAuth() async {
        #if !os(tvOS)
        isLoading = true
        errorMessage = nil

        do {
            // 1. Generate PKCE Verifier and Challenge
            let codeVerifier = generateCodeVerifier()
            let codeChallenge = generateCodeChallenge(from: codeVerifier)

            // 2. Construct Auth URL
            // Base: https://auth.floatplane.com/realms/floatplane/protocol/openid-connect/auth
            var components = URLComponents(string: "https://auth.floatplane.com/realms/floatplane/protocol/openid-connect/auth")!
            components.queryItems = [
                URLQueryItem(name: "client_id", value: "floatnative"),
                URLQueryItem(name: "response_type", value: "code"),
                URLQueryItem(name: "redirect_uri", value: "floatnative://auth"),
                URLQueryItem(name: "scope", value: "openid offline_access"),
                URLQueryItem(name: "code_challenge", value: codeChallenge),
                URLQueryItem(name: "code_challenge_method", value: "S256")
            ]

            guard let authURL = components.url else {
                throw FloatplaneAPIError.invalidURL
            }

            // 3. Start ASWebAuthenticationSession
            // Note: This needs to run on MainActor
            try await withCheckedThrowingContinuation(function: "startIOSAuth") { (continuation: CheckedContinuation<Void, Error>) in
                let session = ASWebAuthenticationSession(url: authURL, callbackURLScheme: "floatnative") { callbackURL, error in
                    if let error = error {
                        continuation.resume(throwing: error)
                        return
                    }

                    guard let callbackURL = callbackURL,
                          let components = URLComponents(url: callbackURL, resolvingAgainstBaseURL: true),
                          let code = components.queryItems?.first(where: { $0.name == "code" })?.value else {
                        continuation.resume(throwing: FloatplaneAPIError.invalidResponse)
                        return
                    }

                    // 4. Exchange Code for Token
                    Task {
                        do {
                            // Verify code with API
                            try await FloatplaneAPI.shared.exchangeAuthCode(
                                code: code,
                                verifier: codeVerifier,
                                redirectUri: "floatnative://auth"
                            )

                            // Update UI on success
                            await MainActor.run {
                                self.isAuthenticated = true
                                self.isLoading = false
                                continuation.resume()
                            }
                        } catch {
                            await MainActor.run {
                                self.errorMessage = "Failed to exchange token: \(error.localizedDescription)"
                                self.isLoading = false
                                continuation.resume(throwing: error)
                            }
                        }
                    }
                }



                // Start session
                session.presentationContextProvider = AuthenticationContextProvider.shared
                session.start()
            }
        } catch {
            errorMessage = "Login failed: \(error.localizedDescription)"
            isLoading = false
        }
        #endif
    }

    private func generateCodeVerifier() -> String {
        var buffer = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, buffer.count, &buffer)
        return Data(buffer).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
            .trimmingCharacters(in: .whitespaces)
    }

    private func generateCodeChallenge(from verifier: String) -> String {
        guard let data = verifier.data(using: .utf8) else { return "" }
        let hashed = SHA256.hash(data: data)
        return Data(hashed).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
            .trimmingCharacters(in: .whitespaces)
    }
}

// MARK: - Authentication Context Provider

#if !os(tvOS)
class AuthenticationContextProvider: NSObject, ASWebAuthenticationPresentationContextProviding {
    static let shared = AuthenticationContextProvider()
    
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        // Find the active window scene's key window
        let scene = UIApplication.shared.connectedScenes
            .first { $0.activationState == .foregroundActive } as? UIWindowScene
        return scene?.windows.first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }
}
#endif



#Preview {
    LoginView()
}
