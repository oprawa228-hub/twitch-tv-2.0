package com.oprawa.twitch20.playback

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistParserFactory
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.ParsingLoadable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** DEBUG-only correlation. Capabilities are compared in memory; output is static labels/numbers. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class DebugHlsCorrelation {
    private data class Role(val kind: Int, val index: Int, val height: Int)
    private val roles = ConcurrentHashMap<Uri, Role>()
    private val parsedCount = AtomicInteger()

    fun role(uri: Uri): String {
        val role = roles[uri] ?: Role(-1, -1, -1)
        return "role=${role.kind} variant_index=${role.index} height=${role.height}"
    }

    fun error(uri: Uri, manifest: HlsManifest?) {
        val primary = manifest?.mediaPlaylist?.baseUri?.let { Uri.parse(it) }
        Log.i("TwitchPlayback", "debug_hls_failure ${role(uri)} primary=${if (uri == primary) 1 else 0}")
    }

    fun sourceFactory(delegate: MediaSource.Factory, dataSource: DataSource.Factory): MediaSource.Factory =
        object : MediaSource.Factory by delegate {
            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                val uri = mediaItem.localConfiguration?.uri
                if (uri?.lastPathSegment?.endsWith(".m3u8", ignoreCase = true) != true)
                    return delegate.createMediaSource(mediaItem)
                roles[uri] = Role(0, -1, -1) // 0 master, 1 video, 2 audio, -1 unknown.
                return HlsMediaSource.Factory(dataSource)
                    .setPlaylistParserFactory(parserFactory()).createMediaSource(mediaItem)
            }
        }

    private fun parserFactory(): HlsPlaylistParserFactory = object : HlsPlaylistParserFactory {
        private val delegate = DefaultHlsPlaylistParserFactory()
        override fun createPlaylistParser(): ParsingLoadable.Parser<HlsPlaylist> = wrap(delegate.createPlaylistParser())
        override fun createPlaylistParser(multivariantPlaylist: HlsMultivariantPlaylist,
            previousMediaPlaylist: HlsMediaPlaylist?): ParsingLoadable.Parser<HlsPlaylist> =
            wrap(delegate.createPlaylistParser(multivariantPlaylist, previousMediaPlaylist))
    }

    private fun wrap(delegate: ParsingLoadable.Parser<HlsPlaylist>): ParsingLoadable.Parser<HlsPlaylist> =
        ParsingLoadable.Parser { uri, input ->
            delegate.parse(uri, input).also { parsed ->
                val count = parsedCount.incrementAndGet()
                when (parsed) {
                    is HlsMultivariantPlaylist -> {
                        parsed.variants.forEachIndexed { index, variant ->
                            roles[variant.url] = Role(if (variant.format.height > 0) 1 else 2, index, variant.format.height)
                        }
                        parsed.audios.forEachIndexed { index, audio -> audio.url?.let { roles[it] = Role(2, index, -1) } }
                        Log.i("TwitchPlayback", "debug_hls_parsed count=$count ${role(uri)} master=1 variants=${parsed.variants.size}")
                    }
                    is HlsMediaPlaylist -> Log.i("TwitchPlayback", "debug_hls_parsed count=$count ${role(uri)} master=0" +
                        " sequence=${parsed.mediaSequence} segments=${parsed.segments.size} duration_ms=${parsed.durationUs / 1_000}" +
                        " target_ms=${parsed.targetDurationUs / 1_000}" +
                        " start_ms=${parsed.startOffsetUs.takeUnless { it == C.TIME_UNSET }?.div(1_000) ?: -1}" +
                        " end_tag=${if (parsed.hasEndTag) 1 else 0}")
                }
            }
        }
}
