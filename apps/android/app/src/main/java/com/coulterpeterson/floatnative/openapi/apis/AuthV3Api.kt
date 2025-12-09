package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.AuthLoginV3Request
import com.coulterpeterson.floatnative.openapi.models.AuthLoginV3Response
import com.coulterpeterson.floatnative.openapi.models.CheckFor2faLoginRequest
import com.coulterpeterson.floatnative.openapi.models.ErrorModel
import com.coulterpeterson.floatnative.openapi.models.GetCaptchaInfoResponse

interface AuthV3Api {
    /**
     * POST api/v3/auth/checkFor2faLogin
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
     * @return [AuthLoginV3Response]
     */
    @POST("api/v3/auth/checkFor2faLogin")
    suspend fun checkFor2faLoginV3(@Body checkFor2faLoginRequest: CheckFor2faLoginRequest): Response<AuthLoginV3Response>

    /**
     * GET api/v3/auth/captcha/info
     * Get Captcha Info
     * Gets the site keys used for Google Recaptcha V2 and V3. These are useful when providing a captcha token when logging in or signing up.
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [GetCaptchaInfoResponse]
     */
    @GET("api/v3/auth/captcha/info")
    suspend fun getCaptchaInfo(): Response<GetCaptchaInfoResponse>

    /**
     * POST api/v3/auth/login
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
     * @param authLoginV3Request 
     * @return [AuthLoginV3Response]
     */
    @POST("api/v3/auth/login")
    suspend fun loginV3(@Body authLoginV3Request: AuthLoginV3Request): Response<AuthLoginV3Response>

    /**
     * POST api/v3/auth/logout
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
    @POST("api/v3/auth/logout")
    suspend fun logoutV3(): Response<kotlin.String>

}
