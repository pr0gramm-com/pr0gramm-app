package com.pr0gramm.app.ui.compose.viewer

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.pr0gramm.app.ui.compose.components.BusyIndicator

/**
 * Displays a static image via Coil. No zoom/pan — just loads and displays the image
 * scaled to fit the width. Replaces the old [SubsamplingScaleImageView]-based ImageMediaView.
 */
@Composable
internal fun ImageViewer(
    uri: Uri,
    aspect: Float,
    modifier: Modifier = Modifier,
    onMediaShown: () -> Unit = {},
    onSingleTap: () -> Unit = {},
) {
    var loading by remember { mutableStateOf(true) }
    var shownFired by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (aspect > 0f) Modifier.aspectRatio(aspect, matchHeightConstraintsFirst = false) else Modifier)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onSingleTap() },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
            onState = { state ->
                when (state) {
                    is AsyncImagePainter.State.Success -> {
                        loading = false
                        if (!shownFired) {
                            shownFired = true
                            onMediaShown()
                        }
                    }

                    is AsyncImagePainter.State.Error -> {
                        loading = false
                    }

                    else -> {}
                }
            },
        )

        AnimatedVisibility(
            visible = loading,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            BusyIndicator()
        }
    }
}
