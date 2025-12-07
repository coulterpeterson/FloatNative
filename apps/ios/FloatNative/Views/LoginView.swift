//
//  LoginView.swift
//  FloatNative
//
//  Login screen with liquid glass design
//  Created by Claude on 2025-10-08.
//

import SwiftUI

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
                VStack(spacing: 32) {
                    Spacer()
                        .frame(height: 60)

                    // Logo and title
                    VStack(spacing: 16) {
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
                    if viewModel.needs2FA {
                        twoFactorForm
                    } else {
                        #if os(tvOS)
                        // tvOS: Native segmented picker with conditional rendering
                        VStack(spacing: 20) {
                            Picker("", selection: $loginMode) {
                                Text("QR Code").tag(LoginMode.qrCode)
                                Text("Token").tag(LoginMode.token)
                                //Text("Password").tag(LoginMode.password)
                            }
                            .pickerStyle(.segmented)
                            .padding(.horizontal, 40)

                            if loginMode == .qrCode {
                                qrCodeLoginForm
                            } else { // if loginMode == .token
                                tokenLoginForm
                            } /*else {
                                passwordLoginForm
                            }*/
                        }
                        .padding(.top, 20)
                        #else
                        // iOS: Swipeable page-style tabs
                        TabView(selection: $loginMode) {
                            tokenLoginForm
                                .tag(LoginMode.token)

                            // passwordLoginForm
                            //     .tag(LoginMode.password)
                        }
                        .tabViewStyle(.page(indexDisplayMode: .always))
                        .frame(height: 500)
                        .padding(.top, 20)
                        #endif
                    }

                    Spacer()
                }
                .padding()
            }

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

    // MARK: - QR Code Login Form

    private var qrCodeLoginForm: some View {
        VStack(spacing: 24) {
            // Tab indicator
            Text("QR Code Login")
                .font(.title3)
                .fontWeight(.semibold)
                .foregroundStyle(colorScheme == .dark ? .white : .primary)
                .padding(.bottom, 8)

            // Instructions
            Text("Scan this QR code with your phone")
                .font(.subheadline)
                .foregroundStyle(colorScheme == .dark ? .white.opacity(0.7) : .secondary)
                .multilineTextAlignment(.center)

            // QR Code
            if let qrSession = viewModel.qrSession {
                VStack(spacing: 20) {
                    QRCodeView(url: qrSession.loginUrl, size: 300)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .shadow(radius: 5)

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

                    // Expiration timer
                    if let expiresAt = viewModel.qrExpiresAt {
                        let timeRemaining = Int(expiresAt.timeIntervalSinceNow)
                        if timeRemaining > 0 {
                            Text("Expires in \(timeRemaining)s")
                                .font(.caption)
                                .foregroundStyle(.orange)
                        } else {
                            Text("QR code expired")
                                .font(.caption)
                                .foregroundStyle(.red)
                        }
                    }
                }
            } else {
                // Loading state
                ProgressView()
                    .scaleEffect(1.5)
                    .frame(width: 300, height: 300)
            }

            // Refresh button (if expired)
            if let expiresAt = viewModel.qrExpiresAt,
               expiresAt.timeIntervalSinceNow < 0 {
                Button {
                    Task {
                        await viewModel.generateQRCode()
                    }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "arrow.clockwise")
                        Text("Generate New QR Code")
                    }
                    .frame(maxWidth: .infinity)
                }
                #if os(tvOS)
                .buttonStyle(.card)
                #else
                .buttonStyle(.borderedProminent)
                #endif
                .controlSize(.large)
            }
        }
        .padding(.horizontal, 32)
        .padding(.vertical, 40)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24))
        .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: 5)
        .padding(.horizontal)
        #if os(tvOS)
        .frame(maxWidth: 800)
        #endif
        .onAppear {
            Task {
                await viewModel.generateQRCode()
            }
        }
        .onDisappear {
            viewModel.stopQRCodePolling()
        }
    }

    // MARK: - Token Login Form

    private var tokenLoginForm: some View {
        VStack(spacing: 20) {
            // Tab indicator
            Text("Token Login")
                .font(.title3)
                .fontWeight(.semibold)
                .foregroundStyle(colorScheme == .dark ? .white : .primary)
                .padding(.bottom, 8)

            // Instructions
            VStack(alignment: .leading, spacing: 12) {
                Text("How to get your token:")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundStyle(colorScheme == .dark ? .white : .black)

                VStack(alignment: .leading, spacing: 6) {
                    HStack(alignment: .top, spacing: 8) {
                        Text("1.")
                            .fontWeight(.medium)
                        Text("Log into Floatplane.com in Safari/Chrome")
                    }

                    HStack(alignment: .top, spacing: 8) {
                        Text("2.")
                            .fontWeight(.medium)
                        Text("Open DevTools (right-click → Inspect)")
                    }

                    HStack(alignment: .top, spacing: 8) {
                        Text("3.")
                            .fontWeight(.medium)
                        Text("Go to Application → Cookies → floatplane.com")
                    }

                    HStack(alignment: .top, spacing: 8) {
                        Text("4.")
                            .fontWeight(.medium)
                        Text("Copy the 'sails.sid' cookie value")
                    }
                }
                .font(.caption)
                .foregroundStyle(colorScheme == .dark ? Color.white.opacity(0.9) : .black)
            }
            .padding()
            .background(Color.secondary.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: 12))

            // Token field
            VStack(alignment: .leading, spacing: 8) {
                Text("Authentication Token")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                TextField("Paste sails.sid token here", text: $viewModel.authToken)
                    #if os(tvOS)
                    .textFieldStyle(.automatic)
                    #else
                    .textFieldStyle(.roundedBorder)
                    #endif
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }

            // Login button
            Button {
                Task {
                    await viewModel.tokenLogin()
                }
            } label: {
                HStack(spacing: 8) {
                    Text("Login with Token")
                    Image(systemName: "arrow.right")
                }
                .frame(maxWidth: .infinity)
            }
            #if os(tvOS)
            .buttonStyle(.card)
            #else
            .buttonStyle(.borderedProminent)
            #endif
            .controlSize(.large)
            .padding(.top, 16)
            .disabled(viewModel.authToken.isEmpty)
        }
        .padding(.horizontal, 32)
        .padding(.vertical, 40)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24))
        .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: 5)
        .padding(.horizontal)
        #if os(tvOS)
        .frame(maxWidth: 700)
        #endif
    }

    // MARK: - Password Login Form

    private var passwordLoginForm: some View {
        VStack(spacing: 20) {
            // Tab indicator
            Text("Password Login")
                .font(.title3)
                .fontWeight(.semibold)
                .foregroundStyle(colorScheme == .dark ? .white : .primary)
                .padding(.bottom, 8)

            // Username field
            VStack(alignment: .leading, spacing: 8) {
                Text("Username or Email")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                TextField("", text: $viewModel.username)
                    #if os(tvOS)
                    .textFieldStyle(.automatic)
                    #else
                    .textFieldStyle(.roundedBorder)
                    #endif
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }

            // Password field
            VStack(alignment: .leading, spacing: 8) {
                Text("Password")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                SecureField("", text: $viewModel.password)
                    #if os(tvOS)
                    .textFieldStyle(.automatic)
                    #else
                    .textFieldStyle(.roundedBorder)
                    #endif
                    .textInputAutocapitalization(.never)
            }

            // Login button
            Button {
                Task {
                    await viewModel.login()
                }
            } label: {
                HStack(spacing: 8) {
                    Text("Sign In")
                    Image(systemName: "arrow.right")
                }
                .frame(maxWidth: .infinity)
            }
            #if os(tvOS)
            .buttonStyle(.card)
            #else
            .buttonStyle(.borderedProminent)
            #endif
            .controlSize(.large)
            .padding(.top, 16)
            .disabled(viewModel.username.isEmpty || viewModel.password.isEmpty)
        }
        .padding(.horizontal, 32)
        .padding(.vertical, 40)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24))
        .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: 5)
        .padding(.horizontal)
        #if os(tvOS)
        .frame(maxWidth: 700)
        #endif
    }

    // MARK: - 2FA Form

    private var twoFactorForm: some View {
        VStack(spacing: 20) {
            Image(systemName: "lock.shield")
                .font(.system(size: 50))
                .foregroundStyle(.blue)

            Text("Two-Factor Authentication")
                .font(.headline)
                .foregroundStyle(.primary)

            Text("Enter the code from your authenticator app")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            // 2FA code field
            TextField("", text: $viewModel.twoFactorCode)
                #if os(tvOS)
                .textFieldStyle(.automatic)
                #else
                .textFieldStyle(.roundedBorder)
                #endif
                .textInputAutocapitalization(.never)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.center)
                .font(.system(size: 24, weight: .semibold))

            // Verify button
            Button {
                Task {
                    await viewModel.verify2FA()
                }
            } label: {
                HStack(spacing: 8) {
                    Text("Verify")
                    Image(systemName: "checkmark")
                }
                .frame(maxWidth: .infinity)
            }
            #if os(tvOS)
            .buttonStyle(.card)
            #else
            .buttonStyle(.borderedProminent)
            #endif
            .controlSize(.large)
            .padding(.top, 16)
            .disabled(viewModel.twoFactorCode.isEmpty)

            // Back button
            Button("Back to login") {
                viewModel.needs2FA = false
                viewModel.twoFactorCode = ""
            }
            .font(.subheadline)
            .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 32)
        .padding(.vertical, 40)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24))
        .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: 5)
        .padding(.horizontal)
        #if os(tvOS)
        .frame(maxWidth: 700)
        #endif
    }
}

// MARK: - View Model

@MainActor
class LoginViewModel: ObservableObject {
    @Published var username = ""
    @Published var password = ""
    @Published var twoFactorCode = ""
    @Published var authToken = ""
    @Published var needs2FA = false
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var isAuthenticated = false

    // QR Code authentication
    @Published var qrSession: QRCodeGenerateResponse?
    @Published var qrStatusMessage = "Waiting for scan..."
    @Published var isPollingQRCode = false
    @Published var qrExpiresAt: Date?

    private let api = FloatplaneAPI.shared
    private let companionAPI = CompanionAPI.shared
    private var pollingTask: Task<Void, Never>?

    func login() async {
        guard !username.isEmpty && !password.isEmpty else { return }

        isLoading = true
        errorMessage = nil

        do {
            let response = try await api.login(
                username: username,
                password: password
            )

            if response.needs2FA {
                needs2FA = true
                isLoading = false
            } else {
                // Successfully logged in - store credentials in Keychain if auto re-login is enabled
                if api.autoReloginEnabled {
                    let saved = KeychainManager.shared.saveCredentials(
                        username: username,
                        password: password
                    )
                    if !saved {
                        print("⚠️ Failed to save credentials to Keychain")
                    }
                }

                isAuthenticated = true
                isLoading = false
            }
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
        }
    }

    func verify2FA() async {
        guard !twoFactorCode.isEmpty else { return }

        isLoading = true
        errorMessage = nil

        do {
            _ = try await api.verify2FA(token: twoFactorCode)

            // Successfully completed 2FA - store credentials in Keychain if auto re-login is enabled
            if api.autoReloginEnabled {
                let saved = KeychainManager.shared.saveCredentials(
                    username: username,
                    password: password
                )
                if !saved {
                    print("⚠️ Failed to save credentials to Keychain")
                }
            }

            isAuthenticated = true
            isLoading = false
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
        }
    }

    func tokenLogin() async {
        guard !authToken.isEmpty else { return }

        isLoading = true
        errorMessage = nil

        do {
            try await api.loginWithToken(authToken)
            isAuthenticated = true
            isLoading = false
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
        }
    }

    // MARK: - QR Code Authentication

    func generateQRCode() async {
        qrSession = nil
        qrStatusMessage = "Generating QR code..."
        errorMessage = nil

        do {
            // Generate QR code session
            #if os(tvOS)
            let deviceInfo = "Apple TV"
            #else
            let deviceInfo = "iOS Device"
            #endif

            let session = try await companionAPI.generateQRCode(deviceInfo: deviceInfo)

            qrSession = session
            qrExpiresAt = session.expiresAt
            qrStatusMessage = "Scan QR code with your phone"

            // Start polling
            startQRCodePolling()
        } catch {
            errorMessage = "Failed to generate QR code: \(error.localizedDescription)"
            qrStatusMessage = "Failed to generate QR code"
        }
    }

    func startQRCodePolling() {
        guard let sessionId = qrSession?.sessionId else {
            return
        }

        // Cancel any existing polling task
        stopQRCodePolling()

        isPollingQRCode = true

        pollingTask = Task {
            while !Task.isCancelled {
                do {
                    let response = try await companionAPI.pollQRCode(sessionId: sessionId)

                    switch response.status {
                    case .pending:
                        qrStatusMessage = response.message
                        // Poll every 2 seconds
                        try? await Task.sleep(nanoseconds: 2_000_000_000)

                    case .completed:
                        // Login successful - we got the token
                        qrStatusMessage = "Logging in..."
                        isPollingQRCode = false

                        // Store the API key for companion API access
                        if let apiKey = response.apiKey {
                            _ = KeychainManager.shared.saveAPIKey(apiKey)
                        }

                        // Use the sails.sid token to login to Floatplane
                        // Run login in a detached task so it won't be cancelled if polling stops
                        let tokenToUse = response.sailsSid ?? response.apiKey
                        if let sailsSid = tokenToUse {

                            // Use detached task to prevent cancellation when polling task is cancelled
                            Task.detached { [weak self] in
                                guard let self = self else { return }

                                do {
                                    try await self.api.loginWithToken(sailsSid)

                                    // Update UI state on MainActor
                                    await MainActor.run {
                                        self.qrStatusMessage = "Login successful!"
                                        self.isAuthenticated = true
                                    }
                                } catch {
                                    // Update UI state on MainActor
                                    await MainActor.run {
                                        self.errorMessage = "Failed to complete login: \(error.localizedDescription)"
                                        self.qrStatusMessage = "Login failed"
                                    }
                                }
                            }
                        } else {
                            errorMessage = "No token received from QR code session"
                            qrStatusMessage = "Login failed"
                        }

                        return

                    case .expired:
                        qrStatusMessage = "QR code expired"
                        isPollingQRCode = false
                        return
                    }
                } catch {
                    // Network error - retry after a short delay
                    try? await Task.sleep(nanoseconds: 2_000_000_000)
                }
            }
        }
    }

    func stopQRCodePolling() {
        pollingTask?.cancel()
        pollingTask = nil
        isPollingQRCode = false
    }
}

#Preview {
    LoginView()
}
