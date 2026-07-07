package com.pr0gramm.app.ui.fragments

import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pr0gramm.app.R
import com.pr0gramm.app.services.DownloadService
import com.pr0gramm.app.ui.compose.theme.Pr0grammTheme

/**
 * This dialog shows the progress while downloading something.
 */
class ProgressDialogController(context: Context) {
    // null means indeterminate progress
    private var progress by mutableStateOf<Float?>(null)

    private val dialog: Dialog = Dialog(context).apply {
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                Pr0grammTheme {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.please_wait_update_hint),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )

                        val current = progress
                        if (current == null) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(
                                progress = { current },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        // a plain Dialog does not provide ViewTree owners, but ComposeView needs them.
        context.findComponentActivity()?.let { owner ->
            composeView.setViewTreeLifecycleOwner(owner)
            composeView.setViewTreeViewModelStoreOwner(owner)
            composeView.setViewTreeSavedStateRegistryOwner(owner)
        }

        setContentView(composeView)
    }

    fun updateStatus(status: DownloadService.Status) {
        if (!dialog.isShowing) {
            return
        }

        progress = status.progress.takeIf { it >= 0 }
    }

    fun show() {
        dialog.show()
    }

    fun dismiss() {
        dialog.dismiss()
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
