package com.pr0gramm.app.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

/**
 * Observes a [LiveData] from Compose without pulling in the (unused elsewhere in this project)
 * `androidx.compose.runtime:runtime-livedata` artifact just for this. Observes for as long as
 * this composable stays in the composition (`observeForever` + manual removal on dispose), which
 * is fine for the short-lived, screen-scoped `Pagination`/paging sources this is used with.
 */
@Composable
fun <T> LiveData<T>.observeAsStateCompat(): State<T?> {
    val state = remember(this) { mutableStateOf(value) }

    DisposableEffect(this) {
        val observer = Observer<T> { state.value = it }
        observeForever(observer)
        onDispose { removeObserver(observer) }
    }

    return state
}
