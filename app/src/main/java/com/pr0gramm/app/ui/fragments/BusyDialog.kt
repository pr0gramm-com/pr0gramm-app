package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.compose.BusyOverlayContent
import com.pr0gramm.app.ui.compose.theme.Pr0grammTheme
import com.pr0gramm.app.ui.fragments.BusyDialogHelper.dismiss
import com.pr0gramm.app.util.checkMainThread

private object BusyDialogHelper {
    private val logger = Logger("BusyDialog")

    fun show(context: Context, text: String): Dialog {
        val dialog = Dialog(context)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                Pr0grammTheme {
                    BusyOverlayContent(text)
                }
            }
        }

        // a plain Dialog does not provide ViewTree owners, but ComposeView needs them.
        // pull them from the hosting activity.
        context.findComponentActivity()?.let { owner ->
            composeView.setViewTreeLifecycleOwner(owner)
            composeView.setViewTreeViewModelStoreOwner(owner)
            composeView.setViewTreeSavedStateRegistryOwner(owner)
        }

        dialog.setContentView(composeView)
        dialog.show()
        return dialog
    }

    fun dismiss(dialog: Dialog) {
        try {
            checkMainThread()
            dialog.dismiss()
        } catch (err: Throwable) {
            logger.warn("Could not dismiss busy dialog:", err)

            if (BuildConfig.DEBUG) {
                throw err
            }
        }
    }
}

private fun Context.findComponentActivity(): ComponentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

suspend fun <T> withBusyDialog(
        context: Context, @StringRes textId: Int = R.string.please_wait, block: suspend () -> T): T {

    checkMainThread()

    val dialog = run {
        val text = context.getString(textId)
        BusyDialogHelper.show(context, text)
    }

    try {
        return block()
    } finally {
        dismiss(dialog)
    }
}
