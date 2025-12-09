package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.api.WatchHistoryResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed class HistoryState {
    object Initial : HistoryState()
    object Loading : HistoryState()
    data class Content(val history: List<WatchHistoryResponse>, val groupedHistory: Map<String, List<WatchHistoryResponse>>) : HistoryState()
    data class Error(val message: String) : HistoryState()
}

class HistoryViewModel : ViewModel() {

    private val _state = MutableStateFlow<HistoryState>(HistoryState.Initial)
    val state = _state.asStateFlow()

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
                    val grouped = groupHistory(rawHistory)
                    _state.value = HistoryState.Content(rawHistory, grouped)
                } else {
                    _state.value = HistoryState.Error("Failed to fetch history: ${response.code()}")
                }
            } catch (e: Exception) {
                _state.value = HistoryState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun groupHistory(history: List<WatchHistoryResponse>): Map<String, List<WatchHistoryResponse>> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        isoFormat.timeZone = TimeZone.getTimeZone("UTC")
        val displayFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        
        // Helper to parse date string
        fun parseDate(dateStr: String): Date {
            return try {
                isoFormat.parse(dateStr) ?: Date()
            } catch (e: Exception) {
                Date()
            }
        }

        // Group by day formatted string
        return history.groupBy { item ->
            val date = parseDate(item.updatedAt)
            displayFormat.format(date)
        }
    }
}
