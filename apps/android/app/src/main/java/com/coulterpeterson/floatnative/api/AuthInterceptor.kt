package com.coulterpeterson.floatnative.api

import com.coulterpeterson.floatnative.data.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Locale


class AuthInterceptor(
    private val tokenManager: TokenManager,
    private val dpopManager: com.coulterpeterson.floatnative.data.DPoPManager,
    private val authApiProvider: () -> OAuthApi // Lazy provider to avoid circular dependency
) : Interceptor {

    companion object {
        private const val USER_AGENT = "FloatNative/1.0 (Android)"
        private const val MAX_NONCE_RETRIES = 3
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        var accessToken = tokenManager.accessToken

        var request = authorize(originalRequest, accessToken)
        var response = proceedCapturingNonce(chain, request)

        // Cloudflare HTML challenges and DPoP nonce challenges both surface as
        // 401/403. Handle nonce first (no token refresh). Only then try refresh
        // for real auth failures — refreshing on every 403 caused request storms
        // and 429s once Floatplane started requiring DPoP nonces more strictly.
        var nonceAttempts = 0
        while (isAuthChallenge(response) && isDpopNonceChallenge(response) && nonceAttempts < MAX_NONCE_RETRIES) {
            nonceAttempts++
            response.close()
            accessToken = tokenManager.accessToken
            request = authorize(originalRequest, accessToken)
            response = proceedCapturingNonce(chain, request)
        }

        if (isAuthChallenge(response) && !isCloudflareChallenge(response) && !isDpopNonceChallenge(response)) {
            val refreshToken = tokenManager.refreshToken
            if (refreshToken != null) {
                synchronized(this) {
                    val currentAccessToken = tokenManager.accessToken
                    if (currentAccessToken != null && currentAccessToken != accessToken) {
                        response.close()
                        return proceedCapturingNonce(chain, authorize(originalRequest, currentAccessToken))
                    }

                    try {
                        val newAccess = refreshAccessToken(refreshToken)
                        response.close()
                        var retry = proceedCapturingNonce(chain, authorize(originalRequest, newAccess))

                        // Fresh token may still need a nonce round-trip.
                        var postRefreshNonceAttempts = 0
                        while (
                            isAuthChallenge(retry) &&
                            isDpopNonceChallenge(retry) &&
                            postRefreshNonceAttempts < MAX_NONCE_RETRIES
                        ) {
                            postRefreshNonceAttempts++
                            retry.close()
                            retry = proceedCapturingNonce(chain, authorize(originalRequest, newAccess))
                        }
                        return retry
                    } catch (e: Exception) {
                        if (e is HttpException && e.code() == 400) {
                            val errorBody = e.response()?.errorBody()?.string().orEmpty()
                            if (errorBody.contains("DPoP", ignoreCase = true) ||
                                errorBody.contains("use_dpop_nonce", ignoreCase = true)
                            ) {
                                try {
                                    autoCorrectTimeSkew(e)
                                    e.response()?.headers()?.let { dpopManager.captureNonce(it) }
                                    val newAccess = refreshAccessToken(refreshToken)
                                    response.close()
                                    return proceedCapturingNonce(chain, authorize(originalRequest, newAccess))
                                } catch (retryEx: Exception) {
                                    android.util.Log.e("AuthInterceptor", "Retry refresh failed", retryEx)
                                    com.coulterpeterson.floatnative.utils.DebugLogManager.auth(
                                        "Token refresh retry failed; signing out",
                                        "${retryEx.javaClass.simpleName}: ${retryEx.message}"
                                    )
                                    tokenManager.clearAll()
                                }
                            } else {
                                com.coulterpeterson.floatnative.utils.DebugLogManager.auth(
                                    "Token refresh failed (non-DPoP error); signing out",
                                    "${e.javaClass.simpleName}: ${e.message}"
                                )
                                tokenManager.clearAll()
                            }
                        } else if (e is javax.net.ssl.SSLHandshakeException ||
                            e is javax.net.ssl.SSLPeerUnverifiedException
                        ) {
                            android.util.Log.e("AuthInterceptor", "SSL error during refresh. Forcing logout.", e)
                            tokenManager.clearAll()
                        } else {
                            com.coulterpeterson.floatnative.utils.DebugLogManager.auth(
                                "Token refresh failed; signing out",
                                "${e.javaClass.simpleName}: ${e.message}"
                            )
                            tokenManager.clearAll()
                        }
                    }
                }
            }
        }

        return response
    }

    private fun authorize(original: Request, accessToken: String?): Request {
        val builder = original.newBuilder()
        builder.header("User-Agent", USER_AGENT)

        val url = original.url.toString()
        val isFloatplane = url.contains("floatplane.com") || url.contains("floatnative.coulterpeterson.com")

        if (accessToken != null && isFloatplane) {
            try {
                val proof = dpopManager.generateProof(original.method, url, accessToken)
                builder.header("DPoP", proof)
                builder.header("Authorization", "DPoP $accessToken")
            } catch (e: Exception) {
                e.printStackTrace()
                builder.header("Authorization", "Bearer $accessToken")
            }
        }

        val authCookie = tokenManager.authCookie
        if (authCookie != null) {
            // Sails still expects the session cookie for some hybrid paths (chat / delivery).
            builder.header("Cookie", "sails.sid=$authCookie")
        }

        return builder.build()
    }

    private fun proceedCapturingNonce(chain: Interceptor.Chain, request: Request): Response {
        val response = chain.proceed(request)
        dpopManager.captureNonce(response.headers)
        captureSailsCookie(response)
        return response
    }

    private fun captureSailsCookie(response: Response) {
        for (cookie in response.headers("Set-Cookie")) {
            if (!cookie.contains("sails.sid")) continue
            for (part in cookie.split(";")) {
                val pair = part.trim().split("=", limit = 2)
                if (pair.size == 2 && pair[0] == "sails.sid") {
                    tokenManager.authCookie = pair[1]
                }
            }
        }
    }

    private fun isAuthChallenge(response: Response): Boolean {
        return response.code == 401 || response.code == 403
    }

    private fun isDpopNonceChallenge(response: Response): Boolean {
        val www = response.header("WWW-Authenticate").orEmpty()
        return www.contains("use_dpop_nonce", ignoreCase = true)
    }

    private fun isCloudflareChallenge(response: Response): Boolean {
        val contentType = response.header("Content-Type").orEmpty()
        if (contentType.contains("text/html", ignoreCase = true)) return true
        if (response.header("cf-mitigated") != null) return true
        return false
    }

    private fun refreshAccessToken(refreshToken: String): String {
        val authApi = authApiProvider()
        val tokenEndpoint = "https://auth.floatplane.com/realms/floatplane/protocol/openid-connect/token"

        fun attempt(): String {
            val refreshProof = dpopManager.generateProof("POST", tokenEndpoint)
            val tokenResponse = runBlocking {
                authApi.getToken(
                    dpop = refreshProof,
                    grantType = "refresh_token",
                    clientId = "floatnative",
                    refreshToken = refreshToken
                )
            }
            tokenManager.accessToken = tokenResponse.access_token
            tokenManager.refreshToken = tokenResponse.refresh_token
            return tokenResponse.access_token
        }

        return try {
            attempt()
        } catch (e: HttpException) {
            // Token endpoint may demand a nonce before accepting the refresh.
            e.response()?.headers()?.let { dpopManager.captureNonce(it) }
            val body = e.response()?.errorBody()?.string().orEmpty()
            if (e.code() == 400 &&
                (body.contains("use_dpop_nonce") ||
                    e.response()?.headers()?.get("WWW-Authenticate")
                        ?.contains("use_dpop_nonce", ignoreCase = true) == true)
            ) {
                attempt()
            } else {
                throw e
            }
        }
    }

    private fun autoCorrectTimeSkew(e: HttpException) {
        val dateHeader = e.response()?.headers()?.get("date") ?: return
        try {
            val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            val serverTime = sdf.parse(dateHeader)?.time ?: return
            val offsetSeconds = (serverTime - System.currentTimeMillis()) / 1000
            dpopManager.timeOffsetSeconds = offsetSeconds
            android.util.Log.i("AuthInterceptor", "Auto-corrected DPoP time offset: $offsetSeconds s")
        } catch (parseEx: Exception) {
            android.util.Log.e("AuthInterceptor", "Failed to parse Date header", parseEx)
        }
    }
}
