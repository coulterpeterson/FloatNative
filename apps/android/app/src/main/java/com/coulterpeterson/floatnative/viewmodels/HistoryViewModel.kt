package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.api.WatchHistoryResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed class HistoryState {
    object Initial : HistoryState()
    object Loading : HistoryState()
    data class Content(val history: List<WatchHistoryResponse>, val groupedHistory: Map<String, List<WatchHistoryResponse>>) : HistoryState()
    data class Error(val message: String) : HistoryState()
}

class HistoryViewModel : TvSidebarViewModel() {

    private val _state = MutableStateFlow<HistoryState>(HistoryState.Initial)
    val state = _state.asStateFlow()

    private val _lastFocusedId = MutableStateFlow<String?>(null)
    val lastFocusedId = _lastFocusedId.asStateFlow()

    fun setLastFocusedId(id: String?) {
        _lastFocusedId.value = id
    }

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _state.value = HistoryState.Loading
            
            try {
                // Using Manual API for history
                val response = FloatplaneApi.manual.getWatchHistory(offset = 0)
                
                if (response.isSuccessful && response.body() != null) {
                    val rawHistory = response.body()!!
                    // Filter out items where blogPost is null (deleted videos, etc)
                    val validHistory = rawHistory.filter { it.blogPost != null }
                    val grouped = groupHistory(validHistory)
                    _state.value = HistoryState.Content(validHistory, grouped)
                } else {
                    _state.value = HistoryState.Error("Failed to fetch history: ${response.code()}")
                }
            } catch (e: Exception) {
                _state.value = HistoryState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun groupHistory(history: List<WatchHistoryResponse>): Map<String, List<WatchHistoryResponse>> {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        isoFormat.timeZone = TimeZone.getTimeZone("UTC")
        val weekdayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        val monthDayFormat = SimpleDateFormat("MMMM d", Locale.getDefault())
        
        // Helper to parse date string
        fun parseDate(dateStr: String): Date {
            return try {
                isoFormat.parse(dateStr) ?: Date()
            } catch (e: Exception) {
                Date()
            }
        }

        val now = Calendar.getInstance()
        val itemCal = Calendar.getInstance()

        // Group by relative date string
        return history.groupBy { item ->
            val date = parseDate(item.updatedAt)
            itemCal.time = date
            
            when {
                isSameDay(now, itemCal) -> "Today"
                isYesterday(now, itemCal) -> "Yesterday"
                isSameWeek(now, itemCal) -> weekdayFormat.format(date)
                else -> monthDayFormat.format(date)
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && 
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(now: Calendar, itemCal: Calendar): Boolean {
        val yesterday = now.clone() as Calendar
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(yesterday, itemCal)
    }

    private fun isSameWeek(now: Calendar, itemCal: Calendar): Boolean {
        return now.get(Calendar.YEAR) == itemCal.get(Calendar.YEAR) &&
               now.get(Calendar.WEEK_OF_YEAR) == itemCal.get(Calendar.WEEK_OF_YEAR)
    }

    // Sidebar Support
    fun openSidebar(post: com.coulterpeterson.floatnative.api.WatchHistoryBlogPost) {
         openSidebar(toBlogPostModel(post))
    }

    private fun toBlogPostModel(post: com.coulterpeterson.floatnative.api.WatchHistoryBlogPost): com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3 {
        // Map Creator
        val creator = post.creator
        val blogCreatorOwner = com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3CreatorOwner(
            id = creator.owner,
            username = "" 
        )
        
        val blogCreator = com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3Creator(
            id = creator.id,
            owner = blogCreatorOwner,
            title = creator.title,
            urlname = creator.urlname,
            description = creator.description,
            about = creator.about,
            category = com.coulterpeterson.floatnative.openapi.models.CreatorModelV3Category(id="unknown", title="Unknown"), // V2 mismatch
            cover = creator.cover,
            icon = creator.icon,
            liveStream = creator.liveStream,
            subscriptionPlans = creator.subscriptionPlans ?: emptyList(),
            discoverable = creator.discoverable,
            subscriberCountDisplay = creator.subscriberCountDisplay,
            incomeDisplay = creator.incomeDisplay,
            defaultChannel = creator.defaultChannel,
            channels = null,
            card = null
        )

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

        return com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3(
            id = post.id,
            guid = post.guid,
            title = post.title,
            text = post.text,
            type = com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3.Type.blogPost,
            channel = blogChannel,
            tags = post.tags,
            attachmentOrder = post.attachmentOrder,
            metadata = post.metadata,
            releaseDate = java.time.OffsetDateTime.parse(post.releaseDate), // History uses String, assumes ISO8601
            likes = post.likes,
            dislikes = post.dislikes,
            score = post.score,
            comments = post.comments,
            creator = blogCreator,
            wasReleasedSilently = post.wasReleasedSilently,
            thumbnail = post.thumbnail,
            isAccessible = post.isAccessible,
            videoAttachments = post.videoAttachments,
            audioAttachments = post.audioAttachments,
            pictureAttachments = post.pictureAttachments,
            galleryAttachments = post.galleryAttachments
        )
    }
}
