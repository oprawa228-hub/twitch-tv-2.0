package com.oprawa.twitch20.data

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCatalogTest {
    @Test fun failedRecordingsStayDistinctFromEmptyCategoriesAndRetryWithoutRefetchingSuccesses() = runTest {
        val recordingsAvailable = AtomicBoolean(false)
        val requests = CopyOnWriteArrayList<String>()
        val repository = repository { index ->
            requests.add(index)
            when (index) {
                "CHANNEL" -> """{"data":{"searchFor":{"channels":{"items":[{"id":"1","login":"caster","displayName":"Caster","stream":null}]}}}}"""
                "GAME" -> """{"data":{"searchFor":{"games":{"items":[]}}}}"""
                "VOD" -> if (recordingsAvailable.get()) {
                    """{"data":{"searchFor":{"videos":{"items":[{"id":"2","title":"Archive","owner":{"login":"caster"}}]}}}}"""
                } else UNAVAILABLE
                else -> error("Unexpected search index: $index")
            }
        }
        val search = repository.catalogSearch("Warcraft")

        search.load()
        assertEquals(listOf("channel:1"), search.state.value.page.items.map { it.key })
        assertEquals(setOf(SearchCatalogType.Recordings), search.state.value.failedTypes)
        assertFalse(search.state.value.page.hasMore)

        recordingsAvailable.set(true)
        search.load()
        assertEquals(listOf("channel:1", "video:2"), search.state.value.page.items.map { it.key })
        assertTrue(search.state.value.failedTypes.isEmpty())
        assertEquals(mapOf("CHANNEL" to 1, "GAME" to 1, "VOD" to 2), requests.groupingBy { it }.eachCount())
    }

    @Test fun everyFailedTypeIsReportedWhenNoSearchSectionLoads() = runTest {
        val search = repository { UNAVAILABLE }.catalogSearch("Warcraft")

        search.load()

        assertTrue(search.state.value.page.initialized)
        assertTrue(search.state.value.page.items.isEmpty())
        assertEquals(SearchCatalogType.entries.toSet(), search.state.value.failedTypes)
    }

    private fun repository(reply: (String) -> String): TwitchRepository = TwitchRepository(
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            val query = JSONObject(body).getString("query")
            val index = requireNotNull(Regex("index:([A-Z]+)").find(query)).groupValues[1]
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(reply(index).toResponseBody("application/json".toMediaType())).build()
        }.build(),
    )

    private companion object {
        const val UNAVAILABLE = """{"errors":[{"message":"unavailable"}]}"""
    }
}
