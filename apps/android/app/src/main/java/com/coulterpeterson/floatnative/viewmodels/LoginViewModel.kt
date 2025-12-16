package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.api.FloatplaneApi
import com.coulterpeterson.floatnative.data.TokenManager
import com.coulterpeterson.floatnative.openapi.models.AuthLoginV3Request
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state = _state.asStateFlow()
    
    // Store codeVerifier temporarily (in memory is okay for this flow)
    private var codeVerifier: String? = null

    fun startAuthFlow(context: android.content.Context) {
        viewModelScope.launch {
            try {
                _state.value = LoginState.Loading
                
                // 1. Generate PKCE
                val verifier = com.coulterpeterson.floatnative.data.PKCEManager.generateCodeVerifier()
                com.coulterpeterson.floatnative.api.FloatplaneApi.tokenManager.codeVerifier = verifier
                val challenge = com.coulterpeterson.floatnative.data.PKCEManager.generateCodeChallenge(verifier)
                
                // 2. Build URL
                val authUrl = "https://auth.floatplane.com/realms/floatplane/protocol/openid-connect/auth" +
                        "?client_id=floatnative" +
                        "&response_type=code" +
                        "&redirect_uri=floatnative://auth" +
                        "&scope=openid offline_access" +
                        "&code_challenge=$challenge" +
                        "&code_challenge_method=S256"
                        
                // 3. Launch Custom Tab
                 val intent = androidx.browser.customtabs.CustomTabsIntent.Builder().build()
                 intent.launchUrl(context, android.net.Uri.parse(authUrl))
                 
            } catch (e: Exception) {
                _state.value = LoginState.Error("Failed to start auth: ${e.message}")
            }
        }
    }
    
    init {
        viewModelScope.launch {
            FloatplaneApi.authCodeFlow.collect { code ->
                 // Retrieve verifier
                 val verifier = FloatplaneApi.tokenManager.codeVerifier
                 if (verifier != null) {
                     exchangeCode(code, verifier)
                 } else {
                     _state.value = LoginState.Error("Code Verifier missing. Please restart auth.")
                 }
            }
        }
    }

    private fun exchangeCode(code: String, verifier: String) {
        viewModelScope.launch {
             _state.value = LoginState.Loading
             try {
                 FloatplaneApi.exchangeAuthCode(code, verifier)
                 _state.value = LoginState.Success
             } catch (e: Exception) {
                 _state.value = LoginState.Error(e.message ?: "Login failed")
             }
        }
    }

    // Legacy login removed
}
