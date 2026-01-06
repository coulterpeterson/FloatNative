package com.coulterpeterson.floatnative.utils

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    fun getRelativeTime(isoString: String): String {
        try {
            // ISO 8601 format: 2023-10-27T10:00:00.000Z
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(isoString) ?: return ""
            val now = System.currentTimeMillis()
            
            return DateUtils.getRelativeTimeSpanString(
                date.time,
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
