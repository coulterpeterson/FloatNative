package com.coulterpeterson.floatnative.ui.components.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.coulterpeterson.floatnative.openapi.models.BlogPostModelV3

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvVideoCard(
    post: BlogPostModelV3,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Note: FloatplaneAPI puts absolute URL in path usually if coming from V3? 
    // Actually V3 usually returns relative path for attachments.
    // VideoCard.kt (mobile) likely constructs it.
    // Assuming simple concatenation for now:
    val thumbnailUrl = post.thumbnail?.path?.toString() // Cast to String
    val fullThumbnailUrl = if (thumbnailUrl?.startsWith("http") == true) thumbnailUrl else "https://pbs.floatplane.com${thumbnailUrl}"

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            AsyncImage(
                model = fullThumbnailUrl,
                contentDescription = post.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop
            )
            
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = post.title ?: "Untitled",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = post.creator?.title ?: "Unknown Creator",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
