import { AuthService } from "./auth";
import { DPoPManager } from "./dpop";

const COMPANION_BASE_URL = "https://api.floatnative.coulterpeterson.com";
const KEY_COMPANION_API_KEY = "fp_companion_key";

export class CompanionAPI {
  private static instance: CompanionAPI;
  private apiKey: string | null = null;

  private constructor() {
    // Load API key from storage
    chrome.storage.local.get([KEY_COMPANION_API_KEY], (result) => {
      if (result && result[KEY_COMPANION_API_KEY]) {
        this.apiKey = result[KEY_COMPANION_API_KEY] as string;
      }
    });
  }

  static getInstance(): CompanionAPI {
    if (!CompanionAPI.instance) {
      CompanionAPI.instance = new CompanionAPI();
    }
    return CompanionAPI.instance;
  }

  private async setApiKey(key: string) {
    this.apiKey = key;
    await chrome.storage.local.set({ [KEY_COMPANION_API_KEY]: key });
  }

  private async request(endpoint: string, method: string = "GET", body: any = null, requiresAuth: boolean = true): Promise<any> {
    const url = `${COMPANION_BASE_URL}${endpoint}`;
    const headers: HeadersInit = {
      "Content-Type": "application/json",
      "User-Agent": "FloatNative/1.0 (ChromeExtension)"
    };

    if (requiresAuth) {
      if (!this.apiKey) {
        // If no key, try to login/register immediately
        await this.ensureLoggedIn();
      }
      if (this.apiKey) {
        headers["Authorization"] = `Bearer ${this.apiKey}`;
      }
    }

    const options: RequestInit = {
      method,
      headers,
      body: body ? JSON.stringify(body) : null
    };

    let response = await fetch(url, options);

    if (response.status === 401 && requiresAuth) {
      // Re-login and retry once
      console.log("CompanionAPI: 401 Unauthorized, re-logging in...");
      await this.ensureLoggedIn();
      if (this.apiKey) {
        headers["Authorization"] = `Bearer ${this.apiKey}`;
        // Re-create options with new header
        const retryOptions: RequestInit = {
          method,
          headers,
          body: body ? JSON.stringify(body) : null
        };
        response = await fetch(url, retryOptions);
      }
    }

    if (!response.ok) {
      const text = await response.text();
      throw new Error(`Companion API Error ${response.status}: ${text}`);
    }

    // Return JSON if content exists, otherwise null
    const contentType = response.headers.get("content-type");
    if (contentType && contentType.indexOf("application/json") !== -1) {
      return await response.json();
    }
    return null;
  }

  async ensureLoggedIn(): Promise<void> {
    console.log("CompanionAPI: ensuring logged in...");
    const authService = AuthService.getInstance();
    let accessToken = await authService.getAccessToken();

    if (!accessToken) {
      // If we don't have a token, we can't login to Companion API without user interaction?
      // Or maybe we just fail.
      // For the extension background worker, if we are not logged in, we probably shouldn't prompt the user unless they initiated it.
      // But valid use case: user is browsing, button is injected. User clicks "Save".
      // We try to call API. 401. We come here.
      // We need an access token.
      // If we can't get one silently, we should probably throw an error that creates a UI prompt to login.
      // Use `interactive: false` first? `getAccessToken` handles refresh logic.
      // If `getAccessToken` returns null, the user needs to login via the Popup.
      throw new Error("UserNotAuthenticated");
    }

    // We have OAuth token, perform exchange login
    const loginResponse = await this.performCompanionLogin(accessToken);
    if (!loginResponse.api_key) {
      throw new Error("Login response missing api_key");
    }
    await this.setApiKey(loginResponse.api_key);
  }

  private async performCompanionLogin(accessToken: string): Promise<{ api_key: string }> {
    const url = `${COMPANION_BASE_URL}/auth/login`;

    // Generate DPoP proof for the Floatplane /user/self endpoint
    // This allows the companion API to validate the token on our behalf
    const dpopProof = await DPoPManager.getInstance().generateProof(
      "GET",
      "https://www.floatplane.com/api/v3/user/self",
      accessToken
    );

    const payload = {
      access_token: accessToken,
      dpop_proof: dpopProof
    };
    console.log("CompanionAPI: Performing login exchange with payload", payload);

    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      const text = await response.text();
      console.error(`CompanionAPI: Login Failed ${response.status}: ${text}`);
      throw new Error(`Companion Login Failed: ${response.status} - ${text}`);
    }
    return await response.json();
  }

  // --- Public Methods ---

  async getPlaylists(includeWatchLater: boolean = false): Promise<any[]> {
    const response = await this.request(`/playlists?include_watch_later=${includeWatchLater}`);
    console.log("CompanionAPI: getPlaylists response", response);
    return response.playlists;
  }

  async addToPlaylist(playlistId: string, videoId: string): Promise<any> {
    console.log("CompanionAPI: addToPlaylist", playlistId, videoId);
    return await this.request(`/playlists/${playlistId}/add`, "PATCH", { video_id: videoId });
  }

  async removeFromPlaylist(playlistId: string, videoId: string): Promise<any> {
    return await this.request(`/playlists/${playlistId}/remove`, "PATCH", { video_id: videoId });
  }

  async createPlaylist(name: string): Promise<any> {
    return await this.request(`/playlists`, "POST", { name });
  }

  async deletePlaylist(playlistId: string): Promise<any> {
    return await this.request(`/playlists/${playlistId}`, "DELETE");
  }
}
