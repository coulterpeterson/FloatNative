package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.ErrorModel

interface AdministrationV2Api {
    /**
     * POST api/v2/administration/moderators/global/add
     * Add Global Moderator
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
    @POST("api/v2/administration/moderators/global/add")
    suspend fun addGlobalModerator(): Response<kotlin.Any>

    /**
     * GET api/v2/administration/moderators/get/{user}
     * Get Moderator
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
     * @param user 
     * @return [kotlin.Any]
     */
    @Deprecated("This api was deprecated")
    @GET("api/v2/administration/moderators/get/{user}")
    suspend fun getModerator(@Path("user") user: kotlin.String): Response<kotlin.Any>

    /**
     * GET api/v2/administration/moderators/list
     * List Moderators
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
    @GET("api/v2/administration/moderators/list")
    suspend fun listModeratorsAdmin(): Response<kotlin.Any>

    /**
     * POST api/v2/administration/moderators/global/remove
     * Remove Global Moderator
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
    @POST("api/v2/administration/moderators/global/remove")
    suspend fun removeGlobalModerator(): Response<kotlin.Any>

    /**
     * POST api/v2/administration/moderators/remove
     * Remove Moderator
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
    @POST("api/v2/administration/moderators/remove")
    suspend fun removeModerator(): Response<kotlin.Any>

}
