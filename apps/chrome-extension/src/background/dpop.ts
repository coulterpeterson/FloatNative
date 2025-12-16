
// Helper for Base64 Url Encoding
function base64UrlEncode(array: Uint8Array | ArrayBuffer): string {
  let bytes = array instanceof Uint8Array ? array : new Uint8Array(array);
  let str = "";
  for (const b of bytes) {
    str += String.fromCharCode(b);
  }
  return btoa(str)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

// Helper to convert string to Uint8Array
function strToBuf(str: string): Uint8Array {
  const encoder = new TextEncoder();
  return encoder.encode(str);
}

export class DPoPManager {
  private static instance: DPoPManager;
  private keyPair: CryptoKeyPair | null = null;
  private static STORAGE_KEY = "fp_dpop_key_jwk";

  private constructor() { }

  static getInstance(): DPoPManager {
    if (!DPoPManager.instance) {
      DPoPManager.instance = new DPoPManager();
    }
    return DPoPManager.instance;
  }

  async getOrGenerateKey(): Promise<CryptoKeyPair> {
    if (this.keyPair) return this.keyPair;

    // Try to load from storage
    const stored = await chrome.storage.local.get([DPoPManager.STORAGE_KEY]);
    if (stored[DPoPManager.STORAGE_KEY]) {
      try {
        const jwkPair = stored[DPoPManager.STORAGE_KEY] as { privateKey: JsonWebKey, publicKey: JsonWebKey };
        const privateKey = await crypto.subtle.importKey(
          "jwk",
          jwkPair.privateKey,
          { name: "ECDSA", namedCurve: "P-256" },
          false,
          ["sign"]
        );
        const publicKey = await crypto.subtle.importKey(
          "jwk",
          jwkPair.publicKey,
          { name: "ECDSA", namedCurve: "P-256" },
          true,
          ["verify"]
        );
        this.keyPair = { privateKey, publicKey };
        return this.keyPair;
      } catch (e) {
        console.error("Failed to load stored DPoP key", e);
      }
    }

    // Generate new key
    this.keyPair = await crypto.subtle.generateKey(
      {
        name: "ECDSA",
        namedCurve: "P-256"
      },
      true, // extractable must be true to export as JWK
      ["sign", "verify"]
    ) as CryptoKeyPair; // TypeScript assumes generateKey can return Key or KeyPair

    // Save to storage
    const privateJwk = await crypto.subtle.exportKey("jwk", this.keyPair.privateKey);
    const publicJwk = await crypto.subtle.exportKey("jwk", this.keyPair.publicKey);

    // Note: privateKey is sensitive. Chrome storage local is reasonably secure for extensions but not perfect.
    // Ideally we'd use non-extractable keys if persistent storage for CryptoKey objects was supported,
    // but IndexDB handles that complexly. For now, saving JWK is standard for extensions without dedicated secure storage.
    await chrome.storage.local.set({
      [DPoPManager.STORAGE_KEY]: {
        privateKey: privateJwk,
        publicKey: publicJwk
      }
    });

    return this.keyPair;
  }

  async generateProof(httpMethod: string, httpUrl: string, accessToken?: string): Promise<string> {
    const keys = await this.getOrGenerateKey();

    // 1. JWK Header
    const publicJwk = await crypto.subtle.exportKey("jwk", keys.publicKey);

    const header = {
      typ: "dpop+jwt",
      alg: "ES256",
      jwk: {
        kty: publicJwk.kty,
        crv: publicJwk.crv,
        x: publicJwk.x,
        y: publicJwk.y
      }
    };

    // 2. Claims
    // Strip query params
    let htu = httpUrl;
    try {
      const urlObj = new URL(httpUrl);
      urlObj.search = "";
      htu = urlObj.toString();
    } catch { }

    let ath: string | undefined;
    if (accessToken) {
      const hash = await crypto.subtle.digest("SHA-256", strToBuf(accessToken) as any);
      ath = base64UrlEncode(hash);
    }

    const claims = {
      iat: Math.floor(Date.now() / 1000),
      jti: crypto.randomUUID(),
      htm: httpMethod,
      htu: htu,
      ath: ath
    };

    // 3. Encode
    const headerStr = base64UrlEncode(strToBuf(JSON.stringify(header)));
    const payloadStr = base64UrlEncode(strToBuf(JSON.stringify(claims)));
    const toSign = `${headerStr}.${payloadStr}`;

    // 4. Sign
    const signature = await crypto.subtle.sign(
      {
        name: "ECDSA",
        hash: { name: "SHA-256" }
      },
      keys.privateKey,
      strToBuf(toSign) as any // Force cast to avoid lib.dom.d.ts BufferSource conflict
    );

    const signatureStr = base64UrlEncode(signature);

    return `${toSign}.${signatureStr}`;
  }
}
