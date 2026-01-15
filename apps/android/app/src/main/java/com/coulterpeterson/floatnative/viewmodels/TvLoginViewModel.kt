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
import org.json.JSONObject

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
        android.util.Log.d("TvLoginViewModel", "startAuthFlow: Starting...")
        viewModelScope.launch {
            try {
                _state.value = TvLoginState.Loading
                val response = FloatplaneApi.startDeviceAuth()
                _state.value = TvLoginState.Content(
                    userCode = response.user_code,
                    verificationUri = response.verification_uri,
                    verificationUriComplete = response.verification_uri_complete ?: response.verification_uri
                )
                
                android.util.Log.d("TvLoginViewModel", "startAuthFlow: Auth started, userCode=${response.user_code}, deviceCode=${response.device_code}")
                
                 // Start polling
                pollForToken(response.device_code, response.interval)
            } catch (e: Exception) {
                android.util.Log.e("TvLoginViewModel", "startAuthFlow: Failed to start auth", e)
                if (e is javax.net.ssl.SSLHandshakeException || e is java.security.cert.CertificateException) {
                     _state.value = TvLoginState.Error("Login Failed: SSL/Cert Error. Please check your device date & time settings.")
                } else {
                    _state.value = TvLoginState.Error("Failed to start login: ${e.message}")
                }
            }
        }
    }

    private suspend fun pollForToken(deviceCode: String, intervalSeconds: Int) {
        android.util.Log.d("TvLoginViewModel", "pollForToken: Starting polling with interval $intervalSeconds")
        var isDone = false
        val baseDelayMs = intervalSeconds * 1000L

        while (!isDone) {
            android.util.Log.d("TvLoginViewModel", "pollForToken: Waiting $baseDelayMs ms...")
            delay(baseDelayMs)
            
            try {
                android.util.Log.d("TvLoginViewModel", "pollForToken: Polling now...")
                val tokenResponse = FloatplaneApi.pollDeviceToken(deviceCode)
                android.util.Log.d("TvLoginViewModel", "pollForToken: Success! Token received. AccessToken length: ${tokenResponse.access_token.length}")
                
                // Success!
                FloatplaneApi.tokenManager.accessToken = tokenResponse.access_token
                FloatplaneApi.tokenManager.refreshToken = tokenResponse.refresh_token
                
                // Also trigger companion login in background
                FloatplaneApi.ensureCompanionLogin()
                
                _state.value = TvLoginState.Success
                isDone = true
            } catch (e: HttpException) {
                if (e.code() == 400) {
                    val errorBody = e.response()?.errorBody()?.string()
                    android.util.Log.d("TvLoginViewModel", "pollForToken: 400 Error Body: $errorBody")
                    
                    if (!errorBody.isNullOrEmpty()) {
                        try {
                            val json = JSONObject(errorBody)
                            val error = json.optString("error")
                            val errorDesc = json.optString("error_description")
                            
                            if (error == "authorization_pending") {
                                // Standard waiting state, continue polling
                                continue
                            } else if (error == "slow_down") {
                                // Per RFC, we should increase interval by 5s. 
                                // For simplicity, we add 5s delay here before next loop logic handles standard delay
                                android.util.Log.w("TvLoginViewModel", "pollForToken: Received slow_down, adding delay")
                                delay(5000)
                                continue
                            } else if (errorDesc.contains("DPoP", ignoreCase = true)) {
                                // Specific handling for DPoP time skew issues
                                android.util.Log.w("TvLoginViewModel", "pollForToken: DPoP Error detected -> System Time Issue.")
                                
                                // Attempt Auto-Correction
                                val dateHeader = e.response()?.headers()?.get("date")
                                if (dateHeader != null) {
                                    try {
                                        // Parse RFC 1123 date: Tue, 13 Jan 2026 17:17:40 GMT
                                        val sdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
                                        val serverTime = sdf.parse(dateHeader)?.time
                                        if (serverTime != null) {
                                            val deviceTime = System.currentTimeMillis()
                                            val offsetSeconds = (serverTime - deviceTime) / 1000
                                            
                                            android.util.Log.i("TvLoginViewModel", "pollForToken: Auto-correcting DPoP time offset. Server: $serverTime, Device: $deviceTime, Offset: $offsetSeconds s")
                                            FloatplaneApi.dpopManager.timeOffsetSeconds = offsetSeconds
                                        }
                                    } catch (parseEx: Exception) {
                                        android.util.Log.e("TvLoginViewModel", "pollForToken: Failed to parse server Date header for auto-correction", parseEx)
                                    }
                                } else {
                                     android.util.Log.w("TvLoginViewModel", "pollForToken: No Date header found for auto-correction.")
                                }
                                
                                // Continue picking up the next loop with corrected time
                                android.util.Log.d("TvLoginViewModel", "pollForToken: Retrying with new offset...")
                                continue
                            } else {
                                // Fatal error (expired_token, access_denied, invalid_grant, etc.)
                                android.util.Log.e("TvLoginViewModel", "pollForToken: Fatal 400 Error: $error / $errorDesc")
                                val displayError = if (errorDesc.isNotEmpty()) errorDesc else "Login Failed: $error"
                                _state.value = TvLoginState.Error(displayError)
                                isDone = true
                            }
                        } catch (ex: Exception) {
                            android.util.Log.e("TvLoginViewModel", "pollForToken: Failed to parse 400 error body", ex)
                            _state.value = TvLoginState.Error("Login Error: Could not parse server response.")
                            isDone = true
                        }
                    } else {
                        // Empty 400? Assume checking again isn't useful if we don't know why. 
                        // But historically code just retried. Let's error out to be safe/visible.
                        _state.value = TvLoginState.Error("Login Error: Bad Request (400)")
                        isDone = true
                    }
                } else {
                     android.util.Log.e("TvLoginViewModel", "pollForToken: Error ${e.code()}", e)
                     _state.value = TvLoginState.Error("Polling error: ${e.code()}")
                     isDone = true
                }
            } catch (e: Exception) {
                 android.util.Log.e("TvLoginViewModel", "pollForToken: Exception", e)
                 _state.value = TvLoginState.Error("Polling exception: ${e.message}")
                 isDone = true
            }
        }
    }
}
