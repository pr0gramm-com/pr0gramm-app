package com.pr0gramm.app.ui.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.Themes

/**
 * Builds a Material 3 [ColorScheme] for the given [Themes] value.
 *
 * The app is effectively a dark theme (dark feed background, white text), so we start from
 * [darkColorScheme] and override the brand colors. Colors are read from `res/values/colors.xml`
 * via [colorResource] so the XML resources remain the single source of truth and stay in sync
 * with the not-yet-migrated View screens.
 */
@Composable
fun pr0grammColorScheme(theme: Themes): ColorScheme {
    val primary = colorResource(theme.primaryColor)
    val primaryDark = colorResource(theme.primaryColorDark)
    val accent = colorResource(theme.accentColor)

    val background = colorResource(R.color.feed_background)
    val surface = colorResource(R.color.feed_background)
    val surfaceVariant = colorResource(R.color.secondary_background)
    val onLight = colorResource(R.color.white)

    return darkColorScheme(
        primary = primary,
        onPrimary = onLight,
        primaryContainer = primaryDark,
        onPrimaryContainer = onLight,

        secondary = accent,
        onSecondary = onLight,
        secondaryContainer = primaryDark,
        onSecondaryContainer = onLight,

        tertiary = accent,
        onTertiary = onLight,

        background = background,
        onBackground = onLight,

        surface = surface,
        onSurface = onLight,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onLight,

        outline = Color(0xFF515158),
    )
}
