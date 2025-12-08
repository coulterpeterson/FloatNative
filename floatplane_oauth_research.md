# Floatplane OAuth Research & Implementation Plan

## Executive Summary

Based on the analysis of `Wasserflug-tvOS` and `Hydravion-AndroidTV`, **we recommend a Hybrid Authentication Strategy** for FloatNative:

*   **iOS (iPhone/iPad)**: Use the **Authorization Code Flow with PKCE**. This is the industry standard for mobile apps, offering the best security and user experience (integrating with password managers and system web credentials).
*   **tvOS**: Use the **Device Authorization Flow** (QR Code). This is the standard for TV interfaces where text input is difficult, matching the implementation in `Wasserflug` and `Hydravion`.

---

## 1. Configuration Details

All clients authenticate against the Floatplane Keycloak instance.

### Base Configuration
*   **Base URL**: `https://auth.floatplane.com`
*   **Realm**: `floatplane`
*   **Issuer**: `https://auth.floatplane.com/realms/floatplane`
*   **Discovery Endpoint**: `https://auth.floatplane.com/realms/floatplane/.well-known/openid-configuration`

### Endpoints
*   **Authorization Endpoint**: `/realms/floatplane/protocol/openid-connect/auth`
*   **Token Endpoint**: `/realms/floatplane/protocol/openid-connect/token`
*   **Device Authorization Endpoint**: `/realms/floatplane/protocol/openid-connect/auth/device`
*   **Revocation Endpoint**: `/realms/floatplane/protocol/openid-connect/revoke`

### Client IDs
Existing 3rd party clients use specific IDs. You should likely register `floatnative` or use a generic one if available.
*   `Wasserflug` uses: `wasserflug`
*   `Hydravion` uses: `hydravion`

---

## 2. iOS Implementation (Authorization Code Flow)

For the iOS app, do **not** use the Device Code (QR) flow. It provides a poor user experience on a handheld device.

**Recommended Library**: `AuthenticationServices` (Native iOS) or `AppAuth-iOS`.

### Technical Flow
1.  **Generate PKCE Verifier & Challenge**:
    *   Create a random `code_verifier`.
    *   Hash it (S256) to get `code_challenge`.
2.  **Authorize Request**:
    *   Open `ASWebAuthenticationSession` with URL:
        ```text
        https://auth.floatplane.com/realms/floatplane/protocol/openid-connect/auth
        ?client_id=[YOUR_CLIENT_ID]
        &response_type=code
        &redirect_uri=[YOUR_REDIRECT_URI]
        &scope=openid offline_access
        &code_challenge=[CHALLENGE]
        &code_challenge_method=S256
        ```
    *   **Note**: `scope=offline_access` is critical to receive a **Refresh Token**.
3.  **Exchange Code for Token**:
    *   Parse `code` from the callback URL.
    *   POST to Token Endpoint:
        ```text
        grant_type=authorization_code
        &client_id=[YOUR_CLIENT_ID]
        &code=[CODE]
        &redirect_uri=[YOUR_REDIRECT_URI]
        &code_verifier=[VERIFIER]
        ```
4.  **Store Tokens**: Securely store `access_token` and `refresh_token` in Keychain.

---

## 3. tvOS Implementation (Device Code Flow)

For tvOS, replicate the logic found in `Wasserflug` and `Hydravion`.

### Technical Flow
1.  **Initiate Device Auth**:
    *   POST to Device Authorization Endpoint:
        ```text
        client_id=[YOUR_CLIENT_ID]
        &scope=openid offline_access
        ```
    *   Response includes: `device_code`, `user_code`, `verification_uri_complete`, `expires_in`, `interval`.
2.  **Display to User**:
    *   Show `user_code` and generate a QR Code from `verification_uri_complete`.
3.  **Poll for Token**:
    *   Poll the Token Endpoint every `interval` seconds:
        ```text
        grant_type=urn:ietf:params:oauth:grant-type:device_code
        &client_id=[YOUR_CLIENT_ID]
        &device_code=[DEVICE_CODE]
        ```
    *   Handle `authorization_pending` (keep polling) and `slow_down` (increase interval).
4.  **Success**:
    *   Receive `access_token` and `refresh_token`.

---

## 4. Reference Findings

### Wasserflug-tvOS
*   **Repo**: `/FlowNative-resources/Wasserflug-tvOS`
*   **Details**: Uses pure Device Flow.
*   **Docs**: Contains a comprehensive guide in `floatplane-oauth.md`.
*   **Code**: `FloatplaneAPIClient` manages the polling loop.

### Hydravion-AndroidTV
*   **Repo**: `/FloatNative-resources/Hydravion-AndroidTV`
*   **Details**: Uses Device Flow. verified in `QrLoginActivity.java`.
*   **Key Code**:
    ```java
    // QrLoginActivity.java
    params.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
    params.put("client_id", "hydravion");
    ```
