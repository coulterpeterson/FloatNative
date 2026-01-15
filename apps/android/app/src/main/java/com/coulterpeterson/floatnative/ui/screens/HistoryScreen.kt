package com.coulterpeterson.floatnative.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.coulterpeterson.floatnative.api.WatchHistoryResponse
import com.coulterpeterson.floatnative.viewmodels.HistoryState
import com.coulterpeterson.floatnative.viewmodels.HistoryViewModel
import com.coulterpeterson.floatnative.openapi.models.ImageModel
import androidx.compose.ui.graphics.Color
import com.coulterpeterson.floatnative.utils.DateUtils

@Composable
fun HistoryScreen(
    onVideoClick: (String) -> Unit = {},
    viewModel: HistoryViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val currentState = state) {
            is HistoryState.Loading, HistoryState.Initial -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is HistoryState.Error -> {
                Text(
                    text = currentState.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is HistoryState.Content -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                   currentState.groupedHistory.forEach { (dateHeader, items) ->
                       item {
                           Text(
                               text = dateHeader,
                               style = MaterialTheme.typography.titleMedium,
                               fontWeight = FontWeight.Bold,
                               color = MaterialTheme.colorScheme.primary,
                               modifier = Modifier.padding(vertical = 8.dp)
                           )
                       }
                       items(items) { historyItem ->
                           HistoryItemCard(item = historyItem, onClick = { 
                               // History item ID is contentId, but we might need postId (blogPost.id)
                               onVideoClick(historyItem.blogPost.id) 
                           })
                       }
                   }
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(item: WatchHistoryResponse, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Thumbnail
        // Thumbnail with Progress Bar
        Box(modifier = Modifier
            .width(160.dp)
            .aspectRatio(16f/9f)
        ) {
            AsyncImage(
                model = item.blogPost.thumbnail?.path?.toString(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            
            // Progress Bar
            if (item.progress > 0) {
                 LinearProgressIndicator(
                    progress = item.progress / 100f,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Color.Red,
                    trackColor = Color.Transparent
                )
            }
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.blogPost.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.blogPost.channel.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Text(
                text = DateUtils.getRelativeTime(item.blogPost.releaseDate),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
    }
}
