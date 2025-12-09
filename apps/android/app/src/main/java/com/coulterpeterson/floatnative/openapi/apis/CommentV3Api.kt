package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.CommentLikeV3PostRequest
import com.coulterpeterson.floatnative.openapi.models.CommentModel
import com.coulterpeterson.floatnative.openapi.models.CommentV3PostRequest
import com.coulterpeterson.floatnative.openapi.models.CommentV3PostResponse
import com.coulterpeterson.floatnative.openapi.models.ErrorModel

interface CommentV3Api {
    /**
     * POST api/v3/comment/delete
     * Delete Comment
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
    @POST("api/v3/comment/delete")
    suspend fun deleteComment(): Response<kotlin.Any>

    /**
     * POST api/v3/comment/dislike
     * Dislike Comment
     * Dislike a comment on a blog post.
     * Responses:
     *  - 200: OK - Comment successfully disliked
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param commentLikeV3PostRequest 
     * @return [kotlin.String]
     */
    @POST("api/v3/comment/dislike")
    suspend fun dislikeComment(@Body commentLikeV3PostRequest: CommentLikeV3PostRequest): Response<kotlin.String>

    /**
     * POST api/v3/comment/edit
     * Edit Comment
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
    @POST("api/v3/comment/edit")
    suspend fun editComment(): Response<kotlin.Any>

    /**
     * POST api/v3/comment/history
     * Get Comment History
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
    @POST("api/v3/comment/history")
    suspend fun getCommentHistory(): Response<kotlin.Any>

    /**
     * GET api/v3/comment/replies
     * Get Comment Replies
     * Retrieve more replies from a comment.
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param comment The identifer of the comment from which to retrieve replies.
     * @param blogPost The identifer of the blog post the &#x60;comment&#x60; belongs to.
     * @param limit How many replies to retrieve.
     * @param rid The identifer of the last reply in the reply chain.
     * @return [kotlin.collections.List<CommentModel>]
     */
    @GET("api/v3/comment/replies")
    suspend fun getCommentReplies(@Query("comment") comment: kotlin.String, @Query("blogPost") blogPost: kotlin.String, @Query("limit") limit: kotlin.Int, @Query("rid") rid: kotlin.String): Response<kotlin.collections.List<CommentModel>>

    /**
     * GET api/v3/comment
     * Get Comments
     * Get comments for a blog post object. Note that replies to each comment tend to be limited to 3. The extra replies can be retrieved via &#x60;getCommentReplies&#x60;. The difference in &#x60;$response.body#/0/totalReplies&#x60; and &#x60;$response.body#/0/replies&#x60;&#39;s length can determine if more comments need to be loaded.
     * Responses:
     *  - 200: OK - All comments returned for the query parameters
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param blogPost Which blog post to retrieve comments for.
     * @param limit The maximum number of comments to return. This should be set to 20 by default.
     * @param fetchAfter When loading more comments on a blog post, this is used to determine which which comments to skip. This is a GUID of the last comment from the previous call to &#x60;getComments&#x60;. (optional)
     * @return [kotlin.collections.List<CommentModel>]
     */
    @GET("api/v3/comment")
    suspend fun getComments(@Query("blogPost") blogPost: kotlin.String, @Query("limit") limit: kotlin.Int, @Query("fetchAfter") fetchAfter: kotlin.String? = null): Response<kotlin.collections.List<CommentModel>>

    /**
     * POST api/v3/comment/like
     * Like Comment
     * Like a comment on a blog post.
     * Responses:
     *  - 200: OK - Comment successfully liked
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param commentLikeV3PostRequest 
     * @return [kotlin.String]
     */
    @POST("api/v3/comment/like")
    suspend fun likeComment(@Body commentLikeV3PostRequest: CommentLikeV3PostRequest): Response<kotlin.String>

    /**
     * POST api/v3/comment/pin
     * Pin Comment
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
    @POST("api/v3/comment/pin")
    suspend fun pinComment(): Response<kotlin.Any>

    /**
     * POST api/v3/comment
     * Post Comment
     * Post a new comment to a blog post object.
     * Responses:
     *  - 200: OK - Commented posted successfully, returning comment details
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param commentV3PostRequest 
     * @return [CommentV3PostResponse]
     */
    @POST("api/v3/comment")
    suspend fun postComment(@Body commentV3PostRequest: CommentV3PostRequest): Response<CommentV3PostResponse>

}
