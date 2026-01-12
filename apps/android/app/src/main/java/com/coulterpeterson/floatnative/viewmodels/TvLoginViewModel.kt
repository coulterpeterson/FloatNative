package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.api.DeviceCodeResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface TvLoginState {
    object Loading : TvLoginState
    data class Content(val userCode: String, val verificationUri: String, val verificationUriComplete: String) : TvLoginState
    data class Error(val message: String) : TvLoginState
    object Success : TvLoginState
}

class TvLoginViewModel : ViewModel() {

    private val _state = MutableStateFlow<TvLoginState>(TvLoginState.Loading)
    val state: StateFlow<TvLoginState> = _state.asStateFlow()

    init {
        startAuthFlow()
    }

    private fun startAuthFlow() {
        viewModelScope.launch {
            try {
                _state.value = TvLoginState.Loading
                val response = FloatplaneApi.startDeviceAuth()
                _state.value = TvLoginState.Content(
                    userCode = response.user_code,
                    verificationUri = response.verification_uri,
                    verificationUriComplete = response.verification_uri_complete ?: response.verification_uri
                )
                
                // Start polling
                pollForToken(response.device_code, response.interval)
            } catch (e: Exception) {
                if (e is javax.net.ssl.SSLHandshakeException || e is java.security.cert.CertificateException) {
                     _state.value = TvLoginState.Error("Login Failed: SSL/Cert Error. Please check your device date & time settings.")
                } else {
                    _state.value = TvLoginState.Error("Failed to start login: ${e.message}")
                }
            }
        }
    }

    private suspend fun pollForToken(deviceCode: String, intervalSeconds: Int) {
        var isDone = false
        val delayMs = intervalSeconds * 1000L

        while (!isDone) {
            delay(delayMs)
            
            try {
                val tokenResponse = FloatplaneApi.pollDeviceToken(deviceCode)
                
                // Success!
                FloatplaneApi.tokenManager.accessToken = tokenResponse.access_token
                FloatplaneApi.tokenManager.refreshToken = tokenResponse.refresh_token
                
                // Also trigger companion login in background
                FloatplaneApi.ensureCompanionLogin()
                
                _state.value = TvLoginState.Success
                isDone = true
            } catch (e: HttpException) {
                if (e.code() == 400) {
                    // Check body for "authorization_pending" vs "slow_down"
                    // Simplification: just continue polling for any 400 that isn't expired
                    // Ideally parse error json.
                    // For now, assume pending.
                    continue
                } else {
                     _state.value = TvLoginState.Error("Polling error: ${e.code()}")
                     isDone = true
                }
            } catch (e: Exception) {
                 _state.value = TvLoginState.Error("Polling exception: ${e.message}")
                 isDone = true
            }
        }
    }
}
