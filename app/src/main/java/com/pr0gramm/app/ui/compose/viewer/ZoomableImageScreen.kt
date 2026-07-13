package com.pr0gramm.app.ui.compose.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.compose.components.BusyIndicator

/**
 * Fullscreen zoomable image viewer. Supports pinch-to-zoom, pan, and
 * double-tap to toggle between 1× and 2× zoom.
 *
 * @param imageUrl         URL of the image to display.
 * @param hqImageUrl       Optional high-quality image URL. When non-null an HQ button is shown.
 * @param onToggleSystemUi Called on single tap to show/hide system chrome.
 */
@Composable
fun ZoomableImageScreen(
    imageUrl: Any?,
    modifier: Modifier = Modifier,
    hqImageUrl: Any? = null,
    onToggleSystemUi: () -> Unit = {},
) {
    var currentUrl by remember(imageUrl) { mutableStateOf(imageUrl) }
    var hqLoaded by remember { mutableStateOf(hqImageUrl == null) }
    var loading by remember { mutableStateOf(true) }

    // transform state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val minScale = 0.5f
    val maxScale = 5f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(minScale, maxScale)

                    // adjust offsets for the new scale
                    val maxOffsetX = (size.width / 2f) * (newScale - 1f)
                    val maxOffsetY = (size.height / 2f) * (newScale - 1f)

                    offsetX = (offsetX + pan.x * newScale * 0.5f).coerceIn(-maxOffsetX, maxOffsetX)
                    offsetY = (offsetY + pan.y * newScale * 0.5f).coerceIn(-maxOffsetY, maxOffsetY)
                    scale = newScale
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale != 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2f
                        }
                    },
                    onTap = { onToggleSystemUi() },
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = currentUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            onState = { state ->
                loading = state is AsyncImagePainter.State.Loading
            },
        )

        AnimatedVisibility(
            visible = loading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            BusyIndicator()
        }

        // HQ button
        if (!hqLoaded && hqImageUrl != null) {
            IconButton(
                onClick = {
                    currentUrl = hqImageUrl
                    hqLoaded = true
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_high_quality),
                    contentDescription = "High quality",
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
