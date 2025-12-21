import { eq } from 'drizzle-orm';
import { createD1Database, type Env } from '../db';
import { fpPosts } from '../db/schema';
import {
  fetchCreatorPosts,
  getThumbnailUrl,
  getChannelIconUrl,
  LTT_CREATOR_ID,
  type FloatplanePost,
  FloatplaneAPIError,
} from '../services/floatplane';
import { sendFloatplaneTokenAlert } from '../utils/email';

/**
 * Convert Floatplane post to fpPosts table format
 */
function convertToFPPost(post: FloatplanePost) {
  return {
    id: post.id,
    title: post.title,
    creator_id: post.creator.id,
    creator_name: post.creator.title,
    channel_id: post.channel.id,
    channel_title: post.channel.title,
    channel_icon_url: getChannelIconUrl(post),
    thumbnail_url: getThumbnailUrl(post),
    has_video: post.metadata.hasVideo,
    video_count: post.metadata.videoCount,
    video_duration: post.metadata.videoDuration,
    has_audio: post.metadata.hasAudio,
    audio_count: post.metadata.audioCount,
    audio_duration: post.metadata.audioDuration,
    has_picture: post.metadata.hasPicture,
    picture_count: post.metadata.pictureCount,
    is_featured: post.metadata.isFeatured,
    has_gallery: post.metadata.hasGallery,
    gallery_count: post.metadata.galleryCount,
    release_date: post.releaseDate,
  };
}

/**
 * Scheduled task to update LTT posts in the database
 */
export async function updateLTTPosts(env: Env): Promise<void> {
  const startTime = Date.now();

  console.log({
    level: 'info',
    message: 'Scheduled task: Updating LTT posts',
    timestamp: new Date().toISOString(),
  });

  const db = createD1Database(env.DB);
  const floatplaneApiUrl = env.FLOATPLANE_API_URL;
  const sailsSid = env.FLOATPLANE_SAILS_SID;

  if (!sailsSid) {
    throw new Error('FLOATPLANE_SAILS_SID environment variable is not set');
  }

  try {
    // Fetch latest 20 posts from LTT
    const posts = await fetchCreatorPosts(sailsSid, LTT_CREATOR_ID, floatplaneApiUrl, 20, 0);

    console.log({
      level: 'info',
      message: 'Fetched posts from Floatplane API',
      posts_count: posts.length,
      timestamp: new Date().toISOString(),
    });

    let savedCount = 0;
    let updatedCount = 0;

    // Process each post
    for (const post of posts) {
      try {
        const fpPost = convertToFPPost(post);

        // Check if post already exists
        const existing = await db.select().from(fpPosts).where(eq(fpPosts.id, post.id)).limit(1);

        if (existing.length > 0) {
          // Update existing post
          await db.update(fpPosts).set(fpPost).where(eq(fpPosts.id, post.id));
          updatedCount++;
        } else {
          // Insert new post
          await db.insert(fpPosts).values(fpPost);
          savedCount++;
        }
      } catch (error) {
        console.error({
          level: 'error',
          message: 'Failed to save post',
          post_id: post.id,
          error: error instanceof Error ? error.message : 'Unknown error',
          timestamp: new Date().toISOString(),
        });
      }
    }

    const duration = ((Date.now() - startTime) / 1000).toFixed(2);

    console.log({
      level: 'info',
      message: 'LTT posts update complete',
      new_posts: savedCount,
      updated_posts: updatedCount,
      duration_seconds: duration,
      timestamp: new Date().toISOString(),
    });
  } catch (error) {
    // Check if this is an authentication error
    if (error instanceof FloatplaneAPIError && (error.statusCode === 401 || error.statusCode === 403)) {
      console.error({
        level: 'error',
        message: 'Floatplane token authentication failed',
        error: error.message,
        statusCode: error.statusCode,
        timestamp: new Date().toISOString(),
        action_required: 'Update FLOATPLANE_SAILS_SID secret',
        instructions: 'Run: pnpm wrangler secret put FLOATPLANE_SAILS_SID',
      });

      // Email alerts temporarily disabled - check Cloudflare Workers Observability logs instead
      // if (env.MAILGUN_API_KEY && env.MAILGUN_DOMAIN && env.ALERT_EMAIL) {
      //   await sendFloatplaneTokenAlert(
      //     env.MAILGUN_API_KEY,
      //     env.MAILGUN_DOMAIN,
      //     env.ALERT_EMAIL,
      //     error.message
      //   );
      // }
    }

    console.error({
      level: 'error',
      message: 'Failed to update LTT posts',
      error: error instanceof Error ? error.message : 'Unknown error',
      stack: error instanceof Error ? error.stack : undefined,
      timestamp: new Date().toISOString(),
    });
    throw error;
  }
}
