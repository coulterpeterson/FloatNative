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
}
