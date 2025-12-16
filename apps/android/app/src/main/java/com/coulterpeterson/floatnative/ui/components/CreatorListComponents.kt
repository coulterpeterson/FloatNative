package com.coulterpeterson.floatnative.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.coulterpeterson.floatnative.openapi.models.ChannelModel
import com.coulterpeterson.floatnative.openapi.models.CreatorModelV3

@Composable
fun CreatorListItem(
    creator: CreatorModelV3,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imageUrl = creator.icon.path
        
        AsyncImage(
            model = imageUrl.toString(),
            contentDescription = creator.title,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = creator.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ChannelListItem(
    channel: ChannelModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 72.dp, top = 12.dp, bottom = 12.dp, end = 16.dp), // Indented
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imageUrl = channel.icon.path

        AsyncImage(
            model = imageUrl.toString(),
            contentDescription = channel.title,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = channel.title,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
