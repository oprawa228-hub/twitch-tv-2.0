package com.oprawa.twitch20.playback

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.oprawa.twitch20.data.VideoVariant
import com.oprawa.twitch20.BuildConfig
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
import okhttp3.Protocol
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
class PlaybackSession internal constructor(context: Context, dataSourceFactory: DataSource.Factory? = null) {
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
    private val hlsDiagnostics = HlsPlaybackDiagnostics()
    private val liveSourceRecovery = LiveSourceRecovery()
    private var sourceRecoveryJob: Job? = null
    private var pendingSourceRecoveryItem: MediaItem? = null
    private val observeStuck60s = BuildConfig.DEBUG && context.getSharedPreferences("playback-diagnostics", Context.MODE_PRIVATE)
        .getBoolean("observe_stuck_60s", false)
    private val recreateSoftwareAvc = BuildConfig.DEBUG && context.getSharedPreferences("playback-diagnostics", Context.MODE_PRIVATE)
        .getBoolean("recreate_software_avc", false)
    private val debugHls = if (BuildConfig.DEBUG) DebugHlsCorrelation() else null

    private val http = OkHttpClient.Builder().apply {
        if (BuildConfig.DEBUG) addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (chain.request().url.encodedPath.endsWith(".m3u8", ignoreCase = true)) {
                val uri = android.net.Uri.parse(chain.request().url.toString())
                Log.i("TwitchPlayback", "debug_hls_http ${debugHls?.role(uri)} code=${response.code}" +
                    " http_version=${if (response.protocol == Protocol.HTTP_2) 2 else 1}" +
                    " gzip=${if (response.header("Content-Encoding") == "gzip") 1 else 0}" +
                    " age_s=${response.header("Age")?.toLongOrNull() ?: -1}")
            }
            response
        }
    }.build()
    private val playbackDataSource = dataSourceFactory ?: OkHttpDataSource.Factory(http)
    private val playbackMediaSourceFactory = DefaultMediaSourceFactory(context.applicationContext)
        .setDataSourceFactory(playbackDataSource).let { default -> LiveHlsSourceFactory(default, playbackDataSource, debugHls, observeStuck60s) }
    private val renderers = DefaultRenderersFactory(context.applicationContext).apply {
        setMediaCodecSelector(AvcResolutionCompatibility.selector(includeSoftwareProbe = recreateSoftwareAvc))
    }
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext, renderers)
        .setMediaSourceFactory(playbackMediaSourceFactory)
        .build().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
    // A stopped Activity can retain its owner while another owner is constructed.
    // Session identity belongs to this owner, not to a channel or the default empty ID.
    private val sessionPlayer = object : ForwardingPlayer(player) {
        override fun play() = this@PlaybackSession.resume()
        override fun pause() = this@PlaybackSession.pause()
        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (playWhenReady) this@PlaybackSession.resume() else this@PlaybackSession.pause()
        }
    }
    private val mediaSession = MediaSession.Builder(context.applicationContext, sessionPlayer)
        .setId(UUID.randomUUID().toString()).build()
    internal val sessionToken get() = mediaSession.token

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
            Log.w("TwitchPlayback", "player_error_code=${error.errorCode}" +
                " cause_types=${safePlaybackErrorCauseNames(error)}")
            metrics.finish(SystemClock.elapsedRealtime())
            val playlistFailure = playbackErrorCauses(error).any {
                it is HlsPlaylistTracker.PlaylistStuckException || it is HlsPlaylistTracker.PlaylistResetException
            }
            if (BuildConfig.DEBUG) playbackErrorCauses(error).forEach { cause ->
                val uri = when (cause) {
                    is HlsPlaylistTracker.PlaylistStuckException -> cause.url
                    is HlsPlaylistTracker.PlaylistResetException -> cause.url
                    else -> null
                }
                if (uri != null) debugHls?.error(uri, player.currentManifest as? HlsManifest)
            }
            val failedItem = player.currentMediaItem
            if (liveSourceRecovery.claim(mutableState.value.isLive, playlistFailure,
                    foreground = !backgrounded, hasItem = failedItem != null)) {
                mutableState.value = mutableState.value.copy(status = PlaybackStatus.Buffering,
                    isPlaying = false, error = null)
                sourceRecoveryJob?.cancel()
                pendingSourceRecoveryItem = failedItem
                sourceRecoveryJob = scope.launch {
                    delay(1_000)
                    if (released || backgrounded || player.currentMediaItem != failedItem) return@launch
                    // Read at execution time: PAUSE during the delay must win over recovery.
                    restartLiveSource(requireNotNull(failedItem), player.playWhenReady)
                }
                return
            }
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW &&
                mutableState.value.isLive && !recoveredLiveWindow) {
                recoveredLiveWindow = true
                metrics.markPreparation(BufferingReason.RECOVERY, SystemClock.elapsedRealtime())
                player.seekToDefaultPosition()
                player.prepare()
                // Recovery repairs the expired window, not the user's play/pause intent.
                // A paused HLS stream can fall behind while its playlist keeps refreshing.
                return
            }
            val message = if (playlistFailure) "Поток Twitch перестал обновляться. Повторите подключение." else when (error.errorCode) {
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
        }
    }

    init {
        if (observeStuck60s) Log.i("TwitchPlayback", "debug_stuck_observation enabled=1 coefficient=12 buffering_timeout_ms=90000")
        if (recreateSoftwareAvc) Log.i("TwitchPlayback", "debug_software_avc_recreate enabled=1")
        player.addListener(listener)
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            private var videoWindowStartMs = 0L

            override fun onVideoInputFormatChanged(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
                if (eventTime.windowIndex !in 0 until eventTime.timeline.windowCount) return
                val id = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem.mediaId
                if (id != mutableState.value.mediaId) return
                // All tracks in an ABR selection can be selected simultaneously. The renderer
                // input, not the last selected TrackGroup entry, tells us what is playing.
                Log.i("TwitchPlayback", "video_input width=${format.width} height=${format.height}" +
                    " fps=${format.frameRate.takeIf { it.isFinite() }?.roundToInt() ?: -1}" +
                    " reuse_result=${decoderReuseEvaluation?.result ?: -1}" +
                    " reuse_discard=${decoderReuseEvaluation?.discardReasons ?: 0}")
                publish()
            }

            override fun onLoadStarted(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData) {
                if (BuildConfig.DEBUG && mediaLoadData.dataType == C.DATA_TYPE_MANIFEST)
                    Log.i("TwitchPlayback", "debug_hls_loading ${debugHls?.role(loadEventInfo.uri)}")
            }

            override fun onLoadCompleted(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData) {
                if (!BuildConfig.DEBUG || mediaLoadData.dataType != C.DATA_TYPE_MANIFEST) return
                val primary = (player.currentManifest as? HlsManifest)?.mediaPlaylist?.baseUri?.let { android.net.Uri.parse(it) }
                Log.i("TwitchPlayback", "debug_hls_loaded ${debugHls?.role(loadEventInfo.uri)}" +
                    " primary=${if (loadEventInfo.uri == primary) 1 else 0}" +
                    " bytes=${loadEventInfo.bytesLoaded} load_ms=${loadEventInfo.loadDurationMs}")
            }

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
        sourceRecoveryJob?.cancel()
        sourceRecoveryJob = null
        pendingSourceRecoveryItem = null
        liveSourceRecovery.reset()
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
        hlsDiagnostics.reset()
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
        // Repeated PLAY commands must be idempotent. Seeking on every command can
        // restart buffered live segments (including an in-stream commercial slate).
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) return
        if (mutableState.value.isLive) seekToAvailableLiveEdge()
        if (player.playbackState == Player.STATE_ENDED && !mutableState.value.isLive) player.seekTo(0)
        player.play()
    }

    fun retry() {
        if (released || player.currentMediaItem == null) return
        sourceRecoveryJob?.cancel()
        sourceRecoveryJob = null
        pendingSourceRecoveryItem = null
        liveSourceRecovery.reset()
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
        seekToAvailableLiveEdge()
        player.play()
    }

    private fun seekToAvailableLiveEdge() {
        val timeline = player.currentTimeline
        if (player.currentMediaItemIndex !in 0 until timeline.windowCount) return
        val window = timeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
        val playlist = (window.manifest as? HlsManifest)?.mediaPlaylist ?: return
        if (!window.isDynamic || playlist.hasEndTag) return
        val target = PlaybackPolicy.liveEdgeTarget(player.currentPosition, window.durationMs,
            playlist.durationUs / 1_000, playlist.segments.map { it.relativeStartTimeUs / 1_000 }) ?: return
        // START=0 is an initial source position, not a request to replay its beginning on resume.
        Log.i("TwitchPlayback", "live_edge_seek from_ms=${player.currentPosition} to_ms=$target")
        player.seekTo(target)
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
            // The user's recovery choice supersedes the delayed automatic repair.
            // Keep its consumed budget so a READY/error cycle cannot create a loop.
            sourceRecoveryJob?.cancel()
            sourceRecoveryJob = null
            pendingSourceRecoveryItem = null
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
        sourceRecoveryJob?.cancel()
        sourceRecoveryJob = null
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
        pendingSourceRecoveryItem?.let { pending ->
            if (pending == player.currentMediaItem) {
                // ON_STOP cancelled the timer, not the claimed repair. Complete it once
                // on return, retaining the user's pre-background pause/play intent.
                restartLiveSource(pending, resumeAfterBackground)
                return
            }
            pendingSourceRecoveryItem = null
        }
        bufferingStartedAtMs = 0L
        metrics.markPreparation(BufferingReason.FOREGROUND, SystemClock.elapsedRealtime())
        if (mutableState.value.isLive) {
            if (resumeAfterBackground) seekToAvailableLiveEdge()
        } else player.seekTo(savedPositionMs)
        player.prepare()
        player.playWhenReady = resumeAfterBackground
    }

    private fun restartLiveSource(item: MediaItem, requestedPlayback: Boolean) {
        pendingSourceRecoveryItem = null
        bufferingStartedAtMs = 0L
        hlsDiagnostics.reset()
        metrics.markPreparation(BufferingReason.RECOVERY, SystemClock.elapsedRealtime())
        // Keep semantic height/fps preferences; discard TrackGroups owned by the failed source.
        player.trackSelectionParameters = qualityParameters().build()
        // A new source/tracker discards the frozen/reset playlist snapshot. Same URI/token.
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = requestedPlayback
        Log.i("TwitchPlayback", "live_source_recovery attempt=1 same_item=1" +
            " play_requested=${if (requestedPlayback) 1 else 0}")
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
        if (!backgrounded && previous.isLive && player.currentMediaItem?.mediaId == previous.mediaId) {
            (player.currentManifest as? HlsManifest)?.mediaPlaylist?.let { playlist ->
                hlsDiagnostics.observe(playlist.mediaSequence, playlist.segments.size,
                    playlist.trailingParts.size, playlist.durationUs, playlist.hasEndTag,
                    playlist.tags, SystemClock.elapsedRealtime())?.let { safeSummary ->
                    val timeline = player.currentTimeline
                    val defaultPositionMs = if (player.currentMediaItemIndex in 0 until timeline.windowCount)
                        timeline.getWindow(player.currentMediaItemIndex, Timeline.Window()).defaultPositionMs else C.TIME_UNSET
                    Log.i("TwitchPlayback", safeSummary +
                        " position_ms=${player.currentPosition} buffered_ms=${player.bufferedPosition}" +
                        " live_offset_ms=${player.currentLiveOffset.takeUnless { it == C.TIME_UNSET } ?: -1}" +
                        " default_position_ms=${defaultPositionMs.takeUnless { it == C.TIME_UNSET } ?: -1}" +
                        " playing=${if (player.isPlaying) 1 else 0} play_requested=${if (player.playWhenReady) 1 else 0}")
                    debugHls?.primary(player.currentManifest as? HlsManifest)
                }
            }
        }
        if (!backgrounded && player.playbackState == Player.STATE_BUFFERING) {
            val now = SystemClock.elapsedRealtime()
            if (bufferingStartedAtMs == 0L) bufferingStartedAtMs = now
            if (now - bufferingStartedAtMs > if (observeStuck60s) 90_000 else 45_000) {
                player.stop()
                mutableState.value = mutableState.value.copy(status = PlaybackStatus.Error,
                    isPlaying = false, error = "Видео не загружается. Проверьте сеть и повторите.")
                metrics.finish(now)
                return
            }
        } else bufferingStartedAtMs = 0L
        val status = when {
            sourceRecoveryJob?.isActive == true -> PlaybackStatus.Buffering
            player.playerError != null || previous.error != null -> PlaybackStatus.Error
            player.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.Buffering
            player.playbackState == Player.STATE_READY -> PlaybackStatus.Ready
            player.playbackState == Player.STATE_ENDED -> PlaybackStatus.Ended
            else -> PlaybackStatus.Idle
        }
        metrics.onState(status, player.playWhenReady, !backgrounded, SystemClock.elapsedRealtime())
        mutableState.value = previous.copy(status = status, isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
            activeQuality = player.videoFormat?.let { format ->
                if (format.height > 0) "${format.height}p${format.frameRate.takeIf { it.isFinite() && it > 30 }?.roundToInt() ?: ""}"
                else ""
            }.orEmpty(),
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
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_VIDEO) for (trackIndex in 0 until group.length) {
                if (group.isTrackSupported(trackIndex)) {
                    val format = group.getTrackFormat(trackIndex)
                    val height = format.height.takeIf { it > 0 }
                    val fps = format.frameRate.takeIf { it > 0 && it.isFinite() }?.roundToInt()
                    val label = if (height != null) "${height}p${fps?.takeIf { it > 30 } ?: ""}" else "Исходное"
                    qualities += QualityOption("$groupIndex:$trackIndex", label, height, fps)
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
            selectedQualityId = restored?.id)
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
