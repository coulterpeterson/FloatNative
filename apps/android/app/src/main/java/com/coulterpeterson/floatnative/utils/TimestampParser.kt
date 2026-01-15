package com.coulterpeterson.floatnative.utils

import java.util.regex.Pattern

object TimestampParser {
    // Matches:
    // 1:20 (M:SS)
    // 01:20 (MM:SS)
    // 1:20:30 (H:MM:SS)
    // 12:20:30 (HH:MM:SS)
    // Word boundaries to avoid matching inside other numbers if possible, 
    // but typically timestamps are standalone or surrounded by spaces/parens.
    // simpler regex: \b(\d{1,2}:)?\d{1,2}:\d{2}\b
    private val TIMESTAMP_PATTERN = Pattern.compile("\\b(\\d{1,2}:)?\\d{1,2}:\\d{2}\\b")

    fun parseTimestamps(text: String): List<TimestampMatch> {
        val matcher = TIMESTAMP_PATTERN.matcher(text)
        val matches = mutableListOf<TimestampMatch>()

        while (matcher.find()) {
            val timeString = matcher.group()
            val milliseconds = parseTimeStringToMillis(timeString)
            if (milliseconds >= 0) {
                matches.add(TimestampMatch(matcher.start(), matcher.end(), timeString, milliseconds))
            }
        }
        return matches
    }

    fun parseTimeStringToMillis(timeString: String): Long {
        val parts = timeString.split(":").map { it.toLongOrNull() ?: 0L }
        return when (parts.size) {
            2 -> { // MM:SS
                (parts[0] * 60 + parts[1]) * 1000
            }
            3 -> { // HH:MM:SS
                (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
            }
            else -> -1L
        }
    }
}

data class TimestampMatch(
    val start: Int,
    val end: Int,
    val text: String,
    val timeMillis: Long
)
