package com.coulterpeterson.floatnative.api

import com.coulterpeterson.floatnative.openapi.models.UserSubscriptionModel
import retrofit2.Response
import retrofit2.http.GET

interface ManualApi {
    @GET("api/v3/user/subscriptions")
    suspend fun getSubscriptions(): Response<List<UserSubscriptionModel>>

    @GET("api/v3/content/history")
    suspend fun getWatchHistory(@retrofit2.http.Query("offset") offset: Int): Response<List<WatchHistoryResponse>>
}
