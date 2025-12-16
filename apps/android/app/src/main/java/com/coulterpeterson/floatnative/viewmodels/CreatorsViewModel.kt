package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.openapi.models.CreatorModelV3
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CreatorsState {
    object Initial : CreatorsState()
    object Loading : CreatorsState()
    data class Content(val creators: List<CreatorModelV3>) : CreatorsState()
    data class Error(val message: String) : CreatorsState()
}

class CreatorsViewModel : ViewModel() {

    private val _state = MutableStateFlow<CreatorsState>(CreatorsState.Initial)
    val state = _state.asStateFlow()

    init {
        loadCreators()
    }

    private fun loadCreators() {
        viewModelScope.launch {
            _state.value = CreatorsState.Loading
            try {
                val creatorsDeferred = async { FloatplaneApi.creatorV3.getCreators(search = "") }
                val subscriptionsDeferred = async { FloatplaneApi.subscriptionsV3.listUserSubscriptionsV3() }

                val creatorsResponse = creatorsDeferred.await()
                val subscriptionsResponse = subscriptionsDeferred.await()

                if (creatorsResponse.isSuccessful && subscriptionsResponse.isSuccessful) {
                    val allCreators = creatorsResponse.body() ?: emptyList()
                    val subscriptions = subscriptionsResponse.body() ?: emptyList()
                    
                    val subscribedCreatorIds = subscriptions.map { it.creator }.toSet()
                    val subscribedCreators = allCreators.filter { it.id in subscribedCreatorIds }
                    
                    android.util.Log.d("CreatorsDebug", "Filtered ${subscribedCreators.size} subscribed creators from ${allCreators.size} total.")
                    _state.value = CreatorsState.Content(subscribedCreators)
                } else {
                    _state.value = CreatorsState.Error("Failed to fetch data. Creators: ${creatorsResponse.code()}, Subs: ${subscriptionsResponse.code()}")
                }
            } catch (e: Exception) {
                _state.value = CreatorsState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
