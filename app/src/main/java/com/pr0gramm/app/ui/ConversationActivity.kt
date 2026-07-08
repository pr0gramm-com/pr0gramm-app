package com.pr0gramm.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.pr0gramm.app.services.NotificationService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.launchWhenCreated
import com.pr0gramm.app.ui.compose.setComposeContent
import com.pr0gramm.app.ui.fragments.conversation.ConversationScreen
import com.pr0gramm.app.ui.fragments.conversation.ConversationViewModel
import com.pr0gramm.app.util.activityIntent
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.startActivity

/**
 * Displays a single conversation thread with a private-message partner.
 */
class ConversationActivity : BaseAppCompatActivity("ConversationActivity") {
    private val notificationService: NotificationService by instance()

    private val conversationName: String by lazy {
        intent.getStringExtra(EXTRA_CONVERSATION_NAME) ?: error("missing conversation name")
    }

    private val model: ConversationViewModel by viewModels {
        ConversationViewModel(instance(), conversationName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.noActionBar)
        super.onCreate(savedInstanceState)

        if (intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            Track.inboxNotificationClosed("clicked")
        }

        setComposeContent {
            ConversationScreen(
                conversationName = conversationName,
                model = model,
                notificationService = notificationService,
                onBack = { finish() },
                onOpenProfile = { openUserProfile() },
                onDeleteConversation = { deleteConversation() },
            )
        }
    }

    private fun openUserProfile() {
        startActivity<MainActivity> { intent ->
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse("https://pr0gramm.com/user/$conversationName")
        }
    }

    private fun deleteConversation() {
        launchWhenCreated(busyIndicator = true) {
            model.delete(conversationName)
            finish()
        }
    }

    companion object {
        const val EXTRA_FROM_NOTIFICATION = "ConversationActivity.fromNotification"
        const val EXTRA_CONVERSATION_NAME = "ConversationActivity.name"

        fun start(context: Context, name: String, skipInbox: Boolean = false) {
            val activities = mutableListOf<Intent>()

            if (!skipInbox) {
                activities += activityIntent<InboxActivity>(context)
            }

            activities += activityIntent<ConversationActivity>(context) {
                putExtra(EXTRA_CONVERSATION_NAME, name)
            }

            context.startActivities(activities.toTypedArray())
        }
    }
}
