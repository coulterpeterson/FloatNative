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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.coulterpeterson.floatnative.openapi.models.CommentModel
import com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
    views: Int? = null // Views not readily available in Post model?
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
                    textView.text = HtmlCompat.fromHtml(descriptionHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
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
    totalComments: Int
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
                CommentItem(comment = comment, depth = 0)
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun CommentItem(
    comment: CommentModel,
    depth: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp, top = 8.dp, bottom = 8.dp, end = 16.dp)
            .padding(start = if(depth > 0) 8.dp else 16.dp) // Base padding
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // Profile Pic Placeholder
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(32.dp)
            ) {
                 Box(contentAlignment = Alignment.Center) {
                     Text(
                         text = comment.user.username.firstOrNull()?.toString()?.uppercase() ?: "?",
                         style = MaterialTheme.typography.labelMedium
                     )
                 }
            }
            
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
                
                Text(
                    text = comment.text,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Interaction Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ThumbUp,
                        contentDescription = "Like",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${comment.likes}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Text(
                        text = "Reply",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    // Recursive Replies
    comment.replies?.forEach { reply ->
        CommentItem(comment = reply, depth = depth + 1)
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
