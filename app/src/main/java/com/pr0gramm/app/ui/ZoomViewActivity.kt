package com.pr0gramm.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pr0gramm.app.Settings
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.parcel.getExtraParcelableOrThrow
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.compose.setComposeContent
import com.pr0gramm.app.ui.compose.viewer.ZoomableImageScreen

class ZoomViewActivity : BaseAppCompatActivity("ZoomViewActivity") {

    internal val item: FeedItem by lazy {
        intent.getExtraParcelableOrThrow("ZoomViewActivity__item")
    }

    private var systemUiVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.fullscreen)
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        Track.openZoomView(item.id)

        val uriHelper = UriHelper.of(this)
        val imageUrl = uriHelper.media(item)
        val hqUrl = if (item.fullsize.isNotBlank()) {
            uriHelper.media(item, hq = true)
        } else null

        val autoHq = Settings.loadHqInZoomView && hqUrl != null

        setComposeContent {
            ZoomableImageScreen(
                imageUrl = if (autoHq) hqUrl else imageUrl,
                hqImageUrl = if (autoHq) null else hqUrl,
                onToggleSystemUi = { toggleSystemUi() },
            )
        }

        showSystemUi()
    }

    private fun toggleSystemUi() {
        if (systemUiVisible) hideSystemUi() else showSystemUi()
    }

    private fun hideSystemUi() {
        systemUiVisible = false
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun showSystemUi() {
        systemUiVisible = true
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        fun newIntent(context: Context, item: FeedItem): Intent {
            val intent = Intent(context, ZoomViewActivity::class.java)
            intent.putExtra("ZoomViewActivity__item", item)
            return intent
        }
    }
}
