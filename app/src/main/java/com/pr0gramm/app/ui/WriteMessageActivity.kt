package com.pr0gramm.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.api.pr0gramm.MessageConverter
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.parcel.DefaultParcelable
import com.pr0gramm.app.ui.compose.components.MessageRow
import com.pr0gramm.app.parcel.MessageSerializer
import com.pr0gramm.app.parcel.NewCommentParceler
import com.pr0gramm.app.parcel.SimpleCreator
import com.pr0gramm.app.parcel.getParcelableOrNull
import com.pr0gramm.app.parcel.javaClassOf
import com.pr0gramm.app.parcel.readStringNotNull
import com.pr0gramm.app.parcel.readValues
import com.pr0gramm.app.parcel.writeValues
import com.pr0gramm.app.services.InboxService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.UserSuggestionService
import com.pr0gramm.app.services.VoteService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.compose.Pr0grammAlertDialog
import com.pr0gramm.app.ui.compose.setComposeContent
import com.pr0gramm.app.util.TextViewCache
import com.pr0gramm.app.util.activityIntent
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.hideSoftKeyboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 */
class WriteMessageActivity : BaseAppCompatActivity("WriteMessageActivity") {
    private val inboxService: InboxService by instance()
    private val userService: UserService by instance()
    private val voteService: VoteService by instance()
    private val suggestionService: UserSuggestionService by instance()

    private val receiverName: String by lazy { intent.getStringExtra(ARGUMENT_RECEIVER_NAME)!! }
    private val receiverId: Long by lazy { intent.getLongExtra(ARGUMENT_RECEIVER_ID, 0) }
    private val isCommentAnswer: Boolean by lazy { intent.hasExtra(ARGUMENT_COMMENT_ID) }
    private val parentCommentId: Long by lazy { intent.getLongExtra(ARGUMENT_COMMENT_ID, 0) }
    private val itemId: Long by lazy { intent.getLongExtra(ARGUMENT_ITEM_ID, 0) }
    private val titleOverride: String? by lazy { intent.getStringExtra(ARGUMENT_TITLE) }

    private val parentComments: List<ParentComment> by lazy {
        intent.getParcelableExtra<ParentComments>(ARGUMENT_EXCERPTS)?.comments ?: listOf()
    }

    private val quotedMessage: Message? by lazy {
        intent?.extras?.getParcelableOrNull<MessageSerializer>(ARGUMENT_MESSAGE)?.message
    }

    private val cacheKey: String by lazy {
        if (isCommentAnswer) "$itemId-$parentCommentId" else "msg-$receiverId"
    }

    private var selectedUsers by mutableStateOf(setOf<String>())
    private var submitting by mutableStateOf(false)
    private var showEmptyMessageDialog by mutableStateOf(false)

    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.noActionBar)
        super.onCreate(savedInstanceState)

        val loggedInName = userService.loginState.name

        // only show if we can link to someone else
        val relevantParentComments = parentComments.filter {
            it.user != receiverName && it.user != loggedInName
        }

        setComposeContent {
            WriteMessageScreen(
                title = titleOverride ?: getString(R.string.write_message_title, receiverName),
                isCommentAnswer = isCommentAnswer,
                quotedMessage = quotedMessage,
                currentUsername = userService.name,
                admin = userService.userIsAdmin,
                parentComments = relevantParentComments,
                receiverName = receiverName,
                loggedInName = loggedInName,
                initialDraft = TextViewCache.getDraft(cacheKey) ?: "",
                selectedUsers = selectedUsers,
                onToggleUser = { user ->
                    selectedUsers = if (user in selectedUsers) selectedUsers - user else selectedUsers + user
                },
                submitting = submitting,
                suggestUsers = { term ->
                    withContext(Dispatchers.IO) { suggestionService.suggestUsers(term) }
                },
                onDraftChanged = { TextViewCache.putDraft(cacheKey, it) },
                onBack = { finish() },
                onSubmit = ::sendMessageNow,
                showEmptyMessageDialog = showEmptyMessageDialog,
                onDismissEmptyMessageDialog = { showEmptyMessageDialog = false },
            )
        }
    }

    override fun finish() {
        // hide keyboard before closing the activity.
        hideSoftKeyboard()

        super.finish()
    }

    private fun finishAfterSending() {
        TextViewCache.invalidateDraft(cacheKey)

        finish()
    }

    private fun sendMessageNow(message: String) {
        if (message.isEmpty()) {
            showEmptyMessageDialog = true
            return
        }

        if (isCommentAnswer) {
            val itemId = itemId
            val parentComment = parentCommentId

            launchWhenStarted(busyIndicator = true) {
                submitting = true
                try {
                    val newComments = withContext(NonCancellable + Dispatchers.Default) {
                        voteService.postComment(itemId, parentComment, message)
                    }

                    val result = Intent()
                    result.putExtra(RESULT_EXTRA_NEW_COMMENT, NewCommentParceler(newComments))
                    setResult(Activity.RESULT_OK, result)

                    finishAfterSending()
                } finally {
                    submitting = false
                }
            }

            Track.writeComment(root = parentCommentId == 0L)

        } else {
            launchWhenStarted(busyIndicator = true) {
                submitting = true
                try {
                    withContext(NonCancellable + Dispatchers.Default) {
                        inboxService.send(receiverId, message)
                    }

                    finishAfterSending()
                } finally {
                    submitting = false
                }
            }

            Track.writeMessage()
        }
    }

    companion object {
        private const val ARGUMENT_MESSAGE = "WriteMessageFragment.message"
        private const val ARGUMENT_RECEIVER_ID = "WriteMessageFragment.userId"
        private const val ARGUMENT_RECEIVER_NAME = "WriteMessageFragment.userName"
        private const val ARGUMENT_COMMENT_ID = "WriteMessageFragment.commentId"
        private const val ARGUMENT_ITEM_ID = "WriteMessageFragment.itemId"
        private const val ARGUMENT_EXCERPTS = "WriteMessageFragment.excerpts"
        private const val ARGUMENT_TITLE = "WriteMessageFragment.title"

        private const val RESULT_EXTRA_NEW_COMMENT = "WriteMessageFragment.result.newComment"

        fun intent(context: Context, message: Message): Intent {
            return activityIntent<WriteMessageActivity>(context) {
                putExtra(ARGUMENT_RECEIVER_ID, message.senderId.toLong())
                putExtra(ARGUMENT_RECEIVER_NAME, message.name)
                putExtra(ARGUMENT_MESSAGE, MessageSerializer(message))
            }
        }

        fun intent(context: Context, userId: Long, userName: String): Intent {
            return activityIntent<WriteMessageActivity>(context) {
                putExtra(ARGUMENT_RECEIVER_ID, userId)
                putExtra(ARGUMENT_RECEIVER_NAME, userName)
            }
        }

        fun newComment(context: Context, item: FeedItem): Intent {
            return activityIntent<WriteMessageActivity>(context) {
                putExtra(ARGUMENT_ITEM_ID, item.id)
                putExtra(ARGUMENT_COMMENT_ID, 0L)
                putExtra(ARGUMENT_TITLE, context.getString(R.string.write_comment, item.user))
            }
        }

        fun answerToComment(
            context: Context, feedItem: FeedItem, comment: Api.Comment,
            parentComments: List<ParentComment>
        ): Intent {

            return answerToComment(context, MessageConverter.of(feedItem, comment), parentComments)
        }

        fun answerToComment(
            context: Context, message: Message,
            parentComments: List<ParentComment> = listOf()
        ): Intent {

            val itemId = message.itemId
            val commentId = message.id

            return intent(context, message).apply {
                putExtra(ARGUMENT_ITEM_ID, itemId)
                putExtra(ARGUMENT_COMMENT_ID, commentId)
                putExtra(ARGUMENT_EXCERPTS, ParentComments(parentComments))
            }
        }

        fun getNewCommentFromActivityResult(data: Intent): Api.NewComment {
            return data.extras
                ?.getParcelableOrNull<NewCommentParceler>(RESULT_EXTRA_NEW_COMMENT)
                ?.value
                ?: throw IllegalArgumentException("no comment found in Intent")
        }
    }

    data class ParentComment(val user: String, val excerpt: String) {
        companion object {
            fun ofComment(comment: Api.Comment): ParentComment {
                val cleaned = comment.content.replace("\\s+".toRegex(), " ")
                val content = if (cleaned.length < 120) cleaned else cleaned.take(120) + "…"
                return ParentComment(comment.name, content)
            }
        }
    }

    class ParentComments(val comments: List<ParentComment>) : DefaultParcelable {
        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeValues(comments) { comment ->
                writeString(comment.user)
                writeString(comment.excerpt)
            }
        }

        companion object CREATOR : SimpleCreator<ParentComments>(javaClassOf()) {
            override fun createFromParcel(source: Parcel): ParentComments {
                val comments = source.readValues {
                    ParentComment(
                        user = source.readStringNotNull(),
                        excerpt = source.readStringNotNull(),
                    )
                }

                return ParentComments(comments)
            }
        }
    }
}

/**
 * The `@name` token (if any) the cursor currently sits inside of, used to drive username
 * autocomplete suggestions. Mirrors the matching rules of the old `UsernameTokenizer` +
 * `UsernameAutoCompleteAdapter` pair.
 */
private data class AtToken(val start: Int, val end: Int, val term: String)

private val atTokenPattern = Regex("^@[a-zA-Z0-9]{2,}$")

private fun findAtToken(text: String, cursor: Int): AtToken? {
    if (cursor < 0 || cursor > text.length) return null

    var idx = cursor - 1
    while (idx > 0 && text[idx].isLetterOrDigit()) idx--

    if (idx < 0 || text[idx] != '@') return null

    val token = text.substring(idx, cursor)
    if (!atTokenPattern.matches(token)) return null

    return AtToken(idx, cursor, token.substring(1))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WriteMessageScreen(
    title: String,
    isCommentAnswer: Boolean,
    quotedMessage: Message?,
    currentUsername: String?,
    admin: Boolean,
    parentComments: List<WriteMessageActivity.ParentComment>,
    receiverName: String,
    loggedInName: String?,
    initialDraft: String,
    selectedUsers: Set<String>,
    onToggleUser: (String) -> Unit,
    submitting: Boolean,
    suggestUsers: suspend (String) -> List<String>,
    onDraftChanged: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    showEmptyMessageDialog: Boolean,
    onDismissEmptyMessageDialog: () -> Unit,
) {
    var messageValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialDraft))
    }

    val atToken = if (messageValue.selection.collapsed) {
        findAtToken(messageValue.text, messageValue.selection.end)
    } else {
        null
    }

    var suggestions by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(atToken?.term) {
        suggestions = atToken?.term?.let { suggestUsers(it) } ?: emptyList()
    }

    fun applySuggestion(name: String) {
        val token = atToken ?: return
        val newText = messageValue.text.replaceRange(token.start, token.end, "@$name ")
        messageValue = TextFieldValue(newText, TextRange(token.start + name.length + 2))
        suggestions = emptyList()
    }

    fun finalMessage(): String {
        val typed = messageValue.text.trim()
        if (selectedUsers.isEmpty()) return typed

        val mentions = selectedUsers.sorted().joinToString(" ") { "@$it" }
        return if (typed.isEmpty()) mentions else "$typed\n$mentions"
    }

    val canSubmit = !submitting && messageValue.text.trim().length >= 3

    if (showEmptyMessageDialog) {
        Pr0grammAlertDialog(
            onDismissRequest = onDismissEmptyMessageDialog,
            text = stringResource(R.string.message_must_not_be_empty),
            confirmText = stringResource(android.R.string.ok),
            onConfirm = onDismissEmptyMessageDialog,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { onSubmit(finalMessage()) }, enabled = canSubmit) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (quotedMessage != null) {
                MessageRow(
                    message = quotedMessage,
                    currentUsername = currentUsername,
                    admin = admin,
                    showTypeLabel = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = messageValue,
                    onValueChange = {
                        messageValue = it
                        onDraftChanged(it.text)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !submitting,
                    minLines = 5,
                    label = {
                        Text(
                            if (isCommentAnswer) {
                                stringResource(R.string.comment_hint)
                            } else {
                                stringResource(R.string.write_message_placeholder)
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )

                DropdownMenu(
                    expanded = suggestions.isNotEmpty(),
                    onDismissRequest = { suggestions = emptyList() },
                    properties = PopupProperties(focusable = false),
                ) {
                    for (suggestion in suggestions) {
                        DropdownMenuItem(
                            text = { Text(suggestion) },
                            onClick = { applySuggestion(suggestion) },
                        )
                    }
                }
            }

            Button(
                onClick = { onSubmit(finalMessage()) },
                enabled = canSubmit,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.action_send))
            }

            if (parentComments.isNotEmpty()) {
                Text(
                    stringResource(R.string.authors_hint),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )

                Column {
                    for (comment in parentComments) {
                        val enabled = comment.user != receiverName && comment.user != loggedInName
                        val selected = comment.user in selectedUsers

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (enabled) {
                                        Modifier.clickable { onToggleUser(comment.user) }
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)

                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(comment.user)
                                    }
                                    append(" ")
                                    append(comment.excerpt)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
