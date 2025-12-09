package com.coulterpeterson.floatnative.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.coulterpeterson.floatnative.ui.components.CreatorCard
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
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(currentState.creators) { creator ->
                        CreatorCard(
                            creator = creator,
                            onClick = { onCreatorClick(creator.id) },
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}
