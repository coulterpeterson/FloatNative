import QRCode from 'qrcode';

console.log("Popup script loaded");

const elements = {
  loginView: document.getElementById("login-view")!,
  successView: document.getElementById("success-view")!,
  qrCanvas: document.getElementById("qrcode") as HTMLCanvasElement,
  userCode: document.getElementById("user-code")!,
  verifyLink: document.getElementById("verify-link") as HTMLAnchorElement,
  status: document.getElementById("status")!
};

let pollIntervalId: any = null;

document.addEventListener("DOMContentLoaded", async () => {
  // Check if already logged in?
  // We can't easily check auth state synchronously, but usually popup is for quick actions or login.
  // If we assume popup is ONLY for login (since main features are injected), we start auth immediately.

  startAuth();
});

async function startAuth() {
  elements.status.textContent = "Requesting device code...";

  try {
    const response = await chrome.runtime.sendMessage({ type: "START_DEVICE_AUTH" });

    if (!response.success) {
      elements.status.textContent = "Error: " + response.error;
      return;
    }

    const data = response.data;
    // data: { deviceCode, userCode, verificationUri, expiresIn, interval }

    elements.userCode.textContent = data.userCode;
    elements.verifyLink.href = data.verificationUri;
    elements.verifyLink.textContent = data.verificationUri.replace(/^https?:\/\//, '');

    // Render QR
    // construct full URL: verificationUri?user_code=... or just verificationUri if user has to type it?
    // Usually Device Flow is 2 steps: go to URL, enter code. QR can encode the URL.
    // Some implementations support `verification_uri_complete`. Floatplane might not.
    // Assuming we just encode the URI.
    await QRCode.toCanvas(elements.qrCanvas, data.verificationUri, {
      width: 180,
      margin: 1,
      color: {
        dark: "#000000",
        light: "#ffffff"
      }
    });

    elements.status.textContent = "Waiting for approval...";

    // Start polling
    const interval = (data.interval || 5) * 1000;
    pollIntervalId = setInterval(() => poll(data.deviceCode), interval);

  } catch (e: any) {
    elements.status.textContent = "Error: " + e.message;
  }
}

async function poll(deviceCode: string) {
  try {
    const response = await chrome.runtime.sendMessage({ type: "POLL_DEVICE_TOKEN", deviceCode });

    if (!response.success) {
      // Transport error
      clearInterval(pollIntervalId);
      elements.status.textContent = "Polling Error: " + response.error;
      return;
    }

    const data = response.data;

    if (data.access_token) {
      // Success!
      clearInterval(pollIntervalId);
      showSuccess();
    } else if (data.error) {
      if (data.error === "authorization_pending") {
        // Continue waiting
      } else if (data.error === "slow_down") {
        // Should increase interval, but simple implementation: just process next tick
      } else if (data.error === "expired_token") {
        clearInterval(pollIntervalId);
        elements.status.textContent = "Code expired. Re-open to try again.";
      } else {
        clearInterval(pollIntervalId);
        elements.status.textContent = "Auth Error: " + data.error_description || data.error;
      }
    }
  } catch (e) {
    // Ignore network glitches?
    console.error("Poll error", e);
  }
}

function showSuccess() {
  elements.loginView.style.display = "none";
  elements.successView.style.display = "block";
  // Close after delay
  setTimeout(() => window.close(), 3000);
}
