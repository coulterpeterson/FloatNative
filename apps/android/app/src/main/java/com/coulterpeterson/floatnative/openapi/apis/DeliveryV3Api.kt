package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.CdnDeliveryV3Response
import com.coulterpeterson.floatnative.openapi.models.ErrorModel

interface DeliveryV3Api {

    /**
    * enum for parameter scenario
    */
    enum class ScenarioGetDeliveryInfoV3(val value: kotlin.String) {
        @Json(name = "onDemand") onDemand("onDemand"),
        @Json(name = "download") download("download"),
        @Json(name = "live") live("live")
    }


    /**
    * enum for parameter outputKind
    */
    enum class OutputKindGetDeliveryInfoV3(val value: kotlin.String) {
        @Json(name = "hls.mpegts") hlsPeriodMpegts("hls.mpegts"),
        @Json(name = "hls.fmp4") hlsPeriodFmp4("hls.fmp4"),
        @Json(name = "dash.mpegts") dashPeriodMpegts("dash.mpegts"),
        @Json(name = "dash.m4s") dashPeriodM4s("dash.m4s"),
        @Json(name = "flat") flat("flat")
    }

    /**
     * GET api/v3/delivery/info
     * Get Delivery Info
     * Given an video/audio attachment or livestream identifier, retrieves the information necessary to play, download, or livestream the media at various quality levels.
     * Responses:
     *  - 200: OK - Information on how to stream or download the requested video from the CDN in various levels of quality.
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param scenario Used to determine the scenario in which to consume the media.  - &#x60;onDemand&#x60; &#x3D; stream a Video/Audio On Demand - &#x60;download&#x60; &#x3D; Download the content for the user to play later. - &#x60;live&#x60; &#x3D; Livestream the content
     * @param entityId The attachment or livestream identifier for the requested media. For video and audio, this would be from the &#x60;videoAttachments&#x60; or &#x60;audioAttachments&#x60; objects. For livestreams, this is the &#x60;liveStream.id&#x60; from the creator object.
     * @param outputKind Use &#x60;outputKind&#x60; to ensure the right vehicle is used for your client, e.g. &#x60;outputKind&#x3D;hls.fmp4&#x60; is optimal for tvOS 10+. (optional)
     * @return [CdnDeliveryV3Response]
     */
    @GET("api/v3/delivery/info")
    suspend fun getDeliveryInfoV3(@Query("scenario") scenario: ScenarioGetDeliveryInfoV3, @Query("entityId") entityId: kotlin.String, @Query("outputKind") outputKind: OutputKindGetDeliveryInfoV3? = null): Response<CdnDeliveryV3Response>

}
