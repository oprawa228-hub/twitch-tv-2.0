package com.oprawa.twitch20.playback

import android.net.Uri
import android.util.Log
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistParserFactory
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory
import androidx.media3.exoplayer.upstream.ParsingLoadable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.security.MessageDigest

/** DEBUG-only correlation. Capabilities are compared in memory; output is static labels/numbers. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class DebugHlsCorrelation {
    private data class Role(val kind: Int, val index: Int, val height: Int)
    private val roles = ConcurrentHashMap<Uri, Role>()
    private val parsedCount = AtomicInteger()
    private data class Payload(val count: Int, val timeMs: Long, val fingerprint: ByteArray,
        val snapshot: HlsPayloadObservation.Snapshot)
    private val payloads = ConcurrentHashMap<Uri, Payload>()
    private data class ParsedSegments(val refs: ByteArray, val timing: ByteArray)
    private val parsedSegments = ConcurrentHashMap<Uri, ParsedSegments>()

    fun role(uri: Uri): String {
        val role = roles[uri] ?: Role(-1, -1, -1)
        return "role=${role.kind} variant_index=${role.index} height=${role.height}"
    }

    fun error(uri: Uri, manifest: HlsManifest?) {
        val primary = manifest?.mediaPlaylist?.baseUri?.let { Uri.parse(it) }
        Log.i("TwitchPlayback", "debug_hls_failure ${role(uri)} primary=${if (uri == primary) 1 else 0}")
        payloads[uri]?.let { logPayload("debug_hls_failure_payload", uri, it) }
    }

    fun primary(manifest: HlsManifest?) {
        val playlist = manifest?.mediaPlaylist ?: return
        val uri = Uri.parse(playlist.baseUri)
        payloads[uri]?.let { logPayload("debug_hls_primary_payload", uri, it) }
        parsedSegments[uri]?.let { latest ->
            val current = segmentFingerprints(playlist)
            Log.i("TwitchPlayback", "debug_hls_primary_match ${role(uri)}" +
                " segment_refs_match=${if (latest.refs.contentEquals(current.refs)) 1 else 0}" +
                " segment_timing_match=${if (latest.timing.contentEquals(current.timing)) 1 else 0}")
        }
    }

    private fun segmentFingerprints(playlist: HlsMediaPlaylist): ParsedSegments {
        val refs = MessageDigest.getInstance("SHA-256")
        val timing = MessageDigest.getInstance("SHA-256")
        playlist.segments.forEach { segment ->
            refs.update(segment.url.toByteArray(Charsets.UTF_8)); refs.update(0.toByte())
            timing.update("${segment.relativeStartTimeUs}:${segment.durationUs}:${segment.relativeDiscontinuitySequence};".toByteArray())
        }
        return ParsedSegments(refs.digest(), timing.digest())
    }

    private fun logPayload(event: String, uri: Uri, payload: Payload) {
        Log.i("TwitchPlayback", "$event ${role(uri)} count=${payload.count}" +
            " payload_age_ms=${SystemClock.elapsedRealtime() - payload.timeMs} ${payload.snapshot.safeFields()}")
    }

    fun registerMaster(uri: Uri) { roles[uri] = Role(0, -1, -1) }

    fun parserFactory(delegate: HlsPlaylistParserFactory): HlsPlaylistParserFactory = object : HlsPlaylistParserFactory {
        override fun createPlaylistParser(): ParsingLoadable.Parser<HlsPlaylist> = wrap(delegate.createPlaylistParser())
        override fun createPlaylistParser(multivariantPlaylist: HlsMultivariantPlaylist,
            previousMediaPlaylist: HlsMediaPlaylist?): ParsingLoadable.Parser<HlsPlaylist> =
            wrap(delegate.createPlaylistParser(multivariantPlaylist, previousMediaPlaylist))
    }

    private fun wrap(delegate: ParsingLoadable.Parser<HlsPlaylist>): ParsingLoadable.Parser<HlsPlaylist> =
        ParsingLoadable.Parser { uri, input ->
            val count = parsedCount.incrementAndGet()
            val observed = HlsPayloadObservation(input)
            try {
              delegate.parse(uri, observed).also { parsed ->
                when (parsed) {
                    is HlsMultivariantPlaylist -> {
                        parsed.variants.forEachIndexed { index, variant ->
                            roles[variant.url] = Role(if (variant.format.height > 0) 1 else 2, index, variant.format.height)
                        }
                        parsed.audios.forEachIndexed { index, audio -> audio.url?.let { roles[it] = Role(2, index, -1) } }
                        Log.i("TwitchPlayback", "debug_hls_parsed count=$count ${role(uri)} master=1 variants=${parsed.variants.size}")
                    }
                    is HlsMediaPlaylist -> {
                      val fingerprints = segmentFingerprints(parsed)
                      val previous = parsedSegments.put(uri, fingerprints)
                      Log.i("TwitchPlayback", "debug_hls_parsed count=$count ${role(uri)} master=0" +
                        " sequence=${parsed.mediaSequence} segments=${parsed.segments.size} duration_ms=${parsed.durationUs / 1_000}" +
                        " target_ms=${parsed.targetDurationUs / 1_000}" +
                        " start_ms=${parsed.startOffsetUs.takeUnless { it == C.TIME_UNSET }?.div(1_000) ?: -1}" +
                        " end_tag=${if (parsed.hasEndTag) 1 else 0}" +
                        " segment_refs_changed=${previous?.let { if (it.refs.contentEquals(fingerprints.refs)) 0 else 1 } ?: -1}" +
                        " segment_timing_changed=${previous?.let { if (it.timing.contentEquals(fingerprints.timing)) 0 else 1 } ?: -1}")
                    }
                }
              }
            } finally {
                // No second request: these are the exact bytes consumed by the native parser.
                val payload = Payload(count, SystemClock.elapsedRealtime(), observed.fingerprint(), observed.snapshot())
                val previous = payloads.put(uri, payload)
                val changed = previous?.let { if (it.fingerprint.contentEquals(payload.fingerprint)) 0 else 1 } ?: -1
                Log.i("TwitchPlayback", "debug_hls_payload count=$count ${role(uri)} payload_changed=$changed ${payload.snapshot.safeFields()}")
            }
        }
}
