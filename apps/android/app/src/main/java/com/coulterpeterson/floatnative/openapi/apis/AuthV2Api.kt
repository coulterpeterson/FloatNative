package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.AuthLoginV2Request
import com.coulterpeterson.floatnative.openapi.models.AuthLoginV2Response
import com.coulterpeterson.floatnative.openapi.models.CheckFor2faLoginRequest
import com.coulterpeterson.floatnative.openapi.models.ErrorModel

interface AuthV2Api {
    /**
     * POST api/v2/auth/spoof/begin
     * Begin Spoofing
     * TODO
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.Any]
     */
    @POST("api/v2/auth/spoof/begin")
    suspend fun beginSpoofing(): Response<kotlin.Any>

    /**
     * POST api/v2/auth/checkFor2faLogin
     * Check For 2FA Login
     * Complete the login process if a two-factor authentication token is required from the beginning of the login process.
     * Responses:
     *  - 200: OK - Returns the header and information about the logged-in user, including the id, username, and profile image.
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The login attempt failed, either due to a bad username or password.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param checkFor2faLoginRequest 
     * @return [AuthLoginV2Response]
     */
    @POST("api/v2/auth/checkFor2faLogin")
    suspend fun checkFor2faLogin(@Body checkFor2faLoginRequest: CheckFor2faLoginRequest): Response<AuthLoginV2Response>

    /**
     * POST api/v2/auth/spoof/end
     * End Spoofing
     * TODO
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.Any]
     */
    @POST("api/v2/auth/spoof/end")
    suspend fun endSpoofing(): Response<kotlin.Any>

    /**
     * POST api/v2/auth/login
     * Login
     * Login to Floatplane with the provided username and password, retrieving the authentication/authorization cookie from the response for subsequent requests.
     * Responses:
     *  - 200: OK - Returns the header and information about the logged-in user, including the id, username, and profile image.
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The login attempt failed, either due to a bad username or password.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param authLoginV2Request 
     * @return [AuthLoginV2Response]
     */
    @POST("api/v2/auth/login")
    suspend fun login(@Body authLoginV2Request: AuthLoginV2Request): Response<AuthLoginV2Response>

    /**
     * POST api/v2/auth/logout
     * Logout
     * Log out of Floatplane, invalidating the authentication/authorization cookie.
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.String]
     */
    @POST("api/v2/auth/logout")
    suspend fun logout(): Response<kotlin.String>

    /**
     * POST api/v2/auth/signup
     * Signup
     * TODO
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.Any]
     */
    @POST("api/v2/auth/signup")
    suspend fun signup(): Response<kotlin.Any>

}
