package com.coulterpeterson.floatnative.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
fun VideoCard(
    post: BlogPostModelV3,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    progress: Float = 0f,
    menuItems: (@Composable ColumnScope.(onDismiss: () -> Unit) -> Unit)? = null
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp), // Check design system spacing
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                AsyncImage(
                    model = post.thumbnail?.path?.toString(),
                    contentDescription = post.title,
                    modifier = Modifier.fillMaxSize(),
                )
                
                // Duration Badge
                if (post.metadata.videoDuration.toLong() > 0) {
                    Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = formatDuration(post.metadata.videoDuration.toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // Progress Bar
                if (progress > 0f) {
                     Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(androidx.compose.ui.Alignment.BottomStart)
                            .padding(bottom = 0.dp) // Anchored to bottom
                     ) {
                         // Background
                         Box(
                             modifier = Modifier
                                 .fillMaxSize()
                                 .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f))
                         )
                         // Progress
                         Box(
                             modifier = Modifier
                                 .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                                 .fillMaxHeight()
                                 .background(androidx.compose.ui.graphics.Color.Red)
                         )
                     }
                }
            }

            // Metadata
            Row(modifier = Modifier.padding(12.dp)) {
                // Avatar
                // Helper to get icon from ImageModel
                fun getIconPath(imageModel: com.coulterpeterson.floatnative.openapi.models.ImageModel?): String? {
                    if (imageModel == null) return null
                    return (imageModel.childImages?.firstOrNull()?.path ?: imageModel.path).toString()
                }

                val channelIcon = getIconPath(post.channel.icon)
                val creatorIcon = getIconPath(post.creator.icon)
                
                val iconUrl = channelIcon ?: creatorIcon
                
                AsyncImage(
                    model = iconUrl?.toString(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        minLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val creatorName = post.channel?.title ?: post.creator?.title ?: ""
                    val date = post.releaseDate?.let { com.coulterpeterson.floatnative.utils.DateUtils.getRelativeTime(it.toString()) } ?: ""
                    
                    Text(
                        text = "$creatorName • $date",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                
                if (menuItems != null) {
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { expanded = true }) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.MoreVert,
                                contentDescription = "More"
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            menuItems(this) { expanded = false }
                        }
                    }
                }
            }
        }
    }
}

internal fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format("%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%d:%02d", mins, secs)
    }
}
