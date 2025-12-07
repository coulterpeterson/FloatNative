import { Hono } from 'hono';
import type { Env } from '../db';
import { logo } from '../asssets/logo';
import { rateLimit, byIP } from '../middleware/rate-limit';

const publicRoutes = new Hono<{ Bindings: Env }>();

/**
 * GET /public/qr-login.html
 * Serve the QR login page (inline HTML)
 * Rate limited: 100 req/min by IP
 */
publicRoutes.get('/qr-login.html', rateLimit('PUBLIC_PAGE_LIMITER', byIP), (c) => {
  const html = `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>FloatNative Login - QR Code</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
            background: linear-gradient(135deg, #2E7FD8 0%, #5BA5E8 100%);
            min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 20px;
        }
        .container {
            background: white; border-radius: 16px; box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
            max-width: 500px; width: 100%; padding: 40px 30px;
        }
        .logo { text-align: center; margin-bottom: 30px; }
        .logo img { width: 120px; height: 120px; margin-bottom: 20px; display: block; margin-left: auto; margin-right: auto; border-radius: 20px; }
        .logo h1 { font-size: 28px; color: #333; margin-bottom: 8px; }
        .logo p { color: #666; font-size: 14px; }
        .form-group { margin-bottom: 25px; }
        label { display: block; font-weight: 600; color: #333; margin-bottom: 8px; font-size: 14px; }
        .help-text { color: #666; font-size: 13px; margin-bottom: 12px; line-height: 1.5; }
        .help-text a { color: #2E7FD8; text-decoration: none; }
        .help-text a:hover { text-decoration: underline; }
        input[type="text"] {
            width: 100%; padding: 14px; border: 2px solid #e0e0e0; border-radius: 8px;
            font-size: 16px; transition: border-color 0.3s; font-family: monospace;
        }
        input[type="text"]:focus { outline: none; border-color: #2E7FD8; }
        button {
            width: 100%; padding: 16px; background: linear-gradient(135deg, #2E7FD8 0%, #5BA5E8 100%);
            color: white; border: none; border-radius: 8px; font-size: 16px; font-weight: 600;
            cursor: pointer; transition: transform 0.2s, box-shadow 0.2s;
        }
        button:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 10px 20px rgba(46, 127, 216, 0.4); }
        button:active:not(:disabled) { transform: translateY(0); }
        button:disabled { opacity: 0.6; cursor: not-allowed; }
        .message {
            padding: 14px; border-radius: 8px; margin-bottom: 20px; font-size: 14px; display: none;
        }
        .message.success { background-color: #d4edda; color: #155724; border: 1px solid #c3e6cb; }
        .message.error { background-color: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }
        .message.show { display: block; }
        .spinner {
            border: 3px solid rgba(255, 255, 255, 0.3); border-top: 3px solid white; border-radius: 50%;
            width: 20px; height: 20px; animation: spin 0.8s linear infinite; display: inline-block;
            margin-right: 10px; vertical-align: middle;
        }
        @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
        .success-icon { text-align: center; font-size: 64px; margin-bottom: 20px; display: none; }
        .success-icon.show { display: block; }
        @media (max-width: 480px) {
            .container { padding: 30px 20px; }
            .logo h1 { font-size: 24px; }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="logo">
            <img src="data:image/png;base64, `+logo+`" alt="FloatNative Logo" />
            <h1>FloatNative Login</h1>
            <p>Authorize your device with QR code</p>
        </div>
        <div id="successIcon" class="success-icon">✅</div>
        <div id="message" class="message"></div>
        <form id="loginForm">
            <div class="form-group">
                <label for="token">Floatplane Token (sails.sid)</label>
                <div class="help-text">
                    To find your token: Open Floatplane in your browser, press F12,
                    go to Application → Cookies → find <strong>sails.sid</strong>
                    <br>
                    <a href="https://www.floatplane.com" target="_blank">Open Floatplane →</a>
                </div>
                <input type="text" id="token" name="token" placeholder="Paste your sails.sid token here"
                    required autocomplete="off" spellcheck="false">
            </div>
            <button type="submit" id="submitBtn">Login</button>
        </form>
    </div>
    <script>
        const form = document.getElementById('loginForm');
        const submitBtn = document.getElementById('submitBtn');
        const tokenInput = document.getElementById('token');
        const message = document.getElementById('message');
        const successIcon = document.getElementById('successIcon');
        const urlParams = new URLSearchParams(window.location.search);
        const sessionId = urlParams.get('session');

        if (!sessionId) {
            showMessage('Invalid QR code. Missing session ID.', 'error');
            submitBtn.disabled = true;
        }

        function showMessage(text, type) {
            message.textContent = text;
            message.className = \`message \${type} show\`;
        }

        function hideMessage() { message.classList.remove('show'); }

        async function submitToken(token) {
            try {
                const response = await fetch('/auth/qr/submit', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ session_id: sessionId, sails_sid: token.trim() }),
                });

                const data = await response.json();

                if (!response.ok) {
                    throw new Error(data.message || 'Failed to submit token');
                }

                form.style.display = 'none';
                successIcon.classList.add('show');
                showMessage('✅ Success! You can now close this tab and return to the app.', 'success');
                tokenInput.value = '';
            } catch (error) {
                showMessage(\`❌ \${error.message}\`, 'error');
                submitBtn.disabled = false;
                submitBtn.innerHTML = 'Login';
            }
        }

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const token = tokenInput.value.trim();
            if (!token) {
                showMessage('Please enter your Floatplane token', 'error');
                return;
            }
            hideMessage();
            submitBtn.disabled = true;
            submitBtn.innerHTML = '<span class="spinner"></span>Logging in...';
            await submitToken(token);
        });
    </script>
</body>
</html>`;

  return c.html(html);
});

export default publicRoutes;
