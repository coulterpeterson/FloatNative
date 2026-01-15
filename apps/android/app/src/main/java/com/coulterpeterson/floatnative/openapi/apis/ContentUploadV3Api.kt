package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.ErrorModel

interface ContentUploadV3Api {
    /**
     * POST api/v3/content/upload/s3/multipart/abort
     * Abort Multipart Upload
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
    @POST("api/v3/content/upload/s3/multipart/abort")
    suspend fun abortMultipartUploadV3(): Response<kotlin.Any>

    /**
     * POST api/v3/content/upload/s3/multipart/complete
     * Complete Multipart Upload
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
    @POST("api/v3/content/upload/s3/multipart/complete")
    suspend fun completeMultipartUploadContent(): Response<kotlin.Any>

    /**
     * POST api/v3/content/upload/s3/multipart
     * Create Multipart Upload
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
    @POST("api/v3/content/upload/s3/multipart")
    suspend fun createMultipartUploadV3(): Response<kotlin.Any>

    /**
     * GET api/v3/content/upload/s3/multipart
     * Get Uploaded Parts
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
    @GET("api/v3/content/upload/s3/multipart")
    suspend fun getUploadedPartsV3(): Response<kotlin.Any>

    /**
     * GET api/v3/content/upload/s3/multipart/sign
     * Sign Part Upload
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
    @GET("api/v3/content/upload/s3/multipart/sign")
    suspend fun signPartUploadContent(): Response<kotlin.Any>

    /**
     * POST api/v3/content/thumbnail/subscribe
     * Thumbnail Update Subscribe
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
    @POST("api/v3/content/thumbnail/subscribe")
    suspend fun thumbnailUpdateSubscribe(): Response<kotlin.Any>

    /**
     * POST api/v3/content/upload/thumbnail
     * Upload Thumbnail
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
    @POST("api/v3/content/upload/thumbnail")
    suspend fun uploadThumbnailV3(): Response<kotlin.Any>

}
