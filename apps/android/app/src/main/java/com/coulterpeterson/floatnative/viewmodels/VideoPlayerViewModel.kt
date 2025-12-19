package com.coulterpeterson.floatnative.viewmodels

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.openapi.apis.DeliveryV3Api
import com.coulterpeterson.floatnative.openapi.models.*
import java.io.File
import java.io.FileOutputStream
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import okhttp3.Request
import java.io.InputStream

sealed class VideoPlayerState {
    object Idle : VideoPlayerState()
    object Loading : VideoPlayerState()
    data class Content(
        val blogPost: ContentPostV3Response,
        val videoUrl: String,
        // Interaction State
        val likes: Int,
        val dislikes: Int,
        val userInteraction: ContentPostV3Response.UserInteraction? = null,
        // Comments State
        val comments: List<CommentModel> = emptyList(),
        val isLoadingComments: Boolean = false,
        // Quality State
        // Quality State
        val availableQualities: List<CdnDeliveryV3Variant> = emptyList(),
        val currentQuality: CdnDeliveryV3Variant? = null,
        val group: CdnDeliveryV3Group? = null,
        val currentUser: UserModel? = null,
        val replyingToComment: CommentModel? = null
    ) : VideoPlayerState()
    data class Error(val message: String) : VideoPlayerState()
}

sealed class PlayerAction {
    data class Seek(val position: Long) : PlayerAction()
}

class VideoPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<VideoPlayerState>(VideoPlayerState.Idle)
    val state = _state.asStateFlow()

    private val _downloadState = MutableStateFlow<Boolean>(false)
    val downloadState = _downloadState.asStateFlow()

    private val _downloadCompletedEvent = MutableSharedFlow<String>()
    val downloadCompletedEvent = _downloadCompletedEvent.asSharedFlow()

    private val _requestPermissionEvent = MutableSharedFlow<Unit>()
    val requestPermissionEvent = _requestPermissionEvent.asSharedFlow()

    private val _playerAction = MutableSharedFlow<PlayerAction>()
    val playerAction = _playerAction.asSharedFlow()

    fun downloadVideo() {
        val currentState = _state.value as? VideoPlayerState.Content ?: return
        val context = getApplication<Application>()
        
        viewModelScope.launch {
            _downloadState.value = true
            try {
                // 1. Get video ID
                val videoId = currentState.blogPost.videoAttachments?.firstOrNull()?.id ?: run {
                     _downloadState.value = false
                     return@launch
                }

                // 2. Fetch Download Delivery Info
                val deliveryResponse = FloatplaneApi.deliveryV3.getDeliveryInfoV3(
                    scenario = DeliveryV3Api.ScenarioGetDeliveryInfoV3.download,
                    entityId = videoId,
                    outputKind = DeliveryV3Api.OutputKindGetDeliveryInfoV3.flat
                )

                if (!deliveryResponse.isSuccessful || deliveryResponse.body() == null) {
                    _downloadState.value = false
                     return@launch
                }

                val deliveryInfo = deliveryResponse.body()!!
                val downloadGroup = deliveryInfo.groups.firstOrNull()
                val downloadVariants = downloadGroup?.variants ?: emptyList()

                // 3. Match quality
                // Try to find a download variant that matches the *current* playback quality's resolution/label
                val targetQuality = currentState.currentQuality
                var selectedVariant = downloadVariants.find { 
                    it.label == targetQuality?.label 
                }
                
                // Fallback: Best available if no exact match or no target
                if (selectedVariant == null) {
                     selectedVariant = downloadVariants.firstOrNull()
                }

                if (selectedVariant == null) {
                    _downloadState.value = false
                    return@launch
                }
                
                val downloadUrl = selectedVariant.url
                // Note: 'flat' URLs might still need resolution if they are relative? 
                // Usually flat MP4s are absolute, but let's check.
                // If it's relative, we can use the same resolveUrl helper using the downloadGroup.

                var finalUrl = downloadUrl
                if (downloadGroup != null) {
                    finalUrl = resolveUrl(downloadUrl, selectedVariant, downloadGroup)
                }

                // 4. Create File (Check permissions)
                val downloadUriString = FloatplaneApi.tokenManager.downloadLocationUri
                if (downloadUriString == null) {
                    _downloadState.value = false
                    _requestPermissionEvent.emit(Unit)
                    return@launch
                }

                val treeUri = android.net.Uri.parse(downloadUriString)
                val fileName = "${currentState.blogPost.title.replace("[^a-zA-Z0-9.-]", "_")}.mp4"
                
                val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                val newFile = docFile?.createFile("video/mp4", fileName)
                
                if (newFile == null) {
                     _downloadState.value = false
                     _requestPermissionEvent.emit(Unit)
                     return@launch
                }

                withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(finalUrl).build()
                    val response = FloatplaneApi.okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")

                    val body = response.body ?: throw Exception("No body")
                    val inputStream = body.byteStream()
                    // Open output stream via ContentResolver
                    val outputStream = context.contentResolver.openOutputStream(newFile.uri) ?: throw Exception("Cannot open output stream")

                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                
                _downloadCompletedEvent.emit(fileName)

            } catch (e: Exception) {
                e.printStackTrace()
                // Could emit error event here
                FloatplaneApi.tokenManager.downloadLocationUri = null // Reset if failed?
                _requestPermissionEvent.emit(Unit) // Retry permission
            } finally {
                _downloadState.value = false
            }
        }
    }

    fun setDownloadLocation(uri: String) {
        FloatplaneApi.tokenManager.downloadLocationUri = uri
        // Auto-retry download?
        downloadVideo()
    }


    fun loadVideo(postId: String) {
        if (_state.value is VideoPlayerState.Content && (_state.value as VideoPlayerState.Content).blogPost.id == postId) {
            return // Already loaded
        }

        viewModelScope.launch {
            _state.value = VideoPlayerState.Loading

            try {
                // 1. Fetch Blog Post details
                val postResponse = FloatplaneApi.contentV3.getBlogPost(postId)
                if (!postResponse.isSuccessful || postResponse.body() == null) {
                    _state.value = VideoPlayerState.Error("Failed to load post details")
                    return@launch
                }
                val post = postResponse.body()!!

                // Determine initial interaction state causing types to match
                val initialInteraction = post.userInteraction?.firstOrNull()

                // 2. Get Video ID (first attachment)
                val videoId = post.videoAttachments?.firstOrNull()?.id
                if (videoId == null) {
                    // It might be a text/image post, but strictly for VideoPlayerScreen we expect video?
                    // For parity with iOS, if no video, we just show content. But let's handle video flow first.
                     _state.value = VideoPlayerState.Error("No video found in this post")
                    return@launch
                }

                // 3. Get Delivery Info
                val deliveryResponse = FloatplaneApi.deliveryV3.getDeliveryInfoV3(
                    scenario = DeliveryV3Api.ScenarioGetDeliveryInfoV3.onDemand,
                    entityId = videoId,
                    outputKind = DeliveryV3Api.OutputKindGetDeliveryInfoV3.hlsPeriodFmp4
                )

                if (!deliveryResponse.isSuccessful || deliveryResponse.body() == null) {
                    _state.value = VideoPlayerState.Error("Failed to load video stream")
                    return@launch
                }

                val deliveryInfo = deliveryResponse.body()!!
                val group = deliveryInfo.groups.firstOrNull()
                // Get all variants
                var variants = group?.variants ?: emptyList()
                
                // User Request: Remove 4K options
                variants = variants.filter { variant ->
                    !variant.label.contains("4K", true) && !variant.label.contains("2160p", true)
                }

                // Default to 1080p or first available
                val variant = variants.find { it.label.contains("1080p", true) } ?: variants.firstOrNull()

                if (group != null && variant != null) {
                    val streamUrl = resolveUrl(variant.url, variant, group)
                    
                    _state.value = VideoPlayerState.Content(
                        blogPost = post,
                        videoUrl = streamUrl,
                        likes = post.likes,
                        dislikes = post.dislikes,
                        userInteraction = initialInteraction,
                        availableQualities = variants,
                        currentQuality = variant,
                        group = group
                    )
                    
                    // Load comments after initial content load
                    loadComments(postId)
                    
                    // Fetch Current User separately (fire and forget update)
                    launch {
                        try {
                            val userResponse = FloatplaneApi.userV3.getSelf()
                            if (userResponse.isSuccessful && userResponse.body() != null) {
                                val userSelf = userResponse.body()!!
                                val userModel = UserModel(
                                    id = userSelf.id,
                                    username = userSelf.username,
                                    profileImage = userSelf.profileImage
                                )
                                // Update state with current user
                                val updateState = _state.value as? VideoPlayerState.Content
                                if (updateState != null) {
                                    _state.value = updateState.copy(currentUser = userModel)
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore user fetch failure
                        }
                    }
                    
                } else {
                    _state.value = VideoPlayerState.Error("No stream URL found")
                }

            } catch (e: Exception) {
                _state.value = VideoPlayerState.Error(e.message ?: "Unknown error")
            }
        }
    }
    


    private fun loadComments(postId: String) {
        val currentState = _state.value as? VideoPlayerState.Content ?: return
        
        viewModelScope.launch {
            // Update state to loading comments
            _state.value = currentState.copy(isLoadingComments = true)
            
            try {
                val response = FloatplaneApi.commentV3.getComments(blogPost = postId, limit = 20)
                if (response.isSuccessful && response.body() != null) {
                    val comments = response.body()!!
                    val updatedState = (_state.value as? VideoPlayerState.Content)?.copy(
                        comments = comments,
                        isLoadingComments = false
                    )
                    if (updatedState != null) {
                        _state.value = updatedState
                    }
                } else {
                     // Silently fail comment load or show error state in comments section
                     val updatedState = (_state.value as? VideoPlayerState.Content)?.copy(isLoadingComments = false)
                     if (updatedState != null) _state.value = updatedState
                }
            } catch (e: Exception) {
                val updatedState = (_state.value as? VideoPlayerState.Content)?.copy(isLoadingComments = false)
                if (updatedState != null) _state.value = updatedState
            }
        }
    }

    fun toggleLike() {
        val currentState = _state.value as? VideoPlayerState.Content ?: return
        val currentInteraction = currentState.userInteraction
        
        val newInteraction = if (currentInteraction == ContentPostV3Response.UserInteraction.like) null else ContentPostV3Response.UserInteraction.like
        
        // Optimistic Update
        var newLikes = currentState.likes
        var newDislikes = currentState.dislikes
        
        if (currentInteraction == ContentPostV3Response.UserInteraction.like) {
            newLikes -= 1
        } else if (currentInteraction == ContentPostV3Response.UserInteraction.dislike) {
            newDislikes -= 1
            newLikes += 1
        } else {
            newLikes += 1
        }
        
        if (newInteraction == null) {
             // If we untoggled like, undo the increment
             // Logic above handles "if previously liked, decrement". 
             // If we are setting to null (removing like), newLikes should be decremented.
             // Wait, the logic block above handles:
             // 1. Was Like -> remove like (likes -1), new state null. Correct.
             // 2. Was Dislike -> remove dislike (dislikes -1), add like (likes +1), new state like. Correct.
             // 3. Was Null -> add like (likes +1), new state like. Correct.
        } else if (newInteraction == ContentPostV3Response.UserInteraction.dislike) {
             // This function is toggleLike, so we only switch to Like or Null.
             // But if we toggle like while Disliked, we switch to Like.
        }

        _state.value = currentState.copy(
            likes = newLikes,
            dislikes = newDislikes,
            userInteraction = newInteraction
        )
        
        viewModelScope.launch {
            try {
                FloatplaneApi.contentV3.likeContent(
                    ContentLikeV3Request(
                        id = currentState.blogPost.id, 
                        contentType = ContentLikeV3Request.ContentType.blogPost
                    )
                )
            } catch (e: Exception) {
                // Revert on error? For now, just log/ignore
            }
        }
    }

    fun toggleDislike() {
        val currentState = _state.value as? VideoPlayerState.Content ?: return
        val currentInteraction = currentState.userInteraction
        
        val newInteraction = if (currentInteraction == ContentPostV3Response.UserInteraction.dislike) null else ContentPostV3Response.UserInteraction.dislike
        
        // Optimistic Update
        var newLikes = currentState.likes
        var newDislikes = currentState.dislikes
        
        if (currentInteraction == ContentPostV3Response.UserInteraction.dislike) {
            newDislikes -= 1
        } else if (currentInteraction == ContentPostV3Response.UserInteraction.like) {
            newLikes -= 1
            newDislikes += 1
        } else {
            newDislikes += 1
        }

        _state.value = currentState.copy(
            likes = newLikes,
            dislikes = newDislikes,
            userInteraction = newInteraction
        )
        
        viewModelScope.launch {
            try {
                FloatplaneApi.contentV3.dislikeContent(
                    ContentLikeV3Request(
                        id = currentState.blogPost.id, 
                        contentType = ContentLikeV3Request.ContentType.blogPost
                    )
                )
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun startReply(comment: CommentModel) {
        val currentState = _state.value as? VideoPlayerState.Content ?: return
        _state.value = currentState.copy(replyingToComment = comment)
    }

    fun cancelReply() {
        val currentState = _state.value as? VideoPlayerState.Content ?: return
        _state.value = currentState.copy(replyingToComment = null)
    }

    fun likeComment(commentId: String) {
        val currentState = _state.value as? VideoPlayerState.Content ?: return
        
        // Optimistic update
        _state.value = currentState.copy(
            comments = updateCommentInteraction(currentState.comments, commentId, true)
        )
        
        viewModelScope.launch {
            try {
                val request = CommentLikeV3PostRequest(
                    comment = commentId,
                    blogPost = currentState.blogPost.id
                )
                FloatplaneApi.commentV3.likeComment(request)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun dislikeComment(commentId: String) {
        val currentState = _state.value as? VideoPlayerState.Content ?: return

        // Optimistic update
        _state.value = currentState.copy(
            comments = updateCommentInteraction(currentState.comments, commentId, false)
        )

        viewModelScope.launch {
            try {
                val request = CommentLikeV3PostRequest(
                    comment = commentId,
                    blogPost = currentState.blogPost.id
                )
                FloatplaneApi.commentV3.dislikeComment(request)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun updateCommentInteraction(comments: List<CommentModel>, targetId: String, isLike: Boolean): List<CommentModel> {
        return comments.map { comment ->
            if (comment.id == targetId) {
                val currentLikes = comment.userInteraction?.contains(CommentModel.UserInteraction.like) == true
                val currentDislikes = comment.userInteraction?.contains(CommentModel.UserInteraction.dislike) == true
                
                val newInteraction = ArrayList<CommentModel.UserInteraction>()
                // Copy existing interactions excluding like/dislike
                comment.userInteraction?.forEach {
                    if (it != CommentModel.UserInteraction.like && it != CommentModel.UserInteraction.dislike) {
                        newInteraction.add(it)
                    }
                }

                var newLikeCount = comment.likes
                var newDislikeCount = comment.dislikes
                
                if (isLike) {
                   if (currentLikes) {
                       newLikeCount--
                   } else {
                       newLikeCount++
                       newInteraction.add(CommentModel.UserInteraction.like)
                       if (currentDislikes) newDislikeCount--
                   }
                } else { // Dislike
                    if (currentDislikes) {
                        newDislikeCount--
                    } else {
                        newDislikeCount++
                        newInteraction.add(CommentModel.UserInteraction.dislike)
                        if (currentLikes) newLikeCount--
                    }
                }
                
                comment.copy(
                    likes = newLikeCount, 
                    dislikes = newDislikeCount,
                    userInteraction = newInteraction,
                    interactionCounts = com.coulterpeterson.floatnative.openapi.models.CommentV3PostResponseInteractionCounts(
                        like = newLikeCount,
                         dislike = newDislikeCount
                    )
                )
            } else {
                val updatedReplies = if (!comment.replies.isNullOrEmpty()) {
                    updateCommentInteraction(comment.replies!!, targetId, isLike)
                } else {
                    comment.replies
                }
                comment.copy(replies = updatedReplies)
            }
        }
    }
    
    fun changeQuality(quality: CdnDeliveryV3Variant) {
        val currentState = _state.value as? VideoPlayerState.Content ?: return
        if (quality == currentState.currentQuality) return
        
        // Use stored group to resolve URL
        val group = currentState.group
        var streamUrl = quality.url
        
        if (group != null) {
             streamUrl = resolveUrl(quality.url, quality, group)
        }
        
        _state.value = currentState.copy(
            currentQuality = quality,
            videoUrl = streamUrl // This forces a player reload in the UI
        )
    }

    fun postComment(text: String) {
        val currentState = _state.value as? VideoPlayerState.Content ?: return
        val currentUser = currentState.currentUser ?: return // Need user info for optimistic update
        val replyingTo = currentState.replyingToComment
        
        // Construct fake comment immediately
        val newComment = CommentModel(
            id = UUID.randomUUID().toString(),
            blogPost = currentState.blogPost.id,
            user = currentUser,
            text = text,
            replying = replyingTo?.id,
            postDate = OffsetDateTime.now(),
            editDate = null,
            editCount = 0,
            isEdited = false,
            likes = 0,
            dislikes = 0,
            score = 0,
            interactionCounts = CommentV3PostResponseInteractionCounts(0, 0),
            userInteraction = emptyList(),
            totalReplies = 0,
            replies = emptyList()
        )
        
        // Optimistically update
        val newComments = if (replyingTo != null) {
            insertReply(currentState.comments, replyingTo.id, newComment)
        } else {
            listOf(newComment) + currentState.comments
        }
        
        _state.value = currentState.copy(
            comments = newComments,
            replyingToComment = null // Clear reply state
        )
        
        // Fire and forget API call
        viewModelScope.launch {
            try {
                if (replyingTo != null) {
                    val request = CommentV3ReplyRequest(
                        blogPost = currentState.blogPost.id,
                        text = text,
                        replyTo = replyingTo.id
                    )
                    FloatplaneApi.commentV3.postReply(request)
                } else {
                    val request = CommentV3PostRequest(
                        blogPost = currentState.blogPost.id,
                        text = text
                    )
                    FloatplaneApi.commentV3.postComment(request)
                }
            } catch (e: Exception) {
                // Ignore failure as requested
            }
        }
    }

    private fun insertReply(comments: List<CommentModel>, parentId: String, newReply: CommentModel): List<CommentModel> {
        return comments.map { comment ->
            if (comment.id == parentId) {
                val currentReplies = comment.replies ?: emptyList()
                val newReplies = currentReplies + newReply
                comment.copy(replies = newReplies, totalReplies = (comment.totalReplies ?: 0) + 1)
            } else {
                val updatedReplies = if (!comment.replies.isNullOrEmpty()) {
                    insertReply(comment.replies!!, parentId, newReply)
                } else {
                    comment.replies
                }
                comment.copy(replies = updatedReplies)
            }
        }
    }

    fun resolveUrl(url: String, variant: CdnDeliveryV3Variant, group: CdnDeliveryV3Group): String {
        var streamUrl = url
        if (!streamUrl.startsWith("http")) {
            val origin = variant.origins?.firstOrNull() ?: group.origins?.firstOrNull()
            val baseUrl = origin?.url?.toString()?.trimEnd('/') ?: "https://www.floatplane.com"
            val relativePath = streamUrl.trimStart('/')
            streamUrl = "$baseUrl/$relativePath"
        }
        return streamUrl
    }

    fun seekTo(position: Long) {
        viewModelScope.launch {
            _playerAction.emit(PlayerAction.Seek(position))
        }
    }
}

