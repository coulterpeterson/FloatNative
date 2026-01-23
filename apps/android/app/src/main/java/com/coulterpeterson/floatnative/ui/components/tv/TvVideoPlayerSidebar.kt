@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.coulterpeterson.floatnative.ui.components.tv

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.URLSpan
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.coulterpeterson.floatnative.openapi.models.CommentModel
import com.coulterpeterson.floatnative.openapi.models.ContentPostV3Response
import com.coulterpeterson.floatnative.ui.components.timeAgo
import com.coulterpeterson.floatnative.viewmodels.PlayerSidebarMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp

@androidx.annotation.OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun TvVideoPlayerSidebar(
    mode: PlayerSidebarMode,
    descriptionHtml: String,
    title: String,
    publishDate: java.time.OffsetDateTime?,
    comments: List<CommentModel>,
    onDismiss: () -> Unit,
    onSeek: (Long) -> Unit
) {
    if (mode == PlayerSidebarMode.None) return

    val listState = rememberTvLazyListState()
    val focusRequester = remember { FocusRequester() }

    // Trap focus in sidebar or handle back
    BackHandler {
        onDismiss()
    }

    LaunchedEffect(mode) {
        if (mode != PlayerSidebarMode.None) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(400.dp) // Fixed width sidebar
            .background(Color(0xFF1F1F1F))
            .padding(16.dp)
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
            },
            label = "SidebarModeTransition"
        ) { currentMode ->
            when (currentMode) {
                PlayerSidebarMode.Description -> {
                    TvLazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        item {
                            if (publishDate != null) {
                                Text(
                                    text = "Published on ${publishDate.toLocalDate()}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.LightGray
                                )
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Split description logic
                        val paragraphs = processDescriptionForTv(descriptionHtml)
                        items(paragraphs) { paragraph ->
                            // We use AndroidView for link/HTML rendering inside each paragraph
                            // Note: Focus handling on individual links inside TextView on TV is tricky.
                            // For this pass, we make the row focusable to scroll, but links might not be clickable via D-pad
                            // without complex movement method logic.
                            // Given the prompt asks for "scrolling ... using up/down arrows", breaking by paragraph satisfies this.
                            
                            val textColor = Color.White
                            val linkColor = Color(0xFF8AB4F8)
                            
                            val androidTextColor = android.graphics.Color.argb(
                                (textColor.alpha * 255).toInt(),
                                (textColor.red * 255).toInt(),
                                (textColor.green * 255).toInt(),
                                (textColor.blue * 255).toInt()
                            )
                            val androidLinkColor = android.graphics.Color.argb(
                                (linkColor.alpha * 255).toInt(),
                                (linkColor.red * 255).toInt(),
                                (linkColor.green * 255).toInt(),
                                (linkColor.blue * 255).toInt()
                            )

                            // Each paragraph is a focusable surface to allow "scrolling" by moving focus
                            Surface(
                                onClick = {}, // No-op, just for focus
                                shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                )
                            ) {
                                AndroidView(
                                    factory = { context ->
                                        TextView(context).apply {
                                            textSize = 14f // Approximate bodyMedium
                                            setLineSpacing(0f, 1.2f)
                                        }
                                    },
                                    update = { textView ->
                                        textView.setTextColor(androidTextColor)
                                        textView.setLinkTextColor(androidLinkColor)
                                        // We do NOT set movementMethod to LinkMovementMethod instance here
                                        // because that steals focus/touch events.
                                        // On TV, if we want to click links, we'd need D-pad navigation *inside* the text view.
                                        // For now, we prioritize scrolling the text.
                                        textView.text = processDescriptionText(paragraph, onSeek)
                                    },
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
                PlayerSidebarMode.Comments -> {
                    TvLazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(
                                text = "Comments (${comments.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp),
                                color = Color.White
                            )
                        }
                        
                        // Recursive flattening would be ideal, but for now linear list of top interactions
                        // Or we render the tree. TvLazyColumn doesn't support recursion easily like Column.
                        // We will flatten the list for the UI adapter if needed, or just show top level for MVP.
                        // Let's iterate top level and their replies linearly?
                        // Or just standard items.
                        
                        // Function to flatten comments for display
                        val flatComments = flattenComments(comments)
                        
                        items(flatComments) { (comment, depth) ->
                            TvCommentItem(comment, depth)
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun TvCommentItem(comment: CommentModel, depth: Int) {
    Surface(
        onClick = {}, // TODO: Expand/Reply/Like logic?
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.1f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = comment.user.profileImage?.path?.toString() ?: "",
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = comment.user.username,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8AB4F8)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = timeAgo(comment.postDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.LightGray
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${comment.likes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray
                )
            }
        }
    }
}

// Helpers

private fun flattenComments(comments: List<CommentModel>, depth: Int = 0): List<Pair<CommentModel, Int>> {
    val result = mutableListOf<Pair<CommentModel, Int>>()
    comments.forEach { comment ->
        result.add(comment to depth)
        if (!comment.replies.isNullOrEmpty()) {
            result.addAll(flattenComments(comment.replies!!, depth + 1))
        }
    }
    return result
}

// Splits HTML into paragraphs (by block tags or newlines)
private fun processDescriptionForTv(html: String): List<String> {
    // Basic split: replace <br> with \n, remove tags, split by \n
    // Better: split raw HTML by <p>, <br>, <div> etc?
    // Doing strict text split loses formatting like Bold.
    // Approach: Convert entire HTML to Spanned, then split the String by \n but keep Spans?
    // That's hard.
    // Simpler: Replace <br> with specific marker, split string, process each chunk as HTML.
    
    val cleanHtml = html.replace("<br>", "\n").replace("<br/>", "\n").replace("<p>", "\n").replace("</p>", "")
    return cleanHtml.split("\n").filter { it.isNotBlank() }
}


private fun processDescriptionText(chunk: String, onSeek: (Long) -> Unit): CharSequence {
    // Reusing logic from phone app but applied to chunk
    val spanned = HtmlCompat.fromHtml(chunk, HtmlCompat.FROM_HTML_MODE_COMPACT)
    val builder = SpannableStringBuilder(spanned)

    val urlSpans = builder.getSpans(0, builder.length, URLSpan::class.java)
    for (span in urlSpans.reversed()) {
        val start = builder.getSpanStart(span)
        val end = builder.getSpanEnd(span)
        val url = span.url
        
        var displayText = url
        displayText = displayText.replace("http://", "").replace("https://", "")
        if (displayText.startsWith("www.")) {
            displayText = displayText.substring(4)
        }
        if (displayText.length > 40) {
            displayText = displayText.take(40) + "..."
        }
        
        builder.replace(start, end, displayText)
        builder.removeSpan(span)
        builder.setSpan(URLSpan(url), start, start + displayText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    
    // Timestamps parsing (simplified version of TimestampParser logic used in phone app)
    // Assuming TimestampParser isn't easily accessible or we duplicate regex
    val timestampRegex = Regex("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")
    val matches = timestampRegex.findAll(builder.toString())
    for (match in matches) {
        val groups = match.groupValues
        // logic to calculate seconds...
        // Just skip actual click logic for now unless we reimplement the parser or access it
        // Phone app uses TimestampParser.parseTimestamps. Let's try to access it if visible.
    }

    return builder
}
