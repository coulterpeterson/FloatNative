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

    sealed class FeedFilter {
        object All : FeedFilter()
        data class Creator(val id: String) : FeedFilter()
        data class Channel(val id: String, val creatorId: String) : FeedFilter()
    }

    private val _filter = MutableStateFlow<FeedFilter>(FeedFilter.All)
    val filter = _filter.asStateFlow()

    private var lastFetchAfter: Int = 0 // For single creator/channel offset pagination

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

    fun setFilter(filter: FeedFilter) {
        _filter.value = filter
        loadFeed(isRefresh = true)
    }

    private fun loadFeed(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!isRefresh) _state.value = HomeFeedState.Loading
            
            try {
                // 1. Get Subscriptions if needed (only for All filter)
                if (_filter.value is FeedFilter.All && subscriptions.isEmpty()) {
                    val subResponse = FloatplaneApi.manual.getSubscriptions()
                    if (subResponse.isSuccessful && subResponse.body() != null) {
                        subscriptions = subResponse.body()!!
                    } else {
                        // If loading all feed and subs fail, we can't show anything
                        if (_filter.value is FeedFilter.All) {
                            _state.value = HomeFeedState.Error("Failed to load subscriptions")
                            return@launch
                        }
                    }
                }

                if (_filter.value is FeedFilter.All && subscriptions.isEmpty()) {
                     _state.value = HomeFeedState.Error("No subscriptions found")
                     return@launch
                }

                // 2. Get Feed based on filter
                if (isRefresh) {
                    currentPosts.clear()
                    lastCursors = null
                    lastFetchAfter = 0
                }

                when (val currentFilter = _filter.value) {
                    is FeedFilter.All -> {
                        val creatorIds = subscriptions.mapNotNull { it.creator }
                        val response = FloatplaneApi.contentV3.getMultiCreatorBlogPosts(
                            ids = creatorIds,
                            limit = 20,
                            fetchAfter = emptyMap()
                        )

                        if (response.isSuccessful && response.body() != null) {
                            val feedData = response.body()!!
                            val newPosts = feedData.blogPosts
                            if (newPosts != null) {
                                currentPosts.addAll(newPosts)
                            }
                            lastCursors = feedData.lastElements
                            _state.value = HomeFeedState.Content(currentPosts.toList())
                        } else {
                            _state.value = HomeFeedState.Error("Failed to load feed")
                        }
                    }
                    is FeedFilter.Creator -> {
                        val response = FloatplaneApi.contentV3.getCreatorBlogPosts(
                            id = currentFilter.id,
                            limit = 20,
                            fetchAfter = 0
                        )
                         if (response.isSuccessful && response.body() != null) {
                            val newPosts = response.body()!!
                            currentPosts.addAll(newPosts)
                            lastFetchAfter = newPosts.size
                            _state.value = HomeFeedState.Content(currentPosts.toList())
                        } else {
                            _state.value = HomeFeedState.Error("Failed to load creator feed")
                        }
                    }
                    is FeedFilter.Channel -> {
                        val response = FloatplaneApi.contentV3.getCreatorBlogPosts(
                            id = currentFilter.creatorId,
                            channel = currentFilter.id,
                            limit = 20,
                            fetchAfter = 0
                        )
                        if (response.isSuccessful && response.body() != null) {
                            val newPosts = response.body()!!
                            currentPosts.addAll(newPosts)
                            lastFetchAfter = newPosts.size
                            _state.value = HomeFeedState.Content(currentPosts.toList())
                        } else {
                            _state.value = HomeFeedState.Error("Failed to load channel feed")
                        }
                    }
                }

            } catch (e: Exception) {
                _state.value = HomeFeedState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value) return
        
        // Validation based on filter type
        when (_filter.value) {
            is FeedFilter.All -> {
                 if (lastCursors == null || lastCursors!!.isEmpty()) return
                 val hasMoreToFetch = lastCursors!!.any { it.moreFetchable == true }
                 if (!hasMoreToFetch) return
            }
            else -> {
                // For single creator/channel, we infer more content if we have some posts
                // A better check would be if the last fetch returned full limit, but we'll assume yes for now
                if (currentPosts.isEmpty()) return
            }
        }

        viewModelScope.launch {
            _isLoadingMore.value = true
            
            try {
                // ... (Auth/Sub checks if needed) ...

                when (val currentFilter = _filter.value) {
                    is FeedFilter.All -> {
                        // Ensure subscriptions are loaded
                        if (subscriptions.isEmpty()) {
                             _isLoadingMore.value = false
                             return@launch
                        }
                        
                        val creatorIds = subscriptions.mapNotNull { it.creator }
                        
                        // Manually serialize fetchAfter cursors to query map
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

                        val response = FloatplaneApi.contentV3.getMultiCreatorBlogPosts(
                            ids = creatorIds,
                            limit = 20,
                            fetchAfter = fetchAfterMap
                        )

                        if (response.isSuccessful && response.body() != null) {
                            val feedData = response.body()!!
                            val newPosts = feedData.blogPosts
                            
                            if (!newPosts.isNullOrEmpty()) {
                                handleNewPosts(newPosts)
                            }
                            lastCursors = feedData.lastElements
                        }
                    }
                    is FeedFilter.Creator -> {
                         val response = FloatplaneApi.contentV3.getCreatorBlogPosts(
                            id = currentFilter.id,
                            limit = 20,
                            fetchAfter = lastFetchAfter
                        )
                        if (response.isSuccessful && response.body() != null) {
                            val newPosts = response.body()!!
                            if (newPosts.isNotEmpty()) {
                                handleNewPosts(newPosts)
                                lastFetchAfter += newPosts.size
                            }
                        }
                    }
                    is FeedFilter.Channel -> {
                        val response = FloatplaneApi.contentV3.getCreatorBlogPosts(
                            id = currentFilter.creatorId,
                            channel = currentFilter.id,
                            limit = 20,
                            fetchAfter = lastFetchAfter
                        )
                        if (response.isSuccessful && response.body() != null) {
                            val newPosts = response.body()!!
                             if (newPosts.isNotEmpty()) {
                                handleNewPosts(newPosts)
                                lastFetchAfter += newPosts.size
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                // Silently fail for load more
            } finally {
                _isLoadingMore.value = false
            }
        }
    }
    
    private fun handleNewPosts(newPosts: List<BlogPostModelV3>) {
        // Filter out duplicates based on ID
        val existingIds = currentPosts.map { it.id }.toSet()
        val uniqueNewPosts = newPosts.filter { !existingIds.contains(it.id) }
        
        if (uniqueNewPosts.isNotEmpty()) {
            currentPosts.addAll(uniqueNewPosts)
             _state.value = HomeFeedState.Content(currentPosts.toList())
        }
    }
}
