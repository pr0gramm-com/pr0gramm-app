package com.pr0gramm.app.ui.compose

import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.pr0gramm.app.ui.compose.theme.Pr0grammTheme

/**
 * Creates a [ComposeView] hosting [content] wrapped in [Pr0grammTheme].
 *
 * Recommended [ViewCompositionStrategy] values:
 * - Fragment views: [ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed] (default here).
 * - Views inside a `RecyclerView`/adapter:
 *   [ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool] to avoid leaks when
 *   views are recycled (see task 22).
 */
fun Fragment.composeView(
    strategy: ViewCompositionStrategy = ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
    content: @Composable () -> Unit,
): ComposeView {
    return ComposeView(requireContext()).apply {
        setViewCompositionStrategy(strategy)
        setContent {
            Pr0grammTheme {
                content()
            }
        }
    }
}

/**
 * Sets the activity content to [content] wrapped in [Pr0grammTheme], for activities that become
 * fully Compose.
 */
fun AppCompatActivity.setComposeContent(content: @Composable () -> Unit) {
    setContent {
        Pr0grammTheme {
            content()
        }
    }
}
