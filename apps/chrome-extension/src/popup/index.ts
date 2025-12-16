console.log("Popup script loaded");

const elements = {
  loginView: document.getElementById("login-view")!,
  successView: document.getElementById("success-view")!,
  loginBtn: document.getElementById("login-btn")!,
  status: document.getElementById("status")!
};

document.addEventListener("DOMContentLoaded", async () => {
  elements.loginBtn.addEventListener("click", startAuth);

  // Check if already logged in
  try {
    const response = await chrome.runtime.sendMessage({ type: "CHECK_AUTH_STATUS" });
    if (response && response.isAuthenticated) {
      showSuccess();
    }
  } catch (e) {
    console.error("Failed to check auth status", e);
  }
});

async function startAuth() {
  elements.status.textContent = "Launching login window...";
  elements.loginBtn.setAttribute("disabled", "true");

  try {
    const response = await chrome.runtime.sendMessage({ type: "START_AUTH_FLOW" });

    if (!response || !response.success) {
      elements.status.textContent = "Error: " + (response?.error || "Unknown error");
      elements.loginBtn.removeAttribute("disabled");
      return;
    }

    // Success
    showSuccess();

  } catch (e: any) {
    elements.status.textContent = "Error: " + e.message;
    elements.loginBtn.removeAttribute("disabled");
  }
}

function showSuccess() {
  elements.loginView.style.display = "none";
  elements.successView.style.display = "block";
  // Close after delay
  setTimeout(() => window.close(), 2000);
}
