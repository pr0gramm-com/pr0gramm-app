package com.pr0gramm.app.ui.compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.pr0gramm.app.util.UserDrawables

@Composable
fun UserAvatar(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val bitmap = remember(name) {
        val drawables = UserDrawables(context)
        drawables.drawable(name).bitmap.asImageBitmap()
    }

    Image(bitmap = bitmap, contentDescription = name, modifier = modifier.clip(CircleShape))
}
