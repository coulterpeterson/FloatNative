package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.api.FloatplaneApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SettingsState {
    object Idle : SettingsState()
    object Loading : SettingsState()
    object LoggedOut : SettingsState()
    data class Error(val message: String) : SettingsState()
}

class SettingsViewModel : ViewModel() {

    private val _state = MutableStateFlow<SettingsState>(SettingsState.Idle)
    val state = _state.asStateFlow()

    fun logout() {
        viewModelScope.launch {
            _state.value = SettingsState.Loading
            try {
                // Attempt server-side logout
                val response = FloatplaneApi.authV3.logoutV3()
                
                // Regardless of server success (e.g. 401 if already expired), clear local tokens
                FloatplaneApi.tokenManager.clearAll()
                
                _state.value = SettingsState.LoggedOut
            } catch (e: Exception) {
                // Network error? Still clear local tokens to ensure user isn't stuck
                FloatplaneApi.tokenManager.clearAll()
                _state.value = SettingsState.LoggedOut
            }
        }
    }
}
