package com.coulterpeterson.floatnative.ui.screens.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.coulterpeterson.floatnative.openapi.models.CreatorModelV3
import com.coulterpeterson.floatnative.viewmodels.CreatorsState
import com.coulterpeterson.floatnative.viewmodels.CreatorsViewModel
import com.coulterpeterson.floatnative.viewmodels.HomeFeedViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvCreatorsScreen(
    onFilterSelected: (HomeFeedViewModel.FeedFilter) -> Unit,
    viewModel: CreatorsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val currentState = state) {
            is CreatorsState.Loading, CreatorsState.Initial -> {
                Text(
                    text = "Loading...",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            is CreatorsState.Error -> {
                Text(
                    text = currentState.message,
                    color = Color.Red,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is CreatorsState.Content -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 50.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(currentState.creators) { creator ->
                        CreatorItem(
                            creator = creator,
                            onFilterSelected = onFilterSelected
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CreatorItem(
    creator: CreatorModelV3,
    onFilterSelected: (HomeFeedViewModel.FeedFilter) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
    val context = androidx.compose.ui.platform.LocalContext.current

// ...

        // Creator Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            AsyncImage(
                model = creator.icon?.path.toString(), // Assuming icon has a path or url
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = creator.title ?: "Unknown Creator",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = "${creator.channels.size} channels",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF4CAF50) // Green color like in screenshot
                )
            }
             Spacer(modifier = Modifier.weight(1f))
        }

        // Actions List
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "View All Videos" Button
            CreatorActionButton(
                title = "View All Videos",
                icon = androidx.compose.material.icons.Icons.Default.GridView, 
                 onClick = {
                    onFilterSelected(
                        HomeFeedViewModel.FeedFilter.Creator(
                            id = creator.id,
                            displayTitle = creator.title ?: "Creator",
                            icon = creator.icon?.path.toString()
                        )
                    )
                }
            )

            // Channels
            creator.channels.forEach { channel ->
                CreatorActionButton(
                    title = channel.title ?: "Unknown Channel",
                    icon = null, // Or channel icon if available
                    imageUrl = channel.icon?.path.toString(),
                    onClick = {
                        onFilterSelected(
                            HomeFeedViewModel.FeedFilter.Channel(
                                id = channel.id,
                                creatorId = creator.id,
                                displayTitle = channel.title ?: "Channel",
                                icon = channel.icon?.path.toString()
                            )
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CreatorActionButton(
    title: String,
    icon: Any? = null, // ImageVector or null
    imageUrl: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF303030),
            focusedContentColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imageUrl != null) {
                 AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else if (icon is androidx.compose.ui.graphics.vector.ImageVector) {
                 Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFF2196F3) // Blue tint for generic actions
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.Gray
            )
        }
    }
}
