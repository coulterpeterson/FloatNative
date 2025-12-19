package com.coulterpeterson.floatnative.api

import android.content.Context
import com.coulterpeterson.floatnative.data.TokenManager
import com.coulterpeterson.floatnative.openapi.apis.*
import com.coulterpeterson.floatnative.openapi.infrastructure.ApiClient
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

import com.coulterpeterson.floatnative.openapi.infrastructure.Serializer

object FloatplaneApi {

    val authCodeFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 0)

    lateinit var dpopManager: com.coulterpeterson.floatnative.data.DPoPManager
        private set
        
    private lateinit var oauthApi: OAuthApi

    // Use PUBLIC setter for tokenManager to avoid any potential visibility weirdness with lateinit
    lateinit var tokenManager: TokenManager

    private lateinit var apiClient: ApiClient
    private lateinit var retrofit: Retrofit 
    private lateinit var loggingInterceptor: HttpLoggingInterceptor 
    private lateinit var moshi: Moshi 

    // --- Companion API Setup ---
    private const val COMPANION_BASE_URL = "https://api.floatnative.coulterpeterson.com"

    private lateinit var companionAuthInterceptor: CompanionAuthInterceptor
    private lateinit var companionOkHttpClient: OkHttpClient
    private lateinit var companionRetrofit: Retrofit
    lateinit var companionApi: CompanionApi

    // --- Singleton Initializers for OpenAPI services ---
    val authV2: AuthV2Api by lazy { apiClient.createService(AuthV2Api::class.java) }
    val authV3: AuthV3Api by lazy { retrofit.create(AuthV3Api::class.java) }

    val userV2: UserV2Api by lazy { retrofit.create(UserV2Api::class.java) }
    val userV3: UserV3Api by lazy { retrofit.create(UserV3Api::class.java) }

    // Content
    val contentV3: ContentV3Api by lazy { retrofit.create(ContentV3Api::class.java) }
    
    // Creator
    val creatorV3: CreatorV3Api by lazy { retrofit.create(CreatorV3Api::class.java) }
    
    // Delivery
    val deliveryV3: DeliveryV3Api by lazy { retrofit.create(DeliveryV3Api::class.java) }

    // Video
    val videoV2: VideoV2Api by lazy { retrofit.create(VideoV2Api::class.java) }

    val subscriptionsV3: SubscriptionsV3Api by lazy { retrofit.create(SubscriptionsV3Api::class.java) }

    // Manual API for missing endpoints
    val manual: ManualApi by lazy {
        apiClient.createService(ManualApi::class.java)
    }
    
    // Interactive
    val commentV3: CommentV3Api by lazy { apiClient.createService(CommentV3Api::class.java) }

    lateinit var okHttpClient: OkHttpClient
        private set

    fun init(context: Context) {
        tokenManager = TokenManager(context)

        moshi = Serializer.moshiBuilder.build()
            
        loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // TODO: Reduce level in production
        }

        // OAuth API for refreshing (no interceptor)
        val oauthRetrofit = Retrofit.Builder()
            .baseUrl("https://auth.floatplane.com/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            
        oauthApi = oauthRetrofit.create(OAuthApi::class.java)

        dpopManager = com.coulterpeterson.floatnative.data.DPoPManager(context)
        val authInterceptor = AuthInterceptor(tokenManager, dpopManager) { oauthApi }

        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()

        // Initialize generated ApiClient
        apiClient = ApiClient(
            okHttpClientBuilder = okHttpClient.newBuilder()
        )
        
        // Initialize main Retrofit for manual services
        retrofit = Retrofit.Builder()
            .baseUrl("https://www.floatplane.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            
        // --- Companion API Initialization ---
        companionAuthInterceptor = CompanionAuthInterceptor(tokenManager)
        
        companionOkHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(companionAuthInterceptor)
            .build()
            
        companionRetrofit = Retrofit.Builder()
            .baseUrl(COMPANION_BASE_URL)
            .client(companionOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            
        companionApi = companionRetrofit.create(CompanionApi::class.java)
    }
    
    suspend fun exchangeAuthCode(code: String, verifier: String) {
        val tokenEndpoint = "https://auth.floatplane.com/realms/floatplane/protocol/openid-connect/token"
        
        // 1. Generate DPoP proof
        val dpop = dpopManager.generateProof("POST", tokenEndpoint)
        
        // 2. Exchange Token
        val response = oauthApi.getToken(
            dpop = dpop,
            grantType = "authorization_code",
            clientId = "floatnative",
            code = code,
            codeVerifier = verifier,
            redirectUri = "floatnative://auth"
        )
        
        // 3. Save Tokens
        tokenManager.accessToken = response.access_token
        tokenManager.refreshToken = response.refresh_token
        // TODO: Save expiry logic if needed in TokenManager
        
        // 4. Companion Login
        // Generate DPoP for user/self
        val userSelfUrl = "https://www.floatplane.com/api/v3/user/self"
        val loginProof = dpopManager.generateProof("GET", userSelfUrl, response.access_token)
        
        // 5. Fetch User Self to prime "sails.sid" session cookie
        // The interceptor will automatically extract the cookie from the response.
        try {
            userV3.getSelf()
        } catch (e: Exception) {
            android.util.Log.e("FloatplaneApi", "Failed to fetch self for cookie priming", e)
            // Continue, as some things might still work
        }

        val loginRequest = CompanionLoginRequest(
            accessToken = response.access_token,
            dpopProof = loginProof
        )
        
        val companionResponse = companionApi.login(loginRequest)
        if (companionResponse.isSuccessful) {
            val body = companionResponse.body()
            if (body != null) {
                tokenManager.companionApiKey = body.apiKey
            }
        } else {
             // Handle error? Throw?
             // For now just log or ignore, app works without companion login partially (but playlists fail)
             // Ideally we throw so ViewModel shows error
             // throw Exception("Companion Login Failed: ${companionResponse.code()}")
             android.util.Log.e("FloatplaneApi", "Companion Login Failed: ${companionResponse.code()}")
        }
    }

    suspend fun ensureCompanionLogin(forceRefresh: Boolean = false): Boolean {
        android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: Called with forceRefresh=$forceRefresh")
        
        // If we have an API key and not forcing refresh, we're good
        if (!forceRefresh && tokenManager.companionApiKey != null) {
            android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: API key already exists, skipping login")
            return true
        }
        
        // If force refresh, clear the old API key
        if (forceRefresh) {
            android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: Clearing old API key due to forceRefresh")
            tokenManager.companionApiKey = null
        }

        var accessToken = tokenManager.accessToken 
        if (accessToken == null) {
            android.util.Log.e("FloatplaneApi", "ensureCompanionLogin: No access token available")
            return false
        }
        
        android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: Generating DPoP proof")
        // Generate DPoP for user/self
        val userSelfUrl = "https://www.floatplane.com/api/v3/user/self"
        var loginProof = dpopManager.generateProof("GET", userSelfUrl, accessToken)
        
        var loginRequest = CompanionLoginRequest(
            accessToken = accessToken,
            dpopProof = loginProof
        )
        
        // Log the request details (first 100 chars of token and proof for security)
        android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: Access token (first 100 chars): ${accessToken.take(100)}...")
        android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: DPoP proof (first 100 chars): ${loginProof.take(100)}...")
        android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: DPoP proof length: ${loginProof.length}")
        android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: Access token length: ${accessToken.length}")
        
        android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: Calling companion login API")
        var response = companionApi.login(loginRequest)
        android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: Got response with code ${response.code()}")
        
        // If 401, the access token might be expired - try refreshing it
        if (response.code() == 401 && tokenManager.refreshToken != null) {
            android.util.Log.w("FloatplaneApi", "ensureCompanionLogin: Got 401, attempting to refresh access token")
            try {
                // Refresh the access token
                val refreshToken = tokenManager.refreshToken!!
                val tokenEndpoint = "https://auth.floatplane.com/realms/floatplane/protocol/openid-connect/token"
                val dpop = dpopManager.generateProof("POST", tokenEndpoint)
                
                android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: Calling token refresh")
                val tokenResponse = oauthApi.getToken(
                    dpop = dpop,
                    grantType = "refresh_token",
                    clientId = "floatnative",
                    refreshToken = refreshToken
                )
                
                // Update tokens
                tokenManager.accessToken = tokenResponse.access_token
                tokenManager.refreshToken = tokenResponse.refresh_token
                accessToken = tokenResponse.access_token
                
                android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: Access token refreshed, retrying companion login")
                
                // Retry companion login with new access token
                loginProof = dpopManager.generateProof("GET", userSelfUrl, accessToken)
                loginRequest = CompanionLoginRequest(
                    accessToken = accessToken,
                    dpopProof = loginProof
                )
                
                // Log the retry request details
                android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: RETRY - Access token (first 100 chars): ${accessToken.take(100)}...")
                android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: RETRY - DPoP proof (first 100 chars): ${loginProof.take(100)}...")
                android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: RETRY - DPoP proof length: ${loginProof.length}")
                android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: RETRY - Access token length: ${accessToken.length}")
                
                response = companionApi.login(loginRequest)
                android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: Retry response code: ${response.code()}")
            } catch (e: Exception) {
                android.util.Log.e("FloatplaneApi", "ensureCompanionLogin: Failed to refresh access token", e)
                return false
            }
        }
        
        if (response.isSuccessful && response.body() != null) {
            tokenManager.companionApiKey = response.body()!!.apiKey
            android.util.Log.d("FloatplaneApi", "ensureCompanionLogin: Successfully obtained API key")
            return true
        } else {
            android.util.Log.e("FloatplaneApi", "ensureCompanionLogin: Failed with code ${response.code()}")
            return false
        }
    }
}
