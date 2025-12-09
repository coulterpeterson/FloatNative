package com.coulterpeterson.floatnative.api

import com.coulterpeterson.floatnative.data.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

class CompanionAuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Skip auth for login endpoint
        if (originalRequest.url.encodedPath.contains("/auth/login")) {
            return chain.proceed(originalRequest)
        }

        val apiKey = tokenManager.companionApiKey
        
        val newRequest = if (apiKey != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $apiKey")
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(newRequest)
    }
}
