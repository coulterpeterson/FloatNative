package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.ErrorModel
import com.coulterpeterson.floatnative.openapi.models.PlanInfoV2Response

interface CreatorSubscriptionPlanV2Api {
    /**
     * GET api/v2/plan/info
     * Get Creator Sub Info Public
     * Retrieve detailed information about a creator&#39;s subscription plans and their subscriber count.
     * Responses:
     *  - 200: OK - Information about the plans for the creator
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param creatorId The GUID for the creator being search.
     * @return [PlanInfoV2Response]
     */
    @GET("api/v2/plan/info")
    suspend fun getCreatorSubInfoPublic(@Query("creatorId") creatorId: kotlin.String): Response<PlanInfoV2Response>

    /**
     * POST api/v2/plan/list
     * List Subscription Plans
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
    @POST("api/v2/plan/list")
    suspend fun listSubscriptionPlans(): Response<kotlin.Any>

    /**
     * POST api/v2/plan/publish
     * Publish Subscription Plan
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
    @POST("api/v2/plan/publish")
    suspend fun publishSubscriptionPlan(): Response<kotlin.Any>

    /**
     * POST api/v2/plan/update
     * Update Subscription Plans
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
    @POST("api/v2/plan/update")
    suspend fun updateSubscriptionPlans(): Response<kotlin.Any>

}
