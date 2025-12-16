import { DPoPManager } from "./dpop";

// Helper for random strings
function generateRandomString(length: number = 21): string {
  const array = new Uint8Array(length);
  crypto.getRandomValues(array);
  return Array.from(array, dec => ('0' + dec.toString(16)).substr(-2)).join('');
}

const AUTH_BASE_URL = "https://auth.floatplane.com";
const FLOATPLANE_BASE_URL = "https://www.floatplane.com";
const CLIENT_ID = "floatnative"; // Updated to match iOS
// Configuration
// Configuration at lines 8-10 is kept.
const TOKEN_ENDPOINT = `${AUTH_BASE_URL}/realms/floatplane/protocol/openid-connect/token`;
const AUTH_ENDPOINT = `${AUTH_BASE_URL}/realms/floatplane/protocol/openid-connect/auth`;

// Storage Keys
const KEY_ACCESS_TOKEN = "fp_access_token";
const KEY_REFRESH_TOKEN = "fp_refresh_token";
const KEY_EXPIRES_AT = "fp_expires_at";

export class AuthService {
  private static instance: AuthService;

  private constructor() { }

  static getInstance(): AuthService {
    if (!AuthService.instance) {
      AuthService.instance = new AuthService();
    }
    return AuthService.instance;
  }

  // --- PKCE Auth Flow ---

  // Helper to generate Code Verifier
  private generateCodeVerifier(): string {
    const array = new Uint8Array(32);
    crypto.getRandomValues(array);
    return this.base64UrlEncode(array);
  }

  // Helper to generate Code Challenge
  private async generateCodeChallenge(verifier: string): Promise<string> {
    const encoder = new TextEncoder();
    const data = encoder.encode(verifier);
    const hash = await crypto.subtle.digest("SHA-256", data);
    return this.base64UrlEncode(new Uint8Array(hash));
  }

  private base64UrlEncode(array: Uint8Array): string {
    let str = "";
    const bytes = Array.from(array);
    for (const b of bytes) {
      str += String.fromCharCode(b);
    }
    return btoa(str)
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=+$/, "");
  }


  async startAuthFlow(): Promise<boolean> {
    try {
      // 1. Get Redirect URI dynamically
      const redirectUri = chrome.identity.getRedirectURL();
      console.log("Expected Redirect URI:", "https://cnjjpkfgigpedhakhcaoanjdjcccajci.chromiumapp.org/");
      console.log("Actual Redirect URI:  ", redirectUri);

      if (!redirectUri.includes("cnjjpkfgigpedhakhcaoanjdjcccajci")) {
        console.warn("WARNING: Extension ID mismatch. Auth will likely fail at the provider level or browser level.");
      }

      // 2. Generate PKCE
      const verifier = this.generateCodeVerifier();
      const challenge = await this.generateCodeChallenge(verifier);

      // 3. Construct Auth URL
      const authUrl = new URL(AUTH_ENDPOINT);
      authUrl.searchParams.set("client_id", CLIENT_ID);
      authUrl.searchParams.set("response_type", "code");
      authUrl.searchParams.set("redirect_uri", redirectUri);
      authUrl.searchParams.set("scope", "openid offline_access");
      authUrl.searchParams.set("code_challenge", challenge);
      authUrl.searchParams.set("code_challenge_method", "S256");

      console.log("Launching Web Auth Flow:", authUrl.toString());

      // 4. Launch Web Auth Flow
      const responseUrl = await chrome.identity.launchWebAuthFlow({
        url: authUrl.toString(),
        interactive: true
      });

      if (chrome.runtime.lastError || !responseUrl) {
        throw new Error(chrome.runtime.lastError?.message || "Auth failed (no redirect URL)");
      }

      // 5. Extract Code
      // URL will look like: https://<app-id>.chromiumapp.org/?code=...
      const url = new URL(responseUrl);
      const code = url.searchParams.get("code");

      if (!code) {
        throw new Error("No code found in redirect URL");
      }

      // 6. Exchange Code for Token
      await this.exchangeAuthCode(code, verifier, redirectUri);
      return true;

    } catch (e: any) {
      console.error("Auth Flow Error:", e);
      throw e;
    }
  }

  private async exchangeAuthCode(code: string, verifier: string, redirectUri: string): Promise<void> {
    const dpopProof = await DPoPManager.getInstance().generateProof("POST", TOKEN_ENDPOINT);

    const body = new URLSearchParams({
      grant_type: "authorization_code",
      client_id: CLIENT_ID,
      code: code,
      redirect_uri: redirectUri,
      code_verifier: verifier
    });

    const response = await fetch(TOKEN_ENDPOINT, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "DPoP": dpopProof
      },
      body: body.toString()
    });

    if (!response.ok) {
      const text = await response.text();
      throw new Error(`Token Exchange Failed: ${response.status} ${text}`);
    }

    const data = await response.json();
    await this.handleTokenResponse(data);
  }

  // --- Token Management ---

  async getAccessToken(): Promise<string | null> {
    const data = await chrome.storage.local.get([KEY_ACCESS_TOKEN, KEY_EXPIRES_AT, KEY_REFRESH_TOKEN]);
    const accessToken = data[KEY_ACCESS_TOKEN] as string | undefined;
    const expiresAt = data[KEY_EXPIRES_AT] as number | undefined;
    const refreshToken = data[KEY_REFRESH_TOKEN] as string | undefined;

    if (!accessToken) return null;

    if (!expiresAt || Date.now() > expiresAt) {
      // Token expired, try refresh
      if (refreshToken) {
        try {
          await this.refreshAccessToken(refreshToken);
          const newData = await chrome.storage.local.get([KEY_ACCESS_TOKEN]);
          return newData[KEY_ACCESS_TOKEN] as string;
        } catch (e) {
          console.error("Refresh failed", e);
          await this.logout();
          return null;
        }
      } else {
        await this.logout();
        return null;
      }
    }

    return accessToken;
  }

  private async refreshAccessToken(refreshToken: string): Promise<void> {
    const dpopProof = await DPoPManager.getInstance().generateProof("POST", TOKEN_ENDPOINT);

    const body = new URLSearchParams({
      grant_type: "refresh_token",
      client_id: CLIENT_ID,
      refresh_token: refreshToken
    });

    const response = await fetch(TOKEN_ENDPOINT, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "DPoP": dpopProof
      },
      body: body.toString()
    });

    if (!response.ok) {
      throw new Error(`Token Refresh Failed: ${response.status}`);
    }

    const data = await response.json();
    await this.handleTokenResponse(data);
  }

  private async handleTokenResponse(data: any) {
    const accessToken = data.access_token;
    const expiresIn = data.expires_in; // seconds
    const refreshToken = data.refresh_token; // may be undefined?
    const expiresAt = Date.now() + (expiresIn * 1000);

    await chrome.storage.local.set({
      [KEY_ACCESS_TOKEN]: accessToken,
      [KEY_EXPIRES_AT]: expiresAt,
      [KEY_REFRESH_TOKEN]: refreshToken
    });
  }

  async logout(): Promise<void> {
    await chrome.storage.local.remove([KEY_ACCESS_TOKEN, KEY_EXPIRES_AT, KEY_REFRESH_TOKEN]);
    // Also clear companion api key? Yes probably.
    // await chrome.storage.local.remove(["fp_companion_key"]); // This is managed by CompanionAPI class but we can clear it.
  }
}
