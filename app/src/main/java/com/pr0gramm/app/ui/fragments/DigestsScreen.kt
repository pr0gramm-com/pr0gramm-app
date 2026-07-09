package com.pr0gramm.app.ui.fragments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.Thumbnail
import com.pr0gramm.app.services.DigestsService
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.compose.components.LoadingHint
import com.pr0gramm.app.ui.compose.image.NetworkImage
import kotlin.math.min

/**
 * Grid of digest highlights, replacing `DigestsFragment` + `digests_header.xml` +
 * `feed_item_view.xml` rows.
 */
@Composable
fun DigestsScreen(
    digestsService: DigestsService,
    onItemClicked: (id: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var digests by remember { mutableStateOf(listOf<Api.DigestsInbox.Digest>()) }
    var loading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTrigger) {
        loading = true
        digests = digestsService.digests()
        loading = false
        refreshing = false
    }

    val spanCount = thumbnailColumnCount()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            refreshTrigger++
        },
        modifier = modifier,
    ) {
        LazyVerticalGrid(columns = GridCells.Fixed(spanCount), modifier = Modifier.fillMaxSize()) {
            digests.forEach { digest ->
                item(
                    span = { GridItemSpan(spanCount) },
                ) {
                    DigestHeader(digest)
                }

                items(digest.items) { highlight ->
                    DigestThumbnail(highlight, onClick = { onItemClicked(highlight.id) })
                }
            }

            if (loading && digests.isEmpty()) {
                item(span = { GridItemSpan(spanCount) }) {
                    LoadingHint()
                }
            }
        }
    }
}

@Composable
private fun DigestHeader(digest: Api.DigestsInbox.Digest) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
    ) {
        if (digest.pushNotification.title.isNotBlank()) {
            Text(
                text = digest.pushNotification.title,
                style = MaterialTheme.typography.headlineSmall
            )
        }

        if (digest.message.isNotBlank()) {
            Text(
                text = digest.message,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        digest.notice?.takeIf { it.isNotBlank() }?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun DigestThumbnail(item: Api.DigestsInbox.ItemHighlight, onClick: () -> Unit) {
    val context = LocalContext.current

    val uri = remember(item.id) {
        UriHelper.of(context).thumbnail(Thumbnail(item.id, item.thumbnail))
    }

    NetworkImage(
        model = uri,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
    )
}

/**
 * Depending on whether the screen is landscape or portrait, and how large
 * the screen is, we show a different number of items per row.
 */
@Composable
private fun thumbnailColumnCount(): Int {
    val configuration = LocalConfiguration.current
    val portrait = configuration.screenWidthDp < configuration.screenHeightDp
    val screenWidth = configuration.screenWidthDp
    return min((screenWidth / 120.0 + 0.5).toInt(), if (portrait) 5 else 7)
}
