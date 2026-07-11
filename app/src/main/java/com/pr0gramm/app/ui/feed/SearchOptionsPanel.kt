package com.pr0gramm.app.ui.feed

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.R
import com.pr0gramm.app.feed.Tags
import kotlin.math.pow
import kotlin.math.sign

data class SearchState(
    val queryTerm: String = "",
    val customExcludes: String = "",
    val excludedTags: Set<String> = emptySet(),
    val minScore: Int = 0,
)

data class SearchQuery(val combined: String, val queryTerm: String)

fun searchStateFromQueryTerm(queryTerm: String): SearchState = SearchState(queryTerm = queryTerm)

private fun roundScoreValue(score: Int): Int {
    val result = (score / 100.0).pow(2.0) * 90
    return (0.5 + result / 100.0).toInt() * 100 * score.sign
}

/**
 * Shows a search/filter bottom sheet. Call this conditionally when search is visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBottomSheet(
    initialState: SearchState = SearchState(),
    queryHint: String = "",
    showExtended: Boolean = true,
    recentSearches: List<String> = emptyList(),
    onSearch: (SearchQuery) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var state by remember { mutableStateOf(initialState) }
    var showSuggestions by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val filteredSuggestions = remember(state.queryTerm, recentSearches) {
        if (state.queryTerm.isBlank()) recentSearches.take(8)
        else recentSearches.filter { it.contains(state.queryTerm, ignoreCase = true) }.take(8)
    }

    fun handleSearch() {
        val baseTerm = state.queryTerm.trim()
        val specialTerms = mutableListOf<String>()

        val score = roundScoreValue(state.minScore)
        if (score != 0) {
            specialTerms += "s:$score"
        }

        val allExcludes = buildList {
            addAll(state.excludedTags)
            val custom = state.customExcludes.trim()
            if (custom.isNotEmpty()) {
                custom.split("\\s+".toRegex()).forEach { add(it) }
            }
        }
        if (allExcludes.isNotEmpty()) {
            specialTerms += "-(${allExcludes.joinToString("|")})"
        }

        val combined = if (specialTerms.isNotEmpty()) {
            val special = "!" + specialTerms.joinToString("&")
            Tags.joinAnd(baseTerm, special)
        } else {
            baseTerm
        }.replace('\n', ' ')

        onSearch(SearchQuery(combined = combined, queryTerm = baseTerm))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            // Search field row
            OutlinedTextField(
                value = state.queryTerm,
                onValueChange = {
                    state = state.copy(queryTerm = it)
                    showSuggestions = true
                },
                placeholder = { Text(queryHint) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.queryTerm.isNotEmpty()) {
                        IconButton(onClick = { state = state.copy(queryTerm = "") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { handleSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focusRequester),
            )

            // Recent searches dropdown
            if (showSuggestions && filteredSuggestions.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    filteredSuggestions.forEach { suggestion ->
                        TextButton(
                            onClick = {
                                state = state.copy(queryTerm = suggestion)
                                showSuggestions = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                suggestion,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // Extended section
            if (showExtended) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Score slider
                    val scoreDisplay = roundScoreValue(state.minScore)
                    val scoreLabel = when {
                        scoreDisplay > 0 -> stringResource(R.string.search_score_min, scoreDisplay)
                        scoreDisplay < 0 -> stringResource(R.string.search_score_max, scoreDisplay)
                        else -> stringResource(R.string.search_score_0)
                    }
                    Text(text = scoreLabel, style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = state.minScore.toFloat(),
                        onValueChange = { state = state.copy(minScore = it.toInt()) },
                        valueRange = -250f..800f,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Exclude checkboxes
                    Text(
                        stringResource(R.string.search_without_tags),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    val excludeOptions = listOf(
                        "f:sound" to "sound",
                        "video" to "video",
                        "f:repost" to "repost",
                        "m:ftb" to "ftb",
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        excludeOptions.forEach { (tag, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = tag in state.excludedTags,
                                    onCheckedChange = { checked ->
                                        state = state.copy(
                                            excludedTags = if (checked) state.excludedTags + tag
                                            else state.excludedTags - tag
                                        )
                                    },
                                )
                                Text(label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Custom excludes
                    Text(
                        stringResource(R.string.search_without_custom_tags),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = state.customExcludes,
                        onValueChange = { state = state.copy(customExcludes = it) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { handleSearch() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Bottom buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (showExtended) {
                    TextButton(onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://pr0gramm.com/new/2782197")
                        )
                        context.startActivity(intent)
                    }) {
                        Text(stringResource(R.string.search_advanced))
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }

                TextButton(onClick = { state = SearchState() }) {
                    Text(stringResource(R.string.action_done).uppercase())
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(onClick = { handleSearch() }) {
                    Text(stringResource(R.string.search_search))
                }
            }
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}
