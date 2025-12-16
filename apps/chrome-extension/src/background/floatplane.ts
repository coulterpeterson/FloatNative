
import { AuthService } from "./auth";
import { DPoPManager } from "./dpop";

export interface BlogPost {
  id: string;
  title: string;
  text: string;
  thumbnail?: ImageModel;
  creator: CreatorModel;
  metadata?: {
    hasVideo: boolean;
    videoDuration?: number;
    videoCount?: number;
  };
  releaseDate: string;
}

export interface ImageModel {
  width: number;
  height: number;
  path: string;
  childImages?: ImageModel[];
}

export interface CreatorModel {
  id: string;
  title: string;
  urlname: string;
  icon: ImageModel;
}

export class FloatplaneAPI {
  private static instance: FloatplaneAPI;
  private baseUrl = "https://www.floatplane.com";

  private constructor() { }

  static getInstance(): FloatplaneAPI {
    if (!this.instance) {
      this.instance = new FloatplaneAPI();
    }
    return this.instance;
  }

  async getPostDetails(ids: string[]): Promise<BlogPost[]> {
    console.log(`FloatplaneAPI: getPostDetails checking ${ids.length} videos`, ids);

    const results: BlogPost[] = [];
    const chunkSize = 5; // Conservative concurrency limit to avoid 429s or stalled connections

    for (let i = 0; i < ids.length; i += chunkSize) {
      const chunk = ids.slice(i, i + chunkSize);
      console.log(`FloatplaneAPI: Fetching chunk ${i / chunkSize + 1}/${Math.ceil(ids.length / chunkSize)}`, chunk);

      const chunkPromises = chunk.map(id => this.fetchPost(id)
        .catch(err => {
          console.error(`FloatplaneAPI: Failed to fetch post ${id}`, err);
          return null;
        })
      );

      const chunkResults = await Promise.all(chunkPromises);
      chunkResults.forEach(res => {
        if (res) results.push(res);
      });

      // Small delay between chunks to be nice to the API
      // if (i + chunkSize < ids.length) await new Promise(r => setTimeout(r, 100));
    }

    console.log(`FloatplaneAPI: getPostDetails completed. Got ${results.length} posts.`);
    return results;
  }

  async fetchPost(id: string): Promise<BlogPost> {
    const token = await AuthService.getInstance().getAccessToken();
    if (!token) {
      throw new Error("Not authenticated");
    }

    const url = `${this.baseUrl}/api/v3/content/post?id=${id}`;

    // Generate DPoP proof
    const dpopProof = await DPoPManager.getInstance().generateProof("GET", url, token);

    // console.log(`FloatplaneAPI: Fetching post ${id}...`);
    const response = await fetch(url, {
      headers: {
        Authorization: `DPoP ${token}`,
        DPoP: dpopProof
      },
    });

    if (!response.ok) {
      throw new Error(`Error fetching post ${id}: ${response.status} ${response.statusText}`);
    }

    const json = await response.json();
    // iOS `getBlogPost` expects `BlogPostDetailedWithInteraction` which wraps `post`.
    // Let's assume the root has `post`.
    return json.post ? json.post : json;
  }
}
