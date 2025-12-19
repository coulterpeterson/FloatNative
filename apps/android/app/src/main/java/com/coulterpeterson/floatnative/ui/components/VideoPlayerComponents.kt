package com.coulterpeterson.floatnative.ui.components

import android.widget.TextView
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import android.text.style.URLSpan
import android.text.style.ClickableSpan
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.util.Log
import com.coulterpeterson.floatnative.utils.TimestampParser
import com.coulterpeterson.floatnative.openapi.models.CommentModel
import com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape

@Composable
fun VideoActionButtons(
    likes: Int,
    dislikes: Int,
    userInteraction: ContentPostV3Response.UserInteraction?,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onQualityClick: () -> Unit,
    qualityLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Like Button
        ActionButton(
            icon = if (userInteraction == ContentPostV3Response.UserInteraction.like) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
            label = "$likes",
            isActive = userInteraction == ContentPostV3Response.UserInteraction.like,
            onClick = onLikeClick
        )

        // Dislike Button
        ActionButton(
            icon = if (userInteraction == ContentPostV3Response.UserInteraction.dislike) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
            label = "$dislikes",
            isActive = userInteraction == ContentPostV3Response.UserInteraction.dislike,
            onClick = onDislikeClick
        )

        // Download Button
        ActionButton(
            icon = Icons.Default.Download,
            label = "Download",
            onClick = onDownloadClick
        )

        // Quality Button (Replaces Share)
        ActionButton(
            icon = Icons.Default.Settings,
            label = qualityLabel,
            onClick = onQualityClick
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun VideoDescription(
    title: String,
    descriptionHtml: String,
    releaseDate: java.time.OffsetDateTime,
    views: Int? = null,
    onSeek: (Long) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .animateContentSize()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        val dateStr = releaseDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        Text(
            text = "Published on $dateStr", // Add views here if available
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isExpanded) {
            AndroidView(
                factory = { context ->
                    TextView(context).apply {
                        setTextColor(android.graphics.Color.WHITE) // TODO: Use theme color
                        setLinkTextColor(android.graphics.Color.CYAN)
                        // Make links clickable
                        movementMethod = android.text.method.LinkMovementMethod.getInstance()
                    }
                },
                update = { textView ->
                    textView.text = processDescription(descriptionHtml, onSeek)
                }
            )
            
            TextButton(onClick = { isExpanded = false }) {
                Text("Show less")
            }
        } else {
             // Collapsed view: Show plain text preview
             // Simple strip HTML for preview or just show first few lines
             val plainText = HtmlCompat.fromHtml(descriptionHtml, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
             
             Text(
                 text = plainText,
                 style = MaterialTheme.typography.bodyMedium,
                 maxLines = 2,
                 overflow = TextOverflow.Ellipsis
             )
             
             TextButton(onClick = { isExpanded = true }) {
                Text("more")
            }
        }
    }
}

@Composable
fun CommentSection(
    comments: List<CommentModel>,
    isLoading: Boolean,
    totalComments: Int,
    onLikeComment: (String) -> Unit,
    onDislikeComment: (String) -> Unit,
    onReplyComment: (CommentModel) -> Unit,
    onSeek: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Comments ($totalComments)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // We can't use LazyColumn inside a ScrollView (which the main layout likely checks).
            // So we use Column and standard recursion. 
            // BUT for performance with deep trees, we might want to flatten it or be careful.
            // Given "Comments View Hierarchy", let's do correct recursion.
            
            comments.forEach { comment ->
                CommentItem(
                    comment = comment, 
                    depth = 0,
                    onLike = onLikeComment,
                    onDislike = onDislikeComment,
                    onReply = onReplyComment,
                    onSeek = onSeek
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun CommentItem(
    comment: CommentModel,
    depth: Int,
    onLike: (String) -> Unit,
    onDislike: (String) -> Unit,
    onReply: (CommentModel) -> Unit,
    onSeek: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 32).dp, top = 8.dp, bottom = 8.dp, end = 16.dp)
            .padding(start = 16.dp) // Consistent base padding
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // Profile Pic
            AsyncImage(
                model = comment.user.profileImage?.path?.toString() ?: "",
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.user.username,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${timeAgo(comment.postDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                
                val annotatedText = remember(comment.text) {
                     buildTimestampAnnotatedString(comment.text)
                }
                
                ClickableText(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    onClick = { offset ->
                        annotatedText.getStringAnnotations(tag = "TIMESTAMP", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                 val timeMillis = annotation.item.toLongOrNull() ?: 0L
                                 onSeek(timeMillis)
                            }
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Interaction Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Like Button
                    val isLiked = comment.userInteraction?.contains(CommentModel.UserInteraction.like) == true
                    Row(
                        modifier = Modifier
                            .clickable { onLike(comment.id) }
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Like",
                            modifier = Modifier.size(16.dp),
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${comment.likes}",
                            style = MaterialTheme.typography.labelSmall,
                             color = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))

                    // Dislike Button
                    val isDisliked = comment.userInteraction?.contains(CommentModel.UserInteraction.dislike) == true
                    Row(
                        modifier = Modifier
                            .clickable { onDislike(comment.id) }
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                             imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Dislike",
                            modifier = Modifier.size(16.dp),
                             tint = if (isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${comment.dislikes}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Text(
                        text = "Reply",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { onReply(comment) }
                            .padding(4.dp)
                    )
                }
            }
        }
    }
    
    // Recursive Replies
    comment.replies?.forEach { reply ->
        CommentItem(
            comment = reply, 
            depth = depth + 1,
            onLike = onLike,
            onDislike = onDislike,
            onReply = onReply,
            onSeek = onSeek
        )
    }
}

// Simple Time Ago helper
fun timeAgo(date: java.time.OffsetDateTime): String {
    val now = java.time.OffsetDateTime.now()
    val diff = java.time.Duration.between(date, now)
    
    return when {
        diff.toDays() > 365 -> "${diff.toDays() / 365}y"
        diff.toDays() > 30 -> "${diff.toDays() / 30}mo"
        diff.toDays() > 0 -> "${diff.toDays()}d"
        diff.toHours() > 0 -> "${diff.toHours()}h"
        diff.toMinutes() > 0 -> "${diff.toMinutes()}m"
        else -> "now"
    }
}

// Helper to process HTML description: truncate links and add clickable timestamps
private fun processDescription(html: String, onSeek: (Long) -> Unit): CharSequence {
    // 1. Initial HTML Parse
    val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
    val builder = SpannableStringBuilder(spanned)

    // 2. Truncate Links
    val urlSpans = builder.getSpans(0, builder.length, URLSpan::class.java)
    // Process in reverse to maintain indices
    for (span in urlSpans.reversed()) {
        val start = builder.getSpanStart(span)
        val end = builder.getSpanEnd(span)
        val url = span.url
        
        // Logic: strip protocol, strip www, truncate at 40
        var displayText = url
        displayText = displayText.replace("http://", "").replace("https://", "")
        if (displayText.startsWith("www.")) {
            displayText = displayText.substring(4)
        }
        
        // Truncate
        if (displayText.length > 40) {
            displayText = displayText.take(40) + "..."
        }
        
        // Replace text
        builder.replace(start, end, displayText)
        
        // Re-apply span to new range
        builder.removeSpan(span)
        builder.setSpan(URLSpan(url), start, start + displayText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    
    // 3. Linkify Timestamps
    val text = builder.toString()
    val matches = TimestampParser.parseTimestamps(text)
    
    for (match in matches) {
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: android.view.View) {
                onSeek(match.timeMillis)
            }
            
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = true
                ds.color = android.graphics.Color.CYAN // Or theme color?
            }
        }
        
        // Check if range is valid in builder (in case modifications shifted things)
        if (match.end <= builder.length) {
             builder.setSpan(clickableSpan, match.start, match.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    return builder
}

// Helper to build AnnotatedString for Comments with clickable timestamps
private fun buildTimestampAnnotatedString(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        append(text)
        
        val matches = TimestampParser.parseTimestamps(text)
        for (match in matches) {
            addStyle(
                style = SpanStyle(
                    color = Color.Cyan, // Or Primary
                    textDecoration = TextDecoration.Underline
                ),
                start = match.start,
                end = match.end
            )
            
            addStringAnnotation(
                tag = "TIMESTAMP",
                annotation = match.timeMillis.toString(),
                start = match.start,
                end = match.end
            )
        }
    }
}
