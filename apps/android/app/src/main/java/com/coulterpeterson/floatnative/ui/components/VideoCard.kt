package com.coulterpeterson.floatnative.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                    contentScale = ContentScale.Crop
                )
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
