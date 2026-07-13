package com.pr0gramm.app.ui.compose.viewer

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.SubtitleExtractor
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.webvtt.WebvttParser
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.compose.components.BusyIndicator
import com.pr0gramm.app.ui.views.viewer.ExoPlayerRecycler
import com.pr0gramm.app.ui.views.viewer.MediaUri
import com.pr0gramm.app.ui.views.viewer.SeekController
import com.pr0gramm.app.ui.views.viewer.video.InputStreamCacheDataSource
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.priority
import kotlinx.coroutines.delay

private val logger = Logger("VideoViewer")

/**
 * Compose video viewer backed by Media3 ExoPlayer. Replaces [SimpleVideoMediaView].
 *
 * Lifecycle: the player is created when [isPlaying] is true and destroyed when false
 * or when the composable leaves composition. Seek position is persisted via [SeekController].
 */
@OptIn(UnstableApi::class)
@Composable
internal fun VideoViewer(
    mediaUri: MediaUri,
    aspect: Float,
    audio: Boolean,
    subtitles: List<Api.Feed.Subtitle>,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    onMediaShown: () -> Unit = {},
    onSingleTap: () -> Unit = {},
    onDoubleTap: (normalizedX: Float) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // --- player state ---
    var exo by remember { mutableStateOf<ExoPlayer?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var videoAspect by remember { mutableFloatStateOf(if (aspect > 0f) aspect else 16f / 9f) }
    var shownFired by remember { mutableStateOf(false) }

    // progress
    var progress by remember { mutableFloatStateOf(0f) }
    var buffered by remember { mutableFloatStateOf(0f) }
    var seekbarVisible by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }

    // mute
    val prefs = remember { context.injector.instance<SharedPreferences>() }
    var muted by remember { mutableStateOf(isMuted(prefs)) }

    // subtitles
    var subtitlesEnabled by remember { mutableStateOf(prefs.getBoolean("subtitles", false)) }
    var subtitleText by remember { mutableStateOf<String?>(null) }

    val effectiveAspect = if (aspect > 0f) aspect else videoAspect

    // --- player listener ---
    val playerListener = remember {
        object : Player.Listener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                buffering = playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                if (aspect <= 0f && size.width > 0 && size.height > 0) {
                    videoAspect = size.width.toFloat() / size.height.toFloat() * size.pixelWidthHeightRatio
                }
            }

            override fun onRenderedFirstFrame() {
                buffering = false
                if (!shownFired) {
                    shownFired = true
                    onMediaShown()
                }
            }

            override fun onCues(cueGroup: CueGroup) {
                subtitleText = cueGroup.cues.mapNotNull { it.text?.toString() }
                    .joinToString("\n")
                    .ifBlank { null }
            }
        }
    }

    // --- create/destroy player based on isPlaying ---
    DisposableEffect(isPlaying) {
        if (isPlaying) {
            val player = ExoPlayerRecycler.get(context)
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.playWhenReady = true
            player.volume = if (audio && !isMuted(prefs)) 1f else 0f

            player.trackSelectionParameters = TrackSelectionParameters.DEFAULT.buildUpon()
                .setSelectUndeterminedTextLanguage(true)
                .build()

            player.addListener(playerListener)

            val mediaSource = createMediaSource(
                context = context,
                uri = mediaUri.baseUri,
                subtitles = subtitles,
                onSubtitleError = {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                },
            )

            player.setMediaSource(mediaSource, false)
            player.prepare()
            SeekController.restore(mediaUri.id, player)

            if (audio) {
                applyMuteState(context, player, prefs)
                muted = player.volume < 0.1f
            }

            exo = player
        }

        onDispose {
            exo?.let { player ->
                SeekController.store(mediaUri.id, player)
                player.removeListener(playerListener)
                player.stop()
                player.clearMediaItems()
                player.setVideoTextureView(null)
                ExoPlayerRecycler.release(player)
            }
            exo = null
        }
    }

    // lifecycle pause/resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exo?.playWhenReady = false
                Lifecycle.Event.ON_RESUME -> if (isPlaying) exo?.playWhenReady = true
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // progress ticker
    LaunchedEffect(exo) {
        val player = exo ?: return@LaunchedEffect
        while (true) {
            if (!isSeeking) {
                val dur = player.contentDuration.takeIf { it > 0 } ?: 1L
                progress = player.currentPosition.toFloat() / dur
                buffered = player.contentBufferedPosition.toFloat() / dur
            }
            delay(100)
        }
    }

    // --- UI ---
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(effectiveAspect, matchHeightConstraintsFirst = false)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {
                seekbarVisible = !seekbarVisible
                onSingleTap()
            },
        contentAlignment = Alignment.Center,
    ) {
        // TextureView for video
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).also { tv ->
                    exo?.setVideoTextureView(tv)
                }
            },
            update = { tv ->
                exo?.setVideoTextureView(tv)
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Busy indicator
        AnimatedVisibility(
            visible = buffering,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            BusyIndicator()
        }

        // Subtitles
        if (subtitlesEnabled && subtitleText != null) {
            Text(
                text = subtitleText.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp, start = 16.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            // Thin progress bar (always visible when playing)
            AnimatedVisibility(visible = !seekbarVisible) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                )
            }

            // Seekbar + controls
            AnimatedVisibility(visible = seekbarVisible) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 4.dp),
                ) {
                    Slider(
                        value = if (isSeeking) seekValue else progress.coerceIn(0f, 1f),
                        onValueChange = {
                            isSeeking = true
                            seekValue = it
                        },
                        onValueChangeFinished = {
                            exo?.let { player ->
                                val dur = player.duration.takeIf { d -> d > 0 } ?: return@let
                                player.seekTo((dur * seekValue.coerceAtLeast(0f)).toLong())
                            }
                            isSeeking = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Pause / play
                        IconButton(onClick = {
                            exo?.let { player ->
                                player.playWhenReady = !player.playWhenReady
                            }
                        }) {
                            val playing = exo?.playWhenReady == true
                            Icon(
                                painter = painterResource(
                                    if (playing) R.drawable.ic_video_pause else R.drawable.ic_video_play
                                ),
                                contentDescription = if (playing) "Pause" else "Play",
                                tint = if (playing) MaterialTheme.colorScheme.secondary else Color.White,
                            )
                        }

                        Row {
                            // Subtitles toggle
                            if (subtitles.isNotEmpty()) {
                                IconButton(onClick = {
                                    subtitlesEnabled = !subtitlesEnabled
                                    prefs.edit { putBoolean("subtitles", subtitlesEnabled) }
                                }) {
                                    Icon(
                                        painter = painterResource(
                                            if (subtitlesEnabled) R.drawable.ic_subtitles_on else R.drawable.ic_subtitles_off
                                        ),
                                        contentDescription = "Subtitles",
                                        tint = if (subtitlesEnabled) MaterialTheme.colorScheme.secondary else Color.White,
                                    )
                                }
                            }

                            // Mute toggle
                            if (audio) {
                                IconButton(onClick = {
                                    muted = !muted
                                    exo?.volume = if (muted) 0f else 1f
                                    storeMuteTime(prefs, if (muted) 0L else System.currentTimeMillis())
                                    if (!muted) {
                                        requestAudioFocus(context)
                                    }
                                }) {
                                    Icon(
                                        painter = painterResource(
                                            if (muted) R.drawable.ic_video_mute_on else R.drawable.ic_video_mute_off
                                        ),
                                        contentDescription = if (muted) "Unmute" else "Mute",
                                        tint = if (!muted) MaterialTheme.colorScheme.secondary else Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- helper functions ---

private const val LAST_UNMUTED_KEY = "VolumeController.lastUnmutedVideo"

private fun isMuted(prefs: SharedPreferences): Boolean {
    val lastUnmuted = prefs.getLong(LAST_UNMUTED_KEY, 0L)
    return (System.currentTimeMillis() - lastUnmuted) / 1000 > 10 * 60
}

private fun storeMuteTime(prefs: SharedPreferences, time: Long) {
    prefs.edit { putLong(LAST_UNMUTED_KEY, time) }
}

private fun applyMuteState(context: Context, exo: ExoPlayer, prefs: SharedPreferences) {
    if (isMuted(prefs)) {
        exo.volume = 0f
    } else {
        exo.volume = 1f
        requestAudioFocus(context)
    }
}

private fun requestAudioFocus(context: Context) {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val gain = if (Settings.audioFocusTransient) {
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
    } else {
        AudioManager.AUDIOFOCUS_GAIN
    }
    @Suppress("DEPRECATION")
    am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, gain)
}

@OptIn(UnstableApi::class)
private fun createMediaSource(
    context: Context,
    uri: android.net.Uri,
    subtitles: List<Api.Feed.Subtitle>,
    onSubtitleError: () -> Unit,
): androidx.media3.exoplayer.source.MediaSource {
    val dataSourceFactory = DefaultDataSource.Factory(context) {
        val cache = context.injector.instance<Cache>()
        InputStreamCacheDataSource(cache)
    }

    val extractorsFactory = ExtractorsFactory {
        arrayOf(
            FragmentedMp4Extractor(SubtitleParser.Factory.UNSUPPORTED),
            Mp4Extractor(SubtitleParser.Factory.UNSUPPORTED),
            MatroskaExtractor(SubtitleParser.Factory.UNSUPPORTED),
            SubtitleExtractor(
                WebvttParser(), Format.Builder()
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            ),
        )
    }

    val subtitleConfigs = subtitles.minByOrNull(Api.Feed.Subtitle::priority)?.let { subtitle ->
        val config = MediaItem.SubtitleConfiguration.Builder(
            UriHelper.NoPreload.subtitle(subtitle.path)
        )
            .setLanguage(subtitle.language)
            .setMimeType(MimeTypes.TEXT_VTT)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        listOf(config)
    }

    val mediaItem = MediaItem.Builder()
        .setUri(uri)
        .setSubtitleConfigurations(subtitleConfigs.orEmpty())
        .build()

    val policy = object : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val isSubtitle = loadErrorInfo.loadEventInfo.uri.toString().endsWith(".vtt")
            if (isSubtitle) {
                logger.warn { "Disabling subtitles due to load error" }
                onSubtitleError()
            }
            return super.getRetryDelayMsFor(loadErrorInfo)
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int = 3
    }

    return DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
        .setLoadErrorHandlingPolicy(policy)
        .createMediaSource(mediaItem)
}
