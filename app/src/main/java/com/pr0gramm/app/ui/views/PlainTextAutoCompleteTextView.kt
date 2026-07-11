package com.pr0gramm.app.ui.views

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView
import androidx.core.view.inputmethod.EditorInfoCompat
import com.pr0gramm.app.Settings

class PlainMultiTextAutoCompleteTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatMultiAutoCompleteTextView(context, attrs, defStyleAttr) {

    init {
        adjustImeOptions(this)
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        return if (id == android.R.id.paste) {
            handlePlainTextPaste(this) { super.onTextContextMenuItem(it) }
        } else {
            super.onTextContextMenuItem(id)
        }
    }
}

inline fun handlePlainTextPaste(view: EditText, superCall: (id: Int) -> Boolean): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        return superCall(android.R.id.pasteAsPlainText)
    }

    val selectionStartPrePaste = view.selectionStart
    val result = superCall(android.R.id.paste)

    var text: CharSequence = view.text
    var selectionStart = view.selectionStart
    var selectionEnd = view.selectionEnd

    val startIndex = selectionStart - 1
    val pasteStringLength = selectionStart - selectionStartPrePaste

    if (pasteStringLength == 1 && text[startIndex] == '\uFFFC') {
        val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null) {
            val item = clip.getItemAt(0)
            val sb = StringBuilder(text)
            val url = item.text.toString()
            sb.replace(selectionStartPrePaste, selectionStart, url)
            text = sb.toString()
            selectionStart = selectionStartPrePaste + url.length
            selectionEnd = selectionStart
        }
    }

    view.setText(text.toString(), TextView.BufferType.EDITABLE)
    view.setSelection(selectionStart, selectionEnd)

    return result
}

fun adjustImeOptions(view: EditText) {
    if (Settings.privateInput) {
        view.imeOptions = view.imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
    }
}
