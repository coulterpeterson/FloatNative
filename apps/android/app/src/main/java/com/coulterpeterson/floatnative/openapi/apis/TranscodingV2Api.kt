package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.ErrorModel

interface TranscodingV2Api {
    /**
     * GET api/v2/video/transcode/progress
     * Get Transcode Progress
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
    @GET("api/v2/video/transcode/progress")
    suspend fun getTranscodeProgress(): Response<kotlin.Any>

    /**
     * POST api/v2/video/transcode/progress/subscribe
     * Progress Subscribe
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
    @POST("api/v2/video/transcode/progress/subscribe")
    suspend fun progressSubscribe(): Response<kotlin.Any>

    /**
     * POST api/v2/video/transcode/progress/unsubscribe
     * Progress Unsubscribe
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
    @POST("api/v2/video/transcode/progress/unsubscribe")
    suspend fun progressUnsubscribe(): Response<kotlin.Any>

}
