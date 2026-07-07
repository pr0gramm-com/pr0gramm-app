package com.pr0gramm.app.ui.fragments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.parcel.getParcelableOrNull
import com.pr0gramm.app.services.AdminService
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.compose.ComposeDialogFragment
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.di.instance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 */
class ItemUserAdminDialog : ComposeDialogFragment("ItemUserAdminDialog") {
    private val adminService: AdminService by instance()
    private val configService: ConfigService by instance()

    private val reasons: List<String> by lazy { configService.config().adminReasons }

    // one of those must be set.
    private val user: String? by lazy { arguments?.getString(KEY_USER) }
    private val item: FeedItem? by lazy { arguments?.getParcelableOrNull(KEY_FEED_ITEM) }
    private val comment: Long? by lazy { arguments?.getLong(KEY_COMMENT)?.takeIf { it > 0 } }

    private enum class Mode { COMMENT, USER, ITEM }

    @Composable
    override fun DialogContent() {
        val mode = remember {
            when {
                comment != null -> Mode.COMMENT
                user != null -> Mode.USER
                else -> Mode.ITEM
            }
        }

        var customReason by remember { mutableStateOf("") }
        // in ban-user mode the user is always blocked (checkbox checked + disabled).
        var blockUser by remember { mutableStateOf(mode == Mode.USER) }
        var blockUserDays by remember { mutableStateOf("1") }
        var blockModeIndex by remember { mutableStateOf(0) }
        var softDelete by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { dismiss() },
            text = {
                Column {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(reasons) { reason ->
                            Text(
                                reason,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { customReason = reason }
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customReason,
                        onValueChange = { customReason = it },
                        label = { Text(stringResource(R.string.reason)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )

                    if (mode == Mode.COMMENT) {
                        LabeledCheckbox(
                            checked = softDelete,
                            onCheckedChange = { softDelete = it },
                            label = stringResource(R.string.delete_comment_soft),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = blockUser,
                            onCheckedChange = { blockUser = it },
                            enabled = mode != Mode.USER,
                        )
                        Text(stringResource(R.string.hint_block_user_for))

                        OutlinedTextField(
                            value = blockUserDays,
                            onValueChange = { blockUserDays = it },
                            enabled = blockUser,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.padding(start = 8.dp).width(96.dp),
                        )
                    }

                    BlockModeDropdown(
                        enabled = blockUser,
                        selectedIndex = blockModeIndex,
                        onSelected = { blockModeIndex = it },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirmClicked(customReason, blockUser, blockUserDays, blockModeIndex, softDelete)
                }) { Text(stringResource(R.string.okay)) }
            },
            dismissButton = {
                TextButton(onClick = { dismiss() }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    @Composable
    private fun LabeledCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(label)
        }
    }

    @Composable
    private fun BlockModeDropdown(enabled: Boolean, selectedIndex: Int, onSelected: (Int) -> Unit) {
        val modes = listOf(
            stringResource(R.string.hint_block_mode__default),
            stringResource(R.string.hint_block_mode__single),
            stringResource(R.string.hint_block_mode__branch),
        )

        var expanded by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            OutlinedTextField(
                value = modes[selectedIndex],
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { expanded = true },
            )

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                modes.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelected(index)
                            expanded = false
                        },
                    )
                }
            }
        }
    }

    private fun onConfirmClicked(
        reason: String,
        blockUser: Boolean,
        blockUserDays: String,
        blockModeIndex: Int,
        softDelete: Boolean,
    ) {
        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty()) {
            return
        }

        launchWhenStarted(busyIndicator = true) {
            withContext(NonCancellable + Dispatchers.Default) {
                user?.let { blockUser(it, trimmedReason, blockUser, blockUserDays, blockModeIndex) }
                item?.let { deleteItem(it, trimmedReason, blockUser, blockUserDays) }
                comment?.let { deleteComment(it, trimmedReason, softDelete) }
            }

            dismiss()
        }
    }

    private suspend fun deleteItem(item: FeedItem, reason: String, block: Boolean, blockDays: String) {
        val banUserDays = if (block) blockDays.toFloatOrNull() else null
        adminService.deleteItem(item, reason, banUserDays)
    }

    private suspend fun blockUser(
        user: String,
        reason: String,
        block: Boolean,
        blockDays: String,
        blockModeIndex: Int,
    ) {
        if (!block)
            return

        val modes = listOf(Api.BanMode.Default, Api.BanMode.Single, Api.BanMode.Branch)
        val mode = modes.getOrNull(blockModeIndex) ?: Api.BanMode.Default

        val banUserDays = blockDays.toFloatOrNull() ?: 0f
        adminService.banUser(user, reason, banUserDays, mode)
    }

    private suspend fun deleteComment(commentId: Long, reason: String, softDelete: Boolean) {
        adminService.deleteComment(!softDelete, commentId, reason)
    }

    companion object {
        private const val KEY_USER = "userId"
        private const val KEY_FEED_ITEM = "feedItem"
        private const val KEY_COMMENT = "commentId"

        fun forItem(item: FeedItem) = ItemUserAdminDialog().arguments {
            putParcelable(KEY_FEED_ITEM, item)
        }

        fun forUser(name: String) = ItemUserAdminDialog().arguments {
            putString(KEY_USER, name)
        }

        fun forComment(commentId: Long, user: String) = ItemUserAdminDialog().arguments {
            putLong(KEY_COMMENT, commentId)
            putString(KEY_USER, user)
        }
    }
}
