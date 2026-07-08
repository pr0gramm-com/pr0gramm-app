package com.pr0gramm.app.ui.fragments

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.InboxService
import com.pr0gramm.app.ui.Pagination
import com.pr0gramm.app.ui.compose.components.EmptyHint
import com.pr0gramm.app.ui.compose.components.ErrorHint
import com.pr0gramm.app.ui.compose.components.LoadingHint
import com.pr0gramm.app.ui.compose.components.Username
import com.pr0gramm.app.ui.compose.observeAsStateCompat
import com.pr0gramm.app.util.DurationFormat
import com.pr0gramm.app.util.ErrorFormatting
import com.pr0gramm.app.util.UserDrawables

/**
 * The list of private-message conversations, replacing `ConversationsFragment` +
 * `item_conversation.xml`.
 */
@Composable
fun ConversationsScreen(
    inboxService: InboxService,
    onConversationClicked: (Api.Conversation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pagination = remember { Pagination(scope, ConversationsLoader(inboxService)) }

    var conversations by remember { mutableStateOf(listOf<Api.Conversation>()) }
    var tailState by remember { mutableStateOf(Pagination.EndState<Api.Conversation>(hasMore = true)) }
    var refreshing by remember { mutableStateOf(false) }

    val update by pagination.updates.observeAsStateCompat()

    fun reload() {
        conversations = listOf()
        tailState = Pagination.EndState(hasMore = true)
        pagination.initialize()
    }

    LaunchedEffect(pagination) { reload() }

    LaunchedEffect(update) {
        val u = update ?: return@LaunchedEffect

        conversations = if (u.newValues.isNotEmpty()) {
            (conversations + u.newValues).distinctBy { it.name }
        } else {
            conversations
        }

        tailState = u.state.tailState

        if (!u.state.tailState.loading) {
            refreshing = false
        }
    }

    // keep unread counts fresh whenever the screen (re)appears
    LaunchedEffect(Unit) {
        val response = inboxService.listConversations()
        conversations = (conversations + response.conversations).distinctBy { it.name }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(listState, pagination) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index
            lastVisible to layoutInfo.totalItemsCount
        }.collect { (lastVisible, total) ->
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
            items(conversations, key = { it.name }) { conversation ->
                ConversationRow(
                    conversation = conversation,
                    onClick = {
                        conversations = conversations.map {
                            if (it.name == conversation.name) it.copy(unreadCount = 0) else it
                        }

                        onConversationClicked(conversation)
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }

            when {
                tailState.error != null -> item(key = "error") {
                    ErrorHint(ErrorFormatting.format(context, tailState.error!!))
                }

                tailState.hasMore -> item(key = "loading") { LoadingHint() }
            }

            if (conversations.isEmpty() && !tailState.hasMore && tailState.error == null) {
                item(key = "empty") { EmptyHint() }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Api.Conversation, onClick: () -> Unit) {
    val context = LocalContext.current

    val avatar = remember(conversation.name) {
        val userDrawables = UserDrawables(context)
        (userDrawables.drawable(conversation.name) as BitmapDrawable).bitmap.asImageBitmap()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            bitmap = avatar,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Username(name = conversation.name, mark = conversation.mark)

            Text(
                text = DurationFormat.timeSincePastPointInTime(context, conversation.lastMessage, short = true),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (conversation.unreadCount > 0) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = conversation.unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondary,
                )
            }
        }
    }
}

private class ConversationsLoader(private val inboxService: InboxService) : Pagination.Loader<Api.Conversation>() {
    override suspend fun loadAfter(currentValue: Api.Conversation?): Pagination.Page<Api.Conversation> {
        val olderThan = currentValue?.lastMessage
        val response = inboxService.listConversations(olderThan)
        return Pagination.Page.atTail(response.conversations, hasMore = !response.atEnd)
    }
}
