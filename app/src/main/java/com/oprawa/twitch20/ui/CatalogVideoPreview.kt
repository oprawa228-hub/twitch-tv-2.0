package com.oprawa.twitch20.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.oprawa.twitch20.data.Channel
import com.oprawa.twitch20.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/** A catalog owns one preview, independently of the full-screen player's MediaSession. */
internal class CatalogPreview(val channelId: String, val player: ExoPlayer) {
    var rendered by mutableStateOf(false)
}

private val previewSerials = AtomicLong()

/** Main-thread lease: a newly visible preview silences the previous surface synchronously. */
@androidx.annotation.OptIn(UnstableApi::class)
private object PreviewAudio {
    private var owner: ExoPlayer? = null
    fun claim(player: ExoPlayer) {
        if (owner !== player) owner?.volume = 0f
        owner = player
        player.volume = .45f
    }
    fun release(player: ExoPlayer) {
        player.volume = 0f
        if (owner === player) owner = null
    }
}

/** Main-thread ownership makes coroutine cancellation and composition disposal idempotent. */
@androidx.annotation.OptIn(UnstableApi::class)
private class PreviewPlayer(val player: ExoPlayer) {
    private val serial = previewSerials.incrementAndGet()
    private var released = false

    fun firstFrame() {
        if (!released) debugEvent("firstFrame")
    }

    fun release() {
        if (released) return
        released = true
        PreviewAudio.release(player)
        val muted = player.volume == 0f
        val audioDisabled = C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes
        player.release()
        if (BuildConfig.DEBUG) Log.d("TwitchCatalogPreview",
            "instance=$serial event=release muted=$muted audioDisabled=$audioDisabled")
    }

    private fun debugEvent(event: String) {
        if (BuildConfig.DEBUG) Log.d("TwitchCatalogPreview",
            "instance=$serial event=$event muted=${player.volume == 0f} " +
                "audioDisabled=${C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes}")
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private class CatalogPreviewResources {
    val http = OkHttpClient()
    private var current: PreviewPlayer? = null
    private var closed = false

    fun own(player: ExoPlayer): PreviewPlayer {
        current?.release()
        return PreviewPlayer(player).also { current = it }
    }

    fun release(player: PreviewPlayer?) {
        player?.release()
        if (current === player) current = null
    }

    fun close() {
        if (closed) return
        closed = true
        // Release the decoder/loading task before retiring its private network client.
        // This also works if Compose disposes before the cancelled coroutine's finally block.
        release(current)
        // TLS socket close can write close_notify: cancel/evict must never run on Main.
        // Queue cleanup on this client's executor before retiring that executor itself.
        val closingClient = http
        val cleanupExecutor = closingClient.dispatcher.executorService
        cleanupExecutor.execute {
            try {
                closingClient.dispatcher.cancelAll()
                closingClient.connectionPool.evictAll()
            } finally {
                cleanupExecutor.shutdown()
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun rememberCatalogVideoPreview(
    channel: Channel?,
    enabled: Boolean,
    scrolling: Boolean,
    resolvePreview: (suspend (Channel) -> String)?,
    soundEnabled: Boolean = false,
): CatalogPreview? {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val currentResolver by rememberUpdatedState(resolvePreview)
    val currentSound by rememberUpdatedState(soundEnabled)
    val resources = remember { CatalogPreviewResources() }
    DisposableEffect(resources) {
        onDispose { resources.close() }
    }
    var preview by remember { mutableStateOf<CatalogPreview?>(null) }
    LaunchedEffect(soundEnabled, preview?.player, preview?.rendered) {
        preview?.let { active ->
            active.player.trackSelectionParameters = active.player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !soundEnabled).build()
            if (soundEnabled && active.rendered && foreground) PreviewAudio.claim(active.player)
            else PreviewAudio.release(active.player)
        }
    }

    LaunchedEffect(channel?.id, channel?.login, channel?.live, enabled, scrolling,
        foreground, resolvePreview != null) {
        preview = null
        if (channel == null || !channel.live || !enabled || scrolling || !foreground) return@LaunchedEffect
        val resolver = currentResolver ?: return@LaunchedEffect
        var ownedPlayer: PreviewPlayer? = null
        try {
            // Crossing cards never starts a decoder or fetches a signed media URL.
            delay(700)
            val source = withTimeout(12_000) { resolver(channel) }
            coroutineContext.ensureActive()
            if (source.isBlank()) return@LaunchedEffect
            val player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(OkHttpDataSource.Factory(resources.http)))
                .setLoadControl(DefaultLoadControl.Builder()
                    .setBufferDurationsMs(1_000, 5_000, 250, 500)
                    .build())
                .build()
            val owned = resources.own(player)
            ownedPlayer = owned
            val activePreview = CatalogPreview(channel.id, player)
            val firstFrame = CompletableDeferred<Boolean>()
            val ended = CompletableDeferred<Unit>()
            player.volume = 0f
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !currentSound)
                .setMaxVideoSize(640, 360)
                .setMaxVideoBitrate(1_200_000)
                .build()
            player.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    activePreview.rendered = true
                    if (!firstFrame.isCompleted) owned.firstFrame()
                    firstFrame.complete(true)
                }
                override fun onPlayerError(error: PlaybackException) {
                    firstFrame.complete(false)
                    ended.complete(Unit)
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        firstFrame.complete(false)
                        ended.complete(Unit)
                    }
                }
            })
            preview = activePreview
            player.setMediaItem(MediaItem.fromUri(source))
            player.prepare()
            player.play()
            if (withTimeoutOrNull(15_000) { firstFrame.await() } != true) return@LaunchedEffect
            ended.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Preview is optional: retain the poster without an error banner or URL logging.
        } finally {
            if (preview?.player === ownedPlayer?.player) preview = null
            resources.release(ownedPlayer)
        }
    }
    return preview
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun CatalogVideoPreview(preview: CatalogPreview, modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true) {
    val motion = rememberPreviewMotionEnabled(animationsEnabled)
    val reveal by androidx.compose.animation.core.animateFloatAsState(if (preview.rendered) 1f else 0f,
        androidx.compose.animation.core.tween(if (motion) 240 else 0), label = "preview first frame")
    // Keep the poster visible until a real decoded frame exists. TextureView follows card
    // clipping and scrolling; the surface has no focus target and owns no MediaSession.
    ContentFrame(
        player = preview.player,
        modifier = modifier.graphicsLayer { alpha = reveal },
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        contentScale = ContentScale.Fit,
        shutter = {},
    )
}
