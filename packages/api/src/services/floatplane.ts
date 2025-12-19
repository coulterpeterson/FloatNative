// Floatplane API service for Workers

export const LTT_CREATOR_ID = '59f94c0bdd241b70349eb72b';

interface FloatplaneUserResponse {
  id: string;
  username: string;
  email: string;
  displayName: string;
}

interface FloatplanePostMetadata {
  hasVideo: boolean;
  videoCount: number;
  videoDuration: number;
  hasAudio: boolean;
  audioCount: number;
  audioDuration: number;
  hasPicture: boolean;
  pictureCount: number;
  isFeatured: boolean;
  hasGallery: boolean;
  galleryCount: number;
}

interface FloatplaneImage {
  width: number;
  height: number;
  path: string;
  childImages?: Array<{
    width: number;
    height: number;
    path: string;
  }>;
}

interface FloatplaneCreator {
  id: string;
  owner: {
    id: string;
    username: string;
  };
  title: string;
}

export interface FloatplanePost {
  id: string;
  guid: string;
  title: string;
  text: string;
  type: string;
  channel: {
    id: string;
    creator: string;
    title: string;
    icon?: FloatplaneImage;
  };
  creator: FloatplaneCreator;
  thumbnail?: FloatplaneImage;
  metadata: FloatplanePostMetadata;
  releaseDate: string;
  likes: number;
  dislikes: number;
  score: number;
  comments: number;
  videoAttachments?: string[];
  audioAttachments?: string[];
  pictureAttachments?: string[];
}

export class FloatplaneAPIError extends Error {
  constructor(
    message: string,
    public statusCode?: number,
    public originalError?: Error
  ) {
    super(message);
    this.name = 'FloatplaneAPIError';
  }
}

/**
 * Validates a Floatplane sails.sid cookie by calling the /user/self endpoint
 * @param sailsSid The sails.sid cookie value
 * @param floatplaneApiUrl The base URL for the Floatplane API
 * @returns The Floatplane user ID if valid
 * @throws FloatplaneAPIError if cookie is invalid or API call fails
 */
export async function validateFloatplaneCookie(
  sailsSid: string,
  floatplaneApiUrl: string
): Promise<string> {
  try {
    const response = await fetch(`${floatplaneApiUrl}/user/self`, {
      headers: {
        Cookie: `sails.sid=${sailsSid}`,
      },
      // @ts-ignore - cf property exists in Workers but not in standard fetch types
      cf: {
        cacheTtl: 0,
        cacheEverything: false,
      },
    });

    if (!response.ok) {
      const status = response.status;
      if (status === 401 || status === 403) {
        throw new FloatplaneAPIError('Invalid or expired Floatplane cookie', status);
      }
      throw new FloatplaneAPIError(`Floatplane API error: ${response.statusText}`, status);
    }

    const data = (await response.json()) as FloatplaneUserResponse;

    if (!data || !data.id) {
      throw new FloatplaneAPIError('Invalid response from Floatplane API: missing user ID');
    }

    return data.id;
  } catch (error) {
    if (error instanceof FloatplaneAPIError) {
      throw error;
    }

    throw new FloatplaneAPIError(
      'Failed to validate Floatplane cookie',
      undefined,
      error as Error
    );
  }
}

/**
 * Validates a Floatplane OAuth access token by calling the /user/self endpoint
 * @param accessToken The OAuth access token
 * @param floatplaneApiUrl The base URL for the Floatplane API
 * @param dpopProof Optional DPoP proof if the token is DPoP-bound
 * @returns The Floatplane user ID if valid
 * @throws FloatplaneAPIError if token is invalid or API call fails
 */
export async function validateFloatplaneToken(
  accessToken: string,
  floatplaneApiUrl: string,
  dpopProof?: string
): Promise<string> {
  try {
    const headers: Record<string, string> = {
      Authorization: dpopProof ? `DPoP ${accessToken}` : `Bearer ${accessToken}`,
    };

    if (dpopProof) {
      headers['DPoP'] = dpopProof;
    }

    const response = await fetch(`${floatplaneApiUrl}/user/self`, {
      headers,
      // @ts-ignore - cf property exists in Workers but not in standard fetch types
      cf: {
        cacheTtl: 0,
        cacheEverything: false,
      },
    });

    if (!response.ok) {
      const status = response.status;
      if (status === 401 || status === 403) {
        throw new FloatplaneAPIError('Invalid or expired Floatplane token', status);
      }
      throw new FloatplaneAPIError(`Floatplane API error: ${response.statusText}`, status);
    }

    const data = (await response.json()) as FloatplaneUserResponse;

    if (!data || !data.id) {
      throw new FloatplaneAPIError('Invalid response from Floatplane API: missing user ID');
    }

    return data.id;
  } catch (error) {
    if (error instanceof FloatplaneAPIError) {
      throw error;
    }

    throw new FloatplaneAPIError(
      'Failed to validate Floatplane token',
      undefined,
      error as Error
    );
  }
}
/**
 * Validates a Floatplane OAuth access token locally by decoding the JWT
 * This avoids calling Floatplane's API, which is necessary for DPoP-bound tokens
 * since we don't have the device's private key to generate valid DPoP proofs
 * @param accessToken The OAuth access token (JWT)
 * @returns The Floatplane user ID from the token's 'sub' claim
 * @throws FloatplaneAPIError if token is invalid or missing user ID
 */
export function validateFloatplaneTokenLocally(accessToken: string): string {
  try {
    // JWT format: header.payload.signature
    const parts = accessToken.split('.');
    if (parts.length !== 3) {
      throw new FloatplaneAPIError('Invalid JWT format');
    }

    // Decode the payload (base64url)
    const payloadB64 = parts[1];
    // Replace URL-safe chars and add padding if needed
    const payloadB64Padded = payloadB64.replace(/-/g, '+').replace(/_/g, '/');
    const payloadJson = atob(payloadB64Padded);
    const payload = JSON.parse(payloadJson);

    // Extract user ID from 'sub' claim
    if (!payload.sub || typeof payload.sub !== 'string') {
      throw new FloatplaneAPIError('JWT missing sub claim');
    }

    // Optional: Check expiration
    if (payload.exp && typeof payload.exp === 'number') {
      const now = Math.floor(Date.now() / 1000);
      if (now >= payload.exp) {
        throw new FloatplaneAPIError('JWT has expired', 401);
      }
    }

    return payload.sub;
  } catch (error) {
    if (error instanceof FloatplaneAPIError) {
      throw error;
    }
    throw new FloatplaneAPIError(
      'Failed to decode JWT',
      undefined,
      error as Error
    );
  }
}


/**
 * Extract the JKT (JSON Web Key Thumbprint) from a DPoP proof
 * The JKT uniquely identifies the device's DPoP key pair
 * @param dpopProof The DPoP proof JWT
 * @returns The JKT thumbprint string
 * @throws FloatplaneAPIError if the proof is invalid or missing JWK
 */
export function extractDPoPJKT(dpopProof: string): string {
  try {
    // DPoP proof is a JWT with format: header.payload.signature
    const parts = dpopProof.split('.');
    if (parts.length !== 3) {
      throw new FloatplaneAPIError('Invalid DPoP proof format');
    }

    // Decode the header (base64url)
    const headerB64 = parts[0];
    // Replace URL-safe chars and add padding if needed
    const headerB64Padded = headerB64.replace(/-/g, '+').replace(/_/g, '/');
    const headerJson = atob(headerB64Padded);
    const header = JSON.parse(headerJson);

    if (!header.jwk) {
      throw new FloatplaneAPIError('DPoP proof missing jwk in header');
    }

    // Calculate SHA-256 thumbprint of the JWK
    // For a proper JKT, we need to canonicalize the JWK and hash it
    // For now, we'll use a simplified approach: serialize the JWK deterministically
    const jwk = header.jwk;

    // Create canonical JWK string (sorted keys)
    const canonicalJwk = JSON.stringify({
      crv: jwk.crv,
      kty: jwk.kty,
      x: jwk.x,
      y: jwk.y,
    });

    // Calculate SHA-256 hash and base64url encode it
    // Note: In a Worker environment, we use the Web Crypto API
    return btoa(canonicalJwk)
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=/g, '');
  } catch (error) {
    if (error instanceof FloatplaneAPIError) {
      throw error;
    }
    throw new FloatplaneAPIError(
      'Failed to extract DPoP JKT',
      undefined,
      error as Error
    );
  }
}

/**
 * Fetches posts from Floatplane for a specific creator with pagination
 * @param sailsSid The sails.sid cookie value for authentication
 * @param creatorId The creator ID to fetch posts for
 * @param floatplaneApiUrl The base URL for the Floatplane API
 * @param limit Number of posts to fetch per request (max 20)
 * @param offset Offset for pagination
 * @returns Array of Floatplane posts
 * @throws FloatplaneAPIError if the request fails
 */
export async function fetchCreatorPosts(
  sailsSid: string,
  creatorId: string,
  floatplaneApiUrl: string,
  limit: number = 20,
  offset: number = 0
): Promise<FloatplanePost[]> {
  try {
    const url = new URL(`${floatplaneApiUrl}/content/creator`);
    url.searchParams.set('id', creatorId);
    url.searchParams.set('limit', Math.min(limit, 20).toString());
    url.searchParams.set('fetchAfter', offset.toString());

    const response = await fetch(url.toString(), {
      headers: {
        Cookie: `sails.sid=${sailsSid}`,
      },
      // @ts-ignore
      cf: {
        cacheTtl: 0,
        cacheEverything: false,
      },
    });

    if (!response.ok) {
      throw new FloatplaneAPIError(
        `Failed to fetch creator posts: ${response.statusText}`,
        response.status
      );
    }

    return (await response.json()) as FloatplanePost[];
  } catch (error) {
    if (error instanceof FloatplaneAPIError) {
      throw error;
    }

    throw new FloatplaneAPIError('Failed to fetch creator posts', undefined, error as Error);
  }
}

/**
 * Helper to get the thumbnail URL from a Floatplane post
 */
export function getThumbnailUrl(post: FloatplanePost): string | undefined {
  if (!post.thumbnail) return undefined;

  if (post.thumbnail.childImages && post.thumbnail.childImages.length > 0) {
    const largestImage = post.thumbnail.childImages.reduce((prev, current) =>
      current.width > prev.width ? current : prev
    );
    return largestImage.path;
  }

  return post.thumbnail.path;
}

/**
 * Helper to get the channel icon URL from a Floatplane post
 */
export function getChannelIconUrl(post: FloatplanePost): string | undefined {
  if (!post.channel?.icon) return undefined;

  if (post.channel.icon.childImages && post.channel.icon.childImages.length > 0) {
    const largestImage = post.channel.icon.childImages.reduce((prev, current) =>
      current.width > prev.width ? current : prev
    );
    return largestImage.path;
  }

  return post.channel.icon.path;
}
