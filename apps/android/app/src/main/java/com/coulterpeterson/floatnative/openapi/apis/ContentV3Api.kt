package com.coulterpeterson.floatnative.openapi.apis

import com.coulterpeterson.floatnative.openapi.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3
import com.coulterpeterson.floatnative.openapi.models.ContentCreatorListLastItems
import com.coulterpeterson.floatnative.openapi.models.ContentCreatorListV3Response
import com.coulterpeterson.floatnative.openapi.models.ContentLikeV3Request
import com.coulterpeterson.floatnative.openapi.models.ContentPictureV3Response
import com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response
import com.coulterpeterson.floatnative.openapi.models.ContentVideoV3Response
import com.coulterpeterson.floatnative.openapi.models.ErrorModel
import com.coulterpeterson.floatnative.openapi.models.GetProgressRequest
import com.coulterpeterson.floatnative.openapi.models.GetProgressResponseInner
import com.coulterpeterson.floatnative.openapi.models.UpdateProgressRequest

interface ContentV3Api {
    /**
     * POST api/v3/content/dislike
     * Dislike Content
     * Toggles the dislike status on a piece of content. If liked before, it will turn into a dislike. If disliked before, the dislike will be removed.
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param contentLikeV3Request 
     * @return [kotlin.collections.List<kotlin.String>]
     */
    @POST("api/v3/content/dislike")
    suspend fun dislikeContent(@Body contentLikeV3Request: ContentLikeV3Request): Response<kotlin.collections.List<kotlin.String>>

    /**
     * GET api/v3/content/audio
     * Get Audio Content
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
    @GET("api/v3/content/audio")
    suspend fun getAudioContent(): Response<kotlin.Any>

    /**
     * GET api/v3/content/post
     * Get Blog Post
     * Retrieve more details on a specific blog post object for viewing.
     * Responses:
     *  - 200: OK - Detailed post information
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param id The ID of the post to be retrieved.
     * @return [ContentPostV3Response]
     */
    @GET("api/v3/content/post")
    suspend fun getBlogPost(@Query("id") id: kotlin.String): Response<ContentPostV3Response>

    /**
     * GET api/v3/content/info
     * Get Content
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
    @GET("api/v3/content/info")
    suspend fun getContent(): Response<kotlin.Any>

    /**
     * GET api/v3/content/tags
     * Get Content Tags
     * Retrieve all tags and the number of times the tags have been used for the specified creator(s).
     * Responses:
     *  - 200: OK - Creator tag information
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param creatorIds The creator(s) to search by.
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Int>]
     */
    @GET("api/v3/content/tags")
    suspend fun getContentTags(@Query("creatorIds") creatorIds: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>): Response<kotlin.collections.Map<kotlin.String, kotlin.Int>>


    /**
    * enum for parameter sort
    */
    enum class SortGetCreatorBlogPosts(val value: kotlin.String) {
        @Json(name = "ASC") ASC("ASC"),
        @Json(name = "DESC") DESC("DESC")
    }

    /**
     * GET api/v3/content/creator
     * Get Creator Blog Posts
     * Retrieve a paginated list of blog posts from a creator. Or search for blog posts from a creator.  Example query: https://www.floatplane.com/api/v3/content/creator?id&#x3D;59f94c0bdd241b70349eb72b&amp;fromDate&#x3D;2021-07-24T07:00:00.001Z&amp;toDate&#x3D;2022-07-27T06:59:59.099Z&amp;hasVideo&#x3D;true&amp;hasAudio&#x3D;true&amp;hasPicture&#x3D;false&amp;hasText&#x3D;false&amp;fromDuration&#x3D;1020&amp;toDuration&#x3D;9900&amp;sort&#x3D;DESC&amp;search&#x3D;thor&amp;tags[0]&#x3D;tjm
     * Responses:
     *  - 200: OK - Creator posted returned
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param id The GUID of the creator to retrieve posts from.
     * @param channel The id of a creator&#39;s specific channel from which to retrieve posts. (optional)
     * @param limit The maximum number of posts to return. (optional)
     * @param fetchAfter The number of posts to skip. Usually a multiple of &#x60;limit&#x60;, to get the next \&quot;page\&quot; of results. (optional)
     * @param search Search filter to look for specific posts. (optional)
     * @param tags An array of tags to search against, possibly in addition to &#x60;search&#x60;. (optional)
     * @param hasVideo If true, include blog posts with video attachments. (optional)
     * @param hasAudio If true, include blog posts with audio attachments. (optional)
     * @param hasPicture If true, include blog posts with picture attachments. (optional)
     * @param hasText If true, only include blog posts that are text-only. Text-only posts are ones without any attachments, such as video, audio, picture, and gallery.  This filter and &#x60;hasVideo&#x60;, &#x60;hasAudio&#x60;, and &#x60;hasPicture&#x60; should be mutually exclusive. That is, if &#x60;hasText&#x60; is true then the other three should all be false. Conversely, if any of the other three are true, then &#x60;hasText&#x60; should be false. Otherwise, the filter would produce no results. (optional)
     * @param sort &#x60;DESC&#x60; &#x3D; Newest First. &#x60;ASC&#x60; &#x3D; Oldest First. (optional)
     * @param fromDuration Include video posts where the duration of the video is at minimum &#x60;fromDuration&#x60; seconds long. Usually in multiples of 60 seconds. Implies &#x60;hasVideo&#x3D;true&#x60;. (optional)
     * @param toDuration Include video posts where the duration of the video is at maximum &#x60;toDuration&#x60; seconds long. Usually in multiples of 60 seconds. Implies &#x60;hasVideo&#x3D;true&#x60;. (optional)
     * @param fromDate Include posts where the publication date is on or after this filter date. (optional)
     * @param toDate Include posts where the publication date is on or before this filter date. (optional)
     * @return [kotlin.collections.List<BlogPostModelV3>]
     */
    @GET("api/v3/content/creator")
    suspend fun getCreatorBlogPosts(@Query("id") id: kotlin.String, @Query("channel") channel: kotlin.String? = null, @Query("limit") limit: kotlin.Int? = null, @Query("fetchAfter") fetchAfter: kotlin.Int? = null, @Query("search") search: kotlin.String? = null, @Query("tags") tags: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>? = null, @Query("hasVideo") hasVideo: kotlin.Boolean? = null, @Query("hasAudio") hasAudio: kotlin.Boolean? = null, @Query("hasPicture") hasPicture: kotlin.Boolean? = null, @Query("hasText") hasText: kotlin.Boolean? = null, @Query("sort") sort: SortGetCreatorBlogPosts? = null, @Query("fromDuration") fromDuration: kotlin.Int? = null, @Query("toDuration") toDuration: kotlin.Int? = null, @Query("fromDate") fromDate: java.time.OffsetDateTime? = null, @Query("toDate") toDate: java.time.OffsetDateTime? = null): Response<kotlin.collections.List<BlogPostModelV3>>

    /**
     * GET api/v3/content/creator/list
     * Get Multi Creator Blog Posts
     * Retrieve paginated blog posts from multiple creators for the home page.  Example query: https://www.floatplane.com/api/v3/content/creator/list?ids[0]&#x3D;59f94c0bdd241b70349eb72b&amp;limit&#x3D;20&amp;fetchAfter[0][creatorId]&#x3D;59f94c0bdd241b70349eb72b&amp;fetchAfter[0][blogPostId]&#x3D;B4WsyLnybS&amp;fetchAfter[0][moreFetchable]&#x3D;true
     * Responses:
     *  - 200: OK - Posts returned
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param ids The GUID(s) of the creator(s) to retrieve posts from.
     * @param limit The maximum number of posts to retrieve.
     * @param fetchAfter For pagination, this is used to determine which posts to skip. There should be one &#x60;fetchAfter&#x60; object for each creator in &#x60;ids&#x60;. The &#x60;moreFetchable&#x60; in the request, and all of the data, comes from the &#x60;ContentCreatorListV3Response&#x60;. (optional)
     * @return [ContentCreatorListV3Response]
     */
    @GET("api/v3/content/creator/list")
    suspend fun getMultiCreatorBlogPosts(@Query("ids") ids: @JvmSuppressWildcards kotlin.collections.List<kotlin.String>, @Query("limit") limit: kotlin.Int, @Query("fetchAfter") fetchAfter: @JvmSuppressWildcards kotlin.collections.List<ContentCreatorListLastItems>? = null): Response<ContentCreatorListV3Response>

    /**
     * GET api/v3/content/picture
     * Get Picture Content
     * Retrieve more information on a picture attachment from a blog post in order to consume the picture content.
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param id The ID of the picture attachment object, from the &#x60;BlogPostModelV3&#x60;.
     * @return [ContentPictureV3Response]
     */
    @GET("api/v3/content/picture")
    suspend fun getPictureContent(@Query("id") id: kotlin.String): Response<ContentPictureV3Response>

    /**
     * GET api/v3/content/picture/url
     * Get Picture Url
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
    @GET("api/v3/content/picture/url")
    suspend fun getPictureUrl(): Response<kotlin.Any>

    /**
     * POST api/v3/content/get/progress
     * Get Progress
     * Batch retrieval of watch progress values for blog posts. This API is useful for showing progress of a list of blog posts shown on the screen to the user. When retrieving a list of blog posts, the media attachments only include the identifier; when retrieving full details of a blog post, the attachments include more information, but still fail to return the progress of the media. Only when pulling the full video/audio content does the progress get included in the response. Thus, the recommended approach is to pull paginated results of blog posts first, as usual, and then to call this endpoint to retrieve progress values for each blog post to show in some capacity, usually on the thumbnail as a progress bar on the bottom.  Note that the progress values returned in this endpoint are different from the update progress endpoint and the values returned in video/audio attachments. While the latter are measured in seconds, this endpoint returns progress as a percentage of the media&#39;s total duration. It is presumed that the progress returned is from the first attachment in the blog post&#39;s &#x60;attachmentOrder&#x60; that is either a video or audio attachment. Because this returns progress as an integer percentage (0 to 100), it is not recommended to use this particular value for jumping to a timestamp in the media when resuming playback, as the rounded number may be off by plus/minus several seconds in actual playback. Use the actual attachment progress, measured in seconds, instead.
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param getProgressRequest 
     * @return [kotlin.collections.List<GetProgressResponseInner>]
     */
    @POST("api/v3/content/get/progress")
    suspend fun getProgress(@Body getProgressRequest: GetProgressRequest): Response<kotlin.collections.List<GetProgressResponseInner>>

    /**
     * GET api/v3/content/related
     * Get Related Blog Posts
     * Retrieve a list of blog posts that are related to the post being viewed.
     * Responses:
     *  - 200: OK - Related post details
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param id The ID of the originating post.
     * @return [kotlin.collections.List<BlogPostModelV3>]
     */
    @GET("api/v3/content/related")
    suspend fun getRelatedBlogPosts(@Query("id") id: kotlin.String): Response<kotlin.collections.List<BlogPostModelV3>>

    /**
     * GET api/v3/content/video
     * Get Video Content
     * Retrieve more information on a video attachment from a blog post in order to consume the video content.
     * Responses:
     *  - 200: OK - Video details returned
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param id The ID of the video attachment object, from the &#x60;BlogPostModelV3&#x60;.
     * @return [ContentVideoV3Response]
     */
    @GET("api/v3/content/video")
    suspend fun getVideoContent(@Query("id") id: kotlin.String): Response<ContentVideoV3Response>

    /**
     * POST api/v3/content/like
     * Like Content
     * Toggles the like status on a piece of content. If disliked before, it will turn into a like. If liked before, the like will be removed.
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param contentLikeV3Request 
     * @return [kotlin.collections.List<kotlin.String>]
     */
    @POST("api/v3/content/like")
    suspend fun likeContent(@Body contentLikeV3Request: ContentLikeV3Request): Response<kotlin.collections.List<kotlin.String>>

    /**
     * POST api/v3/content/progress
     * Update Progress
     * Update the watch progress on a piece of media (usually video or audio), stored as the number of seconds in the media.
     * Responses:
     *  - 200: OK
     *  - 400: Bad Request - The request has errors and the server did not process it.
     *  - 401: Unauthenticated - The request was not authenticated to make the request.
     *  - 403: Forbidden - The request was not authenticated to make the request.
     *  - 404: Not Found - The resource was not found.
     *  - 429: Too Many Requests - The resource was requested too many times
     *  - 0: Unexpected response code
     *
     * @param updateProgressRequest 
     * @return [kotlin.String]
     */
    @POST("api/v3/content/progress")
    suspend fun updateProgress(@Body updateProgressRequest: UpdateProgressRequest): Response<kotlin.String>

}
