package com.coulterpeterson.floatnative.openapi.models

import com.squareup.moshi.Json

/**
 * Request body for posting a reply to a comment.
 * @param blogPost The GUID of the blogPost the comment should be posted to.
 * @param text The text of the comment being posted.
 * @param replyTo The GUID of the comment being replied to.
 */
data class CommentV3ReplyRequest (
    @Json(name = "blogPost")
    val blogPost: kotlin.String,

    @Json(name = "text")
    val text: kotlin.String,

    @Json(name = "replyTo")
    val replyTo: kotlin.String
)
