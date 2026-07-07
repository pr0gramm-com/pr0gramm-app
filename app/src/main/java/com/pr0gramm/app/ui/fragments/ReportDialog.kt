package com.pr0gramm.app.ui.fragments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.services.ContactService
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.compose.ComposeDialogFragment
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.fragmentArgument
import com.pr0gramm.app.util.optionalFragmentArgument
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 */
class ReportDialog : ComposeDialogFragment("ReportDialog") {
    private val contactService: ContactService by instance()
    private val config: Config by instance()

    private var itemId: Long by fragmentArgument()
    private var commentId: Long? by optionalFragmentArgument(0)

    @Composable
    override fun DialogContent() {
        val reasons = remember { config.reportReasons }
        var selected by remember { mutableIntStateOf(-1) }

        AlertDialog(
            onDismissRequest = { dismiss() },
            title = { Text("Du möchtest diesen Beitrag melden") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Bitte stelle sicher, dass der Beitrag tatsächlich gegen unsere Regeln " +
                                "verstößt. Missbrauch der Melden Funktion wird nicht geduldet.",
                    )

                    reasons.forEachIndexed { index, reason ->
                        Row(
                            reason = reason,
                            selected = index == selected,
                            onClick = { selected = index },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selected >= 0,
                    onClick = { onConfirmClicked(reasons.getOrNull(selected)) },
                ) {
                    Text(stringResource(R.string.okay))
                }
            },
            dismissButton = {
                TextButton(onClick = { dismiss() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    @Composable
    private fun Row(reason: String, selected: Boolean, onClick: () -> Unit) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(reason, modifier = Modifier.padding(start = 8.dp))
        }
    }

    private fun onConfirmClicked(reason: String?) {
        reason ?: return

        launchWhenStarted(busyIndicator = true) {
            withContext(NonCancellable) {
                contactService.report(itemId, commentId ?: 0, reason)
            }

            dismiss()
        }
    }

    companion object {
        fun forItem(item: FeedItem) = ReportDialog().apply { this.itemId = item.id }

        fun forComment(item: FeedItem, commentId: Long) = ReportDialog().apply {
            this.itemId = item.id
            this.commentId = commentId
        }
    }
}
