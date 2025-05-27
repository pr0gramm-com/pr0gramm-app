package com.pr0gramm.app.ui.views.viewer

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import com.pr0gramm.app.Logger
import com.pr0gramm.app.util.debugOnly

@OptIn(UnstableApi::class)
object ExoPlayerRecycler {
    fun release(exo: ExoPlayer) {
        exo.release()
        return
    }

    fun get(context: Context): ExoPlayer {
        return return newExoPlayer(context)
    }

    private fun newExoPlayer(context: Context): ExoPlayer {
        val ctx = context.applicationContext

        val params = DefaultTrackSelector.Parameters.Builder(context)
            .setPreferredTextLanguage("en")
            .setPreferredTextRoleFlags(C.ROLE_FLAG_SUBTITLE)
            .setSelectUndeterminedTextLanguage(true)
            .setRendererDisabled(C.TRACK_TYPE_TEXT, false)
            .build()

        val selector = DefaultTrackSelector(context, params)

        val player = ExoPlayer
            .Builder(ctx, DefaultRenderersFactory(ctx))
            .setTrackSelector(selector)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                false
            )
            .build()

        debugOnly {
            player.addAnalyticsListener(object : EventLogger() {
                private val logger = Logger("ExoPlayerEventLogger")
                override fun logd(msg: String) {
                    logger.debug { msg }
                }

                override fun loge(msg: String) {
                    logger.error { msg }
                }
            })
        }

        return player
    }
}

