package com.pr0gramm.app.ui.fragments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.AdminService
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.compose.ComposeDialogFragment
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.fragmentArgument

/**
 */
class TagsDetailsDialog : ComposeDialogFragment("TagsDetailsDialog") {
    private val adminService: AdminService by instance()

    private val itemId: Long by fragmentArgument(name = KEY_FEED_ITEM_ID)

    @Composable
    override fun DialogContent() {
        var tags by remember { mutableStateOf<List<Api.TagDetails.TagInfo>?>(null) }
        val selected = remember { SnapshotStateMap<Long, Boolean>() }

        var blockUser by remember { mutableStateOf(false) }
        var blockUserDays by remember { mutableStateOf("1") }

        LaunchedEffect(itemId) {
            tags = adminService.tagsDetails(itemId).tags.sortedBy { it.confidence }
        }

        AlertDialog(
            onDismissRequest = { dismiss() },
            text = {
                Column {
                    val loaded = tags
                    if (loaded == null) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(loaded, key = { it.id }) { tag ->
                                TagRow(
                                    tag = tag,
                                    checked = selected[tag.id] == true,
                                    onCheckedChange = { selected[tag.id] = it },
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = blockUser, onCheckedChange = { blockUser = it })
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
                }
            },
            confirmButton = {
                TextButton(onClick = { onDeleteClicked(selected, blockUser, blockUserDays) }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { dismiss() }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    @Composable
    private fun TagRow(
        tag: Api.TagDetails.TagInfo,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(checked = checked, onCheckedChange = onCheckedChange)
                Text(tag.tag)
            }

            Text(
                String.format("%s, +%d, -%d", tag.user, tag.up, tag.down),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 48.dp),
            )
        }
    }

    private fun onDeleteClicked(
        selected: SnapshotStateMap<Long, Boolean>,
        blockUser: Boolean,
        blockUserDays: String,
    ) {
        val selectedIds = selected.filterValues { it }.keys
        if (selectedIds.isEmpty()) {
            dismiss()
            return
        }

        val blockAmount = if (blockUser) blockUserDays.toFloatOrNull() else null

        launchWhenStarted(busyIndicator = true) {
            adminService.deleteTags(itemId, selectedIds.toList(), blockAmount)
            dismiss()
        }
    }

    companion object {
        private const val KEY_FEED_ITEM_ID = "TagsDetailsDialog__feedItem"

        fun newInstance(itemId: Long) = TagsDetailsDialog().arguments {
            putLong(KEY_FEED_ITEM_ID, itemId)
        }
    }
}
