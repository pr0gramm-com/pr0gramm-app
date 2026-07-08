package com.pr0gramm.app.ui.compose.components

import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pr0gramm.app.util.Linkify

/**
 * Renders [text] with pr0gramm's [Linkify] applied (clickable pr0gramm item/comment/user links,
 * generic web links, inline voice-message playback). `Linkify` is entirely `TextView`/span based
 * (movement methods, `ClickableSpan`s, `MediaPlayer` for voice messages) with no Compose
 * equivalent, so this hosts a plain [AppCompatTextView] via [AndroidView] rather than
 * reimplementing that whole system - the same interop pattern already used for `MessageView` in
 * `WriteMessageActivity`.
 */
@Composable
fun LinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle? = null,
) {
    val colorArgb = remember(color, style) {
        when {
            color.isSpecified -> color.toArgb()
            style?.color?.isSpecified == true -> style.color.toArgb()
            else -> null
        }
    }

    val fontSizeSp = remember(style) {
        style?.fontSize?.takeIf { it.isSpecified }?.value
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            AppCompatTextView(context).apply {
                setTextIsSelectable(true)
            }
        },
        update = { textView ->
            colorArgb?.let { textView.setTextColor(it) }
            fontSizeSp?.let { textView.textSize = it }
            Linkify.linkifyClean(textView, text)
        },
    )
}
