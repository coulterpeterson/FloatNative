package com.coulterpeterson.floatnative.ui.components.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

import androidx.tv.material3.ExperimentalTvMaterial3Api

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSearchKeyboard(
    onCharClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onSpaceClick: () -> Unit,
    onClearClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val row1 = listOf('A', 'B', 'C', 'D', 'E', 'F')
    val row2 = listOf('G', 'H', 'I', 'J', 'K', 'L')
    val row3 = listOf('M', 'N', 'O', 'P', 'Q', 'R')
    val row4 = listOf('S', 'T', 'U', 'V', 'W', 'X')
    val row5 = listOf('Y', 'Z', '0', '1', '2', '3')
    val row6 = listOf('4', '5', '6', '7', '8', '9')

    Column(
        modifier = modifier.padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val spacing = 6.dp
        
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
           row1.forEach { CharKey(it, onCharClick) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
           row2.forEach { CharKey(it, onCharClick) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
           row3.forEach { CharKey(it, onCharClick) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
           row4.forEach { CharKey(it, onCharClick) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
             row5.forEach { CharKey(it, onCharClick) }
        }
         Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
             row6.forEach { CharKey(it, onCharClick) }
        }
        
        // Action Row
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
             ActionKey(label = "SPACE", onClick = onSpaceClick, width = 60.dp)
             ActionKey(label = "DEL", onClick = onBackspaceClick, width = 45.dp)
             ActionKey(label = "CLR", onClick = onClearClick, width = 45.dp)
             ActionKey(label = "SEARCH", onClick = onSearchClick, width = 60.dp)
        }
    }
}

@Composable
private fun CharKey(
    char: Char,
    onClick: (Char) -> Unit
) {
    KeyButton(
        text = char.toString(),
        onClick = { onClick(char) }
    )
}

@Composable
private fun ActionKey(
    label: String,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp = 32.dp
) {
     KeyButton(
        text = label,
        onClick = onClick,
        width = width,
        isAction = true
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun KeyButton(
    text: String,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp = 32.dp,
    height: androidx.compose.ui.unit.Dp = 32.dp,
    isAction: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val backgroundColor = when {
        isFocused -> Color.White
        else -> Color(0xFF333333) // Dark Grey
    }
    
    val textColor = when {
        isFocused -> Color.Black
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(50)) // Circular/Pill
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null // Custom indication handled by color change
            ) { onClick() }
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium, // TV Material
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}
