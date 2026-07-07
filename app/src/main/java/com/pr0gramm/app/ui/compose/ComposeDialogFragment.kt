package com.pr0gramm.app.ui.compose

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.pr0gramm.app.ui.base.BaseDialogFragment
import com.pr0gramm.app.ui.compose.theme.Pr0grammTheme

/**
 * A [BaseDialogFragment] that renders its content with Compose Material 3.
 *
 * The hosted [DialogContent] is expected to draw a Compose dialog surface itself (e.g. via
 * [Pr0grammAlertDialog], `AlertDialog` or [Pr0grammModalBottomSheet]), which renders in its own
 * window with its own scrim. The fragment's own dialog window is therefore made fully transparent
 * and non-dimming so there is no double scrim.
 */
abstract class ComposeDialogFragment(name: String) : BaseDialogFragment(name) {
    final override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
    }

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Pr0grammTheme {
                    DialogContent()
                }
            }
        }
    }

    @Composable
    protected abstract fun DialogContent()
}
