package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3
import com.coulterpeterson.floatnative.openapi.models.ContentCreatorListLastItems
import com.coulterpeterson.floatnative.openapi.models.UserSubscriptionModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class HomeFeedState {
    object Initial : HomeFeedState()
    object Loading : HomeFeedState()
    data class Content(val posts: List<BlogPostModelV3>) : HomeFeedState()
    data class Error(val message: String) : HomeFeedState()
}

class HomeFeedViewModel : ViewModel() {

    private val _state = MutableStateFlow<HomeFeedState>(HomeFeedState.Initial)
    val state = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private var subscriptions: List<UserSubscriptionModel> = emptyList()
    private var lastCursors: List<ContentCreatorListLastItems>? = null
    private var currentPosts: MutableList<BlogPostModelV3> = mutableListOf()

    init {
        loadFeed()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadFeed(isRefresh = true)
            _isRefreshing.value = false
        }
    }

    private fun loadFeed(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!isRefresh) _state.value = HomeFeedState.Loading
            
            try {
                // 1. Get Subscriptions if needed
                if (subscriptions.isEmpty()) {
                    val subResponse = FloatplaneApi.manual.getSubscriptions()
                    if (subResponse.isSuccessful && subResponse.body() != null) {
                        subscriptions = subResponse.body()!!
                    } else {
                        _state.value = HomeFeedState.Error("Failed to load subscriptions")
                        return@launch
                    }
                }

                if (subscriptions.isEmpty()) {
                     _state.value = HomeFeedState.Error("No subscriptions found")
                     return@launch
                }

                // 2. Get Feed
                val creatorIds = subscriptions.mapNotNull { it.creator }
                val response = FloatplaneApi.contentV3.getMultiCreatorBlogPosts(
                    ids = creatorIds,
                    limit = 20,
                    fetchAfter = emptyMap()
                )

                if (response.isSuccessful && response.body() != null) {
                    val feedData = response.body()!!
                    if (isRefresh) {
                        currentPosts.clear()
                    }
                    val newPosts = feedData.blogPosts
                    if (newPosts != null) {
                        currentPosts.addAll(newPosts)
                    }
                    lastCursors = feedData.lastElements
                    
                    _state.value = HomeFeedState.Content(currentPosts.toList())
                } else {
                     _state.value = HomeFeedState.Error("Failed to load feed")
                }

            } catch (e: Exception) {
                _state.value = HomeFeedState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value) return
        if (lastCursors == null || lastCursors!!.isEmpty()) return

        // Check if there are more posts to fetch (any cursor has moreFetchable == true)
        val hasMoreToFetch = lastCursors!!.any { it.moreFetchable == true }
        if (!hasMoreToFetch) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            
            try {
                // Ensure subscriptions are loaded
                if (subscriptions.isEmpty()) {
                    // Should be loaded by now, but safety check
                     _isLoadingMore.value = false
                     return@launch
                }
                
                val creatorIds = subscriptions.mapNotNull { it.creator }
                
                // Manually serialize fetchAfter cursors to query map
                // Format: fetchAfter[index][key]=value
                val fetchAfterMap = lastCursors!!.flatMapIndexed { index, item ->
                    val list = mutableListOf(
                        "fetchAfter[$index][creatorId]" to item.creatorId,
                        "fetchAfter[$index][moreFetchable]" to item.moreFetchable.toString()
                    )
                    item.blogPostId?.let {
                        list.add("fetchAfter[$index][blogPostId]" to it)
                    }
                    list
                }.toMap()

                // Use the multi-creator feed API with cursors
                val response = FloatplaneApi.contentV3.getMultiCreatorBlogPosts(
                    ids = creatorIds,
                    limit = 20,
                    fetchAfter = fetchAfterMap
                )

                if (response.isSuccessful && response.body() != null) {
                    val feedData = response.body()!!
                    val newPosts = feedData.blogPosts
                    
                    if (!newPosts.isNullOrEmpty()) {
                        // Filter out duplicates based on ID
                        val existingIds = currentPosts.map { it.id }.toSet()
                        val uniqueNewPosts = newPosts.filter { !existingIds.contains(it.id) }
                        
                        if (uniqueNewPosts.isNotEmpty()) {
                            currentPosts.addAll(uniqueNewPosts)
                             _state.value = HomeFeedState.Content(currentPosts.toList())
                        }
                    }
                    
                    // Update cursors for next fetch
                    lastCursors = feedData.lastElements
                }
                
            } catch (e: Exception) {
                // Silently fail for load more, user can try again by scrolling
            } finally {
                _isLoadingMore.value = false
            }
        }
    }
}
