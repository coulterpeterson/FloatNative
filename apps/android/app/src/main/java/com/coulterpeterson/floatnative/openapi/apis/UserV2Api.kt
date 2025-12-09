package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.ErrorModel
import com.coulterpeterson.floatnative.openapi.models.UserInfoV2Response
import com.coulterpeterson.floatnative.openapi.models.UserNamedV2Response
import com.coulterpeterson.floatnative.openapi.models.UserSecurityV2Response

interface UserV2Api {
    /**
     * POST api/v2/user/security/backupcode/activate
     * Activate Backup Code
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
    @POST("api/v2/user/security/backupcode/activate")
    suspend fun activateBackupCode(): Response<kotlin.Any>

    /**
     * POST api/v2/user/security/2fa/activate
     * Activate Two Factor Authentication
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
    @POST("api/v2/user/security/2fa/activate")
    suspend fun activateTwoFactorAuthentication(): Response<kotlin.Any>

    /**
     * POST api/v2/user/password/change
     * Change Password
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
    @POST("api/v2/user/password/change")
    suspend fun changePassword(): Response<kotlin.Any>

    /**
     * POST api/v2/user/email/update/complete
     * Complete Email Change
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
    @POST("api/v2/user/email/update/complete")
    suspend fun completeEmailChange(): Response<kotlin.Any>

    /**
     * POST api/v2/user/security/backupcode/deactivate
     * Deactivate Backup Code
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
    @POST("api/v2/user/security/backupcode/deactivate")
    suspend fun deactivateBackupCode(): Response<kotlin.Any>

    /**
     * POST api/v2/user/security/2fa/deactivate
     * Deactivate Two Factor Authentication
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
    @POST("api/v2/user/security/2fa/deactivate")
    suspend fun deactivateTwoFactorAuthentication(): Response<kotlin.Any>

    /**
     * POST api/v2/user/security/backupcode
     * Generate Backup Code
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
    @POST("api/v2/user/security/backupcode")
    suspend fun generateBackupCode(): Response<kotlin.Any>

    /**
     * POST api/v2/user/security/2fa
     * Generate Two Factor Secret
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
    @POST("api/v2/user/security/2fa")
    suspend fun generateTwoFactorSecret(): Response<kotlin.Any>

    /**
     * GET api/v2/user/activity
     * Get Activity Feed
     * TODO - Not used in Floatplane code.
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
    @Deprecated("This api was deprecated")
    @GET("api/v2/user/activity")
    suspend fun getActivityFeedV2(): Response<kotlin.Any>

    /**
     * GET api/v2/user/administrator
     * Get Administrator
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
    @GET("api/v2/user/administrator")
    suspend fun getAdministrator(): Response<kotlin.Any>

    /**
     * GET api/v2/user/security
     * Get Security
     * Retrieve information about the current security configuration for the user.
     * Responses:
     *  - 200: OK - Current security settings
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [UserSecurityV2Response]
     */
    @GET("api/v2/user/security")
    suspend fun getSecurity(): Response<UserSecurityV2Response>

    /**
     * GET api/v2/user/creator
     * Get Creator
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
    @GET("api/v2/user/creator")
    suspend fun getUserCreator(): Response<kotlin.Any>

    /**
     * GET api/v2/user/info
     * Info
     * Retrieve more detailed information about one or more users from their identifiers.
     * Responses:
     *  - 200: OK - Results of the user search
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param id The GUID identifer(s) of the user(s) to be retrieved.
     * @return [UserInfoV2Response]
     */
    @GET("api/v2/user/info")
    suspend fun getUserInfo(@Query("id") id: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>): Response<UserInfoV2Response>

    /**
     * GET api/v2/user/named
     * Get Info By Name
     * Retrieve more detailed information about one or more users from their usernames.
     * Responses:
     *  - 200: OK - Results of the user search
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param username The username(s) of the user(s) to be retrieved.
     * @return [UserNamedV2Response]
     */
    @GET("api/v2/user/named")
    suspend fun getUserInfoByName(@Query("username") username: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>): Response<UserNamedV2Response>

    /**
     * GET api/v2/user/notificationsetting
     * Get User Notification Settings
     * TODO - Not used in Floatplane code.
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
    @Deprecated("This api was deprecated")
    @GET("api/v2/user/notificationsetting")
    suspend fun getUserNotificationSettingsV2(): Response<kotlin.Any>

    /**
     * GET api/v2/user/self
     * Self
     * TODO - Not used in Floatplane code.
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
    @Deprecated("This api was deprecated")
    @GET("api/v2/user/self")
    suspend fun self(): Response<kotlin.Any>

    /**
     * POST api/v2/user/email/update
     * Update Email
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
    @POST("api/v2/user/email/update")
    suspend fun updateEmail(): Response<kotlin.Any>

    /**
     * POST api/v2/user/update
     * Update Info
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
    @POST("api/v2/user/update")
    suspend fun updateInfo(): Response<kotlin.Any>

    /**
     * POST api/v2/user/notificationsetting
     * Update User Notification Settings
     * TODO - Not used in Floatplane code.
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
    @Deprecated("This api was deprecated")
    @POST("api/v2/user/notificationsetting")
    suspend fun updateUserNotificationSettingsV2(): Response<kotlin.Any>

    /**
     * POST api/v2/user/avatar
     * Upload Avatar
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
    @POST("api/v2/user/avatar")
    suspend fun uploadAvatar(): Response<kotlin.Any>

    /**
     * GET api/v2/user/ban/status
     * User Creator Ban Status
     * Determine whether or not the user is banned for a given creator.
     * Responses:
     *  - 200: OK - Whether the user is banned or not
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param creator The GUID of the creator being queried.
     * @return [kotlin.Boolean]
     */
    @GET("api/v2/user/ban/status")
    suspend fun userCreatorBanStatus(@Query("creator") creator: kotlin.String): Response<kotlin.Boolean>

}
