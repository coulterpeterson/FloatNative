import { AuthService } from "./auth";
import { CompanionAPI } from "./api";
import { FloatplaneAPI } from "./floatplane";

console.log("Floatplane Extension Background Worker Loaded");

chrome.runtime.onInstalled.addListener(() => {
  console.log("Extension installed");
});

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  console.log("Background received message:", message);

  if (message.type === "CHECK_AUTH_STATUS") {
    AuthService.getInstance().getAccessToken()
      .then(token => sendResponse({ isAuthenticated: !!token }))
      .catch(err => sendResponse({ isAuthenticated: false, error: err.toString() }));
    return true;
  }

  if (message.type === "START_AUTH_FLOW") {
    AuthService.getInstance().startAuthFlow()
      .then(() => {
        // Ensure companion login
        CompanionAPI.getInstance().ensureLoggedIn().catch(console.error);
        sendResponse({ success: true });
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

  if (message.type === "DELETE_PLAYLIST") {
    const { playlistId } = message;
    CompanionAPI.getInstance().deletePlaylist(playlistId)
      .then(result => sendResponse({ success: true, result }))
      .catch(err => sendResponse({ success: false, error: err.toString() }));
    return true;
  }
});
