package com.coulterpeterson.floatnative.api

import com.coulterpeterson.floatnative.openapi.models.UserSubscriptionModel
import com.coulterpeterson.floatnative.utils.Lossy
import retrofit2.Response
import retrofit2.http.GET

interface ManualApi {
    @GET("api/v3/user/subscriptions")
    suspend fun getSubscriptions(): Response<List<UserSubscriptionModel>>

    // Skips an entry it can't read, so one can't take out the whole history.
    @Lossy
    @GET("api/v3/content/history")
    suspend fun getWatchHistory(@retrofit2.http.Query("offset") offset: Int): Response<List<WatchHistoryResponse>>

    @GET("api/v3/creator/list")
    suspend fun getCreatorsByIds(@retrofit2.http.Query("ids[]") ids: List<String>): Response<List<com.coulterpeterson.floatnative.openapi.models.CreatorModelV3>>
}
