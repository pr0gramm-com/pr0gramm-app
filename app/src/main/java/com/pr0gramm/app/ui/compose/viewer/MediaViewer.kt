package com.pr0gramm.app.ui.compose.viewer

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.ui.views.viewer.MediaUri

/**
 * Top-level media viewer composable. Dispatches to the correct viewer
 * based on [MediaUri.mediaType].
 *
 * @param mediaUri     The media to display.
 * @param aspect       Width / height ratio; ≤ 0 means unknown (the viewer will derive it).
 * @param audio        Whether the media has an audio track (only relevant for video).
 * @param subtitles    Optional subtitle configs (only relevant for video).
 * @param isPlaying    Whether the viewer should actively play / animate.
 * @param onMediaShown Called once when the first frame / image is visible.
 * @param onSingleTap  Called on single-tap for the host to toggle chrome.
 * @param onDoubleTap  Called on double-tap with the normalized X position (0..1).
 */
@Composable
fun MediaViewer(
    mediaUri: MediaUri,
    modifier: Modifier = Modifier,
    aspect: Float = -1f,
    audio: Boolean = false,
    subtitles: List<Api.Feed.Subtitle> = emptyList(),
    isPlaying: Boolean = true,
    onMediaShown: () -> Unit = {},
    onSingleTap: () -> Unit = {},
    onDoubleTap: (normalizedX: Float) -> Unit = {},
) {
    when (mediaUri.mediaType) {
        MediaUri.MediaType.IMAGE -> ImageViewer(
            uri = mediaUri.baseUri,
            aspect = aspect,
            modifier = modifier,
            onMediaShown = onMediaShown,
            onSingleTap = onSingleTap,
        )

        MediaUri.MediaType.GIF -> GifViewer(
            uri = mediaUri.baseUri,
            aspect = aspect,
            modifier = modifier,
            isPlaying = isPlaying,
            onMediaShown = onMediaShown,
            onSingleTap = onSingleTap,
        )

        MediaUri.MediaType.VIDEO -> VideoViewer(
            mediaUri = mediaUri,
            aspect = aspect,
            audio = audio,
            subtitles = subtitles,
            modifier = modifier,
            isPlaying = isPlaying,
            onMediaShown = onMediaShown,
            onSingleTap = onSingleTap,
            onDoubleTap = onDoubleTap,
        )
    }
}
