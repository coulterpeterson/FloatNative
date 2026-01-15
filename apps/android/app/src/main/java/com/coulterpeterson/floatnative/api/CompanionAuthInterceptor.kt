package com.coulterpeterson.floatnative.api

import com.coulterpeterson.floatnative.data.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

class CompanionAuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Skip auth for login and public endpoints if necessary
        if (originalRequest.url.encodedPath.contains("/auth/login")) {
            return chain.proceed(originalRequest)
        }

        val apiKey = tokenManager.companionApiKey
        
        val requestWithAuth = if (apiKey != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $apiKey")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(requestWithAuth)

        if (response.code == 401) {
            // Attempt to refresh token
            synchronized(this) {
                // Determine if we need to refresh (simple check: proceed)
                response.close() // Close the failed response before making new requests

                try {
                    val accessToken = tokenManager.accessToken
                    if (accessToken != null) {
                        // We need to call the login endpoint synchronously.
                        // using runBlocking as Interceptors run on worker threads.
                        val newKey = kotlinx.coroutines.runBlocking {
                            try {
                                // Generate DPoP proof for User Self endpoint as per iOS logic
                                val dpopProof = com.coulterpeterson.floatnative.api.FloatplaneApi.dpopManager.generateProof(
                                    httpMethod = "GET",
                                    httpUrl = "https://www.floatplane.com/api/v3/user/self",
                                    accessToken = accessToken
                                )
                                
                                val loginRequest = CompanionLoginRequest(accessToken, dpopProof)
                                val loginResponse = com.coulterpeterson.floatnative.api.FloatplaneApi.companionApi.login(loginRequest)
                                
                                if (loginResponse.isSuccessful) {
                                    loginResponse.body()?.apiKey
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }

                        if (newKey != null) {
                            tokenManager.companionApiKey = newKey
                            
                            // Retry with new key
                            val retryRequest = originalRequest.newBuilder()
                                .header("Authorization", "Bearer $newKey")
                                .build()
                            return chain.proceed(retryRequest)
                        }
                    }
                } catch (e: Exception) {
                    // Failed to refresh, return 401
                }
            }
            // If refresh failed or exception, return a new 401 response (since we closed the old one)
            // Or easier: Re-execute the original failsafe which will likely return 401 again
            // But better to just construct a 401 response or re-proceed? 
            // OkHttp requires returning a response. Since we closed the original, we must produce one.
            // Simplest is to proceed again with original request (which fails) or construct one.
            // Let's retry the *original* request (without new auth) to get the 401 fresh?
            return chain.proceed(requestWithAuth)
        }

        return response
    }
}
