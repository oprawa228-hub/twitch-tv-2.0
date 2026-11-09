package com.oprawa.twitch20.playback

import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest

/** Observes the exact decompressed bytes consumed by the parser, without changing them.
 * Non-tag lines (media URLs) are never retained. Tag lines are bounded and never exposed.
 * Digest stays in memory and is used only to report equal/not equal payloads. DEBUG caller only.
 */
internal class HlsPayloadObservation(input: InputStream) : FilterInputStream(input) {
    private val digest = MessageDigest.getInstance("SHA-256")
    private val line = StringBuilder()
    private var tagLine = false
    private var lineStarted = false
    private var truncatedLine = false
    private var ended = false
    private var bytes = 0L
    private var sequence = 0L
    private var extinf = 0
    private var durationUs = 0L
    private var targetMs = -1L
    private var startMs = -1L
    private var parts = 0
    private var dateranges = 0
    private var discontinuities = 0
    private var endTag = false

    data class Snapshot(val bytes: Long, val sequence: Long, val segments: Int, val durationUs: Long,
        val targetMs: Long, val startMs: Long, val parts: Int, val dateranges: Int,
        val discontinuities: Int, val endTag: Boolean, val eof: Boolean) {
        fun safeFields() = "payload_bytes=$bytes raw_sequence=$sequence raw_extinf=$segments" +
            " raw_duration_ms=${durationUs / 1_000} raw_target_ms=$targetMs raw_start_ms=$startMs" +
            " raw_parts=$parts raw_dateranges=$dateranges raw_discontinuities=$discontinuities" +
            " raw_end_tag=${if (endTag) 1 else 0} payload_eof=${if (eof) 1 else 0}"
    }

    override fun read(): Int {
        val value = `in`.read()
        if (value >= 0) {
            digest.update(value.toByte()); observe(value)
        } else finishLines()
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = `in`.read(buffer, offset, length)
        if (read > 0) {
            digest.update(buffer, offset, read)
            for (index in offset until offset + read) observe(buffer[index].toInt() and 255)
        } else if (read == -1) finishLines()
        return read
    }

    fun snapshot(): Snapshot = Snapshot(bytes, sequence, extinf, durationUs, targetMs,
        startMs, parts, dateranges, discontinuities, endTag, ended)

    fun fingerprint(): ByteArray = digest.digest()

    private fun observe(value: Int) {
        bytes++
        if (value == 10) { consumeLine(); return }
        if (value == 13) return
        if (!lineStarted && (value == 32 || value == 9)) return
        if (!lineStarted) { lineStarted = true; tagLine = value == '#'.code }
        if (tagLine && !truncatedLine) {
            if (line.length < 1_024) line.append(value.toChar()) else truncatedLine = true
        }
    }

    private fun finishLines() {
        if (!ended) { consumeLine(); ended = true }
    }

    private fun consumeLine() {
        val tag = line.toString()
        when {
            tag.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> sequence = tag.substringAfter(':').toLongOrNull() ?: -1
            tag.startsWith("#EXTINF:") -> {
                extinf++
                val duration = tag.substringAfter(':').substringBefore(',').toBigDecimalOrNull()
                if (duration != null && duration.signum() >= 0 && duration < 86_400.toBigDecimal())
                    durationUs += duration.movePointRight(6).toLong()
            }
            tag.startsWith("#EXT-X-TARGETDURATION:") -> targetMs = tag.substringAfter(':').toLongOrNull()?.times(1_000) ?: -1
            tag.startsWith("#EXT-X-START:") -> startMs = Regex("(?:^|,)TIME-OFFSET=(-?[0-9.]+)")
                .find(tag.substringAfter(':'))?.groupValues?.get(1)?.toDoubleOrNull()
                ?.takeIf { it.isFinite() }?.times(1_000)?.toLong() ?: -1
            tag.startsWith("#EXT-X-PART:") -> parts++
            tag.startsWith("#EXT-X-DATERANGE:") -> dateranges++
            tag == "#EXT-X-DISCONTINUITY" -> discontinuities++
            tag == "#EXT-X-ENDLIST" -> endTag = true
        }
        line.setLength(0)
        tagLine = false; lineStarted = false; truncatedLine = false
    }
}
