package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.ErrorModel

interface RedirectV3Api {
    /**
     * POST api/v3/redirect-yt-latest/{channelKey}
     * Redirect to YouTube Latest Video
     * Redirects (HTTP 302) the user to the latest LMG video for a given LMG channel key. For example, visiting this URL with a &#x60;channelKey&#x60; of &#x60;sc&#x60;, it will take you directly to the latest Short Circuit video on YouTube. Unknown if this works for non-LMG creators for their channels. Not used in Floatplane code.
     * Responses:
     *  - 302: Found
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param channelKey 
     * @return [ErrorModel]
     */
    @POST("api/v3/redirect-yt-latest/{channelKey}")
    suspend fun redirectYTLatest(@Path("channelKey") channelKey: kotlin.String): Response<ErrorModel>

}
