package com.pr0gramm.app.ui.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.util.DurationFormat

/**
 * How the points portion of the [SenderInfo] stats line should be rendered.
 */
sealed interface SenderPoints {
    /** No points are shown (e.g. below the vote threshold or before revealing). */
    data object Hidden : SenderPoints

    /** Points exist but are not revealed yet, shown as three dots. */
    data object Unknown : SenderPoints

    /** A concrete score. */
    data class Value(val points: Int) : SenderPoints
}

/**
 * The sender/uploader info line: a username above a stats line (points + time since posting),
 * with an optional inline "answer" action. Replaces `SenderInfoView`.
 */
@Composable
fun SenderInfo(
    name: String,
    mark: Int,
    date: Instant,
    modifier: Modifier = Modifier,
    op: Boolean = false,
    points: SenderPoints = SenderPoints.Hidden,
    onSenderClick: (() -> Unit)? = null,
    onStatsLongClick: (() -> Unit)? = null,
    answerText: String? = null,
    onAnswerClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    val statsText = remember(points, date) {
        buildString {
            when (points) {
                is SenderPoints.Hidden -> {}
                is SenderPoints.Unknown -> append("\u25CF\u25CF\u25CF   ")
                is SenderPoints.Value -> {
                    val id = if (points.points == 1) R.string.points_one else R.string.points_more
                    append(context.getString(id, points.points))
                    append("   ")
                }
            }

            append(DurationFormat.timeSincePastPointInTime(context, date, short = true))
        }
    }

    Column(modifier = modifier) {
        if (name.isNotBlank()) {
            Username(
                name = name,
                mark = mark,
                op = op,
                modifier = if (onSenderClick != null) {
                    Modifier.clickable { onSenderClick() }
                } else {
                    Modifier
                },
            )
        }

        Row {
            Text(
                text = statsText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (onStatsLongClick != null) {
                    Modifier.clickable { onStatsLongClick() }
                } else {
                    Modifier
                },
            )

            if (answerText != null && onAnswerClick != null) {
                Text(
                    text = answerText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clickable { onAnswerClick() },
                )
            }
        }
    }
}
