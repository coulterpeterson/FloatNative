package com.coulterpeterson.floatnative.api

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface OAuthApi {

    @FormUrlEncoded
    @POST("realms/floatplane/protocol/openid-connect/token")
    suspend fun getToken(
        @retrofit2.http.Header("DPoP") dpop: String,
        @Field("grant_type") grantType: String,
        @Field("client_id") clientId: String,
        @Field("code") code: String? = null,
        @Field("code_verifier") codeVerifier: String? = null,
        @Field("refresh_token") refreshToken: String? = null,
        @Field("redirect_uri") redirectUri: String? = null,
        @Field("device_code") deviceCode: String? = null
    ): OAuthTokenResponse

    @FormUrlEncoded
    @POST("realms/floatplane/protocol/openid-connect/auth/device")
    suspend fun startDeviceAuth(
        @Field("client_id") clientId: String,
        @Field("scope") scope: String = "openid offline_access"
    ): DeviceCodeResponse
}

data class OAuthTokenResponse(
    val access_token: String,
    val expires_in: Int,
    val refresh_token: String?,
    val refresh_expires_in: Int?,
    val token_type: String,
    val id_token: String?,
    val not_before_policy: Int?,
    val session_state: String?,
    val scope: String?
)

data class DeviceCodeResponse(
    val device_code: String,
    val user_code: String,
    val verification_uri: String,
    val verification_uri_complete: String?,
    val expires_in: Int,
    val interval: Int
)
