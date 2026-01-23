package com.coulterpeterson.floatnative.ui.components.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3
import com.coulterpeterson.floatnative.openapi.models.ImageModel
import com.coulterpeterson.floatnative.utils.DateUtils

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvVideoCard(
    post: BlogPostModelV3,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    progress: Float = 0f,
    isBookmarked: Boolean = false
) {
    TvVideoCardContent(
        title = post.title,
        thumbnail = post.thumbnail,
        channelTitle = post.channel.title,
        channelIcon = post.channel.icon,
        creatorIcon = post.creator.icon,
        duration = post.metadata.videoDuration.toLong(),
        releaseDate = post.releaseDate.toString(),
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        progress = progress,
        isBookmarked = isBookmarked
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvVideoCard(
    post: com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    progress: Float = 0f,
    isBookmarked: Boolean = false
) {
    TvVideoCardContent(
        title = post.title,
        thumbnail = post.thumbnail,
        channelTitle = post.channel.title,
        channelIcon = post.channel.icon,
        creatorIcon = post.creator.icon,
        duration = post.metadata.videoDuration.toLong(),
        releaseDate = post.releaseDate.toString(),
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        progress = progress,
        isBookmarked = isBookmarked
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvVideoCard(
    post: com.coulterpeterson.floatnative.api.WatchHistoryBlogPost,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    progress: Float = 0f,
    isBookmarked: Boolean = false
) {
    TvVideoCardContent(
        title = post.title,
        thumbnail = post.thumbnail,
        channelTitle = post.channel.title,
        channelIcon = post.channel.icon,
        creatorIcon = post.creator.icon,
        duration = post.metadata.videoDuration.toLong(),
        releaseDate = post.releaseDate,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        progress = progress,
        isBookmarked = isBookmarked
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvVideoCardContent(
    title: String,
    thumbnail: ImageModel?,
    channelTitle: String,
    channelIcon: ImageModel?,
    creatorIcon: ImageModel?,
    duration: Long,
    releaseDate: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    progress: Float = 0f,
    isBookmarked: Boolean = false
) {
    // Thumbnail Logic
    val thumbnailUrl = thumbnail?.path?.toString()
    val fullThumbnailUrl = if (thumbnailUrl?.startsWith("http") == true) thumbnailUrl else "https://pbs.floatplane.com${thumbnailUrl}"

    // Icon Logic
    fun getIconPath(imageModel: ImageModel?): String? {
        if (imageModel == null) return null
        return (imageModel.childImages?.firstOrNull()?.path ?: imageModel.path).toString()
    }
    val channelIconUrl = getIconPath(channelIcon)
    val creatorIconUrl = getIconPath(creatorIcon)
    val iconUrl = channelIconUrl ?: creatorIconUrl

    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent, // Let image define shape, text is below
            focusedContainerColor = Color.Transparent
        )
    ) {
        Column {
            // 1. Thumbnail Area with Duration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = fullThumbnailUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Duration Chip
                if (duration > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = DateUtils.formatDuration(duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    }
                }
                
                // Progress Bar
                if (progress > 0) {
                     androidx.compose.material3.LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color.Red,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }

                // Bookmark Icon
                if (isBookmarked) {
                    // Shadow layer
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 9.dp, end = 7.dp) // Slight offset
                            .size(28.dp)
                    )
                     androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.Bookmark,
                        contentDescription = "Saved to Playlist", // Foreground
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 2. Info Area
            Row(modifier = Modifier.fillMaxWidth()) {
                // Creator Icon
                if (iconUrl != null) {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                
                // Text Details
                Column {
                    Text(
                        text = title ?: "Untitled",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White, // Explicit White
                        maxLines = 2
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = channelTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray // High contrast grey
                        )
                    }
                    
                    // Release Date Line
                    val relativeTime = DateUtils.getRelativeTime(releaseDate)
                    
                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
