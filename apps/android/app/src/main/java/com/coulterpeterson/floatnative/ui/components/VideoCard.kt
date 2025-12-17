package com.coulterpeterson.floatnative.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3

@Composable
fun VideoCard(
    post: BlogPostModelV3,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
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
                    contentScale = ContentScale.Crop
                )
            }

            // Metadata
            Row(modifier = Modifier.padding(12.dp)) {
                // Avatar
                // iOS shows channel icon if available, else creator icon.
                // Both channel and creator are non-nullable in BlogPostModelV3, but we use safe calls just in case.
                
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

                Column {
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
            }
        }
    }
}
