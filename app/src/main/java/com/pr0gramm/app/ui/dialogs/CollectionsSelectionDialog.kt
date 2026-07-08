package com.pr0gramm.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import androidx.core.text.buildSpannedString
import androidx.core.text.italic
import androidx.compose.runtime.collectAsState
import com.pr0gramm.app.R
import com.pr0gramm.app.services.CollectionItemsService
import com.pr0gramm.app.services.CollectionsService
import com.pr0gramm.app.services.PostCollection
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.launchUntilDestroy
import com.pr0gramm.app.ui.base.launchWhenCreated
import com.pr0gramm.app.ui.compose.ComposeDialogFragment
import com.pr0gramm.app.ui.compose.Pr0grammModalBottomSheet
import com.pr0gramm.app.ui.compose.components.Username
import com.pr0gramm.app.ui.configureNewStyle
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.fragmentArgument
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class CollectionsSelectionDialog : ComposeDialogFragment("CollectionsSelectionDialog") {
    private val itemId: Long by fragmentArgument("itemId")
    private val collectionsService: CollectionsService by instance()
    private val collectionItemsService: CollectionItemsService by instance()
    private val userService: UserService by instance()

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun DialogContent() {
        LaunchedEffect(Unit) {
            // always refresh once in background when we show this
            collectionsService.refresh()
        }

        val collections by collectionsService.collections.asFlow().collectAsState(initial = emptyList())
        var selection by rememberSaveable {
            mutableStateOf(collectionItemsService.collectionsContaining(itemId).toSet())
        }

        val userIsPremium = userService.userIsPremium

        Pr0grammModalBottomSheet(
            onDismissRequest = { dismissAllowingStateLoss() },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.collections_save_to),
                    style = MaterialTheme.typography.bodyLarge,
                )

                TextButton(
                    enabled = userIsPremium || collections.isEmpty(),
                    onClick = { CollectionDialog().show(parentFragmentManager, null) },
                ) {
                    Text(stringResource(R.string.collections_new))
                }
            }

            HorizontalDivider()

            for (collection in collections) {
                val isSelected = collection.id in selection
                val isEnabled = collection.isDefault || userIsPremium

                CollectionRow(
                    collection = collection,
                    selected = isSelected,
                    enabled = isEnabled || isSelected,
                    onToggle = {
                        val newSelected = !isSelected
                        selection = if (newSelected) selection + collection.id else selection - collection.id
                        onCollectionClicked(collection, newSelected)
                    },
                    onEdit = {
                        CollectionDialog.newInstance(collection).show(parentFragmentManager, null)
                    },
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { dismissAllowingStateLoss() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.collections_done), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    @Composable
    private fun CollectionRow(
        collection: PostCollection,
        selected: Boolean,
        enabled: Boolean,
        onToggle: () -> Unit,
        onEdit: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onToggle() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, enabled = enabled, onCheckedChange = { onToggle() })

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(collection.title, maxLines = 1)

                val owner = collection.owner
                if (owner != null) {
                    Text(" (")
                    Username(name = owner.name, mark = owner.mark)
                    Text(")")
                }
            }

            val iconRes = if (collection.isPublic) R.drawable.ic_collection_public else R.drawable.ic_collection_private
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )

            if (!collection.isCuratorCollection) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(onClick = onEdit),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_collection_edit),
                        contentDescription = stringResource(R.string.collection_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(6.dp)
                            .fillMaxWidth(),
                    )
                }
            } else {
                Box(modifier = Modifier.size(36.dp))
            }
        }
    }

    private fun onCollectionClicked(collection: PostCollection, isSelected: Boolean) {
        logger.info { "Updating state for ${collection.key}: $isSelected" }

        launchWhenCreated {
            withContext(NonCancellable) {
                if (isSelected) {
                    // TODO error handling
                    collectionItemsService.addToCollection(itemId, collection.id)
                } else {
                    collectionItemsService.removeFromCollection(collection.id, itemId)
                }
            }
        }
    }

    companion object {
        fun newInstance(itemId: Long): CollectionsSelectionDialog {
            return CollectionsSelectionDialog().arguments {
                putLong("itemId", itemId)
            }
        }

        fun addToCollection(parent: Fragment, itemId: Long) {
            val fragmentView = parent.view ?: return
            val context = fragmentView.context

            val collectionItemsService: CollectionItemsService = context.injector.instance()

            // if the item is already part of a collection, directly show the dialog.
            if (collectionItemsService.isItemInAnyCollection(itemId)) {
                return newInstance(itemId).show(parent.childFragmentManager, null)
            }

            val collectionsService: CollectionsService = context.injector.instance()

            parent.launchWhenCreated {
                val snackbar = Snackbar
                    .make(fragmentView, R.string.collecton_adding, Snackbar.LENGTH_LONG)
                    .configureNewStyle()

                // show info that we are currently doing the request
                snackbar.show()

                // add to default collection
                val result = collectionItemsService.addToCollection(itemId, collectionId = null)

                when (result) {
                    is CollectionItemsService.Result.ItemAdded -> {
                        val collection = collectionsService.byId(result.collectionId)

                        val messageText: CharSequence = buildSpannedString {
                            append(context.getString(R.string.collecton_added))

                            if (collection != null) {
                                append(" ")
                                italic { append(collection.uniqueTitle) }
                            }
                        }

                        snackbar.setText(messageText)
                        snackbar.duration = Snackbar.LENGTH_SHORT

                        snackbar.setAction(R.string.action_change) {
                            newInstance(itemId).show(parent.childFragmentManager, null)
                        }

                        snackbar.show()
                    }

                    else -> {}
                }
            }
        }
    }
}
