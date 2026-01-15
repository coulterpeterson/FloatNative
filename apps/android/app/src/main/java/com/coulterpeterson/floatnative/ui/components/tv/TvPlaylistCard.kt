package com.coulterpeterson.floatnative.ui.components.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.coulterpeterson.floatnative.api.Playlist
import androidx.compose.foundation.background

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPlaylistCard(
    playlist: Playlist,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fullThumbnailUrl = if (thumbnailUrl?.startsWith("http") == true) thumbnailUrl else "https://pbs.floatplane.com${thumbnailUrl}"

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        )
    ) {
        Column {
            // Thumbnail Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (thumbnailUrl == null) Color.DarkGray else Color.Transparent)
            ) {
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = fullThumbnailUrl,
                        contentDescription = playlist.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Placeholder when no thumbnail (empty playlist)
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                         Text(
                             text = "No Videos",
                             style = MaterialTheme.typography.bodySmall,
                             color = Color.White
                         )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Info Area
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "${playlist.videoIds.size} videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}
