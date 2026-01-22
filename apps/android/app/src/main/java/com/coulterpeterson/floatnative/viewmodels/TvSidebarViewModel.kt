package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3
import com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response
import com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response.UserInteraction
import com.coulterpeterson.floatnative.openapi.models.ContentLikeV3Request
import com.coulterpeterson.floatnative.openapi.models.GetProgressRequest
import com.coulterpeterson.floatnative.openapi.models.UpdateProgressRequest
import com.coulterpeterson.floatnative.api.PlaylistAddRequest
import com.coulterpeterson.floatnative.api.PlaylistRemoveRequest
import com.coulterpeterson.floatnative.api.PlaylistCreateRequest
import com.coulterpeterson.floatnative.api.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Unified State for Sidebar
data class SidebarState(
    val post: BlogPostModelV3, // We use BlogPostModelV3 as general model, or map to it
    val interaction: UserInteraction? = null,
    val isLoadingInteraction: Boolean = false,
    val currentView: SidebarView = SidebarView.Main
)

abstract class TvSidebarViewModel : ViewModel() {

    protected val _sidebarState = MutableStateFlow<SidebarState?>(null)
    val sidebarState = _sidebarState.asStateFlow()

    protected val _userPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val userPlaylists = _userPlaylists.asStateFlow()

    protected val _watchProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val watchProgress = _watchProgress.asStateFlow()

    open fun openSidebar(post: BlogPostModelV3) {
        _sidebarState.value = SidebarState(post = post, isLoadingInteraction = true)
        
        viewModelScope.launch {
            loadPlaylists() 
        }
        
        viewModelScope.launch {
            try {
                val response = FloatplaneApi.contentV3.getBlogPost(post.id)
                if (response.isSuccessful && response.body() != null) {
                    val fullPost = response.body()!!
                    val interaction = fullPost.userInteraction?.firstOrNull()
                    val currentState = _sidebarState.value
                    if (currentState != null && currentState.post.id == post.id) {
                         _sidebarState.value = currentState.copy(
                             interaction = interaction,
                             isLoadingInteraction = false
                         )
                    }
                } else {
                     val currentState = _sidebarState.value
                     if (currentState != null && currentState.post.id == post.id) {
                         _sidebarState.value = currentState.copy(isLoadingInteraction = false)
                     }
                }
            } catch (e: Exception) {
                 val currentState = _sidebarState.value
                 if (currentState != null && currentState.post.id == post.id) {
                     _sidebarState.value = currentState.copy(isLoadingInteraction = false)
                 }
            }
        }
    }

    // Overload for ContentPostV3Response if needed, converting to BlogPostModelV3 minimal
    // Overload for ContentPostV3Response if needed, converting to BlogPostModelV3 minimal
    open fun openSidebar(post: ContentPostV3Response) {
        openSidebar(toBlogPostModel(post))
    }
    
    // Shared Mapping Helper
    protected fun toBlogPostModel(post: ContentPostV3Response): BlogPostModelV3 {
        // Map Channel
        val channel = post.channel
        val blogChannel = com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3Channel(
            id = channel.id,
            creator = channel.creator,
            title = channel.title,
            urlname = channel.urlname,
            about = channel.about,
            cover = channel.cover,
            card = channel.card,
            icon = channel.icon,
            order = channel.order,
            socialLinks = channel.socialLinks
        )

        // Map Creator
        val creator = post.creator
        val blogCreatorOwner = com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3CreatorOwner(
            id = creator.owner, // V2 owner is String ID
            username = "" // Not available in V2
        )
        
        // Map Category (try match enum or default)
        val categoryEnum = try {
            com.coulterpeterson.floatnative.openapi.models.CreatorModelV3Category(
                 id = "legacy_v2", 
                 title = creator.category
            )
        } catch (e: Exception) {
             com.coulterpeterson.floatnative.openapi.models.CreatorModelV3Category(id="unknown", title="Unknown")
        }

        val blogCreator = com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3Creator(
            id = creator.id,
            owner = blogCreatorOwner,
            title = creator.title,
            urlname = creator.urlname,
            description = creator.description,
            about = creator.about,
            category = categoryEnum,
            cover = creator.cover,
            icon = creator.icon,
            liveStream = creator.liveStream,
            subscriptionPlans = creator.subscriptionPlans ?: emptyList(),
            discoverable = creator.discoverable,
            subscriberCountDisplay = creator.subscriberCountDisplay,
            incomeDisplay = creator.incomeDisplay,
            defaultChannel = creator.defaultChannel,
            channels = null, // V2 missing
            card = null // V2 missing
        )

        return BlogPostModelV3(
            id = post.id,
            guid = post.guid,
            title = post.title,
            text = post.text,
            type = BlogPostModelV3.Type.blogPost,
            channel = blogChannel,
            tags = post.tags,
            attachmentOrder = post.attachmentOrder,
            metadata = post.metadata,
            releaseDate = post.releaseDate,
            likes = post.likes,
            dislikes = post.dislikes,
            score = post.score,
            comments = post.comments,
            creator = blogCreator,
            wasReleasedSilently = post.wasReleasedSilently,
            thumbnail = post.thumbnail,
            isAccessible = post.isAccessible,
            videoAttachments = post.videoAttachments?.map { it.id }, 
            audioAttachments = null,
            pictureAttachments = null,
            galleryAttachments = null
        )
    }

    // Overloads and Legacy Support
    fun markAsWatched(post: ContentPostV3Response) {
        markAsWatched(toBlogPostModel(post))
    }
    
    fun toggleWatchLater(post: ContentPostV3Response, onResult: ((Boolean) -> Unit)? = null) {
        toggleWatchLater(toBlogPostModel(post)) { result ->
            onResult?.invoke(result)
        }
    }
    
    fun addToPlaylist(playlistId: String, videoId: String) {
        viewModelScope.launch {
            try {
                withCompanionRetry {
                    FloatplaneApi.companionApi.addToPlaylist(
                        id = playlistId,
                        request = PlaylistAddRequest(videoId)
                    )
                }
                loadPlaylists()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun removeFromPlaylist(playlistId: String, videoId: String) {
        viewModelScope.launch {
            try {
                withCompanionRetry {
                    FloatplaneApi.companionApi.removeFromPlaylist(
                        id = playlistId,
                        request = PlaylistRemoveRequest(videoId)
                    )
                }
                loadPlaylists()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    
    fun removeFromPlaylistApi(playlistId: String, videoId: String) = removeFromPlaylist(playlistId, videoId)

    fun closeSidebar() {
        _sidebarState.value = null
    }

    fun toggleSidebarView(view: SidebarView) {
        val currentState = _sidebarState.value ?: return
        _sidebarState.value = currentState.copy(currentView = view)
    }

    fun toggleSidebarLike() {
        val currentState = _sidebarState.value ?: return
        val currentInteraction = currentState.interaction
        val newInteraction = if (currentInteraction == UserInteraction.like) null else UserInteraction.like
        
        _sidebarState.value = currentState.copy(interaction = newInteraction)
        
        viewModelScope.launch {
            try {
                if (newInteraction == UserInteraction.like) {
                    FloatplaneApi.contentV3.likeContent(
                        ContentLikeV3Request(
                            id = currentState.post.id,
                            contentType = ContentLikeV3Request.ContentType.blogPost
                        )
                    )
                } else if (newInteraction == null && currentInteraction == UserInteraction.like) {
                      FloatplaneApi.contentV3.dislikeContent(
                        ContentLikeV3Request(
                            id = currentState.post.id,
                            contentType = ContentLikeV3Request.ContentType.blogPost
                        )
                    )
                }
            } catch (e: Exception) { }
        }
    }

    fun toggleSidebarDislike() {
        val currentState = _sidebarState.value ?: return
        val currentInteraction = currentState.interaction
        val newInteraction = if (currentInteraction == UserInteraction.dislike) null else UserInteraction.dislike
        
        _sidebarState.value = currentState.copy(interaction = newInteraction)
        
        viewModelScope.launch {
            try {
                if (newInteraction == UserInteraction.dislike) {
                    FloatplaneApi.contentV3.dislikeContent(
                        ContentLikeV3Request(
                            id = currentState.post.id,
                            contentType = ContentLikeV3Request.ContentType.blogPost
                        )
                    )
                }
            } catch (e: Exception) { }
        }
    }

    fun toggleWatchLater(post: BlogPostModelV3, onResult: (Boolean) -> Unit) {
         viewModelScope.launch {
            try {
                val playlists = _userPlaylists.value.takeIf { it.isNotEmpty() } 
                    ?: withCompanionRetry { FloatplaneApi.companionApi.getPlaylists(includeWatchLater = true) }.body()?.playlists 
                    ?: emptyList()
                    
                val watchLater = playlists.find { it.isWatchLater } ?: return@launch
                val isAdded = watchLater.videoIds.contains(post.id)
                var wasAdded = false
                
                // Optimistic Update
                val updatedPlaylists = _userPlaylists.value.map { pl ->
                    if (pl.id == watchLater.id) {
                         if (isAdded) {
                             pl.copy(videoIds = pl.videoIds - post.id)
                         } else {
                             pl.copy(videoIds = pl.videoIds + post.id)
                         }
                    } else {
                        pl
                    }
                }
                _userPlaylists.value = updatedPlaylists
                
                if (isAdded) {
                    withCompanionRetry {
                        FloatplaneApi.companionApi.removeFromPlaylist(
                            id = watchLater.id,
                            request = PlaylistRemoveRequest(post.id)
                        )
                    }
                    wasAdded = false
                } else {
                    withCompanionRetry {
                        FloatplaneApi.companionApi.addToPlaylist(
                            id = watchLater.id,
                            request = PlaylistAddRequest(post.id)
                        )
                    }
                    wasAdded = true
                }
                loadPlaylists()
                onResult(wasAdded)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun togglePlaylistMembership(playlist: Playlist, post: BlogPostModelV3) {
        viewModelScope.launch {
             try {
                 val isAdded = playlist.videoIds.contains(post.id)
                 
                 // Optimistic Update
                 val updatedPlaylists = _userPlaylists.value.map { pl ->
                     if (pl.id == playlist.id) {
                         if (isAdded) {
                             pl.copy(videoIds = pl.videoIds - post.id)
                         } else {
                             pl.copy(videoIds = pl.videoIds + post.id)
                         }
                     } else {
                         pl
                     }
                 }
                 _userPlaylists.value = updatedPlaylists

                 if (isAdded) {
                     withCompanionRetry {
                         FloatplaneApi.companionApi.removeFromPlaylist(
                             id = playlist.id,
                             request = PlaylistRemoveRequest(post.id)
                         )
                     }
                 } else {
                     withCompanionRetry {
                         FloatplaneApi.companionApi.addToPlaylist(
                             id = playlist.id,
                             request = PlaylistAddRequest(post.id)
                         )
                     }
                 }
                 loadPlaylists()
             } catch (e: Exception) {
                e.printStackTrace()
                loadPlaylists() 
             }
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            try {
                val response = withCompanionRetry {
                    FloatplaneApi.companionApi.getPlaylists(includeWatchLater = true)
                }
                if (response.isSuccessful && response.body() != null) {
                    _userPlaylists.value = response.body()!!.playlists ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                withCompanionRetry {
                    FloatplaneApi.companionApi.createPlaylist(
                        request = PlaylistCreateRequest(name)
                    )
                }
                loadPlaylists()
            } catch (e: Exception) { }
        }
    }

    fun markAsWatched(post: BlogPostModelV3) {
        viewModelScope.launch {
            try {
                 _watchProgress.value = _watchProgress.value + (post.id to 1.0f)
                 
                 val videoId = post.videoAttachments?.firstOrNull() ?: return@launch
                 val durationSeconds = post.metadata.videoDuration.toInt()
                 
                 FloatplaneApi.contentV3.updateProgress(
                     updateProgressRequest = UpdateProgressRequest(
                         id = videoId,
                         contentType = UpdateProgressRequest.ContentType.video,
                         progress = durationSeconds
                     )
                 )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Helper helpers
    
    protected suspend fun ensureCompanionLogin() {
        FloatplaneApi.ensureCompanionLogin()
    }
    
    protected suspend fun <T> withCompanionRetry(block: suspend () -> retrofit2.Response<T>): retrofit2.Response<T> {
        ensureCompanionLogin()
        val response = block()
        if (response.code() == 401) {
            FloatplaneApi.ensureCompanionLogin(forceRefresh = true)
            return block()
        }
        return response
    }
    
    protected fun fetchWatchProgress(posts: List<BlogPostModelV3>) {
        val videoPosts = posts.filter { it.metadata.hasVideo == true }
        if (videoPosts.isEmpty()) return

        val ids = videoPosts.map { it.id }
        viewModelScope.launch {
            try {
                ids.chunked(20).forEach { batchIds ->
                    val response = FloatplaneApi.contentV3.getProgress(
                        getProgressRequest = GetProgressRequest(
                            ids = batchIds,
                            contentType = GetProgressRequest.ContentType.blogPost
                        )
                    )
                    
                    if (response.isSuccessful && response.body() != null) {
                        val progressMap = response.body()!!.associate { 
                             it.id to (it.progress.toFloat() / 100f).coerceIn(0f, 1f)
                        }
                        
                        _watchProgress.value = _watchProgress.value + progressMap
                    }
                }
            } catch (e: Exception) {
               e.printStackTrace()
            }
        }
    }
}
