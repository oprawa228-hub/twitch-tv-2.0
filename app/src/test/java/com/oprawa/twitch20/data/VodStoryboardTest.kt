package com.oprawa.twitch20.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VodStoryboardTest {
    private val url = "https://vod.example.cloudfront.net/storyboards/42-info.json".toHttpUrl()

    @Test fun intervalAndGridLocateFramesAcrossSheetsAndClampAtEnd() {
        val manifest = JSONArray("""
            [{"cols":2,"rows":2,"interval":12,"count":6,"images":["42-high-0.jpg","42-high-1.jpg"]}]
        """.trimIndent())
        val board = requireNotNull(parseManifest(manifest, url, "42") { null })

        assertEquals(0, board.tileIndex(-1))
        assertEquals(0, board.tileIndex(11_999))
        assertEquals(1, board.tileIndex(12_000))
        val firstTileOnSecondSheet = board.tileIndex(48_000)
        assertEquals(1, board.sheetIndex(firstTileOnSecondSheet))
        assertEquals(0, board.tileColumn(firstTileOnSecondSheet))
        assertEquals(0, board.tileRow(firstTileOnSecondSheet))
        assertEquals(5, board.tileIndex(Long.MAX_VALUE))
        assertEquals(1, board.tileRow(3))
    }

    @Test fun rejectsUnrelatedOrExternalSpritesAndFallsBackToValidVariant() {
        val manifest = JSONArray("""
            [
              {"cols":2,"rows":2,"interval":12,"count":4,"images":["42-low-0.jpg"]},
              {"cols":2,"rows":2,"interval":12,"count":4,"images":["https://example.org/42-high-0.jpg"]}
            ]
        """.trimIndent())
        assertNotNull(parseManifest(manifest, url, "42") { null })
        assertNull(parseManifest(manifest, url, "43") { null })
    }
}
