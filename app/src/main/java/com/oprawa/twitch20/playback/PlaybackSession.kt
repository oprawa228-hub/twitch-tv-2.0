package com.oprawa.twitch20.playback

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import kotlin.math.roundToInt

enum class PlaybackStatus { Idle, Buffering, Ready, Ended, Error }

data class QualityOption(val id: String, val label: String)

data class PlaybackState(
    val mediaId: String = "",
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val isLive: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val error: String? = null,
    val qualityOptions: List<QualityOption> = emptyList(),
    val selectedQualityId: String? = null,
    val activeQuality: String = "",
    val firstFrameMs: Long? = null,
    val droppedFrames: Int = 0,
)

/** One owner per viewing session. All public operations run on the Android main thread. */
@OptIn(UnstableApi::class)
class PlaybackSession(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlaybackState())
    private var progressJob: Job? = null
    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    private var released = false
    private var backgrounded = false
    private var resumeAfterBackground = false
    private var savedPositionMs = 0L
    private var startedAtMs = 0L
    private var recoveredLiveWindow = false
    private var bufferingStartedAtMs = 0L

    private val http = OkHttpClient.Builder().build()
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(context.applicationContext)
            .setDataSourceFactory(OkHttpDataSource.Factory(http)))
        .build().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
    private val mediaSession = MediaSession.Builder(context.applicationContext, player).build()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { publish() }

        override fun onTracksChanged(tracks: Tracks) { publishTracks(tracks) }

        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW &&
                mutableState.value.isLive && !recoveredLiveWindow) {
                recoveredLiveWindow = true
                player.seekToDefaultPosition()
                player.prepare()
                player.play()
                return
            }
            val message = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                    "Нет соединения. Проверьте сеть и повторите."
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                    "Источник недоступен. Откройте канал заново, чтобы обновить ссылку."
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                    "Не удалось декодировать видео на этом устройстве."
                else -> "Воспроизведение прервано (код ${error.errorCode})."
            }
            mutableState.value = mutableState.value.copy(status = PlaybackStatus.Error,
                isPlaying = false, error = message)
            Log.w("TwitchPlayback", "player_error_code=${error.errorCode}")
        }
    }

    init {
        player.addListener(listener)
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            private var videoWindowStartMs = 0L

            override fun onVideoEnabled(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                decoderCounters: DecoderCounters,
            ) {
                videoWindowStartMs = eventTime.realtimeMs
                Log.i("TwitchPlayback", "video_enabled window_start_ms=$videoWindowStartMs")
            }

            override fun onRenderedFirstFrame(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                output: Any,
                renderTimeMs: Long,
            ) {
                if (eventTime.windowIndex !in 0 until eventTime.timeline.windowCount) return
                val renderedId = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem.mediaId
                if (renderedId == mutableState.value.mediaId && mutableState.value.firstFrameMs == null) {
                    val elapsed = SystemClock.elapsedRealtime() - startedAtMs
                    mutableState.value = mutableState.value.copy(firstFrameMs = elapsed)
                    // No URI/token is logged. This excludes source-resolution latency.
                    Log.i("TwitchPlayback", "first_frame_ms=$elapsed")
                }
            }

            override fun onDroppedVideoFrames(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long,
            ) {
                val total = mutableState.value.droppedFrames + droppedFrames
                mutableState.value = mutableState.value.copy(droppedFrames = total)
                Log.i("TwitchPlayback", "dropped_video_frames=$droppedFrames elapsed_ms=$elapsedMs" +
                    " session_total=$total window_start_ms=$videoWindowStartMs")
            }

            override fun onVideoDisabled(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                decoderCounters: DecoderCounters,
            ) {
                // Numeric diagnostics only. Counters describe this renderer-enabled window,
                // including intentional skips separately from frames dropped for lateness.
                decoderCounters.ensureUpdated()
                Log.i("TwitchPlayback", "video_disabled window_start_ms=$videoWindowStartMs" +
                    " window_elapsed_ms=${eventTime.realtimeMs - videoWindowStartMs}" +
                    " rendered=${decoderCounters.renderedOutputBufferCount}" +
                    " dropped=${decoderCounters.droppedBufferCount}" +
                    " dropped_input=${decoderCounters.droppedInputBufferCount}" +
                    " skipped_input=${decoderCounters.skippedInputBufferCount}" +
                    " skipped_output=${decoderCounters.skippedOutputBufferCount}" +
                    " max_consecutive_dropped=${decoderCounters.maxConsecutiveDroppedBufferCount}" +
                    " dropped_to_keyframe=${decoderCounters.droppedToKeyframeCount}")
            }
        })
        startProgressUpdates()
    }

    fun play(url: String, mediaId: String, isLive: Boolean, startPositionMs: Long = 0) {
        check(!released) { "Playback session has been released" }
        backgrounded = false
        startProgressUpdates()
        recoveredLiveWindow = false
        bufferingStartedAtMs = 0L
        startedAtMs = SystemClock.elapsedRealtime()
        mutableState.value = PlaybackState(mediaId = mediaId, isLive = isLive,
            status = PlaybackStatus.Buffering)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
        val item = MediaItem.Builder().setUri(url).setMediaId(mediaId).apply {
            if (isLive) setLiveConfiguration(MediaItem.LiveConfiguration.Builder()
                .setMinPlaybackSpeed(0.97f).setMaxPlaybackSpeed(1.03f).build())
        }.build()
        // Keep stream-declared live target offset; do not impose an unmeasured latency target.
        player.setMediaItem(item)
        if (!isLive && startPositionMs > 0) player.seekTo(startPositionMs)
        player.prepare()
        player.play()
    }

    fun pause() { if (!released) player.pause() }

    fun resume() {
        if (released) return
        if (mutableState.value.isLive) player.seekToDefaultPosition()
        if (player.playbackState == Player.STATE_ENDED && !mutableState.value.isLive) player.seekTo(0)
        player.play()
    }

    fun retry() {
        if (released || player.currentMediaItem == null) return
        mutableState.value = mutableState.value.copy(error = null, status = PlaybackStatus.Buffering)
        recoveredLiveWindow = false
        bufferingStartedAtMs = 0L
        if (mutableState.value.isLive) player.seekToDefaultPosition()
        player.prepare()
        player.play()
    }

    fun seekBy(deltaMs: Long) {
        if (released || mutableState.value.isLive || !player.isCurrentMediaItemSeekable) return
        player.seekTo(PlaybackPolicy.seekTarget(player.currentPosition, deltaMs, player.duration))
        publish()
    }

    fun seekToLive() {
        if (released || !mutableState.value.isLive) return
        player.seekToDefaultPosition()
        player.play()
    }

    fun selectQuality(id: String?) {
        if (released) return
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (id != null) {
            val parts = id.split(':').mapNotNull(String::toIntOrNull)
            if (parts.size != 2) return
            val group = player.currentTracks.groups.getOrNull(parts[0]) ?: return
            if (group.type != C.TRACK_TYPE_VIDEO || parts[1] !in 0 until group.length ||
                !group.isTrackSupported(parts[1])) return
            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, parts[1]))
        }
        player.trackSelectionParameters = builder.build()
        mutableState.value = mutableState.value.copy(selectedQualityId = id)
    }

    fun onBackground() {
        if (released || backgrounded) return
        backgrounded = true
        progressJob?.cancel()
        resumeAfterBackground = player.playWhenReady
        savedPositionMs = player.currentPosition
        player.pause()
        player.stop() // Releases decoder/loading work without discarding the selected media item.
    }

    fun onForeground() {
        if (released || !backgrounded) return
        backgrounded = false
        startProgressUpdates()
        if (player.currentMediaItem == null) return
        if (mutableState.value.isLive) player.seekToDefaultPosition() else player.seekTo(savedPositionMs)
        player.prepare()
        player.playWhenReady = resumeAfterBackground
    }

    fun release() {
        if (released) return
        released = true
        scope.cancel()
        mediaSession.release()
        player.removeListener(listener)
        player.release()
        // Closing an idle TLS connection may write close_notify. Never do it on the UI thread.
        // Reuse this client's executor and retire it after cleanup; no detached session scope
        // or additional persistent thread pool survives each return to the catalogue.
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

    private fun publish() {
        if (released) return
        val previous = mutableState.value
        if (!backgrounded && player.playbackState == Player.STATE_BUFFERING) {
            val now = SystemClock.elapsedRealtime()
            if (bufferingStartedAtMs == 0L) bufferingStartedAtMs = now
            if (now - bufferingStartedAtMs > 45_000) {
                player.stop()
                mutableState.value = mutableState.value.copy(status = PlaybackStatus.Error,
                    isPlaying = false, error = "Видео не загружается. Проверьте сеть и повторите.")
                return
            }
        } else bufferingStartedAtMs = 0L
        val status = when {
            player.playerError != null || previous.error != null -> PlaybackStatus.Error
            player.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.Buffering
            player.playbackState == Player.STATE_READY -> PlaybackStatus.Ready
            player.playbackState == Player.STATE_ENDED -> PlaybackStatus.Ended
            else -> PlaybackStatus.Idle
        }
        mutableState.value = previous.copy(status = status, isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0,
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0))
    }

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch { while (true) { delay(500); publish() } }
    }

    private fun publishTracks(tracks: Tracks) {
        val qualities = mutableListOf<QualityOption>()
        var active = ""
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_VIDEO) for (trackIndex in 0 until group.length) {
                if (group.isTrackSupported(trackIndex)) {
                    val format = group.getTrackFormat(trackIndex)
                    val height = format.height.takeIf { it > 0 }
                    val fps = format.frameRate.takeIf { it > 30 }?.roundToInt()
                    val label = if (height != null) "${height}p${fps ?: ""}" else "Исходное"
                    qualities += QualityOption("$groupIndex:$trackIndex", label)
                    if (group.isTrackSelected(trackIndex)) active = label
                }
            }
        }
        mutableState.value = mutableState.value.copy(qualityOptions = qualities, activeQuality = active)
    }
}
