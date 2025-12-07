/**
 * Email alerting utility using Mailgun API
 */

export interface EmailAlert {
  subject: string;
  message: string;
  timestamp: string;
  error?: string;
}

/**
 * Sends an email alert via Mailgun
 * @param mailgunApiKey The Mailgun API key
 * @param mailgunDomain The Mailgun domain to send from
 * @param alertEmail The email address to send alerts to
 * @param alert The alert details
 * @returns true if email was sent successfully, false otherwise
 */
export async function sendEmailAlert(
  mailgunApiKey: string,
  mailgunDomain: string,
  alertEmail: string,
  alert: EmailAlert
): Promise<boolean> {
  if (!mailgunApiKey || !alertEmail) {
    console.error({
      level: 'error',
      message: 'Email alert not configured - missing MAILGUN_API_KEY or ALERT_EMAIL',
      timestamp: new Date().toISOString(),
    });
    return false;
  }

  if (!mailgunDomain) {
    console.error({
      level: 'error',
      message: 'Email alert not configured - missing MAILGUN_DOMAIN',
      timestamp: new Date().toISOString(),
    });
    return false;
  }

  try {
    // Mailgun API endpoint
    // Format: https://api.mailgun.net/v3/YOUR_DOMAIN/messages
    const mailgunUrl = `https://api.mailgun.net/v3/${mailgunDomain}/messages`;

    // Create form data for Mailgun
    const formData = new FormData();
    formData.append('from', `FloatNative API <alerts@${mailgunDomain}>`);
    formData.append('to', alertEmail);
    formData.append('subject', alert.subject);
    formData.append(
      'text',
      `${alert.message}\n\nTimestamp: ${alert.timestamp}${alert.error ? `\n\nError Details:\n${alert.error}` : ''}`
    );

    // Send request to Mailgun
    const response = await fetch(mailgunUrl, {
      method: 'POST',
      headers: {
        Authorization: `Basic ${btoa(`api:${mailgunApiKey}`)}`,
      },
      body: formData,
    });

    if (!response.ok) {
      const errorText = await response.text();
      console.error({
        level: 'error',
        message: 'Failed to send email alert via Mailgun',
        status: response.status,
        error: errorText,
        timestamp: new Date().toISOString(),
      });
      return false;
    }

    console.log({
      level: 'info',
      message: 'Email alert sent successfully',
      recipient: alertEmail,
      subject: alert.subject,
      timestamp: new Date().toISOString(),
    });

    return true;
  } catch (error) {
    console.error({
      level: 'error',
      message: 'Exception while sending email alert',
      error: error instanceof Error ? error.message : 'Unknown error',
      timestamp: new Date().toISOString(),
    });
    return false;
  }
}

/**
 * Sends an alert for invalid Floatplane token
 */
export async function sendFloatplaneTokenAlert(
  mailgunApiKey: string,
  mailgunDomain: string,
  alertEmail: string,
  errorMessage: string
): Promise<boolean> {
  const alert: EmailAlert = {
    subject: '🚨 FloatNative API: Invalid Floatplane Token',
    message: `The Floatplane sails.sid token used by FloatNative API is invalid or expired.

This means the scheduled task to update LTT posts will fail until the token is refreshed.

Action Required:
1. Log in to floatplane.com in your browser
2. Open DevTools (F12) → Application/Storage → Cookies
3. Copy the value of the 'sails.sid' cookie
4. Update the Worker secret:
   pnpm wrangler secret put FLOATPLANE_SAILS_SID

No redeployment needed - secrets update immediately!`,
    timestamp: new Date().toISOString(),
    error: errorMessage,
  };

  return sendEmailAlert(mailgunApiKey, mailgunDomain, alertEmail, alert);
}
