package com.pr0gramm.app.ui.fragments.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.paging.insertSeparators
import androidx.paging.map
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.NotificationService
import com.pr0gramm.app.ui.compose.components.LinkifiedText
import com.pr0gramm.app.ui.compose.components.LoadingHint
import com.pr0gramm.app.util.ErrorFormatting
import com.pr0gramm.app.util.TextViewCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

sealed class ConversationItem {
    class Message(val message: Api.ConversationMessage) : ConversationItem()
    class Divider(val text: String) : ConversationItem()
}

/**
 * The chat-style thread of messages with a single conversation partner, replacing
 * `ConversationFragment` + `ConversationAdapter` + `PendingMessagesAdapter` +
 * `item_message_sent.xml` / `item_message_received.xml`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    conversationName: String,
    model: ConversationViewModel,
    notificationService: NotificationService,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onDeleteConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draftKey = remember(conversationName) { "conversation:$conversationName" }

    val pagingFlow = remember(model) {
        model.paging.map { pagingData: PagingData<Api.ConversationMessage> ->
            val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

            val messages = pagingData.map { ConversationItem.Message(it) }

            messages.insertSeparators<ConversationItem.Message, ConversationItem> { prev, next ->
                val prevStr = prev?.message?.creationTime?.toString(fmt)
                val nextStr = next?.message?.creationTime?.toString(fmt)
                if (nextStr != null && prevStr != nextStr) ConversationItem.Divider(nextStr) else null
            }
        }
    }

    val lazyPagingItems = pagingFlow.collectAsLazyPagingItems()
    val pendingMessages by model.pendingMessages.collectAsState()
    val partner by model.partner.collectAsState(initial = null)

    var messageValue by rememberSaveable { mutableStateOf(TextViewCache.getDraft(draftKey) ?: "") }
    var menuExpanded by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var lastKnownNewestId by remember { mutableStateOf(0L) }

    LaunchedEffect(lazyPagingItems.itemCount, pendingMessages.size) {
        val newestItem = (0 until lazyPagingItems.itemCount).asSequence()
            .mapNotNull { index -> lazyPagingItems[index] as? ConversationItem.Message }
            .lastOrNull()

        val totalRows = lazyPagingItems.itemCount + pendingMessages.size

        val shouldScroll = (newestItem != null && newestItem.message.id > lastKnownNewestId) ||
                pendingMessages.isNotEmpty()

        if (newestItem != null) {
            lastKnownNewestId = newestItem.message.id
        }

        if (shouldScroll && totalRows > 0) {
            listState.animateScrollToItem(totalRows - 1)
        }
    }

    // periodically refresh while the screen is shown, same cadence as the original fragment
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(15.seconds)

            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            if (lastVisible != null && lastVisible >= lazyPagingItems.itemCount - 1) {
                lazyPagingItems.refresh()
            }
        }
    }

    LaunchedEffect(conversationName) {
        notificationService.cancelForUnreadConversation(conversationName)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(conversationName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val totalRows = lazyPagingItems.itemCount + pendingMessages.size
                            if (totalRows > 0) listState.animateScrollToItem(totalRows - 1)
                        }
                        lazyPagingItems.refresh()
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }

                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }

                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_profile)) },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenProfile()
                            },
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDeleteConversation()
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (lazyPagingItems.loadState.prepend is LoadState.Loading) {
                    item(key = "prepend-loading") { LoadingHint() }
                }

                items(
                    count = lazyPagingItems.itemCount,
                    key = lazyPagingItems.itemKey { item ->
                        when (item) {
                            is ConversationItem.Message -> item.message.id
                            is ConversationItem.Divider -> item.text
                        }
                    },
                    contentType = lazyPagingItems.itemContentType { item -> item::class },
                ) { index ->
                    when (val item = lazyPagingItems[index]) {
                        is ConversationItem.Message -> ConversationBubble(
                            text = item.message.messageText,
                            time = item.message.creationTime,
                            sent = item.message.sent,
                        )

                        is ConversationItem.Divider -> ConversationDateDivider(item.text)
                        null -> Unit
                    }
                }

                items(pendingMessages, key = { "pending-$it" }) { pending ->
                    ConversationBubble(text = pending, time = null, sent = true, pending = true)
                }

                if (lazyPagingItems.itemCount == 0 && lazyPagingItems.loadState.refresh is LoadState.Loading) {
                    item(key = "initial-loading") { LoadingHint() }
                }

                val refreshError = lazyPagingItems.loadState.refresh as? LoadState.Error
                if (refreshError != null) {
                    item(key = "error") {
                        Text(
                            text = ErrorFormatting.format(LocalContext.current, refreshError.error),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            MessageInputBar(
                value = messageValue,
                onValueChange = {
                    messageValue = it
                    TextViewCache.putDraft(draftKey, it)
                },
                enabled = partner?.canReceiveMessages != false,
                hint = partner?.takeIf { !it.canReceiveMessages }?.let {
                    stringResource(R.string.write_message_cannot_receive_messages, it.name)
                },
                onSend = {
                    val text = messageValue.trim()
                    if (text.isNotEmpty()) {
                        messageValue = ""
                        TextViewCache.invalidateDraft(draftKey)
                        scope.launch { model.send(text) }
                    }
                },
            )
        }
    }
}

@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    hint: String?,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                placeholder = { Text(hint ?: stringResource(R.string.write_message_placeholder)) },
                modifier = Modifier.weight(1f),
                maxLines = 6,
            )

            IconButton(onClick = onSend, enabled = enabled && value.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            }
        }
    }
}

@Composable
private fun ConversationBubble(
    text: String,
    time: Instant?,
    sent: Boolean,
    pending: Boolean = false,
) {
    val bubbleColor = if (sent) Color(0xFF222222) else Color(0xFF333333)
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (sent) 48.dp else 16.dp,
                end = if (sent) 16.dp else 48.dp,
                top = 4.dp,
                bottom = 4.dp,
            ),
        contentAlignment = if (sent) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(bubbleColor)
                .alpha(if (pending) 0.5f else 1f)
                .padding(8.dp),
        ) {
            LinkifiedText(
                text = text,
                color = Color.White,
                modifier = Modifier.padding(bottom = 14.dp, end = 28.dp),
            )

            Text(
                text = if (pending) stringResource(R.string.hint_sending) else time?.toString(timeFormat).orEmpty(),
                color = Color(0xFF888888),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun ConversationDateDivider(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF888888),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}
