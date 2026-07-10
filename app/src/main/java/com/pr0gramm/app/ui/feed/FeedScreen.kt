package com.pr0gramm.app.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.api.pr0gramm.asThumbnail
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.services.UserInfo
import com.pr0gramm.app.ui.MessageView
import com.pr0gramm.app.ui.compose.components.EmptyHint
import com.pr0gramm.app.ui.compose.components.ErrorHint
import com.pr0gramm.app.ui.compose.components.LoadingHint
import com.pr0gramm.app.ui.fragments.AdViewHolder
import com.pr0gramm.app.ui.views.UserHintView
import com.pr0gramm.app.ui.views.UserInfoLoadingView
import com.pr0gramm.app.ui.views.UserInfoView

sealed class FeedGridEntry(val stableId: Long) {
    data class Item(
        val item: FeedItem,
        val repost: Boolean,
        val preloaded: Boolean,
        val seen: Boolean,
        val highlight: Boolean,
    ) : FeedGridEntry((6L shl 56) or item.id)

    data class Ad(val index: Long) : FeedGridEntry((8L shl 56) or index)

    data class Spacer(val idx: Long, val heightDp: Int) : FeedGridEntry((7L shl 56) or idx)

    data class Error(val errorText: String) : FeedGridEntry(3L shl 56)

    data object EmptyHint : FeedGridEntry(4L shl 56)
    data object LoadingHint : FeedGridEntry(5L shl 56)

    data class MissingContentType(
        val contentType: ContentType,
        val isAuthorized: Boolean,
        val errorMessage: String?,
        val onAddContentType: () -> Unit,
    ) : FeedGridEntry(10L shl 56)

    data class UserHint(
        val name: String,
        val mark: Int,
        val onClick: (String) -> Unit,
    ) : FeedGridEntry(0L shl 56)

    data class UserLoading(val name: String, val mark: Int) : FeedGridEntry(1L shl 56)

    data class UserInfo(
        val userInfo: com.pr0gramm.app.services.UserInfo,
        val isSelf: Boolean,
        val actionListener: UserInfoView.UserActionListener,
    ) : FeedGridEntry(2L shl 56)

    data class UserComment(
        val message: Message,
        val currentUsername: String?,
    ) : FeedGridEntry((9L shl 56) or message.id)

    data class PlaceholderItem(val itemId: Long) : FeedGridEntry((11L shl 56) or itemId)
}

@Composable
fun FeedScreen(
    entries: List<FeedGridEntry>,
    columnCount: Int,
    isRefreshing: Boolean,
    canRefresh: Boolean,
    gridState: LazyGridState,
    onRefresh: () -> Unit,
    onItemClicked: (FeedItem) -> Unit,
    onItemLongPress: ((FeedItem) -> Unit)? = null,
    onItemLongPressEnd: (() -> Unit)? = null,
    onLoadNext: () -> Unit,
    onLoadPrev: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pagination triggers
    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
            firstVisible to lastVisible
        }.collect { (firstVisible, lastVisible) ->
            val totalItems = gridState.layoutInfo.totalItemsCount
            if (totalItems > 48) {
                if (lastVisible >= totalItems - 48) onLoadNext()
                if (firstVisible < 48) onLoadPrev()
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = entries,
                key = { it.stableId },
                span = { entry -> GridItemSpan(entry.spanSize(columnCount)) },
            ) { entry ->
                when (entry) {
                    is FeedGridEntry.Item -> FeedItemCell(
                        entry = entry,
                        onItemClicked = onItemClicked,
                        onItemLongPress = onItemLongPress,
                        onItemLongPressEnd = onItemLongPressEnd,
                    )

                    is FeedGridEntry.PlaceholderItem -> PlaceholderCell()

                    is FeedGridEntry.Ad -> AdCell()

                    is FeedGridEntry.LoadingHint -> LoadingHint(
                        modifier = Modifier.fillMaxWidth(),
                    )

                    is FeedGridEntry.EmptyHint -> EmptyHint(
                        modifier = Modifier.fillMaxWidth(),
                    )

                    is FeedGridEntry.Error -> ErrorHint(
                        text = entry.errorText,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    is FeedGridEntry.Spacer -> Spacer(
                        modifier = Modifier.fillMaxWidth().height(entry.heightDp.dp),
                    )

                    is FeedGridEntry.MissingContentType -> MissingContentTypeCell(entry)

                    is FeedGridEntry.UserHint -> UserHintCell(entry)

                    is FeedGridEntry.UserLoading -> UserLoadingCell(entry)

                    is FeedGridEntry.UserInfo -> UserInfoCell(entry)

                    is FeedGridEntry.UserComment -> UserCommentCell(entry)
                }
            }
        }
    }
}

private fun FeedGridEntry.spanSize(maxSpan: Int): Int = when (this) {
    is FeedGridEntry.Item -> if (highlight) maxSpan else 1
    is FeedGridEntry.PlaceholderItem -> 1
    else -> maxSpan
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedItemCell(
    entry: FeedGridEntry.Item,
    onItemClicked: (FeedItem) -> Unit,
    onItemLongPress: ((FeedItem) -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") onItemLongPressEnd: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val item = entry.item
    val aspectRatio = if (entry.highlight) {
        item.width.toFloat() / item.height.coerceAtLeast(1)
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .aspectRatio(aspectRatio)
            .padding(2.dp)
            .combinedClickable(
                onClick = { if (!item.placeholder) onItemClicked(item) },
                onLongClick = {
                    if (!item.placeholder) {
                        onItemLongPress?.invoke(item)
                    }
                },
            ),
    ) {
        if (item.placeholder) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF886633)),
            )
        } else {
            val imageUri = remember(item.id, entry.highlight) {
                val uriHelper = UriHelper.of(context)
                if (entry.highlight) {
                    if (item.isImage) {
                        uriHelper.media(item, hq = false)
                    } else {
                        uriHelper.fullThumbnail(item.asThumbnail())
                    }
                } else {
                    uriHelper.thumbnail(item.asThumbnail())
                }
            }

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUri)
                    .crossfade(true)
                    .build(),
                placeholder = ColorPainter(Color(0xFF333333)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Overlay: repost or seen indicator
        if (entry.repost) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_repost),
                    contentDescription = null,
                    tint = Color.White,
                    // modifier = Modifier.size(24.dp),
                )
            }
        } else if (entry.seen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // Flag layer: pinned or preloaded
        if (item.isPinned) {
            Icon(
                painter = painterResource(R.drawable.feed_pinned),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp),
            )
        } else if (entry.preloaded) {
            Icon(
                painter = painterResource(R.drawable.feed_offline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp),
            )
        }
    }
}

@Composable
private fun PlaceholderCell() {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .background(Color(0xFF607D8B)),
    )
}

@Composable
private fun AdCell(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            val holder = AdViewHolder.new(ctx)
            holder.itemView
        },
        modifier = modifier.fillMaxWidth().height(70.dp),
    )
}

@Composable
private fun MissingContentTypeCell(entry: FeedGridEntry.MissingContentType) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.cloud_alert),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )

        val text = entry.errorMessage
            ?: "Content type ${entry.contentType.name} is not enabled."
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (entry.isAuthorized) {
            TextButton(onClick = entry.onAddContentType) {
                Text("Enable")
            }
        }
    }
}

@Composable
private fun UserHintCell(entry: FeedGridEntry.UserHint, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> UserHintView(ctx) },
        update = { view -> view.update(entry.name, entry.mark, entry.onClick) },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun UserLoadingCell(entry: FeedGridEntry.UserLoading, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> UserInfoLoadingView(ctx) },
        update = { view -> view.update(entry.name, entry.mark) },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun UserInfoCell(entry: FeedGridEntry.UserInfo, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> UserInfoView(ctx) },
        update = { view ->
            view.updateUserInfo(
                entry.userInfo.info,
                entry.userInfo.comments,
                entry.isSelf,
                entry.actionListener,
            )
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun UserCommentCell(entry: FeedGridEntry.UserComment, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> MessageView(ctx) },
        update = { view -> view.update(entry.message, entry.currentUsername) },
        modifier = modifier.fillMaxWidth(),
    )
}
