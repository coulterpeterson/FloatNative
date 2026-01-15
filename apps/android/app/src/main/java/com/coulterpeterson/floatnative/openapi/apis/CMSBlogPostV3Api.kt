package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.ErrorModel

interface CMSBlogPostV3Api {
    /**
     * POST api/cms/v3/blogPost/create
     * Create Blog Post
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
    @POST("api/cms/v3/blogPost/create")
    suspend fun createBlogPost(): Response<kotlin.Any>

    /**
     * POST api/cms/v3/blogPost/delete
     * Delete Blog Post
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
    @POST("api/cms/v3/blogPost/delete")
    suspend fun deleteBlogPost(): Response<kotlin.Any>

    /**
     * POST api/cms/v3/blogPost/edit
     * Edit Blog Post
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
    @POST("api/cms/v3/blogPost/edit")
    suspend fun editBlogPost(): Response<kotlin.Any>

    /**
     * GET api/cms/v3/blogPost/get
     * Get Blog Post
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
    @GET("api/cms/v3/blogPost/get")
    suspend fun getBlogPostCMS(): Response<kotlin.Any>

    /**
     * GET api/cms/v3/blogPost/list
     * List Blog Post
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
    @GET("api/cms/v3/blogPost/list")
    suspend fun listBlogPost(): Response<kotlin.Any>

    /**
     * POST api/cms/v3/blogPost/attachments/update
     * Update New Attachments
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
    @POST("api/cms/v3/blogPost/attachments/update")
    suspend fun updateNewAttachments(): Response<kotlin.Any>

}
