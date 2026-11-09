package com.oprawa.twitch20.playback

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class HlsPayloadObservationTest {
    @Test fun `decimal segment durations do not accumulate binary float truncation`() {
        val body = "#EXTM3U\n" + (1..16).joinToString("") { "#EXTINF:2.002,\ns$it.ts\n" }
        val observer = HlsPayloadObservation(ByteArrayInputStream(body.toByteArray()))
        observer.readBytes()
        assertEquals(32_032_000L, observer.snapshot().durationUs)
    }

    @Test fun `observed parser input is byte identical and reports only scalar tags`() {
        val body = "#EXTM3U\r\n#EXT-X-MEDIA-SEQUENCE:7\n#EXT-X-TARGETDURATION:5\n" +
            "#EXT-X-START:TIME-OFFSET=0,PRECISE=YES\n" +
            "#EXT-X-DATERANGE:ID=\"private-account\",URL=\"https://secret?token=hidden\"\n" +
            "#EXTINF:2.0,private-title\nhttps://private/segment?token=hidden\n" +
            "#EXT-X-DISCONTINUITY\n#EXTINF:2.235,\nother-secret.ts\n#EXT-X-ENDLIST"
        val bytes = body.toByteArray()
        val observer = HlsPayloadObservation(ByteArrayInputStream(bytes))
        assertArrayEquals(bytes, observer.readBytes())
        val snapshot = observer.snapshot()
        assertEquals(7L, snapshot.sequence)
        assertEquals(2, snapshot.segments)
        assertEquals(4_235_000L, snapshot.durationUs)
        assertEquals(0L, snapshot.startMs)
        assertEquals(5_000L, snapshot.targetMs)
        assertEquals(1, snapshot.dateranges)
        assertEquals(1, snapshot.discontinuities)
        assertTrue(snapshot.endTag && snapshot.eof)
        listOf("private", "secret", "https", "hidden", "token").forEach { assertFalse(snapshot.safeFields().contains(it)) }
    }

    @Test fun `single byte and chunked reads produce same digest and counters`() {
        val bytes = ("#EXTM3U\n#EXT-X-DATERANGE:" + "x".repeat(10_000) +
            "\n#EXTINF:2,\n" + "s".repeat(10_000) + "\n#EXT-X-PART:DURATION=0.2,URI=\"secret\"\n").toByteArray()
        val single = HlsPayloadObservation(ByteArrayInputStream(bytes))
        while (single.read() != -1) Unit
        val chunks = HlsPayloadObservation(ByteArrayInputStream(bytes))
        val buffer = ByteArray(13)
        while (chunks.read(buffer) != -1) Unit
        assertEquals(single.snapshot(), chunks.snapshot())
        assertArrayEquals(single.fingerprint(), chunks.fingerprint())
        assertEquals(1, single.snapshot().segments)
        assertEquals(1, single.snapshot().parts)
    }

    @Test fun `partial parser consumption is explicitly not a complete response`() {
        val observed = HlsPayloadObservation(ByteArrayInputStream("#EXTM3U\n#EXTINF:2,\nsegment\n".toByteArray()))
        repeat(8) { observed.read() }
        assertFalse(observed.snapshot().eof)
        assertEquals(8L, observed.snapshot().bytes)
    }
}
