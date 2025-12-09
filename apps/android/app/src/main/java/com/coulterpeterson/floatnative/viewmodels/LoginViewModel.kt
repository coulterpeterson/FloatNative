package com.coulterpeterson.floatnative.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coulterpeterson.floatnative.api.FloatplaneApi
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

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _state.value = LoginState.Loading
            try {
                // Call AuthV3Api
                // Note: AuthInterceptor automatically handles saving cookies from response
                val response = FloatplaneApi.authV3.loginV3(AuthLoginV3Request(username, password))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        if (body.needs2FA == true) {
                             _state.value = LoginState.Error("2FA Not Implemented Yet in this prototype")
                        } else {
                            _state.value = LoginState.Success
                        }
                    } else {
                        _state.value = LoginState.Error("Empty response")
                    }
                } else {
                    // TODO: Parse error body for user friendly message
                     _state.value = LoginState.Error("Login failed: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                _state.value = LoginState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
