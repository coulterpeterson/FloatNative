// Helper for random strings
function generateRandomString(length: number = 21): string {
  const array = new Uint8Array(length);
  crypto.getRandomValues(array);
  return Array.from(array, dec => ('0' + dec.toString(16)).substr(-2)).join('');
}

// Configuration
const AUTH_BASE_URL = "https://auth.floatplane.com";
const FLOATPLANE_BASE_URL = "https://www.floatplane.com";
const CLIENT_ID = "wasserflug";
const TOKEN_ENDPOINT = `${AUTH_BASE_URL}/realms/floatplane/protocol/openid-connect/token`;

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

  // --- Device Auth Flow ---

  async startDeviceAuth(): Promise<{ deviceCode: string; userCode: string; verificationUri: string; expiresIn: number; interval: number }> {
    const body = new URLSearchParams({
      client_id: CLIENT_ID,
      scope: "openid offline_access"
    });

    const response = await fetch(`${AUTH_BASE_URL}/realms/floatplane/protocol/openid-connect/auth/device`, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded"
      },
      body: body.toString()
    });

    if (!response.ok) {
      throw new Error(`Device Auth Init Failed: ${response.status}`);
    }

    const data = await response.json();
    return {
      deviceCode: data.device_code,
      userCode: data.user_code,
      verificationUri: data.verification_uri,
      expiresIn: data.expires_in,
      interval: data.interval
    };
  }

  async pollDeviceToken(deviceCode: string): Promise<any> {
    const body = new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:device_code",
      client_id: CLIENT_ID,
      device_code: deviceCode
    });

    const response = await fetch(TOKEN_ENDPOINT, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded"
      },
      body: body.toString()
    });

    const data = await response.json();

    if (response.ok) {
      await this.handleTokenResponse(data);
      return data;
    } else {
      // Return error object to caller (e.g. authorization_pending)
      // { error: "authorization_pending" }
      return data;
    }
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
    const body = new URLSearchParams({
      grant_type: "refresh_token",
      client_id: CLIENT_ID,
      refresh_token: refreshToken
    });

    const response = await fetch(TOKEN_ENDPOINT, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded"
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
