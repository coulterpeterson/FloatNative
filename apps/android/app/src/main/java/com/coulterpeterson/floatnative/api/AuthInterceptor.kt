package com.coulterpeterson.floatnative.api

import com.coulterpeterson.floatnative.data.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class AuthInterceptor(
    private val tokenManager: TokenManager,
    private val dpopManager: com.coulterpeterson.floatnative.data.DPoPManager,
    private val authApiProvider: () -> OAuthApi // Lazy provider to avoid circular dependency
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()

        // 1. Add Headers
        val accessToken = tokenManager.accessToken
        val authCookie = tokenManager.authCookie
        
        val url = originalRequest.url.toString()
        val isFloatplane = url.contains("floatplane.com") || url.contains("floatnative.coulterpeterson.com")

        if (accessToken != null && isFloatplane) {
            // Generate DPoP Proof
            try {
                val method = originalRequest.method
                val url = originalRequest.url.toString()
                val proof = dpopManager.generateProof(method, url, accessToken)
                
                builder.header("DPoP", proof)
                builder.header("Authorization", "DPoP $accessToken")
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback (though DPoP is required now)
                builder.header("Authorization", "Bearer $accessToken")
            }
        } else if (authCookie != null) {
            builder.header("Cookie", "sails.sid=$authCookie")
        }

        builder.header("User-Agent", "FloatNative/1.0 (Android)")

        val response = chain.proceed(builder.build())

        // 2. Extract Cookie from Response (if logging in via legacy or hybrid)
        val cookies = response.headers("Set-Cookie")
        for (cookie in cookies) {
            if (cookie.contains("sails.sid")) {
                // Simple parsing to get value
                val parts = cookie.split(";")
                for (part in parts) {
                    val pair = part.trim().split("=")
                    if (pair.size == 2 && pair[0] == "sails.sid") {
                        tokenManager.authCookie = pair[1]
                    }
                }
            }
        }

        // 3. Handle 401 Unauthorized
        if (response.code == 401) {
            val refreshToken = tokenManager.refreshToken
            if (refreshToken != null) {
                synchronized(this) {
                    // Double check if token was updated by another thread
                    val currentAccessToken = tokenManager.accessToken
                    if (currentAccessToken != null && currentAccessToken != accessToken) {
                        // Token was updated, retry with new token
                        response.close()
                        // Generate new proof for the retried request with new token
                         try {
                            val method = originalRequest.method
                            val url = originalRequest.url.toString()
                            val proof = dpopManager.generateProof(method, url, currentAccessToken)
                            
                            return chain.proceed(
                                originalRequest.newBuilder()
                                    .header("DPoP", proof)
                                    .header("Authorization", "DPoP $currentAccessToken")
                                    .build()
                            )
                        } catch (e: Exception) {
                             return chain.proceed(
                                originalRequest.newBuilder()
                                    .header("Authorization", "Bearer $currentAccessToken")
                                    .build()
                            )
                        }
                    }

                    // Try to refresh
                    try {
                        val authApi = authApiProvider()
                        
                        // Generate DPoP proof for Token Endpoint
                        val tokenEndpoint = "https://auth.floatplane.com/realms/floatplane/protocol/openid-connect/token"
                        val refreshProof = dpopManager.generateProof("POST", tokenEndpoint)

                        // Run blocking because Interceptor is synchronous
                        val tokenResponse = runBlocking {
                            authApi.getToken(
                                dpop = refreshProof,
                                grantType = "refresh_token",
                                clientId = "floatnative",
                                refreshToken = refreshToken
                            )
                        }

                        // Save new tokens
                        tokenManager.accessToken = tokenResponse.access_token
                        tokenManager.refreshToken = tokenResponse.refresh_token
                        // Update expiry if needed

                        // Retry request
                        response.close()
                        
                        // Generate DPoP proof for the retried request with NEW token
                        val method = originalRequest.method
                        val url = originalRequest.url.toString()
                        val proof = dpopManager.generateProof(method, url, tokenResponse.access_token)
                        
                        return chain.proceed(
                            originalRequest.newBuilder()
                                .header("DPoP", proof)
                                .header("Authorization", "DPoP ${tokenResponse.access_token}")
                                .build()
                        )

                    } catch (e: Exception) {
                        // Refresh failed
                        // Don't clear tokens here automatically, let the UI handle the 401 final failure?
                        // Or clear them to force logout?
                        // For now we just return the original 401
                    }
                }
            }
        }

        return response
    }
}
