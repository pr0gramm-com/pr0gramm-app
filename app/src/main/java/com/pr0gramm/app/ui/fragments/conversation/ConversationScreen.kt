package com.pr0gramm.app.ui.fragments.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.NotificationService
import com.pr0gramm.app.ui.compose.components.LinkifiedText
import com.pr0gramm.app.ui.compose.components.LoadingHint
import com.pr0gramm.app.util.ErrorFormatting
import com.pr0gramm.app.util.TextViewCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

data class ConversationItem(
    val message: Api.ConversationMessage,
    val showTime: Boolean = true,
    val divider: String? = null,
)

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

    val pagingItems = model.paging.collectAsLazyPagingItems()

    val pendingMessages by model.pendingMessages.collectAsState()
    val partner by model.partner.collectAsState(initial = null)

    var messageValue by rememberSaveable { mutableStateOf(TextViewCache.getDraft(draftKey) ?: "") }
    var menuExpanded by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // periodically refresh while the screen is shown
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(15.seconds)

            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            if (lastVisible != null && lastVisible >= pagingItems.itemCount - 1) {
                pagingItems.refresh()
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
                            val totalRows = pagingItems.itemCount + pendingMessages.size
                            if (totalRows > 0) listState.animateScrollToItem(0)
                        }
                        pagingItems.refresh()
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }

                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }

                    menuExpanded = showConversationMenu(
                        menuExpanded = menuExpanded,
                        onOpenProfile = onOpenProfile,
                        onDeleteConversation = onDeleteConversation
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Conversation(
                listState = listState,
                pagingItems = pagingItems,
                pendingMessages = pendingMessages,
            )

            MessageInputBar(
                value = messageValue,
                onValueChange = { newValue ->
                    messageValue = newValue
                    TextViewCache.putDraft(draftKey, newValue)
                },
                enabled = partner?.canReceiveMessages != false,
                hint = partner?.takeIf { partner -> !partner.canReceiveMessages }?.let {
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
private fun showConversationMenu(
    menuExpanded: Boolean,
    onOpenProfile: () -> Unit,
    onDeleteConversation: () -> Unit
): Boolean {
    var isMenuExpanded = menuExpanded

    DropdownMenu(
        expanded = isMenuExpanded,
        onDismissRequest = { isMenuExpanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_profile)) },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            onClick = {
                isMenuExpanded = false
                onOpenProfile()
            },
        )

        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            onClick = {
                isMenuExpanded = false
                onDeleteConversation()
            },
        )
    }

    return isMenuExpanded
}

@Composable
private fun ColumnScope.Conversation(
    listState: LazyListState,
    pagingItems: LazyPagingItems<Api.ConversationMessage>,
    pendingMessages: List<String>,
) {
    val fmt = SimpleDateFormat("dd.MM.yyyy", LocalLocale.current.platformLocale)

    LazyColumn(
        state = listState,
        reverseLayout = true,
        verticalArrangement = Arrangement.Bottom,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {

        val refreshError = pagingItems.loadState.refresh as? LoadState.Error
        if (refreshError != null) {
            item {
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

        items(
            count = pagingItems.itemCount,
            key = { index -> pagingItems.peek(pagingItems.itemCount - 1 - index)?.id ?: index },
        ) { itemIndex ->
            val index = pagingItems.itemCount - 1 - itemIndex

            val curr = pagingItems[index] ?: return@items

            // pagingItems are sorted by time ascending
            val latest = index == pagingItems.itemCount - 1

            // get the next item (in the future)
            val next = if (index + 1 < pagingItems.itemCount) pagingItems.peek(index + 1) else null

            // check the date
            val currStr = curr.creationTime.toString(fmt)
            val nextStr = next?.creationTime?.toString(fmt)

            val isSameDay = currStr == nextStr

            val isOtherPerson = curr.sent != next?.sent

            // decide if we should show the time:
            //  show it if is the latest message
            //  show it if the next message is more than 1min later
            val showTime =
                latest || !isSameDay || isOtherPerson || (next.creationTime - curr.creationTime).inMinutes >= 1

            ConversationBubble(
                text = curr.messageText,
                time = curr.creationTime,
                sent = curr.sent,
                showTime = showTime
            )

            if (nextStr != null && currStr != nextStr) {
                ConversationDateDivider(text = nextStr)
            }
        }

        items(pendingMessages.asReversed()) { pending ->
            ConversationBubble(text = pending, time = null, sent = true, pending = true)
        }

        if (pagingItems.itemCount == 0 && pagingItems.loadState.refresh is LoadState.Loading) {
            item { LoadingHint() }
        }

        if (pagingItems.loadState.prepend is LoadState.Loading) {
            item(key = "prepend-loading") { LoadingHint() }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
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

@Composable
private fun ConversationBubble(
    text: String,
    time: Instant?,
    sent: Boolean,
    pending: Boolean = false,
    showTime: Boolean = true,
) {
    val colorA = lerp(MaterialTheme.colorScheme.secondary, Color(0xFF333333), 0.8f)
    val colorB = lerp(MaterialTheme.colorScheme.secondary, Color(0xFF333333), 0.98f)

    val bubbleColor = if (sent) colorA else colorB
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val horizontalAlignment = if (sent) Alignment.Start else Alignment.End

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 4.dp,
            ),

        horizontalAlignment = horizontalAlignment,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topEnd = 8.dp,
                        topStart = 8.dp,
                        bottomStart = if (!sent && showTime) 8.dp else 0.dp,
                        bottomEnd = if (sent && showTime) 8.dp else 0.dp,
                    )
                )
                .background(bubbleColor)
                .alpha(if (pending) 0.5f else 1f)
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            LinkifiedText(
                text = text,
                color = Color.White,
            )
        }

        if (showTime) {
            Text(
                text = if (pending) stringResource(R.string.hint_sending) else time?.toString(
                    timeFormat
                ).orEmpty(),
                color = Color(0xFF888888),
                fontSize = 10.sp,
                modifier = Modifier.align(horizontalAlignment),
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

