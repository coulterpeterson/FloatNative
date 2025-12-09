import { AuthService } from "./auth";
import { CompanionAPI } from "./api";
import { FloatplaneAPI } from "./floatplane";

console.log("Floatplane Extension Background Worker Loaded");

chrome.runtime.onInstalled.addListener(() => {
  console.log("Extension installed");
});

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  console.log("Background received message:", message);

  if (message.type === "START_DEVICE_AUTH") {
    AuthService.getInstance().startDeviceAuth()
      .then(data => sendResponse({ success: true, data }))
      .catch(err => sendResponse({ success: false, error: err.toString() }));
    return true;
  }

  if (message.type === "POLL_DEVICE_TOKEN") {
    const { deviceCode } = message;
    AuthService.getInstance().pollDeviceToken(deviceCode)
      .then(data => {
        // If success, we also ensure Companion Login
        if (data.access_token) {
          CompanionAPI.getInstance().ensureLoggedIn().catch(console.error);
        }
        sendResponse({ success: true, data });
      })
      .catch(err => sendResponse({ success: false, error: err.toString() }));
    return true;
  }

  if (message.type === "GET_PLAYLISTS") {
    const { includeWatchLater } = message;
    CompanionAPI.getInstance().getPlaylists(includeWatchLater)
      .then(playlists => sendResponse({ success: true, playlists }))
    return true;
  }

  if (message.type === "GET_PLAYLIST_VIDEOS") {
    const { videoIds } = message;
    FloatplaneAPI.getInstance().getPostDetails(videoIds)
      .then(posts => sendResponse({ success: true, posts }))
      .catch(err => sendResponse({ success: false, error: err.toString() }));
    return true;
  }

  if (message.type === "ADD_TO_PLAYLIST") {
    const { playlistId, videoId } = message;
    CompanionAPI.getInstance().addToPlaylist(playlistId, videoId)
      .then(result => sendResponse({ success: true, result }))
      .catch(err => sendResponse({ success: false, error: err.toString() }));
    return true;
  }

  if (message.type === "REMOVE_FROM_PLAYLIST") {
    const { playlistId, videoId } = message;
    CompanionAPI.getInstance().removeFromPlaylist(playlistId, videoId)
      .then(result => sendResponse({ success: true, result }))
      .catch(err => sendResponse({ success: false, error: err.toString() }));
    return true;
  }

  if (message.type === "CREATE_PLAYLIST") {
    const { name } = message;
    CompanionAPI.getInstance().createPlaylist(name)
      .then(result => sendResponse({ success: true, result }))
      .catch(err => sendResponse({ success: false, error: err.toString() }));
    return true;
  }
});
