package com.pr0gramm.app.ui.compose.image

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import com.pr0gramm.app.ui.blur

/**
 * Coil 3 counterpart of the Picasso-based `com.pr0gramm.app.ui.BlurTransformation`.
 * Reuses the same stack-blur implementation ([Bitmap.blur]) and is used to obscure
 * NSFW / hidden content in Compose screens.
 */
class BlurTransformation(private val radius: Int = 25) : Transformation() {
    override val cacheKey: String = "blur:$radius"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        return input.blur(radius, inplace = true)
    }
}
