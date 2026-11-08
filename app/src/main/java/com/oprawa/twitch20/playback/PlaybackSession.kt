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
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.oprawa.twitch20.data.VideoVariant
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
import java.util.UUID
import kotlin.math.roundToInt

enum class PlaybackStatus { Idle, Buffering, Ready, Ended, Error }

data class QualityOption(val id: String, val label: String, val height: Int? = null, val frameRate: Int? = null)

data class PlaybackState(
    val mediaId: String = "",
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val isLive: Boolean = false,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val error: String? = null,
    val qualityOptions: List<QualityOption> = emptyList(),
    val canAutoSelectQuality: Boolean = true,
    val selectedQualityId: String? = null,
    val activeQuality: String = "",
    val firstFrameMs: Long? = null,
    val droppedFrames: Int = 0,
) {
    val showPauseAction: Boolean get() = playWhenReady && status != PlaybackStatus.Ended
}

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
    private val metrics = PlaybackMetrics()
    private var progressiveSources: List<VideoVariant> = emptyList()
    private val qualityPreference = QualityPreference()

    private val http = OkHttpClient.Builder().build()
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(context.applicationContext)
            .setDataSourceFactory(OkHttpDataSource.Factory(http)))
        .build().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
    // A stopped Activity can retain its owner while another owner is constructed.
    // Session identity belongs to this owner, not to a channel or the default empty ID.
    private val mediaSession = MediaSession.Builder(context.applicationContext, player)
        .setId(UUID.randomUUID().toString()).build()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { publish() }

        override fun onTracksChanged(tracks: Tracks) {
            // A queued callback from a replaced item must not restore its TrackGroup.
            if (tracks == player.currentTracks) publishTracks(tracks)
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK)
                metrics.markPreparation(BufferingReason.SEEK, SystemClock.elapsedRealtime())
        }

        override fun onPlayerError(error: PlaybackException) {
            metrics.finish(SystemClock.elapsedRealtime())
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW &&
                mutableState.value.isLive && !recoveredLiveWindow) {
                recoveredLiveWindow = true
                metrics.markPreparation(BufferingReason.RECOVERY, SystemClock.elapsedRealtime())
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
                    val now = SystemClock.elapsedRealtime()
                    val elapsed = now - startedAtMs
                    metrics.firstFrame(now)
                    mutableState.value = mutableState.value.copy(firstFrameMs = elapsed)
                    // No URI/token is logged. This excludes source-resolution latency.
                    Log.i("TwitchPlayback", "first_frame_ms=$elapsed")
                    val sample = metrics.snapshot(now)
                    Log.i("TwitchPlayback", "request_first_frame_ms=${sample.requestFirstFrameMs}" +
                        " source_resolve_ms=${sample.sourceResolveMs} attempt=${sample.attemptId}")
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

    fun play(url: String, mediaId: String, isLive: Boolean, startPositionMs: Long = 0,
        requestStartedAtMs: Long = SystemClock.elapsedRealtime(),
        progressiveVariants: List<VideoVariant> = emptyList()) {
        check(!released) { "Playback session has been released" }
        val previous = mutableState.value
        val sameMedia = previous.mediaId == mediaId
        // ON_STOP pauses the player; a source resolving afterwards must retain
        // the user's pre-background intent for the following ON_START.
        val requestedPlayback = if (!sameMedia) true
            else if (backgrounded) resumeAfterBackground else player.playWhenReady
        qualityPreference.beginMedia(mediaId)
        backgrounded = false
        startProgressUpdates()
        recoveredLiveWindow = false
        bufferingStartedAtMs = 0L
        startedAtMs = SystemClock.elapsedRealtime()
        logMetrics(reason = 1, nowMs = startedAtMs)
        metrics.beginAttempt(requestStartedAtMs, startedAtMs)
        progressiveSources = progressiveVariants.toList()
        val progressive = qualityPreference.restoreProgressive(progressiveSources)
        mutableState.value = PlaybackState(mediaId = mediaId, isLive = isLive,
            status = PlaybackStatus.Buffering, playWhenReady = requestedPlayback,
            qualityOptions = if (progressiveSources.isNotEmpty()) progressiveSources.map { QualityOption(it.id, it.label) }
                else if (sameMedia) previous.qualityOptions else emptyList(),
            selectedQualityId = progressive?.id ?: qualityPreference.pendingHlsId(
                if (sameMedia) previous.qualityOptions else emptyList()),
            activeQuality = progressive?.label.orEmpty(),
            canAutoSelectQuality = progressiveSources.isEmpty())
        player.trackSelectionParameters = qualityParameters().build()
        val item = MediaItem.Builder().setUri(progressive?.url ?: url).setMediaId(mediaId).apply {
            if (isLive) setLiveConfiguration(MediaItem.LiveConfiguration.Builder()
                .setMinPlaybackSpeed(0.97f).setMaxPlaybackSpeed(1.03f).build())
        }.build()
        // Keep stream-declared live target offset; do not impose an unmeasured latency target.
        player.setMediaItem(item)
        if (!isLive && startPositionMs > 0) player.seekTo(startPositionMs)
        player.prepare()
        player.playWhenReady = requestedPlayback
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
        metrics.markPreparation(BufferingReason.RECOVERY, SystemClock.elapsedRealtime())
        if (mutableState.value.isLive) player.seekToDefaultPosition()
        player.prepare()
        player.play()
    }

    fun seekBy(deltaMs: Long) {
        if (released || mutableState.value.isLive || !player.isCurrentMediaItemSeekable) return
        player.seekTo(PlaybackPolicy.seekTarget(player.currentPosition, deltaMs, player.duration))
        publish()
    }

    fun seekTo(positionMs: Long, playWhenReady: Boolean? = null) {
        if (released || mutableState.value.isLive || !player.isCurrentMediaItemSeekable) return
        player.seekTo(PlaybackPolicy.seekTarget(0, positionMs, player.duration))
        if (playWhenReady != null) player.playWhenReady = playWhenReady
        publish()
    }

    fun seekToLive() {
        if (released || !mutableState.value.isLive) return
        player.seekToDefaultPosition()
        player.play()
    }

    fun selectQuality(id: String?) {
        if (released) return
        val failed = mutableState.value.status == PlaybackStatus.Error || player.playerError != null
        val current = mutableState.value.copy(positionMs = player.currentPosition.coerceAtLeast(0),
            playWhenReady = player.playWhenReady)
        if (progressiveSources.isNotEmpty()) {
            val source = progressiveSources.firstOrNull { it.id == id } ?: return
            qualityPreference.rememberProgressive(source.id)
            if (source.id == current.selectedQualityId && !failed) return
            val next = PlaybackPolicy.qualitySwitchState(current, source.id, source.label)
            metrics.markPreparation(BufferingReason.QUALITY_SWITCH, SystemClock.elapsedRealtime())
            bufferingStartedAtMs = 0L
            mutableState.value = next
            player.setMediaItem(MediaItem.Builder().setUri(source.url).setMediaId(next.mediaId).build(), next.positionMs)
            player.prepare()
            player.playWhenReady = next.playWhenReady
            return
        }
        val choice = if (id == null) null else current.qualityOptions.firstOrNull { it.id == id } ?: return
        qualityPreference.rememberHls(choice)
        metrics.markPreparation(BufferingReason.QUALITY_SWITCH, SystemClock.elapsedRealtime())
        if (failed) {
            if (player.currentMediaItem == null) return
            val recovery = qualityRecoveryPlan(current, selectedId = choice?.id)
            mutableState.value = recovery.state
            bufferingStartedAtMs = 0L
            recoveredLiveWindow = false
            // Apply semantic limits before preparing, so an explicit lower-quality
            // choice need not first instantiate the previous failing high variant.
            player.trackSelectionParameters = qualityParameters().build()
            if (recovery.seekPositionMs == null) player.seekToDefaultPosition()
            else player.seekTo(recovery.seekPositionMs)
            player.prepare()
            player.playWhenReady = recovery.state.playWhenReady
        } else {
            publishTracks(player.currentTracks)
            if (id == null) {
                player.trackSelectionParameters = qualityParameters().build()
                mutableState.value = mutableState.value.copy(selectedQualityId = null)
            }
        }
    }

    fun onBackground() {
        if (released || backgrounded) return
        backgrounded = true
        metrics.finish(SystemClock.elapsedRealtime())
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
        mutableState.value = PlaybackPolicy.foregroundState(mutableState.value, resumeAfterBackground)
        bufferingStartedAtMs = 0L
        metrics.markPreparation(BufferingReason.FOREGROUND, SystemClock.elapsedRealtime())
        if (mutableState.value.isLive) player.seekToDefaultPosition() else player.seekTo(savedPositionMs)
        player.prepare()
        player.playWhenReady = resumeAfterBackground
    }

    fun release() {
        if (released) return
        released = true
        metrics.finish(SystemClock.elapsedRealtime())
        logMetrics(reason = 2, nowMs = SystemClock.elapsedRealtime())
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
                metrics.finish(now)
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
        metrics.onState(status, player.playWhenReady, !backgrounded, SystemClock.elapsedRealtime())
        mutableState.value = previous.copy(status = status, isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0,
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0))
    }

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch { while (true) { delay(500); publish() } }
    }

    private fun publishTracks(tracks: Tracks) {
        if (released || player.currentMediaItem?.mediaId != mutableState.value.mediaId ||
            progressiveSources.isNotEmpty()) return
        val qualities = mutableListOf<QualityOption>()
        var active = ""
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_VIDEO) for (trackIndex in 0 until group.length) {
                if (group.isTrackSupported(trackIndex)) {
                    val format = group.getTrackFormat(trackIndex)
                    val height = format.height.takeIf { it > 0 }
                    val fps = format.frameRate.takeIf { it > 0 && it.isFinite() }?.roundToInt()
                    val label = if (height != null) "${height}p${fps?.takeIf { it > 30 } ?: ""}" else "Исходное"
                    qualities += QualityOption("$groupIndex:$trackIndex", label, height, fps)
                    if (group.isTrackSelected(trackIndex)) active = label
                }
            }
        }
        // Media3 clears tracks during error/prepare. Keep the last supported menu
        // as semantic recovery choices, never as reusable TrackGroup references.
        if (qualities.isEmpty()) return
        val restored = qualityPreference.restoreHls(qualities)
        val builder = qualityParameters()
        restored?.let { option ->
            val indices = option.id.split(':').map(String::toInt)
            val group = tracks.groups[indices[0]]
            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, indices[1]))
        }
        val parameters = builder.build()
        if (parameters != player.trackSelectionParameters) player.trackSelectionParameters = parameters
        mutableState.value = mutableState.value.copy(qualityOptions = qualities,
            selectedQualityId = restored?.id, activeQuality = active)
    }

    private fun qualityParameters(): TrackSelectionParameters.Builder {
        val preferred = qualityPreference.preferredHls
        return player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .clearVideoSizeConstraints().setMaxVideoFrameRate(Int.MAX_VALUE)
            .apply {
                preferred?.height?.let { setMaxVideoSize(Int.MAX_VALUE, it) }
                preferred?.frameRate?.let { setMaxVideoFrameRate(it) }
            }
    }

    private fun logMetrics(reason: Int, nowMs: Long) {
        val sample = metrics.snapshot(nowMs)
        if (sample.attemptId == 0) return
        Log.i("TwitchPlayback", "playback_summary reason=$reason attempt=${sample.attemptId}" +
            " source_resolve_ms=${sample.sourceResolveMs}" +
            " request_first_frame_ms=${sample.requestFirstFrameMs ?: -1}" +
            " prepare_first_frame_ms=${sample.prepareFirstFrameMs ?: -1}" +
            " rebuffer_count=${sample.rebufferCount} rebuffer_ms=${sample.rebufferDurationMs}" +
            " startup_buffer_ms=${sample.bufferingMs.getValue(BufferingReason.STARTUP)}" +
            " seek_buffer_ms=${sample.bufferingMs.getValue(BufferingReason.SEEK)}" +
            " foreground_buffer_ms=${sample.bufferingMs.getValue(BufferingReason.FOREGROUND)}" +
            " recovery_buffer_ms=${sample.bufferingMs.getValue(BufferingReason.RECOVERY)}" +
            " quality_buffer_ms=${sample.bufferingMs.getValue(BufferingReason.QUALITY_SWITCH)}")
    }
}
