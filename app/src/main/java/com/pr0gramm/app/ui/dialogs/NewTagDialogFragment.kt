package com.pr0gramm.app.ui.dialogs

import android.os.Bundle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.TagSuggestionService
import com.pr0gramm.app.ui.compose.ComposeDialogFragment
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.hideSoftKeyboard

/**
 */
class NewTagDialogFragment : ComposeDialogFragment("NewTagDialogFragment") {
    private val tagSuggestions: TagSuggestionService by instance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    @Composable
    override fun DialogContent() {
        var text by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }

        val questionable = remember(text) { tagSuggestions.containsQuestionableTag(text) }
        val suggestions = remember(text) { suggestionsFor(text) }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        AlertDialog(
            onDismissRequest = {},
            text = {
                Column {
                    Text(stringResource(R.string.add_new_tag_summary))

                    if (questionable) {
                        Text(
                            stringResource(R.string.add_tags_opinion_hint),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.dummy_tags)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )

                    if (suggestions.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            suggestions.forEach { suggestion ->
                                AssistChip(
                                    onClick = { text = applySuggestion(text, suggestion) },
                                    label = { Text(suggestion) },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onOkayClicked(text) }) {
                    Text(stringResource(R.string.dialog_action_add))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    hideSoftKeyboard()
                    dismiss()
                }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    /** Returns tag suggestions matching the currently edited (last) token. */
    private fun suggestionsFor(text: String): List<String> {
        val token = currentToken(text)
        if (token.length < 2) {
            return emptyList()
        }

        return tagSuggestions.tags
            .asSequence()
            .filter { it.startsWith(token, ignoreCase = true) && !it.equals(token, ignoreCase = true) }
            .take(10)
            .toList()
    }

    private fun onOkayClicked(text: String) {
        // split text into tags.
        val tags = text.split(',', '#').map { it.trim() }.filter { it.isNotEmpty() }

        // do nothing if the user had not typed any tags
        if (tags.isEmpty())
            return

        // inform parent
        (parentFragment as OnAddNewTagsListener).onNewTags(tags)

        hideSoftKeyboard()
        dismiss()
    }

    /**
     * The parent fragment must implement this interface.
     * It will be informed by this class if the user added tags.
     */
    interface OnAddNewTagsListener {
        /**
         * Called when the dialog finishes with new tags.
         */
        fun onNewTags(tags: List<String>)
    }

    companion object {
        private fun currentToken(text: String): String {
            val start = text.lastIndexOfAny(charArrayOf(',', '#')) + 1
            return text.substring(start).trim()
        }

        private fun applySuggestion(text: String, suggestion: String): String {
            val start = text.lastIndexOfAny(charArrayOf(',', '#')) + 1
            return text.substring(0, start) + (if (start == 0) "" else " ") + suggestion + ", "
        }
    }
}
