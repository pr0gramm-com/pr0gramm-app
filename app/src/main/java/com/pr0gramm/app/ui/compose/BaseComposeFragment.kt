package com.pr0gramm.app.ui.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import com.pr0gramm.app.ui.base.BaseFragment

/**
 * A [BaseFragment] whose entire content is Compose. Subclasses only implement [Content].
 *
 * DI (`injector.inject`) is inherited from [BaseFragment.onAttach], and [setTitle] remains
 * available. The hosted composable is wrapped in the app theme and disposed together with the
 * fragment view (see [composeView]).
 */
abstract class BaseComposeFragment(name: String) : BaseFragment(name) {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return composeView {
            Content()
        }
    }

    @Composable
    protected abstract fun Content()
}
