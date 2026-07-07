package com.pr0gramm.app.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.R
import com.pr0gramm.app.services.CollectionItemsService
import com.pr0gramm.app.services.CollectionsService
import com.pr0gramm.app.services.PostCollection
import com.pr0gramm.app.services.Result
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.launchUntilDestroy
import com.pr0gramm.app.ui.compose.ComposeDialogFragment
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.optionalFragmentArgument
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class CollectionDialog : ComposeDialogFragment("CollectionDialog") {

    private val collectionsService: CollectionsService by instance()
    private val collectionItemService: CollectionItemsService by instance()
    private val userService: UserService by instance()

    private val editCollectionId: Long? by optionalFragmentArgument(name = "editCollectionId")

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun DialogContent() {
        val editCollection: PostCollection? = remember { editCollectionId?.let { collectionsService.byId(it) } }

        var name by remember { mutableStateOf(editCollection?.title ?: "") }
        var isPublic by remember { mutableStateOf(editCollection?.isPublic == true) }
        var isDefault by remember {
            mutableStateOf(editCollection?.isDefault ?: (collectionsService.defaultCollection == null))
        }
        var showDeleteConfirm by remember { mutableStateOf(false) }

        val nameValid = if (editCollection == null) {
            collectionsService.isValidNameForNewCollection(name.trim())
        } else {
            name.trim().length >= 2
        }

        AlertDialog(
            onDismissRequest = { dismissAllowingStateLoss() },
            title = {
                Text(
                    stringResource(
                        if (editCollection != null) R.string.collection_edit else R.string.collection_new
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.collection_title)) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Switch(
                            checked = isDefault,
                            enabled = userService.userIsPremium,
                            onCheckedChange = { isDefault = it },
                        )
                        Text(stringResource(R.string.collection_default), modifier = Modifier.weight(1f))
                    }

                    PrivacyOption(
                        selected = !isPublic,
                        text = stringResource(R.string.collection_prive),
                        icon = R.drawable.ic_collection_private,
                        onClick = { isPublic = false },
                    )
                    PrivacyOption(
                        selected = isPublic,
                        text = stringResource(R.string.collection_public),
                        icon = R.drawable.ic_collection_public,
                        onClick = { isPublic = true },
                    )

                    if (editCollection != null) {
                        TextButton(onClick = { showDeleteConfirm = true }) {
                            Text(stringResource(R.string.action_delete))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = nameValid,
                    onClick = {
                        val trimmed = name.trim()
                        if (editCollection == null) {
                            createCollection(trimmed, isPublic, isDefault)
                        } else {
                            updateCollection(editCollection.id, trimmed, isPublic, isDefault)
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissAllowingStateLoss() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )

        if (showDeleteConfirm && editCollection != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                text = { Text(stringResource(R.string.collection_delete_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        deleteCollection(editCollection.id)
                    }) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }

    @Composable
    private fun PrivacyOption(selected: Boolean, text: String, icon: Int, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onClick)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Icon(painterResource(icon), contentDescription = null)
            Text(text)
        }
    }

    private fun deleteCollection(collectionId: Long) {
        launchUntilDestroy(busyIndicator = true) {
            withContext(NonCancellable) {
                val result = collectionsService.delete(collectionId)
                if (result is Result.Success) {
                    collectionItemService.deleteCollection(collectionId)
                }
            }

            dismissAllowingStateLoss()
        }
    }

    private fun createCollection(name: String, public: Boolean, default: Boolean) {
        launchUntilDestroy(busyIndicator = true) {
            withContext(NonCancellable) {
                logger.info { "Create collection with the name: '$name' public=$public, default=$default" }
                collectionsService.create(name, public, default)
            }

            dismissAllowingStateLoss()
        }
    }

    private fun updateCollection(id: Long, name: String, public: Boolean, default: Boolean) {
        launchUntilDestroy(busyIndicator = true) {
            withContext(NonCancellable) {
                logger.info { "Edit collection $id: name='$name', public=$public, default=$default" }
                collectionsService.edit(id, name, public, default)
            }

            dismissAllowingStateLoss()
        }
    }

    companion object {
        fun newInstance(collectionToEdit: PostCollection? = null): CollectionDialog {
            return CollectionDialog().arguments {
                if (collectionToEdit != null) {
                    putLong("editCollectionId", collectionToEdit.id)
                }
            }
        }
    }
}
