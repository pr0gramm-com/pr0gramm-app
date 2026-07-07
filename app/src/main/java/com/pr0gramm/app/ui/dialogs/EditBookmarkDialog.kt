package com.pr0gramm.app.ui.dialogs

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.os.bundleOf
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.model.bookmark.Bookmark
import com.pr0gramm.app.orm.link
import com.pr0gramm.app.orm.migrate
import com.pr0gramm.app.orm.uri
import com.pr0gramm.app.services.BookmarkService
import com.pr0gramm.app.ui.MainActivity
import com.pr0gramm.app.ui.compose.ComposeDialogFragment
import com.pr0gramm.app.ui.compose.Pr0grammModalBottomSheet
import com.pr0gramm.app.util.activityIntent
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.fragmentArgument
import okio.ByteString.Companion.encodeUtf8

class EditBookmarkDialog : ComposeDialogFragment("EditBookmarkDialog") {
    private val bookmarkService: BookmarkService by instance()

    private val bookmarkTitle by fragmentArgument<String>("Bookmark")

    private val bookmark get() = bookmarkService.byTitle(bookmarkTitle)?.migrate()

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun DialogContent() {
        val context = LocalContext.current
        val shortcutSupported = remember {
            ShortcutManagerCompat.isRequestPinShortcutSupported(context)
        }

        var title by remember { mutableStateOf(bookmarkTitle) }
        var default by remember {
            mutableStateOf(bookmark?.let { it.uri == Settings.feedStartWithUri } == true)
        }

        Pr0grammModalBottomSheet(onDismissRequest = { dismiss() }) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.bookmark_editor_title),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Switch(
                        checked = default,
                        onCheckedChange = {
                            default = it
                            makeBookmarkTheDefaultFeed(it)
                        },
                    )
                    Text(stringResource(R.string.hint_bookmark_onload), modifier = Modifier.weight(1f))
                }

                Text(stringResource(R.string.hint_edit_bookmark))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(64) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (shortcutSupported) {
                        TextButton(onClick = { shortcutClicked() }) {
                            Text(stringResource(R.string.action_shortcut))
                        }
                    }

                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

                    TextButton(onClick = { deleteClicked() }) {
                        Text(stringResource(R.string.action_delete))
                    }

                    TextButton(onClick = { renameClicked(title) }) {
                        Text(stringResource(R.string.action_rename))
                    }
                }
            }
        }
    }

    private fun renameClicked(rawTitle: String) {
        val newTitle = rawTitle.take(64).trim()

        if (newTitle.length > 1 && newTitle != bookmarkTitle) {
            renameTo(newTitle)
        }

        dismiss()
    }

    private fun renameTo(newTitle: String) {
        this.bookmark?.let { bookmark ->
            bookmarkService.rename(bookmark, newTitle)
        }
    }

    private fun deleteClicked() {
        this.bookmark?.let { bookmark ->
            bookmarkService.delete(bookmark)
        }

        dismiss()
    }

    private fun makeBookmarkTheDefaultFeed(default: Boolean) {
        if (default) {
            Settings.feedStartWithUri = bookmark?.uri
        } else {
            Settings.feedStartWithUri = null
        }
    }

    private fun shortcutClicked() {
        val context = requireContext()
        val bookmark = this.bookmark ?: return

        // derive a unique id from bookmark link
        val id = "bookmark-${bookmark.link.encodeUtf8().md5()}"

        logger.debug { "Create shortcut for ${bookmark.uri} (id=$id)" }

        val intent = activityIntent<MainActivity>(context) {
            action = Intent.ACTION_VIEW
            data = bookmark.uri
        }

        val shortcutInfo = ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(bookmarkTitle)
                .setIntent(intent)
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_roundapp))
                .build()

        ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)

        dismiss()
    }

    companion object {
        fun forBookmark(b: Bookmark): EditBookmarkDialog {
            return EditBookmarkDialog().apply {
                arguments = bundleOf("Bookmark" to b.title)
            }
        }
    }
}
