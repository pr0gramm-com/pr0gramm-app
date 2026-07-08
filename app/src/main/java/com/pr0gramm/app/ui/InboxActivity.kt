package com.pr0gramm.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.api.pr0gramm.MessageConverter
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.feed.FeedType
import com.pr0gramm.app.services.DigestsService
import com.pr0gramm.app.services.InboxService
import com.pr0gramm.app.services.NotificationService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.launchWhenCreated
import com.pr0gramm.app.ui.compose.setComposeContent
import com.pr0gramm.app.ui.fragments.ConversationsScreen
import com.pr0gramm.app.ui.fragments.DigestsScreen
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.startActivity
import kotlinx.coroutines.launch

/**
 * The activity that displays the inbox.
 */
class InboxActivity : BaseAppCompatActivity("InboxActivity") {
    private val userService: UserService by instance()
    private val inboxService: InboxService by instance()
    private val configService: ConfigService by instance()
    private val digestsService: DigestsService by instance()
    private val notificationService: NotificationService by instance()

    private var requestedTab by mutableStateOf<InboxType?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.noActionBar)
        super.onCreate(savedInstanceState)

        if (!userService.isAuthorized) {
            startActivity<MainActivity>()
            finish()
            return
        }

        if (intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            Track.inboxNotificationClosed("clicked")
        }

        intent.getStringExtra(EXTRA_CONVERSATION_NAME)?.let { name ->
            ConversationActivity.start(this, name, skipInbox = true)
        }

        val config = configService.config()
        val showDigests = config.showDigestsInInbox ||
                (userService.userIsAdmin && config.showDigestsInInboxForAdmin)

        val tabs = buildList {
            add(InboxType.PRIVATE)
            add(InboxType.COMMENTS_OUT)
            add(InboxType.ALL)
            add(InboxType.COMMENTS_IN)
            add(InboxType.STALK)
            add(InboxType.NOTIFICATIONS)
            if (showDigests) add(InboxType.DIGESTS)
        }

        requestedTab = requestedTabFrom(intent)

        setComposeContent {
            InboxScreen(
                tabs = tabs,
                requestedTab = requestedTab,
                onRequestedTabHandled = { requestedTab = null },
                onBack = { finish() },
                onCommentClicked = ::openComment,
                onUserClicked = ::openUploads,
                onAnswerToCommentClicked = { message ->
                    startActivity(WriteMessageActivity.answerToComment(this, message))
                },
                onAnswerToPrivateMessage = { message ->
                    ConversationActivity.start(this, message.name, skipInbox = true)
                },
                onConversationClicked = { conversation ->
                    ConversationActivity.start(this, conversation.name, skipInbox = true)
                },
                onDigestItemClicked = { id -> openPost(id) },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        Track.inboxActivity()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedTab = requestedTabFrom(intent)
    }

    private fun requestedTabFrom(intent: Intent): InboxType? {
        val extras = intent.extras ?: return null
        return InboxType.entries.getOrNull(extras.getInt(EXTRA_INBOX_TYPE, 0))
    }

    private fun openComment(message: Message) {
        val uri = UriHelper.of(this).post(FeedType.NEW, message.itemId, message.id)
        openUri(uri, message.creationTime)
    }

    private fun openPost(id: Long) {
        val uri = UriHelper.of(this).post(FeedType.NEW, id)
        openUri(uri)
    }

    private fun openUploads(userId: Int, username: String) {
        openUri(UriHelper.of(this).uploads(username))
    }

    private fun openUri(uri: Uri, notificationTime: Instant? = null) {
        val intent = Intent(Intent.ACTION_VIEW, uri, this, MainActivity::class.java)
        intent.putExtra("MainActivity.NOTIFICATION_TIME", notificationTime)
        startActivity(intent)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun InboxScreen(
        tabs: List<InboxType>,
        requestedTab: InboxType?,
        onRequestedTabHandled: () -> Unit,
        onBack: () -> Unit,
        onCommentClicked: (Message) -> Unit,
        onUserClicked: (userId: Int, username: String) -> Unit,
        onAnswerToCommentClicked: (Message) -> Unit,
        onAnswerToPrivateMessage: (Message) -> Unit,
        onConversationClicked: (com.pr0gramm.app.api.pr0gramm.Api.Conversation) -> Unit,
        onDigestItemClicked: (Long) -> Unit,
    ) {
        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState(pageCount = { tabs.size })

        val counts by inboxService.unreadMessagesCount().collectAsState(initial = null)

        LaunchedEffect(requestedTab, tabs) {
            val index = requestedTab?.let { tabs.indexOf(it) } ?: return@LaunchedEffect
            if (index >= 0) {
                pagerState.scrollToPage(index)
            }
            onRequestedTabHandled()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            tabs.getOrNull(pagerState.currentPage)
                                ?.let { tabTitle(it, counts?.total?.minus(counts?.digests ?: 0) ?: 0) } ?: "")
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    tabs.forEachIndexed { index, type ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(tabShortTitle(type, counts, tabs)) },
                        )
                    }
                }

                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxSize()) { page ->
                    when (val type = tabs[page]) {
                        InboxType.PRIVATE -> ConversationsScreen(
                            inboxService = inboxService,
                            onConversationClicked = onConversationClicked,
                        )

                        InboxType.COMMENTS_OUT -> InboxMessagesScreen(
                            loader = writtenCommentsLoader(),
                            currentUsername = userService.name,
                            admin = userService.userIsAdmin,
                            showTypeLabel = false,
                            onCommentClicked = onCommentClicked,
                            onAnswerToCommentClicked = onAnswerToCommentClicked,
                            onAnswerToPrivateMessage = onAnswerToPrivateMessage,
                            onUserClicked = onUserClicked,
                        )

                        InboxType.ALL -> InboxMessagesScreen(
                            loader = apiMessageLoader(this@InboxActivity, syncOnLoad = true) { olderThan ->
                                inboxService.fetchAll(olderThan)
                            },
                            currentUsername = userService.name,
                            admin = userService.userIsAdmin,
                            showTypeLabel = true,
                            onCommentClicked = onCommentClicked,
                            onAnswerToCommentClicked = onAnswerToCommentClicked,
                            onAnswerToPrivateMessage = onAnswerToPrivateMessage,
                            onUserClicked = onUserClicked,
                        )

                        InboxType.COMMENTS_IN -> InboxMessagesScreen(
                            loader = apiMessageLoader(this@InboxActivity, syncOnLoad = true) { olderThan ->
                                inboxService.fetchComments(olderThan)
                            },
                            currentUsername = userService.name,
                            admin = userService.userIsAdmin,
                            showTypeLabel = false,
                            onCommentClicked = onCommentClicked,
                            onAnswerToCommentClicked = onAnswerToCommentClicked,
                            onAnswerToPrivateMessage = onAnswerToPrivateMessage,
                            onUserClicked = onUserClicked,
                        )

                        InboxType.STALK -> InboxMessagesScreen(
                            loader = apiMessageLoader(this@InboxActivity, syncOnLoad = true) { olderThan ->
                                inboxService.fetchFollows(olderThan)
                            },
                            currentUsername = userService.name,
                            admin = userService.userIsAdmin,
                            showTypeLabel = false,
                            onCommentClicked = onCommentClicked,
                            onAnswerToCommentClicked = onAnswerToCommentClicked,
                            onAnswerToPrivateMessage = onAnswerToPrivateMessage,
                            onUserClicked = onUserClicked,
                        )

                        InboxType.NOTIFICATIONS -> InboxMessagesScreen(
                            loader = apiMessageLoader(this@InboxActivity, syncOnLoad = true) { olderThan ->
                                inboxService.fetchNotifications(olderThan)
                            },
                            currentUsername = userService.name,
                            admin = userService.userIsAdmin,
                            showTypeLabel = false,
                            onCommentClicked = onCommentClicked,
                            onAnswerToCommentClicked = onAnswerToCommentClicked,
                            onAnswerToPrivateMessage = onAnswerToPrivateMessage,
                            onUserClicked = onUserClicked,
                        )

                        InboxType.DIGESTS -> DigestsScreen(
                            digestsService = digestsService,
                            onItemClicked = onDigestItemClicked,
                        )
                    }
                }
            }
        }

        LaunchedEffect(pagerState.currentPage) {
            if (tabs.getOrNull(pagerState.currentPage) == InboxType.COMMENTS_IN) {
                notificationService.cancelForUnreadComments()
            }
        }
    }

    private fun writtenCommentsLoader() = apiMessageLoader(this) { olderThan ->
        val name = userService.name ?: return@apiMessageLoader listOf()
        val userComments = inboxService.getUserComments(name, ContentType.AllSet, olderThan)
        userComments.comments.map { comment -> MessageConverter.of(userComments.user, comment) }
    }

    private fun tabTitle(type: InboxType, allUnread: Int): String {
        val res = when (type) {
            InboxType.PRIVATE -> R.string.inbox_type_private
            InboxType.COMMENTS_OUT -> R.string.inbox_type_comments_out
            InboxType.ALL -> R.string.inbox_type_all
            InboxType.COMMENTS_IN -> R.string.inbox_type_comments_in
            InboxType.STALK -> R.string.inbox_type_stalk
            InboxType.NOTIFICATIONS -> R.string.inbox_type_notifications
            InboxType.DIGESTS -> R.string.inbox_type_digests
        }

        return getString(res)
    }

    private fun tabShortTitle(
        type: InboxType,
        counts: com.pr0gramm.app.api.pr0gramm.Api.InboxCounts?,
        tabs: List<InboxType>,
    ): String {
        val count = when (type) {
            InboxType.PRIVATE -> counts?.messages
            InboxType.COMMENTS_IN -> counts?.comments
            InboxType.STALK -> counts?.follows
            InboxType.NOTIFICATIONS -> counts?.notifications
            InboxType.DIGESTS -> counts?.digests
            InboxType.ALL -> counts?.let { it.total - it.digests }
            InboxType.COMMENTS_OUT -> null
        } ?: 0

        val title = tabTitle(type, count)
        return if (count > 0) "$title ($count)" else title
    }

    companion object {
        const val EXTRA_INBOX_TYPE = "InboxActivity.inboxType"
        const val EXTRA_FROM_NOTIFICATION = "InboxActivity.fromNotification"
        const val EXTRA_CONVERSATION_NAME = "InboxActivity.conversationName"
    }
}
