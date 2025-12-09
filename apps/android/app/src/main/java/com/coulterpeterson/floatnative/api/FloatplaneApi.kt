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

object FloatplaneApi {

    lateinit var tokenManager: TokenManager
        private set

    private lateinit var apiClient: ApiClient
    private lateinit var retrofit: Retrofit // Added for new API initializers
    private lateinit var loggingInterceptor: HttpLoggingInterceptor // Made lateinit to be accessible by companionOkHttpClient
    private lateinit var moshi: Moshi // Added for MoshiConverterFactory

    // --- Companion API Setup ---
    private const val COMPANION_BASE_URL = "https://api.floatnative.coulterpeterson.com"

    private lateinit var companionAuthInterceptor: CompanionAuthInterceptor // Made lateinit
    private lateinit var companionOkHttpClient: OkHttpClient // Made lateinit
    private lateinit var companionRetrofit: Retrofit // Made lateinit
    lateinit var companionApi: CompanionApi // Made lateinit

    // --- Singleton Initializers for OpenAPI services ---
    val authV2 by lazy { apiClient.createService(AuthV2Api::class.java) }
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

    // Manual API for missing endpoints
    val manual: ManualApi by lazy {
        apiClient.createService(ManualApi::class.java)
    }
    
    // Interactive
    val commentV3 by lazy { apiClient.createService(CommentV3Api::class.java) }

    fun init(context: Context) {
        tokenManager = TokenManager(context)

        moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            
        loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // TODO: Reduce level in production
        }

        // OAuth API for refreshing (no interceptor)
        val oauthRetrofit = Retrofit.Builder()
            .baseUrl("https://auth.floatplane.com/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            
        val oauthApi = oauthRetrofit.create(OAuthApi::class.java)

        val authInterceptor = AuthInterceptor(tokenManager) { oauthApi }

        val okHttpClient = OkHttpClient.Builder()
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
}
