export function createPlaylistButton(): HTMLElement {
  // Structure: <div class="_contentButton_..."><i class="material-icons">playlist_add</i></div>
  // We need to mimic the class names. Since they are hashed, we should try to find an existing button and copy its classes
  // or use the ones we saw in the snippet but accept they might break.
  // Better approach: Find a sibling button at runtime and clone it, then modify.

  // However, for this function we are creating a fresh element. We'll rely on the injector to add classes if we want to be fancy,
  // or we just hardcode the ones we saw and hope they are somewhat stable or that we can style it ourselves.
  // Given CSS modules, we should probably style it ourselves mostly, or query the DOM.
  // Let's create a wrapper that we style, and try to reuse the 'material-icons' class which is likely global.

  const button = document.createElement("div");
  button.className = "fp-playlist-button";
  button.title = "Save to Playlist";

  // Attempt to match the style of the user provided snippet
  // class="_contentButton_1ptrm_1 _downloadButton_sjucv_1"
  // We will just add a custom class and some inline styles to match dimensions if needed.
  // But ideally we clone.
  // Let's create a "Save" icon.
  button.innerHTML = '<i class="material-icons" aria-hidden="true" style="font-size: 22px; cursor: pointer;">playlist_add</i>';

  // Add basic styles to make it look native-ish
  button.style.display = "flex";
  button.style.alignItems = "center";
  button.style.justifyContent = "center";
  button.style.height = "40px";
  button.style.borderRadius = "50%";
  button.style.cursor = "pointer";
  button.style.color = "inherit"; // Inherit text color from parent

  // Hover effect (simple)
  button.onmouseover = () => { button.style.backgroundColor = "rgba(255,255,255,0.1)"; };
  button.onmouseout = () => { button.style.backgroundColor = "transparent"; };

  button.onclick = async (e) => {
    e.preventDefault();
    e.stopPropagation();

    // Check if dropdown already exists
    const existing = document.querySelector(".fp-playlist-dropdown");
    if (existing) {
      existing.remove();
      return;
    }

    // Fetch playlists
    button.style.cursor = "wait";
    try {
      const response = await safeSendMessage({ type: "GET_PLAYLISTS", includeWatchLater: true });
      button.style.cursor = "pointer";

      if (response.success) {

        showDropdown(button, response.playlists);
      } else {
        alert("Error fetching playlists: " + response.error);
        if (response.error.includes("UserNotAuthenticated")) {
          alert("Please click the extension icon to login first.");
        }
      }
    } catch (err: any) {
      button.style.cursor = "pointer";
      console.error(err);
      alert("Communication error: " + err.message);
    }
  };

  return button;
}

// Helper to get image URL
function getImageUrl(image: any): string {
  if (!image) return "";
  return image.path; // Floatplane image models are usually full URLs or just need generic handling. 
  // Wait, iOS FloatplaneAPI says `imageBaseURL = "https://pbs.floatplane.com"`.
  // If path is relative, prepend. But API v3 usually returns full URLs in `path` sometimes?
  // Let's check a sample response if we could.
  // iOS `cachedAsyncImage` uses `thumbnail.fullURL`.
  // We might need to assume it's absolute for now or check if it starts with http.
  // Actually, Floatplane V3 usually returns full URLs in `path` if it's not legacy.
  // Safest check:
  if (image.path && (image.path.startsWith("http") || image.path.startsWith("//"))) return image.path;
  return `https://pbs.floatplane.com${image.path}`;
}

function showDropdown(anchor: HTMLElement, playlists: any[]) {
  const dropdown = document.createElement("div");
  dropdown.className = "fp-playlist-dropdown";
  Object.assign(dropdown.style, {
    position: "absolute",
    display: "flex",
    flexDirection: "column",
    backgroundColor: "#282828", // Slightly lighter than pure black, like YouTube dark mode
    color: "white",
    borderRadius: "12px",
    boxShadow: "0 4px 12px rgba(0,0,0,0.5)",
    zIndex: "9999",
    width: "260px",
    maxHeight: "400px",
    fontSize: "14px",
    fontFamily: "Roboto, Arial, sans-serif",
    overflow: "hidden"
  });

  // Calculate position
  const rect = anchor.getBoundingClientRect();
  const scrollX = window.scrollX || window.pageXOffset;
  const scrollY = window.scrollY || window.pageYOffset;

  dropdown.style.top = `${rect.bottom + scrollY + 8}px`;
  dropdown.style.left = `${rect.left + scrollX}px`;

  // Close handler
  const closeHandler = (e: MouseEvent) => {
    if (!dropdown.contains(e.target as Node) && e.target !== anchor) {
      removeDropdown();
    }
  };
  const removeDropdown = () => {
    dropdown.remove();
    document.removeEventListener("click", closeHandler);
  };
  setTimeout(() => document.addEventListener("click", closeHandler), 0);

  // --- Views ---

  // 1. Main View (Header, List, Footer)
  function renderMainView() {
    // Main View
    dropdown.innerHTML = "";

    // Header
    const header = document.createElement("div");
    Object.assign(header.style, {
      padding: "16px",
      borderBottom: "1px solid rgba(255,255,255,0.1)",
      display: "flex",
      justifyContent: "space-between",
      alignItems: "center"
    });
    header.innerHTML = `<span style="font-size: 16px; font-weight: 500;">Save to...</span>`;
    const closeIcon = document.createElement("div");
    closeIcon.innerHTML = `<svg viewBox="0 0 24 24" style="width: 24px; height: 24px; fill: white; cursor: pointer;"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"></path></svg>`;
    closeIcon.onclick = (e) => {
      e.stopPropagation();
      removeDropdown();
    };
    header.appendChild(closeIcon);
    dropdown.appendChild(header);

    // List Container
    const listContainer = document.createElement("div");
    Object.assign(listContainer.style, {
      overflowY: "auto",
      maxHeight: "200px",
      padding: "8px 0"
    });

    const videoId = getVideoIdFromUrl();
    if (!videoId) {
      listContainer.innerHTML = `<div style="padding: 16px; color: #aaa;">Could not identify video. Are you on a video page?</div>`;
    } else {
      if (playlists.length === 0) {
        listContainer.innerHTML = `<div style="padding: 16px; color: #aaa;">No playlists yet.</div>`;
      } else {
        playlists.forEach(pl => {
          const row = document.createElement("div");
          Object.assign(row.style, {
            display: "flex",
            alignItems: "center",
            padding: "8px 16px",
            cursor: "pointer",
            transition: "background-color 0.2s"
          });
          row.onmouseover = () => row.style.backgroundColor = "rgba(255,255,255,0.1)";
          row.onmouseout = () => row.style.backgroundColor = "transparent";

          const isIncludes = pl.video_ids && pl.video_ids.includes(videoId);

          const iconSvg = isIncludes
            ? `<svg viewBox="0 0 24 24" style="width: 24px; height: 24px; fill: #3ea6ff; margin-right: 16px;"><path d="M19 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.11 0 2-.9 2-2V5c0-1.1-.89-2-2-2zm-9 14l-5-5 1.41-1.41L10 12.17l7.59-7.59L19 6l-9 11z"></path></svg>`
            : `<svg viewBox="0 0 24 24" style="width: 24px; height: 24px; fill: #aaa; margin-right: 16px;"><path d="M19 5v14H5V5h14m0-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z"></path></svg>`;

          const label = document.createElement("div");
          label.style.flex = "1";
          label.style.overflow = "hidden";
          label.style.textOverflow = "ellipsis";
          label.style.whiteSpace = "nowrap";
          label.textContent = pl.name || "Untitled";

          const isPrivate = pl.name === "Watch Later";
          const lockIcon = isPrivate ? `<svg viewBox="0 0 24 24" style="width: 16px; height: 16px; fill: #aaa; margin-left: 8px;"><path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3 3.1-3 1.71 0 3.1 1.29 3.1 3v2z"></path></svg>` : "";

          row.innerHTML = `${iconSvg}`;
          row.appendChild(label);
          if (lockIcon) {
            const lockContainer = document.createElement("div");
            lockContainer.innerHTML = lockIcon;
            row.appendChild(lockContainer);
          }

          row.onclick = async (e) => {
            e.stopPropagation();
            const wasIncludes = isIncludes;
            const newIncludes = !wasIncludes;

            if (newIncludes) {
              if (!pl.video_ids) pl.video_ids = [];
              pl.video_ids.push(videoId);
            } else {
              pl.video_ids = pl.video_ids.filter((id: string) => id !== videoId);
            }
            renderMainView();

            const action = newIncludes ? "ADD_TO_PLAYLIST" : "REMOVE_FROM_PLAYLIST";
            try {
              const res = await safeSendMessage({
                type: action,
                playlistId: pl.id,
                videoId: videoId
              });
              if (!res.success) {
                console.error("Playlist action failed", res);
                if (newIncludes) {
                  pl.video_ids = pl.video_ids.filter((id: string) => id !== videoId);
                } else {
                  pl.video_ids.push(videoId);
                }
                renderMainView();
                alert("Failed to update playlist: " + res.error);
              }
            } catch (err) {
              console.error(err);
            }
          };

          listContainer.appendChild(row);
        });
      }
    }
    dropdown.appendChild(listContainer);

    // Footer: Create new playlist
    const footer = document.createElement("div");
    Object.assign(footer.style, {
      padding: "16px",
      borderTop: "1px solid rgba(255,255,255,0.1)",
      cursor: "pointer",
      display: "flex",
      alignItems: "center"
    });
    footer.innerHTML = `
        <svg viewBox="0 0 24 24" style="width: 24px; height: 24px; fill: white; margin-right: 16px;"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"></path></svg>
        <span style="font-weight: 500;">Create new playlist</span>
      `;
    footer.onmouseover = () => footer.style.backgroundColor = "rgba(255,255,255,0.1)";
    footer.onmouseout = () => footer.style.backgroundColor = "transparent";

    footer.onclick = (e) => {

      e.stopPropagation();
      renderCreateView();
    };
    dropdown.appendChild(footer);
  }

  // 2. Create View
  function renderCreateView() {

    dropdown.innerHTML = "";

    const container = document.createElement("div");
    container.style.padding = "16px";
    container.style.display = "flex";
    container.style.flexDirection = "column";
    container.style.gap = "16px";

    const header = document.createElement("div");
    header.textContent = "Create new playlist";
    header.style.fontSize = "16px";
    header.style.fontWeight = "500";
    header.style.marginBottom = "8px";

    const input = document.createElement("input");
    Object.assign(input.style, {
      background: "transparent",
      border: "none",
      borderBottom: "1px solid #aaa",
      color: "white",
      outline: "none",
      padding: "8px 0",
      fontSize: "14px",
      width: "100%"
    });
    input.placeholder = "Playlist name...";

    // Actions
    const actions = document.createElement("div");
    actions.style.display = "flex";
    actions.style.justifyContent = "flex-end";
    actions.style.gap = "8px";
    actions.style.marginTop = "16px";

    const cancelBtn = document.createElement("button");
    cancelBtn.textContent = "Cancel";
    Object.assign(cancelBtn.style, {
      background: "transparent",
      border: "none",
      color: "#aaa",
      cursor: "pointer",
      fontWeight: "500",
      padding: "8px 16px",
      textTransform: "uppercase"
    });
    cancelBtn.onclick = (e) => {
      e.stopPropagation();
      renderMainView();
    };

    const createBtn = document.createElement("button");
    createBtn.textContent = "Create";
    Object.assign(createBtn.style, {
      background: "transparent",
      border: "none",
      color: "#3ea6ff",
      cursor: "pointer",
      fontWeight: "500",
      padding: "8px 16px",
      textTransform: "uppercase"
    });

    createBtn.onclick = async (e) => {
      e.stopPropagation();
      const name = input.value.trim();
      if (!name) return;

      createBtn.textContent = "Creating...";
      createBtn.disabled = true;

      try {
        const res = await safeSendMessage({ type: "CREATE_PLAYLIST", name });

        if (res.success && res.result) {
          const newPl = res.result;
          if (!newPl.video_ids) newPl.video_ids = [];

          // Auto-add video
          const videoId = getVideoIdFromUrl();
          if (videoId && newPl.id) {
            const addRes = await safeSendMessage({
              type: "ADD_TO_PLAYLIST",
              playlistId: newPl.id,
              videoId: videoId
            });
            if (addRes.success) {
              newPl.video_ids.push(videoId);
            }
          }

          playlists.push(newPl);
          renderMainView();
        } else {
          alert("Error creating playlist: " + (res.error || "Unknown error"));
          createBtn.textContent = "Create";
          createBtn.disabled = false;
        }
      } catch (e: any) {
        alert("Error: " + e.message);
        createBtn.textContent = "Create";
        createBtn.disabled = false;
      }
    };

    container.appendChild(header);
    container.appendChild(input);
    actions.appendChild(cancelBtn);
    actions.appendChild(createBtn);
    container.appendChild(actions);

    dropdown.appendChild(container);

    input.focus();
  }

  // Initial Render
  renderMainView();

  document.body.appendChild(dropdown);
}

function getVideoIdFromUrl(): string | null {
  // Current URL: https://www.floatplane.com/post/c2Kxhk8JHB
  // check regex for /post/
  const match = window.location.pathname.match(/\/post\/([a-zA-Z0-9_\-]+)/);
  if (match) return match[1];

  // Check URL params just in case (e.g. video=...)
  const params = new URLSearchParams(window.location.search);
  if (params.get("video")) return params.get("video");

  // Fallback: Check if we are on a page that might have the ID in the URL differently? 
  // No, Floatplane uses /post/ID for video pages.
  return null;
}

export function createSidebarLink(): HTMLElement {
  // Structure:
  // <a class="lnav-item-base-color lnav-link-item" href="/playlists">
  //   <div class="item-icon ..."><svg>...</svg></div>
  //   <div class="item-label" role="tab"><span>Playlists</span></div>
  // </a>

  const link = document.createElement("a");
  link.className = "lnav-item-base-color lnav-link-item fp-sidebar-playlists";
  link.draggable = false;
  // content script cannot easily navigate SPA router without using the History API or window.location
  // we will intercept click
  link.href = "/playlists";

  // Icon
  const iconDiv = document.createElement("div");
  iconDiv.className = "item-icon";
  // Using Material Icon SVG path for 'playlist_play' or similar
  // Or just usage of material-icons class if sidebar supports it. 
  // The snippet uses SVGs (`MuiSvgIcon-root`).
  // Let's use a simple SVG for Playlist.
  iconDiv.innerHTML = `
    <svg class="MuiSvgIcon-root MuiSvgIcon-fontSizeMedium" focusable="false" aria-hidden="true" viewBox="0 0 24 24" style="fill: currentColor; width: 24px; height: 24px;">
        <path d="M19 9H2v2h17V9zm0-4H2v2h17V5zM2 15h13v-2H2v2zm15-2v6l5-3-5-3z"></path>
    </svg>
    `;

  // Label
  const labelDiv = document.createElement("div");
  labelDiv.className = "item-label";
  labelDiv.role = "tab";
  labelDiv.innerHTML = "<span>Playlists</span>";

  link.appendChild(iconDiv);
  link.appendChild(labelDiv);

  // Style adjustments to ensuring it fits
  link.style.display = "flex";
  link.style.alignItems = "center";
  link.style.padding = "10px 16px"; // Approx padding from visual
  link.style.textDecoration = "none";
  link.style.color = "inherit";
  link.style.cursor = "pointer";

  link.onclick = async (e) => {
    e.preventDefault();

    // Show Overlay
    showPlaylistOverlay();
  };

  // Re-create the inner HTML since replace_file_content replaces the whole block or function if we select it right.
  // I am replacing `createSidebarLink` entirely in my mental model, so I will rewrite it here.

  // Icon
  // const iconDiv = document.createElement("div"); // Already declared above
  // iconDiv.className = "item-icon"; // Already set above
  // iconDiv.innerHTML = ` // Already set above
  // <svg class="MuiSvgIcon-root MuiSvgIcon-fontSizeMedium" focusable="false" aria-hidden="true" viewBox="0 0 24 24" style="fill: currentColor; width: 24px; height: 24px;">
  //     <path d="M19 9H2v2h17V9zm0-4H2v2h17V5zM2 15h13v-2H2v2zm15-2v6l5-3-5-3z"></path>
  // </svg>
  // `;

  // const labelDiv = document.createElement("div"); // Already declared above
  // labelDiv.className = "item-label"; // Already set above
  // labelDiv.role = "tab"; // Already set above
  // labelDiv.innerHTML = "<span>Playlists</span>"; // Already set above

  // link.appendChild(iconDiv); // Already appended above
  // link.appendChild(labelDiv); // Already appended above

  // Style
  // link.style.display = "flex"; // Already set above
  // link.style.alignItems = "center"; // Already set above
  // link.style.padding = "10px 16px"; // Already set above
  // link.style.textDecoration = "none"; // Already set above
  // link.style.color = "inherit"; // Already set above
  // link.style.cursor = "pointer"; // Already set above

  return link;
}

async function showPlaylistOverlay() {
  const overlay = document.createElement("div");
  overlay.className = "fp-playlist-overlay";
  overlay.style.position = "fixed";
  overlay.style.top = "0";
  overlay.style.left = "0";
  overlay.style.width = "100%";
  overlay.style.height = "100%";
  overlay.style.backgroundColor = "rgba(0,0,0,0.9)";
  overlay.style.zIndex = "10000";
  overlay.style.display = "flex";
  overlay.style.flexDirection = "column";
  overlay.style.padding = "40px";
  overlay.style.color = "white";
  overlay.style.overflowY = "auto";
  overlay.style.fontFamily = "Roboto, sans-serif";

  const header = document.createElement("div");
  header.style.display = "flex";
  header.style.justifyContent = "space-between";
  header.style.alignItems = "center";
  header.style.marginBottom = "30px";
  header.style.maxWidth = "1200px";
  header.style.width = "100%";
  header.style.alignSelf = "center";

  const title = document.createElement("h2");
  title.textContent = "Your Playlists";
  title.style.fontSize = "24px";
  title.style.fontWeight = "bold";

  const closeBtn = document.createElement("button");
  closeBtn.textContent = "Close";
  closeBtn.style.padding = "8px 24px";
  closeBtn.style.backgroundColor = "#3e8ede";
  closeBtn.style.border = "none";
  closeBtn.style.borderRadius = "4px";
  closeBtn.style.color = "white";
  closeBtn.style.cursor = "pointer";
  closeBtn.style.fontWeight = "bold";
  closeBtn.onclick = () => overlay.remove();

  header.appendChild(title);

  const headerRight = document.createElement("div");
  headerRight.style.display = "flex";
  headerRight.style.alignItems = "center";
  headerRight.style.gap = "16px";

  headerRight.appendChild(closeBtn);
  header.appendChild(headerRight);
  overlay.appendChild(header);

  const container = document.createElement("div");
  container.style.maxWidth = "1200px";
  container.style.width = "100%";
  container.style.alignSelf = "center";
  container.style.display = "flex";
  container.style.flexDirection = "column";
  overlay.appendChild(container);

  const listContent = document.createElement("div");
  container.appendChild(listContent);
  listContent.appendChild(renderPlaylistSkeleton());

  document.body.appendChild(overlay);

  try {
    const response = await safeSendMessage({ type: "GET_PLAYLISTS", includeWatchLater: true });

    if (response.success) {
      listContent.textContent = "";

      if (response.playlists.length === 0) {
        listContent.textContent = "No playlists found.";
      } else {
        response.playlists.forEach((pl: any) => {
          const row = document.createElement("div");
          row.style.padding = "16px";
          row.style.marginBottom = "10px";
          row.style.backgroundColor = "#2d2d2d";
          row.style.borderRadius = "8px";
          row.style.cursor = "pointer";
          row.style.display = "flex";
          row.style.justifyContent = "space-between";
          row.style.alignItems = "center";
          row.style.transition = "background-color 0.2s";

          row.onmouseover = () => row.style.backgroundColor = "#3d3d3d";
          row.onmouseout = () => row.style.backgroundColor = "#2d2d2d";

          const name = pl.name || "Untitled";
          const count = Array.isArray(pl.video_ids) ? pl.video_ids.length : 0;

          // Left side
          const leftDiv = document.createElement("div");
          leftDiv.style.fontSize = "18px";
          leftDiv.style.fontWeight = "500";
          leftDiv.textContent = name;

          // Right side container
          const rightDiv = document.createElement("div");
          rightDiv.style.display = "flex";
          rightDiv.style.alignItems = "center";
          rightDiv.style.gap = "15px";

          const countDiv = document.createElement("div");
          countDiv.style.color = "#aaa";
          countDiv.textContent = `${count} videos`;

          // Delete Button
          const deleteBtn = document.createElement("div");
          Object.assign(deleteBtn.style, {
            width: "28px",
            height: "28px",
            borderRadius: "50%",
            backgroundColor: "#555",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            cursor: "pointer",
            color: "white",
            transition: "background-color 0.2s"
          });
          deleteBtn.title = "Delete Playlist";
          deleteBtn.innerHTML = `<svg viewBox="0 0 24 24" style="width: 18px; height: 18px; fill: white;"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"></path></svg>`;

          deleteBtn.onmouseover = () => deleteBtn.style.backgroundColor = "#ff0000";
          deleteBtn.onmouseout = () => deleteBtn.style.backgroundColor = "#555";

          deleteBtn.onclick = async (e) => {
            e.stopPropagation();
            if (!confirm(`Are you sure you want to delete playlist "${name}"?`)) return;

            deleteBtn.style.cursor = "wait";

            try {
              const res = await safeSendMessage({ type: "DELETE_PLAYLIST", playlistId: pl.id });
              if (res.success) {
                row.remove();
              } else {
                alert("Failed to delete playlist: " + res.error);
                deleteBtn.style.cursor = "pointer";
              }
            } catch (err: any) {
              alert("Error: " + err.message);
              deleteBtn.style.cursor = "pointer";
            }
          };

          rightDiv.appendChild(countDiv);
          rightDiv.appendChild(deleteBtn);

          row.appendChild(leftDiv);
          row.appendChild(rightDiv);

          row.onclick = async () => {
            // Navigate to detail view
            title.textContent = name;
            listContent.style.display = "none"; // Hide list

            // Create Detail View
            const detailContainer = document.createElement("div");
            container.appendChild(detailContainer);

            // Sort Dropdown
            const sortSelect = document.createElement("select");
            Object.assign(sortSelect.style, {
              padding: "6px 12px",
              backgroundColor: "#2d2d2d",
              color: "white",
              border: "1px solid rgba(255,255,255,0.2)",
              borderRadius: "4px",
              fontSize: "14px",
              outline: "none",
              cursor: "pointer"
            });
            const sortOptions = [
              { value: "publish_desc", label: "Publish Date Desc" },
              { value: "publish_asc", label: "Publish Date Asc" },
              { value: "added_desc", label: "Order Added Desc" },
              { value: "added_asc", label: "Order Added Asc" },
            ];
            sortOptions.forEach(opt => {
              const option = document.createElement("option");
              option.value = opt.value;
              option.textContent = opt.label;
              sortSelect.appendChild(option);
            });
            headerRight.prepend(sortSelect);

            // Render Skeleton Loader
            detailContainer.appendChild(renderSkeleton());

            // Back button logic
            closeBtn.textContent = "Back to Playlists";
            closeBtn.onclick = () => {
              sortSelect.remove();
              detailContainer.remove();
              listContent.style.display = "block";
              title.textContent = "Your Playlists";
              closeBtn.textContent = "Close";
              closeBtn.onclick = () => overlay.remove();
            };

            // Fetch videos
            if (!pl.video_ids || pl.video_ids.length === 0) {
              detailContainer.innerHTML = '<div style="padding: 40px; text-align: center; color: #aaa;">No videos in this playlist.</div>';
              return;
            }

            const res = await safeSendMessage({
              type: "GET_PLAYLIST_VIDEOS",
              videoIds: pl.video_ids
            });

            if (res.success) {
              // Render Grid
              detailContainer.innerHTML = "";

              let currentPosts = res.posts.map((p: any, i: number) => ({ ...p, _originalIndex: i }));

              const renderGrid = () => {
                detailContainer.innerHTML = "";

                const grid = document.createElement("div");
                grid.style.display = "grid";
                grid.style.gridTemplateColumns = "repeat(auto-fill, minmax(280px, 1fr))";
                grid.style.gap = "24px";
                grid.style.marginTop = "20px";

                currentPosts.forEach((post: any) => {
                  const card = document.createElement("a");
                  card.href = `/post/${post.id}`; // Native navigation
                  card.style.textDecoration = "none";
                  card.style.color = "inherit";
                  card.style.display = "flex";
                  card.style.flexDirection = "column";
                  card.style.backgroundColor = "#222";
                  card.style.borderRadius = "8px";
                  card.style.overflow = "hidden";
                  card.style.transform = "translateY(0)";
                  card.style.transition = "transform 0.2s";

                  card.onmouseover = () => card.style.transform = "translateY(-4px)";
                  card.onmouseout = () => card.style.transform = "translateY(0)";

                  const thumbUrl = getImageUrl(post.thumbnail);
                  const iconUrl = getImageUrl(post.creator?.icon);
                  const duration = post.metadata?.videoDuration
                    ? new Date(post.metadata.videoDuration * 1000).toISOString().substr(11, 8).replace(/^00:/, '')
                    : "";

                  // Thumbnail Container
                  const thumbContainer = document.createElement("div");
                  Object.assign(thumbContainer.style, {
                    position: "relative",
                    aspectRatio: "16/9",
                    backgroundColor: "#000"
                  });

                  if (thumbUrl) {
                    const img = document.createElement("img");
                    img.src = thumbUrl;
                    Object.assign(img.style, {
                      width: "100%",
                      height: "100%",
                      objectFit: "cover"
                    });
                    thumbContainer.appendChild(img);
                  }

                  if (duration) {
                    const durBadge = document.createElement("div");
                    durBadge.textContent = duration;
                    Object.assign(durBadge.style, {
                      position: "absolute",
                      bottom: "8px",
                      right: "8px",
                      background: "rgba(0,0,0,0.8)",
                      color: "white",
                      padding: "2px 6px",
                      borderRadius: "4px",
                      fontSize: "12px",
                      fontWeight: "bold"
                    });
                    thumbContainer.appendChild(durBadge);
                  }

                  // Remove Button
                  const removeBtn = document.createElement("div");
                  Object.assign(removeBtn.style, {
                    position: "absolute",
                    top: "6px",
                    right: "6px",
                    width: "28px",
                    height: "28px",
                    borderRadius: "50%",
                    backgroundColor: "rgba(0,0,0,0.6)", // Grayish default
                    color: "white",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    cursor: "pointer",
                    transition: "background-color 0.2s",
                    zIndex: "10",
                    opacity: "0" // Hidden by default
                  });
                  removeBtn.innerHTML = `<svg viewBox="0 0 24 24" style="width: 18px; height: 18px; fill: white;"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"></path></svg>`;

                  // Styling handlers
                  removeBtn.onmouseover = () => { removeBtn.style.backgroundColor = "#ff0000"; };
                  removeBtn.onmouseout = () => { removeBtn.style.backgroundColor = "rgba(0,0,0,0.6)"; };

                  // Show on card hover
                  card.addEventListener('mouseenter', () => { removeBtn.style.opacity = "1"; });
                  card.addEventListener('mouseleave', () => { removeBtn.style.opacity = "0"; });

                  removeBtn.onclick = async (e) => {
                    e.preventDefault();
                    e.stopPropagation();

                    if (!confirm("Remove this video from the playlist?")) return;

                    // Optimistic remove? Or Loading state?
                    removeBtn.style.backgroundColor = "#ff0000";
                    removeBtn.style.cursor = "wait";

                    try {
                      const res = await safeSendMessage({
                        type: "REMOVE_FROM_PLAYLIST",
                        playlistId: pl.id,
                        videoId: post.id
                      });

                      if (res.success) {
                        // Remove card and update model
                        card.remove();
                        currentPosts = currentPosts.filter((p: any) => p.id !== post.id);
                      } else {
                        alert("Failed to remove video: " + res.error);
                        removeBtn.style.backgroundColor = "rgba(0,0,0,0.6)";
                        removeBtn.style.cursor = "pointer";
                      }
                    } catch (err: any) {
                      alert("Error: " + err.message);
                    }
                  };

                  thumbContainer.appendChild(removeBtn);
                  card.appendChild(thumbContainer);


                  // Metadata
                  const metaDiv = document.createElement("div");
                  metaDiv.style.padding = "12px";
                  metaDiv.innerHTML = `
                          <div style="display: flex; gap: 10px;">
                              ${iconUrl ? `<img src="${iconUrl}" style="width: 36px; height: 36px; border-radius: 50%; object-fit: cover;">` : ''}
                              <div style="flex: 1; min-width: 0;">
                                  <div style="font-weight: 600; font-size: 15px; margin-bottom: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" title="${post.title}">${post.title}</div>
                                  <div style="color: #aaa; font-size: 13px;">${post.creator?.title || 'Unknown'}</div>
                                  <div style="color: #666; font-size: 12px; margin-top: 4px;">${new Date(post.releaseDate).toLocaleDateString()}</div>
                              </div>
                          </div>
                        `;
                  card.appendChild(metaDiv);

                  grid.appendChild(card);
                });
                detailContainer.appendChild(grid);
              };

              sortSelect.onchange = () => {
                const val = sortSelect.value;
                if (val === "publish_desc") {
                  currentPosts.sort((a: any, b: any) => new Date(b.releaseDate).getTime() - new Date(a.releaseDate).getTime());
                } else if (val === "publish_asc") {
                  currentPosts.sort((a: any, b: any) => new Date(a.releaseDate).getTime() - new Date(b.releaseDate).getTime());
                } else if (val === "added_asc") {
                  currentPosts.sort((a: any, b: any) => a._originalIndex - b._originalIndex);
                } else if (val === "added_desc") {
                  currentPosts.sort((a: any, b: any) => b._originalIndex - a._originalIndex);
                }
                renderGrid();
              };

              // Initial trigger
              sortSelect.onchange(null as any);

            } else {
              detailContainer.innerHTML = `<div style="color: red; padding: 20px;">Error loading videos: ${res.error}</div>`;
            }
          };

          listContent.appendChild(row);
        });
      }
    } else {
      listContent.textContent = "Error: " + response.error;
    }
  } catch (e: any) {
    listContent.textContent = "Error: " + e.message;
  }
}

async function safeSendMessage(message: any): Promise<any> {
  try {
    return await chrome.runtime.sendMessage(message);
  } catch (e: any) {
    if (e.message && e.message.includes("Extension context invalidated")) {
      throw new Error("There was an error, please refresh and try again");
    }
    throw e;
  }
}

function renderSkeleton(): HTMLElement {
  // Inject CSS for shimmer if not present
  if (!document.getElementById("fp-skeleton-style")) {
    const style = document.createElement("style");
    style.id = "fp-skeleton-style";
    style.textContent = `
            @keyframes fp-shimmer {
                0% { background-position: -200% 0; }
                100% { background-position: 200% 0; }
            }
            .fp-skeleton {
                background: linear-gradient(90deg, #2d2d2d 25%, #3d3d3d 50%, #2d2d2d 75%);
                background-size: 200% 100%;
                animation: fp-shimmer 1.5s infinite;
                border-radius: 4px;
            }
        `;
    document.head.appendChild(style);
  }

  const grid = document.createElement("div");
  grid.style.display = "grid";
  grid.style.gridTemplateColumns = "repeat(auto-fill, minmax(280px, 1fr))";
  grid.style.gap = "24px";
  grid.style.marginTop = "20px";

  // 12 empty cards
  for (let i = 0; i < 12; i++) {
    const card = document.createElement("div");
    card.style.display = "flex";
    card.style.flexDirection = "column";
    card.style.backgroundColor = "#222";
    card.style.borderRadius = "8px";
    card.style.overflow = "hidden";

    const thumb = document.createElement("div");
    thumb.className = "fp-skeleton";
    Object.assign(thumb.style, {
      aspectRatio: "16/9",
      width: "100%"
    });

    const content = document.createElement("div");
    content.style.padding = "12px";
    content.style.display = "flex";
    content.style.gap = "10px";

    const avatar = document.createElement("div");
    avatar.className = "fp-skeleton";
    Object.assign(avatar.style, {
      width: "36px",
      height: "36px",
      borderRadius: "50%",
      flexShrink: "0"
    });

    const textLines = document.createElement("div");
    textLines.style.flex = "1";
    textLines.style.display = "flex";
    textLines.style.flexDirection = "column";
    textLines.style.gap = "6px";

    const line1 = document.createElement("div");
    line1.className = "fp-skeleton";
    line1.style.height = "16px";
    line1.style.width = "80%";

    const line2 = document.createElement("div");
    line2.className = "fp-skeleton";
    line2.style.height = "12px";
    line2.style.width = "50%";

    textLines.appendChild(line1);
    textLines.appendChild(line2);

    content.appendChild(avatar);
    content.appendChild(textLines);

    card.appendChild(thumb);
    card.appendChild(content);
    grid.appendChild(card);
  }

  return grid;
}

function renderPlaylistSkeleton(): HTMLElement {
  // Ensure CSS is injected (reuse same style ID)
  if (!document.getElementById("fp-skeleton-style")) {
    // ... (same as above, but just call renderSkeleton() first or duplicate for safety if isolated? 
    // Actually, renderSkeleton checks it. We can just assume it or add the check.
    // Let's add the check to be safe and self-contained).
    const style = document.createElement("style");
    style.id = "fp-skeleton-style";
    style.textContent = `
            @keyframes fp-shimmer {
                0% { background-position: -200% 0; }
                100% { background-position: 200% 0; }
            }
            .fp-skeleton {
                background: linear-gradient(90deg, #2d2d2d 25%, #3d3d3d 50%, #2d2d2d 75%);
                background-size: 200% 100%;
                animation: fp-shimmer 1.5s infinite;
                border-radius: 4px;
            }
        `;
    document.head.appendChild(style);
  }

  const container = document.createElement("div");
  container.style.display = "flex";
  container.style.flexDirection = "column";
  container.style.gap = "10px";

  for (let i = 0; i < 5; i++) {
    const row = document.createElement("div");
    Object.assign(row.style, {
      padding: "16px",
      backgroundColor: "#2d2d2d",
      borderRadius: "8px",
      display: "flex",
      justifyContent: "space-between",
      alignItems: "center"
    });

    // Title skeleton
    const title = document.createElement("div");
    title.className = "fp-skeleton";
    title.style.width = "40%";
    title.style.height = "24px";

    // Count skeleton
    const count = document.createElement("div");
    count.className = "fp-skeleton";
    count.style.width = "15%";
    count.style.height = "18px";

    row.appendChild(title);
    row.appendChild(count);
    container.appendChild(row);
  }

  return container;
}
