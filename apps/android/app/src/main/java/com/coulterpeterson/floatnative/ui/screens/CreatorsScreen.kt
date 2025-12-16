package com.coulterpeterson.floatnative.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coulterpeterson.floatnative.ui.components.ChannelListItem
import com.coulterpeterson.floatnative.ui.components.CreatorListItem
import com.coulterpeterson.floatnative.viewmodels.CreatorsState
import com.coulterpeterson.floatnative.viewmodels.CreatorsViewModel

@Composable
fun CreatorsScreen(
    onCreatorClick: (String) -> Unit = {},
    viewModel: CreatorsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val currentState = state) {
            is CreatorsState.Loading, CreatorsState.Initial -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(currentState.creators) { creator ->
                        CreatorListItem(
                            creator = creator,
                            onClick = { onCreatorClick(creator.id) }
                        )

                        creator.channels.forEach { channel ->
                             ChannelListItem(
                                 channel = channel,
                                 onClick = { /* TODO: Navigate to channel? Or filter feed? */ },
                             )
                        }
                    }
                }
            }
        }
    }
}
