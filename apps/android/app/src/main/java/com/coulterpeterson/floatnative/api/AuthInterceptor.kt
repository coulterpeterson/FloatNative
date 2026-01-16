package com.coulterpeterson.floatnative.api

import com.coulterpeterson.floatnative.data.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.HttpException
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale


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
        } 
        
        // Add Cookie if present (Sails requires this for chat, even if we have DPoP)
        if (authCookie != null) {
            builder.header("Cookie", "sails.sid=$authCookie")
        }

        builder.header("User-Agent", "FloatNative/1.0 (Android)")
        
        val finalRequest = builder.build()

        var response: Response? = null
        try {
            response = chain.proceed(finalRequest)
        } catch (e: Exception) {
            // Check for SSL Handshake or Peer Unverified exceptions which might indicate
            // clock skew or cert issues that a fresh token *might* help with (user request),
            // or network glitches we want to retry once.
            if (e is javax.net.ssl.SSLHandshakeException || e is javax.net.ssl.SSLPeerUnverifiedException) {
                val refreshToken = tokenManager.refreshToken
                if (refreshToken != null) {
                    synchronized(this) {
                        try {
                            // Attempt to refresh token
                            val authApi = authApiProvider()
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

                            // Retry original request with new token
                            val method = originalRequest.method
                            val url = originalRequest.url.toString()
                            val proof = dpopManager.generateProof(method, url, tokenResponse.access_token)

                            return chain.proceed(
                                originalRequest.newBuilder()
                                    .header("DPoP", proof)
                                    .header("Authorization", "DPoP ${tokenResponse.access_token}")
                                    .build()
                            )
                        } catch (refreshEx: Exception) {
                            android.util.Log.e("AuthInterceptor", "Refresh failed during SSL recovery. Forcing logout.", refreshEx)
                            // Force logout so user can try to log in again (as requested)
                            tokenManager.clearAll()
                            throw e
                        }
                    }
                }
            }
            // If not SSL error or no refresh token, rethrow
            throw e
        }

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

        // 3. Handle 401 Unauthorized or 403 Forbidden
        if (response.code == 401 || response.code == 403) {
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
                        // Handle DPoP Time Skew for Refresh Token
                         if (e is HttpException && e.code() == 400) {
                            val errorBody = e.response()?.errorBody()?.string()
                            if (!errorBody.isNullOrEmpty() && errorBody.contains("DPoP", ignoreCase = true)) {
                                try {
                                    // 1. Auto-correct time
                                    val dateHeader = e.response()?.headers()?.get("date")
                                    if (dateHeader != null) {
                                        val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                                        val serverTime = sdf.parse(dateHeader)?.time
                                        if (serverTime != null) {
                                            val deviceTime = System.currentTimeMillis()
                                            val offsetSeconds = (serverTime - deviceTime) / 1000
                                            dpopManager.timeOffsetSeconds = offsetSeconds
                                            android.util.Log.i("AuthInterceptor", "Auto-corrected DPoP time offset: $offsetSeconds s")
                                        }
                                    }

                                    // 2. Retry Refresh
                                    val authApi = authApiProvider()
                                    val tokenEndpoint = "https://auth.floatplane.com/realms/floatplane/protocol/openid-connect/token"
                                    val retryProof = dpopManager.generateProof("POST", tokenEndpoint)

                                    val retryTokenResponse = runBlocking {
                                        authApi.getToken(
                                            dpop = retryProof,
                                            grantType = "refresh_token",
                                            clientId = "floatnative",
                                            refreshToken = refreshToken
                                        )
                                    }

                                    // 3. Save new tokens
                                    tokenManager.accessToken = retryTokenResponse.access_token
                                    tokenManager.refreshToken = retryTokenResponse.refresh_token

                                    // 4. Retry Original Request
                                    response.close()
                                    val method = originalRequest.method
                                    val url = originalRequest.url.toString()
                                    val proof = dpopManager.generateProof(method, url, retryTokenResponse.access_token)

                                    return chain.proceed(
                                        originalRequest.newBuilder()
                                            .header("DPoP", proof)
                                            .header("Authorization", "DPoP ${retryTokenResponse.access_token}")
                                            .build()
                                    )

                                } catch (retryEx: Exception) {
                                    android.util.Log.e("AuthInterceptor", "Retry refresh failed", retryEx)
                                    tokenManager.clearAll()
                                }
                            } else {
                                tokenManager.clearAll()
                            }
                        } else {
                            // Refresh failed, clear tokens to force re-login
                            tokenManager.clearAll()
                        }
                    }
                }
            }
        }

        return response
    }
}
