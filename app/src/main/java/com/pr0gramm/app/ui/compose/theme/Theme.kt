package com.pr0gramm.app.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.Themes

/**
 * Root Material 3 theme for all Compose islands in the app. Drives all 6 color themes from
 * [Themes] via a Material 3 [androidx.compose.material3.ColorScheme].
 *
 * Dynamic color (Material You) is intentionally disabled to preserve the app's brand theming.
 *
 * @param theme the color theme to apply; defaults to the user's currently selected theme.
 */
@Composable
fun Pr0grammTheme(
    theme: Themes = ThemeHelper.theme,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = pr0grammColorScheme(theme),
        typography = Pr0grammTypography,
        shapes = Pr0grammShapes,
        content = content,
    )
}
