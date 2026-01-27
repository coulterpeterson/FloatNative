package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3
import com.coulterpeterson.floatnative.openapi.models.ContentCreatorListLastItems
import com.coulterpeterson.floatnative.openapi.models.UserSubscriptionModel
import com.coulterpeterson.floatnative.api.Playlist

import com.coulterpeterson.floatnative.openapi.models.GetProgressRequest
import com.coulterpeterson.floatnative.openapi.models.CreatorModelV3
import com.coulterpeterson.floatnative.openapi.apis.DeliveryV3Api
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class HomeFeedState {
    object Initial : HomeFeedState()
    object Loading : HomeFeedState()
    data class Content(val posts: List<BlogPostModelV3>) : HomeFeedState()
    data class Error(val message: String) : HomeFeedState()
}

class HomeFeedViewModel : TvSidebarViewModel() {

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
        data class Creator(val id: String, val displayTitle: String, val icon: String? = null) : FeedFilter()
        data class Channel(val id: String, val creatorId: String, val displayTitle: String, val icon: String? = null) : FeedFilter()
    }

    private val _filter = MutableStateFlow<FeedFilter>(FeedFilter.All)
    val filter = _filter.asStateFlow()

    private val _lastFocusedId = MutableStateFlow<String?>(null)
    val lastFocusedId = _lastFocusedId.asStateFlow()

    fun setLastFocusedId(id: String?) {
        _lastFocusedId.value = id
    }

    private val _liveCreators = MutableStateFlow<List<CreatorModelV3>>(emptyList())
    val liveCreators = _liveCreators.asStateFlow()


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
                // 1. Get Subscriptions if needed (only for All filter)
                if (_filter.value is FeedFilter.All && subscriptions.isEmpty()) {
                    val subResponse = FloatplaneApi.manual.getSubscriptions()
                    if (subResponse.isSuccessful && subResponse.body() != null) {
                        subscriptions = subResponse.body()!!
                        checkLiveCreators()
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
                            if (newPosts.isNullOrEmpty()) {
                                _state.value = HomeFeedState.Content(posts = emptyList())
                            } else {
                                currentPosts.addAll(newPosts)
                                lastCursors = feedData.lastElements
                                _state.value = HomeFeedState.Content(currentPosts.toList())
                                fetchWatchProgress(newPosts)
                            }
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
                            fetchWatchProgress(newPosts)
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
                            fetchWatchProgress(newPosts)
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
                                fetchWatchProgress(newPosts)
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
                                fetchWatchProgress(newPosts)
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
                                fetchWatchProgress(newPosts)
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

    // Playlist, WatchProgress, and Sidebar logic is inherited from TvSidebarViewModel

    fun checkLiveCreators() {
        val currentSubs = subscriptions
        if (currentSubs.isEmpty()) return

        val creatorIds = currentSubs.mapNotNull { it.creator }
        if (creatorIds.isEmpty()) return

        viewModelScope.launch {
            try {
                // Batch if too many? For now just send all (assuming reasonable count)
                val response = FloatplaneApi.manual.getCreatorsByIds(creatorIds)
                if (response.isSuccessful && response.body() != null) {
                    val creators = response.body()!!
                    val liveCreatorsList = creators.mapNotNull { creator ->
                        val liveStream = creator.liveStream
                        if (liveStream != null) {
                            // Launch async check for delivery info and HLS polling
                            viewModelScope.async {
                                try {
                                    val delivery = FloatplaneApi.deliveryV3.getDeliveryInfoV3(
                                        scenario = DeliveryV3Api.ScenarioGetDeliveryInfoV3.live,
                                        entityId = liveStream.id,
                                        outputKind = DeliveryV3Api.OutputKindGetDeliveryInfoV3.hlsPeriodMpegts
                                    )
                                    val body = delivery.body()
                                    if (delivery.isSuccessful && body?.groups?.isNotEmpty() == true) {
                                        // Construct Master HLS URL
                                        // Logic: groups[0].origins[0].url + groups[0].variants[0].url
                                        // (Or variant specific origin if available, but usually group origin is base)
                                        val group = body.groups[0]
                                        val variant = group.variants.firstOrNull()
                                        
                                        // Try to find a valid origin
                                        val origin = variant?.origins?.firstOrNull() ?: group.origins?.firstOrNull()
                                        
                                        if (variant != null && origin != null) {
                                            val masterUrl = origin.url.toString() + variant.url
                                            
                                            // POLL the URL to check if it returns 200 OK
                                            if (isUrlReachable(masterUrl)) {
                                                creator
                                            } else {
                                                null
                                            }
                                        } else {
                                            null
                                        }
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        } else {
                            null
                        }
                    }.awaitAll().filterNotNull().toMutableList()

                    if (FloatplaneApi.tokenManager.fakeLiveStreamEnabled) {
                        try {
                             val fakeLiveStream = com.coulterpeterson.floatnative.openapi.models.LiveStreamModel(
                                id = "fake_live_stream_id",
                                title = "Fake Live Stream",
                                description = "Fake description",
                                thumbnail = com.coulterpeterson.floatnative.openapi.models.ImageModel(
                                    width = 400,
                                    height = 225,
                                    path = java.net.URI("https://pbs.floatplane.com/stream_thumbnails/5c13f3c006f1be15e08e05c0/510600934781497_1768590626092_400x225.jpeg"),
                                    childImages = null
                                ),
                                owner = "fake_creator_id",
                                streamPath = "fake_path",
                                offline = com.coulterpeterson.floatnative.openapi.models.LiveStreamModelOffline(
                                    title = "Offline",
                                    description = "Offline",
                                    thumbnail = null
                                )
                            )

                            // We need a full Creator object to display it
                            // Reusing the first creator or creating a dummy
                            val baseCreator = creators.firstOrNull() 
                            if (baseCreator != null) {
                                val fakeCreator = baseCreator.copy(
                                    id = "fake_creator_id",
                                    title = "Fake Creator",
                                    liveStream = fakeLiveStream,
                                    icon = com.coulterpeterson.floatnative.openapi.models.ImageModel(
                                        width = 400,
                                        height = 225,
                                        path = java.net.URI("https://pbs.floatplane.com/stream_thumbnails/5c13f3c006f1be15e08e05c0/510600934781497_1768590626092_400x225.jpeg"),
                                        childImages = null
                                    )
                                )
                                liveCreatorsList.add(0, fakeCreator)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    _liveCreators.value = liveCreatorsList
                }
            } catch (e: Exception) {
               e.printStackTrace()
            }
        }
    }
    
    private suspend fun isUrlReachable(urlString: String): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = java.net.URL(urlString)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET" // Or HEAD, but sometimes HLS CDNs behave better with GET on master playlist
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val code = connection.responseCode
                connection.disconnect()
                code == 200
            } catch (e: Exception) {
                false
            }
        }
    }
}
