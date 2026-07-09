package com.pr0gramm.app.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.Instant
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.api.pr0gramm.MessageType
import com.pr0gramm.app.db.FeedItemInfoQueries
import com.pr0gramm.app.sync.SyncWorker
import com.pr0gramm.app.ui.compose.components.EmptyHint
import com.pr0gramm.app.ui.compose.components.ErrorHint
import com.pr0gramm.app.ui.compose.components.LoadingHint
import com.pr0gramm.app.ui.compose.components.MessageRow
import com.pr0gramm.app.ui.compose.observeAsStateCompat
import com.pr0gramm.app.util.ErrorFormatting
import com.pr0gramm.app.util.di.injector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.TimeUnit

/**
 * A paginated list of inbox [Message]s (private messages / comment replies / notifications /
 * stalk hits / written comments), replacing `InboxFragment`/`GenericInboxFragment`/
 * `WrittenCommentsFragment` + `MessageAdapter` + `row_inbox_message.xml`/`row_inbox_all_message.xml`.
 *
 * The "all" inbox tab additionally shows a per-message type caption ([showTypeLabel]) and an
 * "unread" divider above the first already-read message, matching the original adapter.
 */
@Composable
fun InboxMessagesScreen(
    type: InboxType,
    loader: Pagination.Loader<Message>,
    currentUsername: String?,
    admin: Boolean,
    showTypeLabel: Boolean,
    onCommentClicked: (Message) -> Unit,
    onAnswerToCommentClicked: (Message) -> Unit,
    onAnswerToPrivateMessage: (Message) -> Unit,
    onUserClicked: (userId: Int, username: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val logger = Logger("InboxMessagesScreen")

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pagination = remember(type) { Pagination(scope, loader) }

    var messages by remember(pagination) { mutableStateOf(listOf<Message>()) }
    var tailState by remember(pagination) { mutableStateOf(Pagination.EndState<Message>(hasMore = true)) }
    var refreshing by remember(pagination) { mutableStateOf(false) }

    val update by pagination.updates.observeAsStateCompat()

    fun reload() {
        messages = listOf()
        tailState = Pagination.EndState(hasMore = true)
        pagination.initialize()
    }

    LaunchedEffect(pagination) { reload() }

    LaunchedEffect(update) {
        val u = update ?: return@LaunchedEffect
        messages = (messages + u.newValues).distinctBy { it.id }
        tailState = u.state.tailState

        if (!u.state.tailState.loading) {
            refreshing = false
        }
    }

    val items = remember(messages, tailState) { buildInboxItems(context, messages, tailState) }

    val listState = remember(type) {
        LazyListState()
    }

    LaunchedEffect(listState, pagination) {
        val flow = snapshotFlow {
            logger.info { "ListState ${listState.firstVisibleItemIndex} ${listState.firstVisibleItemScrollOffset}}" }
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index
            lastVisible to layoutInfo.totalItemsCount
        }

        flow.collect { (lastVisible, total) ->
            if (lastVisible != null && total - lastVisible <= 12) {
                pagination.loadAtTail()
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            reload()
        },
        modifier = modifier,
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(items) { item ->
                when (item) {
                    is InboxListItem.MessageItem -> {
                        val message = item.message

                        MessageRow(
                            message = message,
                            currentUsername = currentUsername,
                            admin = admin,
                            showTypeLabel = showTypeLabel,
                            onClick = when (message.type) {
                                MessageType.COMMENT, MessageType.STALK -> ({
                                    onCommentClicked(
                                        message
                                    )
                                })

                                else -> null
                            },
                            onSenderClick = { onUserClicked(message.senderId, message.name) },
                            onAnswerClick = when (message.type) {
                                MessageType.COMMENT -> ({ onAnswerToCommentClicked(message) })
                                MessageType.MESSAGE -> ({ onAnswerToPrivateMessage(message) })
                                else -> null
                            },
                        )

                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }

                    is InboxListItem.DividerItem -> {
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                        )
                    }

                    is InboxListItem.ErrorItem -> ErrorHint(item.text)
                    InboxListItem.LoadingItem -> LoadingHint()
                    InboxListItem.EmptyItem -> EmptyHint()
                }
            }
        }
    }
}

private sealed interface InboxListItem {
    data class MessageItem(val message: Message) : InboxListItem
    data class DividerItem(val text: String) : InboxListItem
    data class ErrorItem(val text: String) : InboxListItem
    data object LoadingItem : InboxListItem
    data object EmptyItem : InboxListItem
}

private fun buildInboxItems(
    context: Context,
    messages: List<Message>,
    tailState: Pagination.EndState<Message>,
): List<InboxListItem> {
    val items = mutableListOf<InboxListItem>()

    val dividerIndex = messages.indexOfFirst { it.read }

    messages.forEachIndexed { index, message ->
        if (index == dividerIndex && dividerIndex > 0) {
            items += InboxListItem.DividerItem(
                text = context.getString(R.string.inbox_type_unread),
            )
        }

        items += InboxListItem.MessageItem(message)
    }

    when {
        tailState.error != null -> items += InboxListItem.ErrorItem(
            ErrorFormatting.format(
                context,
                tailState.error
            )
        )

        tailState.hasMore -> {
            items += InboxListItem.LoadingItem
        }
    }

    if (items.isEmpty()) {
        items += InboxListItem.EmptyItem
    }

    return items
}

/**
 * Builds a [Pagination.Loader] around a raw message-fetching [loader], filling in cached
 * [Message.flags] for items where the API didn't provide them yet, and optionally scheduling a
 * background sync (used by the "all messages" tab to keep unread counts fresh).
 */
fun apiMessageLoader(
    ctx: Context,
    syncOnLoad: Boolean = false,
    loader: suspend (Instant?) -> List<Message>,
): Pagination.Loader<Message> {
    class MessagePaginationLoader : Pagination.Loader<Message>() {
        private val itemQueries = ctx.injector.instance<FeedItemInfoQueries>()
        private val logger = Logger("MessagePaginationLoader")

        override suspend fun loadAfter(currentValue: Message?): Pagination.Page<Message> {
            var messages = loader(currentValue?.creationTime)

            val messagesWithUnknownFlags = messages.filter { m -> m.itemId > 0L && m.flags == 0 }
            if (messagesWithUnknownFlags.isNotEmpty()) {
                val ids = messagesWithUnknownFlags.mapTo(HashSet()) { msg -> msg.itemId }

                val cached = runInterruptible(Dispatchers.IO) {
                    itemQueries.lookup(ids).executeAsList()
                }

                val byId = cached.associateBy(
                    keySelector = { item -> item.id },
                    valueTransform = { item -> item.flags },
                )

                logger.debug { "Found ${cached.size} of ${ids.size} cached items" }

                messages = messages.map { msg ->
                    msg.copy(flags = byId[msg.itemId] ?: msg.flags)
                }
            }

            if (syncOnLoad) {
                SyncWorker.scheduleNextSyncIn(
                    ctx,
                    delay = 3,
                    unit = TimeUnit.SECONDS,
                    sourceTag = "inbox"
                )
            }

            return Pagination.Page(messages, messages.lastOrNull()?.takeIf { messages.size > 10 })
        }
    }

    return MessagePaginationLoader()
}
