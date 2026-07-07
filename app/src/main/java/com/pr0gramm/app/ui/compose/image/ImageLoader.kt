package com.pr0gramm.app.ui.compose.image

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.OkHttpClient
import java.io.File

/**
 * Builds the single, app-wide Coil [ImageLoader] used by all Compose image loading.
 *
 * It reuses the app's [OkHttpClient] (DNS-over-HTTPS, TLS config, cookies, user agent,
 * brotli, ...) as the network stack while keeping its own memory and disk caches. The
 * app's OkHttp cache explicitly does not cache the image hosts (see DoNotCacheInterceptor
 * in Services.kt), so Coil needs its own disk cache.
 */
fun buildImageLoader(app: Application, httpClient: OkHttpClient): ImageLoader {
    return ImageLoader.Builder(app)
        .crossfade(true)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(app, 0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(app.cacheDir, "coilCache"))
                .maxSizeBytes(64L * 1024 * 1024)
                .build()
        }
        .components {
            // reuse the app's OkHttp stack for all network requests
            add(OkHttpNetworkFetcherFactory(callFactory = { httpClient }))

            // animated gif support
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(AnimatedImageDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()
}
