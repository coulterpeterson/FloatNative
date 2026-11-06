package com.coulterpeterson.floatnative.utils

import android.text.format.DateUtils
import com.coulterpeterson.floatnative.openapi.models.PostMetadataModel

/**
 * On a multi-video post (e.g. `C3GeAE0LmM`, videoCount=3) `videoDuration` is
 * the SUM of every video attachment, which made the card look like a 24-minute
 * video when the primary clip is actually 22 minutes (GH #29). Floatplane
 * returns `displayDuration` for that primary clip; prefer it, fall back to
 * videoDuration on legacy responses that don't carry the new field.
 */
val PostMetadataModel.preferredDisplayDuration: Long
    get() = displayDuration?.toLong() ?: videoDuration.toLong()

/**
 * Tail label appended to the duration on cards for posts that bundle extra
 * video parts (GH #23, #29). `videoCount=3` becomes `" +2"`. Empty string
 * for normal single-video posts.
 */
val PostMetadataModel.additionalPartsSuffix: String
    get() = videoCount?.takeIf { it > 1 }?.let { " +${it - 1}" } ?: ""

/**
 * Video attachments in the author-intended order (GH #23). The raw
 * `videoAttachments` array is unordered relative to `attachmentOrder` —
 * confirmed against fixture get_api_v3_content_post_id_C3GeAE0LmM.json
 * captured 2026-05-19 — so feeding the raw array into a picker would
 * mis-sequence multi-part posts. Falls back to insertion order when
 * attachmentOrder is empty, and appends anything we'd otherwise drop.
 */
fun com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response.orderedVideoAttachments(
    attachmentOrder: List<String>
): List<com.coulterpeterson.floatnative.openapi.models.VideoAttachmentModel> {
    val attachments = videoAttachments ?: return emptyList()
    if (attachmentOrder.isEmpty()) return attachments
    val byId = attachments.associateBy { it.id }
    val ordered = attachmentOrder.mapNotNull { byId[it] }
    val missing = attachments.filter { it.id !in attachmentOrder }
    return ordered + missing
}

object DateUtils {
    fun getRelativeTime(isoString: String): String {
        try {
            // Any ISO 8601 form. A parsed date prints without its seconds or
            // milliseconds when they're zero, as in 2023-10-27T10:00Z.
            val date = java.time.OffsetDateTime.parse(isoString)
            val now = System.currentTimeMillis()

            return DateUtils.getRelativeTimeSpanString(
                date.toInstant().toEpochMilli(),
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        } catch (e: Exception) {
            return ""
        }
    }

    fun formatDuration(seconds: Long): String {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format("%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%d:%02d", mins, secs)
        }
    }
}
