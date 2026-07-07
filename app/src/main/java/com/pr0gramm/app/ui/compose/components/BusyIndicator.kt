package com.pr0gramm.app.ui.compose.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.ui.compose.theme.Pr0grammTheme

/**
 * An indeterminate busy indicator in the app's accent color. Replaces the `BusyIndicator` view
 * (a `ProgressWheel` tinted with `ThemeHelper.accentColor`).
 */
@Composable
fun BusyIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(48.dp),
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Preview
@Composable
private fun BusyIndicatorPreview() {
    Pr0grammTheme {
        BusyIndicator()
    }
}
