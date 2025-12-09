package com.coulterpeterson.floatnative.openapi.infrastructure

import com.coulterpeterson.floatnative.openapi.auth.ApiKeyAuth

import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import retrofit2.CallAdapter
import retrofit2.converter.scalars.ScalarsConverterFactory
import com.squareup.moshi.Moshi
import retrofit2.converter.moshi.MoshiConverterFactory


class ApiClient(
    private var baseUrl: String = defaultBasePath,
    private val okHttpClientBuilder: OkHttpClient.Builder? = null,
    private val serializerBuilder: Moshi.Builder = Serializer.moshiBuilder,
    private val callFactory: Call.Factory? = null,
    private val callAdapterFactories: List<CallAdapter.Factory> = listOf(
    ),
    private val converterFactories: List<Converter.Factory> = listOf(
        ScalarsConverterFactory.create(),
        MoshiConverterFactory.create(serializerBuilder.build()),
    )
) {
    private val apiAuthorizations = mutableMapOf<String, Interceptor>()
    var logger: ((String) -> Unit)? = null

    private val retrofitBuilder: Retrofit.Builder by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .apply {
                callAdapterFactories.forEach {
                    addCallAdapterFactory(it)
                }
            }
            .apply {
                converterFactories.forEach {
                    addConverterFactory(it)
                }
            }
    }

    private val clientBuilder: OkHttpClient.Builder by lazy {
        okHttpClientBuilder ?: defaultClientBuilder
    }

    private val defaultClientBuilder: OkHttpClient.Builder by lazy {
        OkHttpClient()
            .newBuilder()
            .addInterceptor(HttpLoggingInterceptor { message -> logger?.invoke(message) }
                .apply { level = HttpLoggingInterceptor.Level.BODY }
            )
    }

    init {
        normalizeBaseUrl()
    }

    constructor(
        baseUrl: String = defaultBasePath,
        okHttpClientBuilder: OkHttpClient.Builder? = null,
        serializerBuilder: Moshi.Builder = Serializer.moshiBuilder,
        authNames: Array<String>
    ) : this(baseUrl, okHttpClientBuilder, serializerBuilder) {
        authNames.forEach { authName ->
            val auth: Interceptor? = when (authName) { 
                "CookieAuth" -> ApiKeyAuth("cookie", "sails.sid")
                
                else -> throw RuntimeException("auth name $authName not found in available auth names")
            }
            if (auth != null) {
                addAuthorization(authName, auth)
            }
        }
    }

    /**
     * Adds an authorization to be used by the client
     * @param authName Authentication name
     * @param authorization Authorization interceptor
     * @return ApiClient
     */
    fun addAuthorization(authName: String, authorization: Interceptor): ApiClient {
        if (apiAuthorizations.containsKey(authName)) {
            throw RuntimeException("auth name $authName already in api authorizations")
        }
        apiAuthorizations[authName] = authorization
        clientBuilder.addInterceptor(authorization)
        return this
    }

    fun setLogger(logger: (String) -> Unit): ApiClient {
        this.logger = logger
        return this
    }

    fun <S> createService(serviceClass: Class<S>): S {
        val usedCallFactory = this.callFactory ?: clientBuilder.build()
        return retrofitBuilder.callFactory(usedCallFactory).build().create(serviceClass)
    }

    private fun normalizeBaseUrl() {
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/"
        }
    }

    private inline fun <T, reified U> Iterable<T>.runOnFirst(callback: U.() -> Unit) {
        for (element in this) {
            if (element is U) {
                callback.invoke(element)
                break
            }
        }
    }

    companion object {
        @JvmStatic
        protected val baseUrlKey: String = "com.coulterpeterson.floatnative.openapi.baseUrl"

        @JvmStatic
        val defaultBasePath: String by lazy {
            System.getProperties().getProperty(baseUrlKey, "https://www.floatplane.com")
        }
    }
}
