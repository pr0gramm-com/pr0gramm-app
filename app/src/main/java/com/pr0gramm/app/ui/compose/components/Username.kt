package com.pr0gramm.app.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pr0gramm.app.UserClassesService
import com.pr0gramm.app.ui.compose.theme.Pr0grammTheme
import com.pr0gramm.app.util.di.injector

/**
 * Renders a username with the user's class symbol (colored) and an optional "OP" badge.
 *
 * Replaces `UsernameView` / `appendUsernameAndMark`. The user class for [mark] is resolved from
 * [UserClassesService] via DI; the composable recomposes when the config (and thus the class
 * colors) changes.
 */
@Composable
fun Username(
    name: String,
    mark: Int,
    modifier: Modifier = Modifier,
    op: Boolean = false,
) {
    val context = LocalContext.current
    val service = remember(context) { context.injector.instance<UserClassesService>() }

    // Recompose when the user-class configuration changes.
    val version by service.onChange.collectAsState(initial = Unit)
    val userClass = remember(mark, version) { service.get(mark) }

    Username(
        name = name,
        symbol = userClass.symbol,
        symbolColor = Color(userClass.color),
        modifier = modifier,
        op = op,
    )
}

/**
 * Lower-level, preview-friendly variant that takes the already-resolved user class symbol/color.
 */
@Composable
fun Username(
    name: String,
    symbol: String,
    symbolColor: Color,
    modifier: Modifier = Modifier,
    op: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (op) {
            OpBadge()
        }

        Text(
            text = usernameText(name, symbol, symbolColor),
            maxLines = 1,
        )
    }
}

private fun usernameText(name: String, symbol: String, symbolColor: Color): AnnotatedString {
    return buildAnnotatedString {
        append(name)
        append("\u2009")
        withStyle(SpanStyle(color = symbolColor)) {
            append(symbol)
        }
    }
}

@Composable
private fun OpBadge() {
    Text(
        text = "OP",
        color = MaterialTheme.colorScheme.onSecondary,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier
            .padding(end = 6.dp)
            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(2.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
    )
}

@Preview
@Composable
private fun UsernamePreview() {
    Pr0grammTheme {
        Username(name = "Mopsalarm", symbol = "\u2605", symbolColor = Color(0xFFEE4488), op = true)
    }
}
