package com.oprawa.twitch20.playback

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.playlist.*
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.ParsingLoadable
import java.util.IdentityHashMap
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

@androidx.annotation.OptIn(UnstableApi::class)
internal class LiveHlsSourceFactory(private val delegate: MediaSource.Factory,
    private val dataSource: DataSource.Factory, private val debug: DebugHlsCorrelation?,
    private val observeStuck60s: Boolean) : MediaSource.Factory by delegate {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri
        if (uri?.lastPathSegment?.endsWith(".m3u8", ignoreCase = true) != true) return delegate.createMediaSource(mediaItem)
        // PlaybackSession sets live speed limits only for live items. VOD keeps normal policy.
        val live = mediaItem.liveConfiguration.minPlaybackSpeed != C.RATE_UNSET
        if (!live && debug == null) return delegate.createMediaSource(mediaItem)
        val guard = if (live && !observeStuck60s) RegressedPlaylistPolicy() else null
        var parser: HlsPlaylistParserFactory = DefaultHlsPlaylistParserFactory()
        if (guard != null) parser = guard.parserFactory(parser)
        if (debug != null) { debug.registerMaster(uri); parser = debug.parserFactory(parser) }
        return HlsMediaSource.Factory(dataSource).setPlaylistParserFactory(parser)
            .setPlaylistTrackerFactory { source, errors, parsers, cmcd, executor ->
                val tracker = DefaultHlsPlaylistTracker(source, errors, parsers, cmcd,
                    if (observeStuck60s) 12.0 else DefaultHlsPlaylistTracker.DEFAULT_PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT, executor)
                guard?.tracker(tracker) ?: tracker
            }.createMediaSource(mediaItem)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private class RegressedPlaylistPolicy {
    private val observations = ConcurrentHashMap<Uri, LivePlaylistProgressGuard>()
    private val logged = Collections.newSetFromMap(ConcurrentHashMap<Uri, Boolean>())

    private fun defer(uri: Uri): Boolean = observations[uri]?.shouldDefer(SystemClock.elapsedRealtime()) == true

    fun parserFactory(delegate: HlsPlaylistParserFactory): HlsPlaylistParserFactory = object : HlsPlaylistParserFactory {
        override fun createPlaylistParser(): ParsingLoadable.Parser<HlsPlaylist> = wrap(delegate.createPlaylistParser())
        override fun createPlaylistParser(multivariantPlaylist: HlsMultivariantPlaylist,
            previousMediaPlaylist: HlsMediaPlaylist?): ParsingLoadable.Parser<HlsPlaylist> =
            wrap(delegate.createPlaylistParser(multivariantPlaylist, previousMediaPlaylist))
    }

    private fun wrap(delegate: ParsingLoadable.Parser<HlsPlaylist>): ParsingLoadable.Parser<HlsPlaylist> =
        ParsingLoadable.Parser { uri, input ->
            delegate.parse(uri, input).also { playlist ->
                if (playlist is HlsMediaPlaylist) observations.getOrPut(uri) { LivePlaylistProgressGuard() }.observe(
                    playlist.mediaSequence, playlist.segments.size, playlist.durationUs / 1_000,
                    playlist.targetDurationUs / 1_000, playlist.startOffsetUs == 0L,
                    playlist.tags.any { it.startsWith("#EXT-X-DATERANGE:") &&
                        (it.contains("twitch-stitched-ad", true) || it.contains("X-TV-TWITCH-AD-", true)) },
                    playlist.hasEndTag, SystemClock.elapsedRealtime())
            }
        }

    fun tracker(delegate: HlsPlaylistTracker): HlsPlaylistTracker = object : HlsPlaylistTracker by delegate {
        private val listeners = IdentityHashMap<HlsPlaylistTracker.PlaylistEventListener, HlsPlaylistTracker.PlaylistEventListener>()

        override fun start(initialPlaylistUri: Uri,
            eventDispatcher: androidx.media3.exoplayer.source.MediaSourceEventListener.EventDispatcher,
            primaryPlaylistListener: HlsPlaylistTracker.PrimaryPlaylistListener,
            bandwidthMeter: androidx.media3.exoplayer.upstream.BandwidthMeter) {
            observations.clear(); logged.clear()
            delegate.start(initialPlaylistUri, eventDispatcher, primaryPlaylistListener, bandwidthMeter)
        }

        override fun addListener(listener: HlsPlaylistTracker.PlaylistEventListener) {
            val relay = object : HlsPlaylistTracker.PlaylistEventListener {
                override fun onPlaylistChanged() = listener.onPlaylistChanged()
                override fun onPlaylistError(url: Uri, loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo, forceRetry: Boolean): Boolean {
                    if (loadErrorInfo.exception is HlsPlaylistTracker.PlaylistStuckException && defer(url)) return true
                    return listener.onPlaylistError(url, loadErrorInfo, forceRetry)
                }
            }
            listeners.put(listener, relay)?.let(delegate::removeListener)
            delegate.addListener(relay)
        }

        override fun removeListener(listener: HlsPlaylistTracker.PlaylistEventListener) {
            listeners.remove(listener)?.let(delegate::removeListener)
        }

        override fun maybeThrowPlaylistRefreshError(url: Uri) = checked { delegate.maybeThrowPlaylistRefreshError(url) }
        override fun maybeThrowPrimaryPlaylistRefreshError() = checked { delegate.maybeThrowPrimaryPlaylistRefreshError() }

        private fun checked(block: () -> Unit) {
            try { block() } catch (error: HlsPlaylistTracker.PlaylistStuckException) {
                if (!defer(error.url)) throw error
                if (logged.add(error.url)) Log.i("TwitchPlayback", "live_playlist_progress_grace active=1 max_ms=60000")
                // Delegate continues its scheduled reloads. No seek, source replacement or token request.
            }
        }
    }
}
