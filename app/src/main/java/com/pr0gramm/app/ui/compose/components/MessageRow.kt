package com.pr0gramm.app.ui.compose.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.api.pr0gramm.MessageType
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.ui.compose.image.NetworkImage
import com.pr0gramm.app.util.UserDrawables

/**
 * A single row in a message list (inbox / written-comments / stalk / notifications), replacing
 * `MessageView` + `row_inbox_message.xml` / `row_inbox_all_message.xml`.
 *
 * @param showTypeLabel shows the message-type caption above the row (used by the combined "all"
 * inbox tab, matching `row_inbox_all_message.xml`'s `message_view_generic` layout).
 */
@Composable
fun MessageRow(
    message: Message,
    currentUsername: String?,
    admin: Boolean,
    showTypeLabel: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onSenderClick: (() -> Unit)? = null,
    onAnswerClick: (() -> Unit)? = null,
) {
    val scoreVisibleThreshold = remember { Instant.now() - Duration.hours(1) }

    val visible = (currentUsername != null && message.name.equals(currentUsername, ignoreCase = true)) ||
            message.creationTime.isBefore(scoreVisibleThreshold)

    val points = when {
        message.type != MessageType.COMMENT -> SenderPoints.Hidden
        admin || visible -> SenderPoints.Value(message.score)
        else -> SenderPoints.Unknown
    }

    val answerText = when (message.type) {
        MessageType.COMMENT -> stringResource(R.string.action_answer)
        MessageType.MESSAGE -> stringResource(R.string.action_to_conversation)
        else -> null
    }

    val typeLabel = when (message.type) {
        MessageType.COMMENT -> stringResource(R.string.message_type_comment)
        MessageType.MESSAGE -> stringResource(R.string.message_type_message)
        MessageType.STALK -> stringResource(R.string.message_type_stalk)
        else -> stringResource(R.string.message_type_notification)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp),
    ) {
        if (showTypeLabel) {
            Text(
                text = typeLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Row {
            MessageAvatar(message = message, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)))

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                LinkifiedText(
                    text = message.message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                SenderInfo(
                    name = message.name,
                    mark = message.mark,
                    date = message.creationTime,
                    points = points,
                    onSenderClick = onSenderClick,
                    answerText = answerText,
                    onAnswerClick = if (answerText != null) onAnswerClick else null,
                )
            }
        }
    }
}

@Composable
private fun MessageAvatar(message: Message, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val thumbnail = message.thumbnail

    if (thumbnail != null) {
        val blur = remember(message.flags) { ContentType.firstOf(message.flags) !in Settings.contentType }

        NetworkImage(
            model = "https://thumb.pr0gramm.com/$thumbnail",
            contentDescription = null,
            blur = blur,
            modifier = modifier,
        )
    } else {
        val userDrawables = remember(context) { UserDrawables(context) }

        val bitmap = remember(message.name) {
            (userDrawables.drawable(message.name) as BitmapDrawable).bitmap.asImageBitmap()
        }

        Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
    }
}
