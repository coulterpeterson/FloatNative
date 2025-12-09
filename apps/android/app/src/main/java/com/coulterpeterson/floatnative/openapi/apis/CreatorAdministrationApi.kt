package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.ErrorModel

interface CreatorAdministrationApi {
    /**
     * POST api/v2/creators/{creator}/administration/moderators/add
     * Add Creator Moderator By Path
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
     * @param creator 
     * @return [kotlin.Any]
     */
    @POST("api/v2/creators/{creator}/administration/moderators/add")
    suspend fun addCreatorModeratorByPath(@Path("creator") creator: kotlin.String): Response<kotlin.Any>

    /**
     * GET api/v2/creator/administration/moderators/list
     * List Moderators
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
    @GET("api/v2/creator/administration/moderators/list")
    suspend fun listModerators(): Response<kotlin.Any>

    /**
     * GET api/v2/creators/{creator}/administration/moderators/list
     * List Moderators By Path
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
     * @param creator 
     * @return [kotlin.Any]
     */
    @GET("api/v2/creators/{creator}/administration/moderators/list")
    suspend fun listModeratorsByPath(@Path("creator") creator: kotlin.String): Response<kotlin.Any>

    /**
     * POST api/v2/creator/administration/moderators/remove
     * Remove Creator Moderator
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
    @POST("api/v2/creator/administration/moderators/remove")
    suspend fun removeCreatorModeratorAdmin(): Response<kotlin.Any>

    /**
     * POST api/v2/creators/{creator}/administration/moderators/remove
     * Remove Creator Moderator By Path
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
     * @param creator 
     * @return [kotlin.Any]
     */
    @POST("api/v2/creators/{creator}/administration/moderators/remove")
    suspend fun removeCreatorModeratorByPath(@Path("creator") creator: kotlin.String): Response<kotlin.Any>

}
