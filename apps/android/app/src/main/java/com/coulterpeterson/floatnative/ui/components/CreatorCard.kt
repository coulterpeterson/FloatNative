package com.coulterpeterson.floatnative.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.coulterpeterson.floatnative.openapi.models.CreatorModelV3

@Composable
fun CreatorCard(
    creator: CreatorModelV3,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column {
            // Use cover image or icon. CreatorModelV3 usually has an icon or cardImage.
            // Let's inspect CreatorModelV3 in next step if needed, but assuming icon/card info is available.
            // For now, attempting to use `icon` which is common.
            val imageUrl = creator.icon?.path // Check model for precise field
            
            if (imageUrl != null) {
                 AsyncImage(
                    model = imageUrl,
                    contentDescription = creator.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f), // Square for grid
                    contentScale = ContentScale.Crop
                )
            }
            
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = creator.title ?: "Unknown Creator",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = creator.description ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
