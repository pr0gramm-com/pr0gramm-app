package com.pr0gramm.app.ui.compose.image

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.ui.compose.theme.Pr0grammTheme

/**
 * Sample composable that loads a remote image through the shared, OkHttp-backed Coil
 * [coil3.ImageLoader]. Note: interactive Android Studio `@Preview` does not perform real
 * network requests; run this in a debug build (or add a test host) to see it load.
 *
 * Demonstrates [NetworkImage] and the [BlurTransformation] blur option.
 */
@Composable
fun ImageLoadingSample(
    url: String = "https://picsum.photos/200/300",
    modifier: Modifier = Modifier,
) {
    Pr0grammTheme {
        Surface {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Coil (shared OkHttp loader)")

                NetworkImage(
                    model = url,
                    contentDescription = "sample image",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop,
                )

                Text("Blurred (NSFW / hidden content)")

                NetworkImage(
                    model = url,
                    contentDescription = "blurred sample image",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop,
                    blur = true,
                )
            }
        }
    }
}
