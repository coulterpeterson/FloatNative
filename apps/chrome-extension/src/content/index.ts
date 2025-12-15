import { createPlaylistButton, createSidebarLink } from "./ui";



// Configuration for selectors (using partial matching for CSS modules)
const SELECTORS = {
  BUTTON_CONTAINER: 'div[class*="_buttonContainer_"]',
  SIDEBAR_LIST: 'ul[role="tablist"]',
  // We want to insert before the download button or share button if possible
  DOWNLOAD_WRAPPER: 'div[class*="_downloadButtonWrapper_"]',
  SHARE_BUTTON_ICON: 'i:contains("share")' // Pseudo-selector logic
};

let observer: MutationObserver | null = null;
let buttonInjected = false;
let sidebarInjected = false;

function init() {


  // Initial check
  checkAndInject();

  // Set up observer
  observer = new MutationObserver((mutations) => {
    let shouldCheck = false;
    for (const mutation of mutations) {
      if (mutation.addedNodes.length > 0) {
        shouldCheck = true;
        break;
      }
    }
    if (shouldCheck) {
      checkAndInject();
    }
  });

  observer.observe(document.body, {
    childList: true,
    subtree: true
  });
}

function checkAndInject() {
  // 1. Video Page Button
  // Reset injection state if we navigated (simple check: if element is missing from DOM)
  if (buttonInjected && !document.querySelector(".fp-playlist-button")) {
    buttonInjected = false;
  }

  if (!buttonInjected) {
    const container = document.querySelector(SELECTORS.BUTTON_CONTAINER);
    if (container) {
      // Try to find download wrapper to insert before, or just append
      const downloadWrapper = container.querySelector(SELECTORS.DOWNLOAD_WRAPPER);

      if (!document.querySelector(".fp-playlist-button")) {
        const button = createPlaylistButton();
        if (downloadWrapper) {
          // container.insertBefore(button, downloadWrapper); // Maybe before download?
          // Actually user image shows it before everything (left of download).
          // "The playlist button would go here" -> Pointing to left of download/share.
          container.insertBefore(button, container.firstChild);
        } else {
          container.prepend(button);
        }
        buttonInjected = true;

      }
    }
  }

  // 2. Sidebar Link
  // We scan all sidebar lists because there might be multiple (e.g. desktop + hidden mobile/floating)
  const sidebarLists = document.querySelectorAll(SELECTORS.SIDEBAR_LIST);
  sidebarLists.forEach(sidebarList => {
    // Check if we already injected into this specific list
    if (!sidebarList.querySelector(".fp-sidebar-playlists")) {
      // Target: After "Browse Creators" (href="/browse")
      const browseLink = sidebarList.querySelector('a[href="/browse"]');
      if (browseLink) {
        const link = createSidebarLink();
        browseLink.insertAdjacentElement('afterend', link);

      }
    }
  });
}

// Start
if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", init);
} else {
  init();
}
