import type { Context } from 'hono';

export interface StructuredLogData {
  timestamp: string;
  level: 'error' | 'warn';
  error_type: string;
  message: string;
  stack?: string;
  user_id?: string;
  endpoint: string;
  method: string;
}

/**
 * Logs an error with structured JSON format for Cloudflare Worker observability
 */
export function logError(c: Context, error: unknown, message: string): void {
  const user = c.get('user');
  const logData: StructuredLogData = {
    timestamp: new Date().toISOString(),
    level: 'error',
    error_type: error instanceof Error ? error.constructor.name : 'Error',
    message: message,
    stack: error instanceof Error ? error.stack : undefined,
    user_id: user?.floatplane_user_id || undefined,
    endpoint: new URL(c.req.url).pathname,
    method: c.req.method,
  };

  console.error(logData);
}

/**
 * Logs a warning with structured JSON format for Cloudflare Worker observability
 */
export function logWarn(c: Context, error: unknown, message: string): void {
  const user = c.get('user');
  const logData: StructuredLogData = {
    timestamp: new Date().toISOString(),
    level: 'warn',
    error_type: error instanceof Error ? error.constructor.name : 'Error',
    message: message,
    stack: error instanceof Error ? error.stack : undefined,
    user_id: user?.floatplane_user_id || undefined,
    endpoint: new URL(c.req.url).pathname,
    method: c.req.method,
  };

  console.warn(logData);
}
