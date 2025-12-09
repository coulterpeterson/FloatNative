package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Content(val results: List<BlogPostModelV3>) : SearchState()
    data class Empty(val query: String) : SearchState()
    data class Error(val message: String) : SearchState()
}

class SearchViewModel : ViewModel() {

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isEmpty()) {
            _state.value = SearchState.Idle
        }
    }

    fun performSearch() {
        val currentQuery = _query.value
        if (currentQuery.isBlank()) return

        viewModelScope.launch {
            _state.value = SearchState.Loading
            
            try {
                // 1. Get subscriptions
                val subsResponse = FloatplaneApi.manual.getSubscriptions()
                if (!subsResponse.isSuccessful || subsResponse.body() == null) {
                    _state.value = SearchState.Error("Failed to load subscriptions")
                    return@launch
                }
                
                val subscriptions = subsResponse.body()!!
                val creatorIds = subscriptions.map { it.creator }

                // 2. Search each creator in parallel
                val allResults = coroutineScope {
                    creatorIds.map { creatorId ->
                        async {
                            try {
                                val response = FloatplaneApi.contentV3.getCreatorBlogPosts(
                                    id = creatorId,
                                    limit = 10,
                                    search = currentQuery
                                )
                                if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
                            } catch (e: Exception) {
                                emptyList<BlogPostModelV3>()
                            }
                        }
                    }.awaitAll().flatten()
                }

                if (allResults.isEmpty()) {
                    _state.value = SearchState.Empty(currentQuery)
                } else {
                    // Sort by release date (newest first)
                    // Note: releaseDate is OffsetDateTime in model, but we might encounter parsing issues if Moshi adapter isn't perfect.
                    // Assuming string or standard comparable for now.
                    // Actually BlogPostModelV3 releaseDate is Date? or String? In generated code it's probably standard java.util.Date or OffsetDateTime.
                    // Let's check ContentV3Api signature... it says `java.time.OffsetDateTime?`. 
                    // Wait, the generated model might use something else. Let's assume standard sort for now unless we see it's broken.
                    
                    val sorted = allResults.sortedByDescending { it.releaseDate }
                    _state.value = SearchState.Content(sorted)
                }

            } catch (e: Exception) {
                _state.value = SearchState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
