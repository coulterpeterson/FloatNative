package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.CdnDeliveryV2Response
import com.coulterpeterson.floatnative.openapi.models.ErrorModel

interface CDNV2Api {

    /**
    * enum for parameter type
    */
    enum class TypeGetDeliveryInfo(val value: kotlin.String) {
        @Json(name = "vod") vod("vod"),
        @Json(name = "aod") aod("aod"),
        @Json(name = "live") live("live"),
        @Json(name = "download") download("download")
    }

    /**
     * GET api/v2/cdn/delivery
     * Get Delivery Info
     * Given an video/audio attachment identifier, retrieves the information necessary to play, download, or livestream the video/audio at various quality levels.
     * Responses:
     *  - 200: OK - Information on how to stream or download the requested video from the CDN in various levels of quality.
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param type Used to determine which kind of retrieval method is requested for the video.  - VOD &#x3D; stream a Video On Demand - AOD &#x3D; stream Audio On Demand - Live &#x3D; Livestream the content - Download &#x3D; Download the content for the user to play later.
     * @param guid The GUID of the attachment for a post, retrievable from the &#x60;videoAttachments&#x60; or &#x60;audioAttachments&#x60; object. Required when &#x60;type&#x60; is &#x60;vod&#x60;, &#x60;aod&#x60;, or &#x60;download&#x60;. Note: either this or &#x60;creator&#x60; must be supplied. (optional)
     * @param creator The GUID of the creator for a livestream, retrievable from &#x60;CreatorModelV2.id&#x60;. Required when &#x60;type&#x60; is &#x60;live&#x60;. Note: either this or &#x60;guid&#x60; must be supplied. Note: for &#x60;vod&#x60; and &#x60;download&#x60;, including this &#x60;creator&#x60; parameter *will* cause an error to be returned. (optional)
     * @return [CdnDeliveryV2Response]
     */
    @GET("api/v2/cdn/delivery")
    suspend fun getDeliveryInfo(@Query("type") type: TypeGetDeliveryInfo, @Query("guid") guid: kotlin.String? = null, @Query("creator") creator: kotlin.String? = null): Response<CdnDeliveryV2Response>

}
