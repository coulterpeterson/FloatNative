package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.ErrorModel
import com.coulterpeterson.floatnative.openapi.models.UserActivityV3Response
import com.coulterpeterson.floatnative.openapi.models.UserNotificationModel
import com.coulterpeterson.floatnative.openapi.models.UserNotificationUpdateV3PostRequest
import com.coulterpeterson.floatnative.openapi.models.UserSelfV3Response
import com.coulterpeterson.floatnative.openapi.models.UserStatusV3Response

interface UserV3Api {
    /**
     * GET api/v3/user/achievement/perks
     * Get Achievement Perks
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
    @GET("api/v3/user/achievement/perks")
    suspend fun getAchievementPerks(): Response<kotlin.Any>

    /**
     * GET api/v3/user/activity
     * Get Activity Feed
     * Retrieve recent activity for a user, such as comments and other interactions they have made on posts for creators.
     * Responses:
     *  - 200: OK - Activity returned
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param id The GUID of the user being queried.
     * @return [UserActivityV3Response]
     */
    @GET("api/v3/user/activity")
    suspend fun getActivityFeedV3(@Query("id") id: kotlin.String): Response<UserActivityV3Response>

    /**
     * GET api/v3/user/self
     * Get Self
     * Retrieve more detailed information about the user, including their name and email.
     * Responses:
     *  - 200: OK - Information returned
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [UserSelfV3Response]
     */
    @GET("api/v3/user/self")
    suspend fun getSelf(): Response<UserSelfV3Response>


    /**
    * enum for parameter platform
    */
    enum class PlatformGetStatus(val value: kotlin.String) {
        @Json(name = "android") android("android"),
        @Json(name = "ios") ios("ios"),
        @Json(name = "web") web("web")
    }

    /**
     * GET api/v3/status
     * Get Status
     * Retrieve more detailed information about the user and status, including their name and email.
     * Responses:
     *  - 200: OK - Information returned
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param platform Platform requesting the status.
     * @param version Version of the app requesting the status.
     * @return [UserStatusV3Response]
     */
    @GET("api/v3/status")
    suspend fun getStatus(@Query("platform") platform: PlatformGetStatus, @Query("version") version: kotlin.String): Response<UserStatusV3Response>

    /**
     * GET api/v3/user/notification/list
     * Get User Notification Settings
     * Retrieve notification details for a user. The details are split into seperate settings for each subscribed creator.
     * Responses:
     *  - 200: OK - Notifications returned
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.collections.List<UserNotificationModel>]
     */
    @GET("api/v3/user/notification/list")
    suspend fun getUserNotificationSettingsV3(): Response<kotlin.collections.List<UserNotificationModel>>

    /**
     * POST api/v3/user/delete
     * Schedule Deletion
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
    @POST("api/v3/user/delete")
    suspend fun scheduleDeletion(): Response<kotlin.Any>

    /**
     * POST api/v3/user/undelete
     * Unschedule Deletion
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
    @POST("api/v3/user/undelete")
    suspend fun unscheduleDeletion(): Response<kotlin.Any>

    /**
     * POST api/v3/user/notification/update
     * Update User Notification Settings
     * Enable or disable email or push notifications for a specific creator.
     * Responses:
     *  - 200: OK - Whether or not the update was successful
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param userNotificationUpdateV3PostRequest 
     * @return [kotlin.Boolean]
     */
    @POST("api/v3/user/notification/update")
    suspend fun updateUserNotificationSettingsV3(@Body userNotificationUpdateV3PostRequest: UserNotificationUpdateV3PostRequest): Response<kotlin.Boolean>

}
