package com.coulterpeterson.floatnative.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface CompanionApi {

    @POST("/auth/login")
    suspend fun login(@Body request: CompanionLoginRequest): Response<CompanionRegisterResponse>

    @GET("/playlists")
    suspend fun getPlaylists(@Query("include_watch_later") includeWatchLater: Boolean): Response<PlaylistResponse>

    @POST("/playlists")
    suspend fun createPlaylist(@Body request: PlaylistCreateRequest): Response<Playlist>
    
    @GET("/ltt/search")
    suspend fun searchLTT(@Query("q") query: String): Response<LTTSearchResponse>

    @POST("/auth/logout")
    suspend fun logout(): Response<Unit>

    @retrofit2.http.PATCH("/watch-later/add")
    suspend fun addToWatchLater(@Body request: WatchLaterAddRequest): Response<WatchLaterResponse>

    @retrofit2.http.DELETE("/playlists/{id}")
    suspend fun deletePlaylist(@retrofit2.http.Path("id") id: String): Response<Unit>

    @retrofit2.http.PATCH("/playlists/{id}/add")
    suspend fun addToPlaylist(@retrofit2.http.Path("id") id: String, @Body request: PlaylistAddRequest): Response<Playlist>

    @retrofit2.http.PATCH("/playlists/{id}/remove")
    suspend fun removeFromPlaylist(@retrofit2.http.Path("id") id: String, @Body request: PlaylistRemoveRequest): Response<Playlist>
}
