package com.oprawa.twitch20.playback

/** Numerical HLS observations only. Never retain URIs, raw tags, titles or account identifiers. */
internal class HlsPlaybackDiagnostics {
    private data class Window(val sequence: Long, val segments: Int, val parts: Int,
        val durationUs: Long, val ended: Boolean)

    private var previous: Window? = null
    private var changedAtMs = 0L
    private var loggedAtMs: Long? = null
    private var loggedSequence: Long? = null
    private var previousCommercialMetadata: Boolean? = null

    fun reset() {
        previous = null
        changedAtMs = 0L
        loggedAtMs = null
        loggedSequence = null
        previousCommercialMetadata = null
    }

    fun observe(sequence: Long, segments: Int, parts: Int, durationUs: Long, ended: Boolean,
        tags: List<String>, nowMs: Long): String? {
        val window = Window(sequence, segments, parts, durationUs, ended)
        if (window != previous) changedAtMs = nowMs
        previous = window
        // Presence in a sliding playlist is not proof that an ad is currently playing.
        // In particular, a DATERANGE can remain after its media has finished.
        val commercialMetadata = tags.any { tag ->
            (tag.startsWith("#EXT-X-DATERANGE:") &&
                (tag.contains("twitch-stitched-ad", ignoreCase = true) ||
                    tag.contains("X-TV-TWITCH-AD-", ignoreCase = true)))
        }
        val markerChanged = previousCommercialMetadata != commercialMetadata
        previousCommercialMetadata = commercialMetadata
        val lastLog = loggedAtMs
        if (!markerChanged && lastLog != null && nowMs - lastLog < 5_000) return null
        loggedAtMs = nowMs
        val sequenceDelta = loggedSequence?.let { sequence - it } ?: 0L
        loggedSequence = sequence
        return "hls_window media_sequence=$sequence sequence_delta=$sequenceDelta" +
            " segment_count=$segments trailing_part_count=$parts window_duration_ms=${durationUs / 1_000}" +
            " playlist_unchanged_ms=${(nowMs - changedAtMs).coerceAtLeast(0)}" +
            " end_tag=${if (ended) 1 else 0} commercial_metadata=${if (commercialMetadata) 1 else 0}"
    }
}
