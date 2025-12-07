import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { createD1Database, type Env } from './db';
import { updateLTTPosts } from './scheduled/update-ltt-posts';
import { rateLimit, byIP } from './middleware/rate-limit';

// Import routes
import auth from './routes/auth';
import qrAuth from './routes/qr-auth';
import playlists from './routes/playlists';
import watchLater from './routes/watch-later';
import ltt from './routes/ltt';
import publicRoutes from './routes/public';

// Create Hono app
const app = new Hono<{ Bindings: Env }>();

// CORS middleware
app.use('*', cors());

// Database middleware - attach DB instance to context
app.use('*', async (c, next) => {
  const db = createD1Database(c.env.DB);
  c.set('db', db);
  await next();
});

// Health check - 200 req/min by IP
app.get('/health', rateLimit('HEALTH_CHECK_LIMITER', byIP), (c) => {
  return c.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
  });
});

// Root endpoint - 200 req/min by IP
app.get('/', rateLimit('HEALTH_CHECK_LIMITER', byIP), (c) => {
  return c.json({
    name: 'Floatplane Companion API',
    version: '1.0.0',
    status: 'running',
  });
});

// Register routes
app.route('/auth', auth);
app.route('/auth/qr', qrAuth);
app.route('/playlists', playlists);
app.route('/watch-later', watchLater);
app.route('/ltt', ltt);
app.route('/public', publicRoutes);

// Export handlers for Cloudflare Workers
export default {
  // HTTP request handler
  fetch: app.fetch,

  // Scheduled event handler (cron triggers)
  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext) {
    console.log({
      level: 'info',
      message: 'Cron trigger fired',
      cron: event.cron,
      timestamp: new Date().toISOString(),
    });

    // Use waitUntil to ensure async work completes
    ctx.waitUntil(
      updateLTTPosts(env).catch((error) => {
        console.error({
          level: 'error',
          message: 'Scheduled task failed',
          error: error instanceof Error ? error.message : 'Unknown error',
          stack: error instanceof Error ? error.stack : undefined,
          timestamp: new Date().toISOString(),
        });
      })
    );
  },
};
