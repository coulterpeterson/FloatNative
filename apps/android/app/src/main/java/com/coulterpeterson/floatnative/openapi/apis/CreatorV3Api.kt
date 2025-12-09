package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.ChannelModel
import com.coulterpeterson.floatnative.openapi.models.CreatorModelV3
import com.coulterpeterson.floatnative.openapi.models.ErrorModel

interface CreatorV3Api {
    /**
     * POST api/v3/creator/invite/bind
     * Bind Creator Invite Code
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
    @POST("api/v3/creator/invite/bind")
    suspend fun bindCreatorInviteCode(): Response<kotlin.Any>

    /**
     * POST api/v3/creator/invite/claim
     * Claim Creator Invite Code
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
    @POST("api/v3/creator/invite/claim")
    suspend fun claimCreatorInviteCode(): Response<kotlin.Any>

    /**
     * GET api/v3/creator/discover
     * Discover Creators
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
    @GET("api/v3/creator/discover")
    suspend fun discoverCreatorsV3(): Response<kotlin.Any>

    /**
     * GET api/v3/creator/info
     * Get Creator
     * Retrieve detailed information about a specific creator.
     * Responses:
     *  - 200: OK - Creator information returned
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param id The GUID of the creator being searched.
     * @return [CreatorModelV3]
     */
    @GET("api/v3/creator/info")
    suspend fun getCreator(@Query("id") id: kotlin.String): Response<CreatorModelV3>

    /**
     * GET api/v3/creator/named
     * Get Creator By Name
     * Retrieve detailed information on one or more creators on Floatplane.
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param creatorURL The &#x60;urlname&#x60;(s) of the creator(s) to be retrieved. See &#x60;CreatorModelV3&#x60;.
     * @return [kotlin.collections.List<CreatorModelV3>]
     */
    @GET("api/v3/creator/named")
    suspend fun getCreatorByName(@Query("creatorURL") creatorURL: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>): Response<kotlin.collections.List<CreatorModelV3>>

    /**
     * GET api/v3/creator/invite/info
     * Get Creator Invite Code Info
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
    @GET("api/v3/creator/invite/info")
    suspend fun getCreatorInviteCodeInfo(): Response<kotlin.Any>

    /**
     * GET api/v3/creator/list
     * Get Creators
     * Retrieve and search for all creators on Floatplane. Useful for creator discovery and filtering.
     * Responses:
     *  - 200: OK - Creators returned
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param search Optional search string for finding particular creators on the platform.
     * @return [kotlin.collections.List<CreatorModelV3>]
     */
    @GET("api/v3/creator/list")
    suspend fun getCreators(@Query("search") search: kotlin.String): Response<kotlin.collections.List<CreatorModelV3>>

    /**
     * GET api/v3/creator/category/list
     * List Creator Categories
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
    @GET("api/v3/creator/category/list")
    suspend fun listCreatorCategoriesV3(): Response<kotlin.Any>

    /**
     * GET api/v3/creator/channels/list
     * List Creator Channels
     * Retrieves a list of channels within the given creator(s).
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param ids The ids of the creator(s) from which to search for channels.
     * @return [kotlin.collections.List<ChannelModel>]
     */
    @GET("api/v3/creator/channels/list")
    suspend fun listCreatorChannelsV3(@Query("ids") ids: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>): Response<kotlin.collections.List<ChannelModel>>

}
