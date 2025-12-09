package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.openapi.models.CreatorModelV3
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
                // Empty search string returns all creators usually, or top creators.
                val response = FloatplaneApi.creatorV3.getCreators(search = "")
                if (response.isSuccessful && response.body() != null) {
                    _state.value = CreatorsState.Content(response.body()!!)
                } else {
                    _state.value = CreatorsState.Error("Failed to fetch creators: ${response.code()}")
                }
            } catch (e: Exception) {
                _state.value = CreatorsState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
