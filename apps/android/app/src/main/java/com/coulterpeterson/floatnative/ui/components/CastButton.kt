package com.coulterpeterson.floatnative.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

@Composable
fun CastButton(
    modifier: Modifier = Modifier,
    color: Int = android.graphics.Color.WHITE // Default white for dark theme
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            // Wrap context with AppCompat theme to avoid "background can not be translucent" crash
            // MediaRouteButton requires a theme with valid background color attributes to calculate contrast
            val wrappedContext = android.view.ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat)
            MediaRouteButton(wrappedContext).apply {
                CastButtonFactory.setUpMediaRouteButton(context.applicationContext, this)
            }
        },
        update = { binding ->
            // Update color if dynamic theming needed, though MediaRouteButton styling is often theme-attr based.
             // binding.dialogFactory = ...
        }
    )
}
