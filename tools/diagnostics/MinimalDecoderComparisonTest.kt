package com.oprawa.twitch20.diagnostics

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oprawa.twitch20.MainActivity
import com.oprawa.twitch20.data.TwitchRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/**
 * Standalone diagnostic, deliberately outside app/src/androidTest.
 * Root copies this file into androidTest only while building its diagnostic APK,
 * then runs this class explicitly. Instrumentation argument `seconds`: 1..900,
 * default 600. This consumes a real public live stream; no account is used.
 *
 * MainActivity is only a manifest-declared host. Its initial Compose content is
 * replaced before playback. No PlaybackSession, MediaSession or ContentFrame.
 * Collect only TwitchComparator logs for the structured result: Media3/platform
 * may produce their own logs, which must be handled separately by the root runner.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class MinimalDecoderComparisonTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun publicLiveAt480pOnPlainSurfaceView() {
        val seconds = InstrumentationRegistry.getArguments().getString("seconds")
            ?.toLongOrNull()?.coerceIn(1L, 900L) ?: 600L
        val surfaceReady = CountDownLatch(1)
        val firstFrameOrFailure = CountDownLatch(1)
        val terminal = CountDownLatch(1)
        val sawFrame = AtomicBoolean(false)
        val saw480Format = AtomicBoolean(false)
        val closing = AtomicBoolean(false)
        val playerError = AtomicInteger(0)
        val invalidRun = AtomicInteger(0) // 1=non-480 format, 2=ended, 3=surface lost.
        val http = OkHttpClient.Builder().build()
        var activity: Activity? = null
        var surface: SurfaceView? = null
        var player: ExoPlayer? = null
        var counters: DecoderCounters? = null // Accessed only on the main thread.
        var window = 0
        var windowStartMs = 0L
        var playbackStartMs = 0L
        var decoder = "unknown"

        fun failRun(reason: Int) {
            invalidRun.compareAndSet(0, reason)
            firstFrameOrFailure.countDown()
            terminal.countDown()
        }

        fun logCounters(event: String, value: DecoderCounters?) {
            if (value == null) return
            value.ensureUpdated()
            Log.i(TAG, "event=$event window=$window elapsed_ms=${SystemClock.elapsedRealtime() - windowStartMs}" +
                " rendered=${value.renderedOutputBufferCount} dropped=${value.droppedBufferCount}" +
                " dropped_input=${value.droppedInputBufferCount} skipped_input=${value.skippedInputBufferCount}" +
                " skipped_output=${value.skippedOutputBufferCount}" +
                " max_consecutive_dropped=${value.maxConsecutiveDroppedBufferCount}")
        }

        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (holder.surface.isValid) surfaceReady.countDown()
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                if (holder.surface.isValid && width > 0 && height > 0) surfaceReady.countDown()
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (!closing.get()) {
                    Log.w(TAG, "event=surface_lost")
                    failRun(3)
                }
            }
        }

        try {
            // Do not propagate a source exception/cause into JUnit output: it may
            // contain implementation-specific request information.
            val source = try {
                runBlocking(Dispatchers.IO) {
                    withTimeout(45_000L) { TwitchRepository().resolveLive("kspksp") }
                }
            } catch (_: Exception) {
                throw AssertionError("Comparator source resolution failed; no source details recorded")
            }
            val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            activity = instrumentation.startActivitySync(intent)
            onMain {
                val host = requireNotNull(activity)
                surface = SurfaceView(host).also {
                    it.holder.addCallback(callback)
                    host.setContentView(it, ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            }
            assertTrue("Comparator SurfaceView was not created", surfaceReady.await(15, TimeUnit.SECONDS))

            onMain {
                val view = requireNotNull(surface)
                check(view.holder.surface.isValid)
                val engine = ExoPlayer.Builder(instrumentation.targetContext)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(instrumentation.targetContext)
                        .setDataSourceFactory(OkHttpDataSource.Factory(http)))
                    .build()
                player = engine
                engine.setVideoSurfaceView(view)
                engine.trackSelectionParameters = engine.trackSelectionParameters.buildUpon()
                    .setMinVideoSize(0, 480).setMaxVideoSize(Int.MAX_VALUE, 480).build()
                engine.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        playerError.compareAndSet(0, error.errorCode)
                        Log.e(TAG, "event=player_error code=${error.errorCode}" +
                            " elapsed_ms=${SystemClock.elapsedRealtime() - playbackStartMs}")
                        firstFrameOrFailure.countDown()
                        terminal.countDown()
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        Log.i(TAG, "event=state state=$state elapsed_ms=${SystemClock.elapsedRealtime() - playbackStartMs}")
                        if (state == Player.STATE_ENDED && !closing.get()) failRun(2)
                    }
                })
                engine.addAnalyticsListener(object : AnalyticsListener {
                    override fun onVideoEnabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
                        window++
                        windowStartMs = eventTime.realtimeMs
                        counters = decoderCounters
                        Log.i(TAG, "event=video_enabled window=$window")
                    }
                    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime,
                        decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                        decoder = safeName(decoderName)
                        Log.i(TAG, "event=decoder window=$window decoder=$decoder init_ms=$initializationDurationMs")
                    }
                    override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime,
                        format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
                        val fps = format.frameRate.takeIf { it.isFinite() && it > 0 }
                            ?.let { (it * 1000).roundToInt() } ?: -1
                        Log.i(TAG, "event=video_format window=$window mime=${safeName(format.sampleMimeType.orEmpty())}" +
                            " width=${format.width} height=${format.height} fps_milli=$fps decoder=$decoder")
                        // A capability fallback would invalidate the fixed-480 comparison.
                        if (format.height == 480) saw480Format.set(true)
                        if (format.height > 0 && format.height != 480) failRun(1)
                    }
                    override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime,
                        output: Any, renderTimeMs: Long) {
                        if (sawFrame.compareAndSet(false, true)) {
                            Log.i(TAG, "event=first_frame elapsed_ms=${SystemClock.elapsedRealtime() - playbackStartMs}")
                            firstFrameOrFailure.countDown()
                        }
                    }
                    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime,
                        droppedFrames: Int, elapsedMs: Long) {
                        Log.i(TAG, "event=dropped window=$window count=$droppedFrames elapsed_ms=$elapsedMs")
                    }
                    override fun onVideoDisabled(eventTime: AnalyticsListener.EventTime, decoderCounters: DecoderCounters) {
                        logCounters("video_disabled", decoderCounters)
                        counters = null
                    }
                })
                playbackStartMs = SystemClock.elapsedRealtime()
                Log.i(TAG, "event=start seconds=$seconds requested_height=480")
                engine.setMediaItem(MediaItem.fromUri(source))
                engine.prepare()
                engine.play()
            }
            val deadline = playbackStartMs + seconds * 1000L
            firstFrameOrFailure.await(minOf(45_000L, seconds * 1000L), TimeUnit.MILLISECONDS)
            assertEquals("Comparator player error before first frame", 0, playerError.get())
            assertEquals("Comparator prerequisite failed (1=format, 2=end, 3=surface)", 0, invalidRun.get())
            assertTrue("Comparator did not render its first frame within the bound", sawFrame.get())
            var nextSample = SystemClock.elapsedRealtime() + 30_000L
            while (SystemClock.elapsedRealtime() < deadline && terminal.count > 0L) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) break
                terminal.await(minOf(1_000L, remaining), TimeUnit.MILLISECONDS)
                if (SystemClock.elapsedRealtime() >= nextSample) {
                    onMain { logCounters("sample", counters) }
                    nextSample = SystemClock.elapsedRealtime() + 30_000L
                }
            }
            assertEquals("Comparator runtime player error", 0, playerError.get())
            assertEquals("Comparator interrupted (1=format, 2=end, 3=surface)", 0, invalidRun.get())
            assertTrue("Comparator did not confirm a 480p video input format", saw480Format.get())
            Log.i(TAG, "event=complete elapsed_ms=${SystemClock.elapsedRealtime() - playbackStartMs} first_frame=1")
        } finally {
            closing.set(true)
            try {
                onMain {
                    try {
                        logCounters("final", counters)
                        surface?.let { player?.clearVideoSurfaceView(it) }
                    } finally {
                        try {
                            player?.release()
                        } finally {
                            player = null
                            surface?.holder?.removeCallback(callback)
                            activity?.finish()
                        }
                    }
                }
            } finally {
                // The instrumentation worker, never the main thread, owns TLS cleanup.
                http.dispatcher.cancelAll()
                http.connectionPool.evictAll()
                http.dispatcher.executorService.shutdown()
                Log.i(TAG, "event=cleanup error_code=${playerError.get()} invalid_run=${invalidRun.get()}")
            }
        }
    }

    private fun onMain(operation: () -> Unit) {
        var failed = false
        instrumentation.runOnMainSync {
            try { operation() } catch (_: Throwable) { failed = true }
        }
        if (failed) throw AssertionError("Comparator main-thread operation failed; details suppressed")
    }

    private fun safeName(value: String): String = value.take(96).replace(Regex("[^A-Za-z0-9._/+\\-]"), "_")

    private companion object { const val TAG = "TwitchComparator" }
}
