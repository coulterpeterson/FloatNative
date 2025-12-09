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
                    fetchAfter = if (isRefresh) null else null // TODO: Handle pagination state reset on refresh
                )

                if (response.isSuccessful && response.body() != null) {
                    val feedData = response.body()!!
                    if (isRefresh) {
                        currentPosts.clear()
                    }
                    val newPosts = feedData.blogPosts // verify field name
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
    
    // TODO: Implement loadMore()
}
