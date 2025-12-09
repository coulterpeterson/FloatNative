package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.ErrorModel

interface LoyaltyRewardsV3Api {
    /**
     * POST api/v3/user/loyaltyreward/claim
     * Claim Loyalty Reward
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
    @POST("api/v3/user/loyaltyreward/claim")
    suspend fun claimLoyaltyReward(): Response<kotlin.Any>

    /**
     * POST api/v3/user/loyaltyreward/list
     * List Creator Loyalty Reward
     * Retrieve a list of loyalty rewards for the user. The reason for why this is a POST and not a GET is unknown.
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @return [kotlin.collections.List<kotlin.Any>]
     */
    @POST("api/v3/user/loyaltyreward/list")
    suspend fun listCreatorLoyaltyReward(): Response<kotlin.collections.List<kotlin.Any>>

}
