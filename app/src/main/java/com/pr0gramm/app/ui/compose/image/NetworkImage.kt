package com.pr0gramm.app.ui.compose.image

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import com.pr0gramm.app.api.pr0gramm.asThumbnail
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.services.UriHelper

/**
 * Loads a remote image via the shared, OkHttp-backed Coil [coil3.ImageLoader]
 * (installed as the singleton in [com.pr0gramm.app.ApplicationClass]).
 *
 * This is the thin, reusable image API for Compose screens; migrated screens use this
 * instead of Picasso / `AspectImageView`.
 *
 * @param blur when true, the image is blurred (e.g. for NSFW / hidden content).
 */
@Composable
fun NetworkImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    blur: Boolean = false,
) {
    val context = LocalContext.current

    val request = remember(model, blur) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .apply {
                if (blur) {
                    transformations(BlurTransformation())
                }
            }
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

/**
 * A feed thumbnail reproducing the aspect-ratio behaviour of `AspectImageView`:
 * it fills the available width and derives its height from [aspect] (width / height).
 * The feed grid uses square thumbnails (`aspect = 1f`), matching `FeedAdapter`.
 */
@Composable
fun FeedThumbnail(
    item: FeedItem,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    aspect: Float = 1f,
    blur: Boolean = false,
) {
    val context = LocalContext.current

    val uri = remember(item.id) {
        UriHelper.of(context).thumbnail(item.asThumbnail())
    }

    NetworkImage(
        model = uri,
        contentDescription = contentDescription,
        modifier = modifier.then(
            if (aspect > 0f) Modifier.aspectRatio(aspect) else Modifier
        ),
        contentScale = ContentScale.Crop,
        blur = blur,
    )
}
