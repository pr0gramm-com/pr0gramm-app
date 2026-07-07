package com.pr0gramm.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.R
import com.pr0gramm.app.model.update.Change
import com.pr0gramm.app.model.update.ChangeGroup
import com.pr0gramm.app.ui.compose.ComposeDialogFragment
import com.pr0gramm.app.util.AndroidUtility
import com.squareup.moshi.adapter
import okio.buffer
import okio.source
import java.io.IOException

class ChangeLogDialog : ComposeDialogFragment("ChangeLogDialog") {
    @Composable
    override fun DialogContent() {
        val context = LocalContext.current
        val changeGroups = remember { loadChangelog(context) }

        AlertDialog(
            onDismissRequest = { dismiss() },
            confirmButton = {
                TextButton(onClick = { dismiss() }) {
                    Text(stringResource(R.string.okay))
                }
            },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    changeGroups.forEach { group ->
                        item {
                            Text(
                                "Version 1.${group.version}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }

                        items(group.changes.size) { idx ->
                            ChangeRow(group.changes[idx])
                        }
                    }
                }
            },
        )
    }

    @Composable
    private fun ChangeRow(change: Change) {
        val accent = MaterialTheme.colorScheme.primary

        val text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = if (change.type == "Neu") accent else Color.Unspecified,
                )
            ) {
                append(change.type)
            }

            append("  ")
            appendWithIssueLinks(change.change)
        }

        Text(text, style = MaterialTheme.typography.bodyMedium)
    }

    companion object {
        private val githubIssue = Regex("#\\d+\\b")

        private fun AnnotatedString.Builder.appendWithIssueLinks(text: String) {
            var last = 0
            for (match in githubIssue.findAll(text)) {
                append(text.substring(last, match.range.first))

                val issue = match.value.substring(1)
                withLink(
                    LinkAnnotation.Url("https://github.com/pr0gramm-com/pr0gramm-app/issues/$issue")
                ) {
                    append(match.value)
                }

                last = match.range.last + 1
            }

            append(text.substring(last))
        }

        private fun loadChangelog(context: Context): List<ChangeGroup> {
            try {
                context.resources.openRawResource(R.raw.changelog).use { input ->
                    val source = input.source().buffer()
                    return MoshiInstance.adapter<List<ChangeGroup>>().nonNull().fromJson(source)!!
                }
            } catch (error: IOException) {
                AndroidUtility.logToCrashlytics(error)
                return emptyList()
            }
        }
    }
}
