package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.ClosePollRequest
import com.coulterpeterson.floatnative.openapi.models.CmsListPollsRequest
import com.coulterpeterson.floatnative.openapi.models.CreateLivePollRequest
import com.coulterpeterson.floatnative.openapi.models.ErrorModel
import com.coulterpeterson.floatnative.openapi.models.VotePollRequest

interface PollV3Api {
    /**
     * POST api/v3/poll/close
     * Close Poll
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
     * @param closePollRequest 
     * @return [kotlin.Any]
     */
    @POST("api/v3/poll/close")
    suspend fun closePoll(@Body closePollRequest: ClosePollRequest): Response<kotlin.Any>

    /**
     * GET api/v3/poll/cms/list
     * CMS List Polls
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
     * @param cmsListPollsRequest 
     * @return [kotlin.Any]
     */
    @GET("api/v3/poll/cms/list")
    suspend fun cmsListPolls(@Body cmsListPollsRequest: CmsListPollsRequest): Response<kotlin.Any>

    /**
     * POST api/v3/poll/live/create
     * Create Live Poll
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
     * @param createLivePollRequest 
     * @return [kotlin.Any]
     */
    @POST("api/v3/poll/live/create")
    suspend fun createLivePoll(@Body createLivePollRequest: CreateLivePollRequest): Response<kotlin.Any>

    /**
     * POST api/v3/poll/live/joinroom
     * Poll Join Live Room
     * Used in Socket.IO/WebSocket connections. See the AsyncAPI documentation for more information. This should not be used on a raw HTTP connection.
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
    @POST("api/v3/poll/live/joinroom")
    suspend fun joinLiveRoom(): Response<kotlin.Any>

    /**
     * POST api/v3/poll/live/joinLiveRoomModerator
     * Poll Join Live Room Moderator
     * TODO - Used in Socket.IO/WebSocket connections. See the AsyncAPI documentation for more information. This should not be used on a raw HTTP connection.
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
    @POST("api/v3/poll/live/joinLiveRoomModerator")
    suspend fun joinLiveRoomModerator(): Response<kotlin.Any>

    /**
     * POST api/v3/poll/live/leaveLiveRoom
     * Poll Leave Live Room
     * Used in Socket.IO/WebSocket connections. See the AsyncAPI documentation for more information. This should not be used on a raw HTTP connection.
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
    @POST("api/v3/poll/live/leaveLiveRoom")
    suspend fun leaveLiveRoom(): Response<kotlin.Any>

    /**
     * POST api/v3/poll/live/leaveLiveRoomModerator
     * Poll Leave Live Room Moderator
     * TODO - Used in Socket.IO/WebSocket connections. See the AsyncAPI documentation for more information. This should not be used on a raw HTTP connection.
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
    @POST("api/v3/poll/live/leaveLiveRoomModerator")
    suspend fun leaveLiveRoomModerator(): Response<kotlin.Any>

    /**
     * POST api/v3/poll/votePoll
     * Vote Poll
     * Vote on an option of a poll. Voting a second time or attempting to change a choice may result in an error.
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param votePollRequest 
     * @return [kotlin.Any]
     */
    @POST("api/v3/poll/votePoll")
    suspend fun votePoll(@Body votePollRequest: VotePollRequest): Response<kotlin.Any>

}
