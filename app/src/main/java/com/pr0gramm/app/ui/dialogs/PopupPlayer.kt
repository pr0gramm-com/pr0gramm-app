package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.Window
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.ui.compose.theme.Pr0grammTheme
import com.pr0gramm.app.ui.compose.viewer.MediaViewer
import com.pr0gramm.app.ui.views.viewer.MediaUri
import com.pr0gramm.app.util.fragmentArgument

/**
 * Creates a player as a dialog.
 */
class PopupPlayer : DialogFragment() {
    var feedItem: FeedItem by fragmentArgument()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val mediaUri = MediaUri.of(requireContext(), feedItem)

        val composeView = ComposeView(requireContext()).apply {
            setContent {
                Pr0grammTheme {
                    MediaViewer(
                        mediaUri = mediaUri,
                        modifier = Modifier.fillMaxWidth(),
                        audio = feedItem.audio,
                        subtitles = feedItem.subtitles,
                        aspect = if (feedItem.width > 0 && feedItem.height > 0) {
                            feedItem.width.toFloat() / feedItem.height
                        } else -1f,
                    )
                }
            }
        }

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(composeView)

        return dialog
    }

    companion object {
        private const val TAG = "PopupPlayer"

        fun open(activity: FragmentActivity, item: FeedItem) {
            close(activity)

            PopupPlayer().apply {
                feedItem = item
                show(activity.supportFragmentManager, TAG)
            }
        }

        fun close(activity: FragmentActivity) {
            val previous = activity.supportFragmentManager
                .findFragmentByTag(TAG) as? DialogFragment

            previous?.dismiss()
        }
    }
}
