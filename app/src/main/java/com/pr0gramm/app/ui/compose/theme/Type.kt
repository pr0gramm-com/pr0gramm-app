package com.pr0gramm.app.ui.compose.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material 3 [Typography] for the app.
 *
 * The body styles mirror the app's `res/values/styles-typo.xml` sizes (12/14/16sp with a bit of
 * extra line spacing). All other roles keep the Material 3 defaults.
 */
val Pr0grammTypography: Typography = Typography().run {
    copy(
        // TextStyle.16
        bodyLarge = bodyLarge.copy(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
        ),
        // TextStyle.14
        bodyMedium = bodyMedium.copy(
            fontSize = 14.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
        ),
        // TextStyle.12
        bodySmall = bodySmall.copy(
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal,
        ),
    )
}
