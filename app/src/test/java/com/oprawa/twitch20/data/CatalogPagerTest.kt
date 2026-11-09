package com.oprawa.twitch20.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class CatalogPagerTest {
    private data class Item(val id: String, val title: String = id)

    @Test fun appendsPagesWithoutMovingExistingIdsAndUpdatesDuplicateMetadata() = runTest {
        val cursors = mutableListOf<String?>()
        val pager = CatalogPager<Item>({ it.id }) { cursor ->
            cursors.add(cursor)
            if (cursor == null) CatalogPage(listOf(Item("a"), Item("b")), "next")
            else CatalogPage(listOf(Item("b", "new"), Item("c")), null)
        }
        pager.loadNext(); pager.loadNext(); pager.loadNext()
        assertEquals(listOf(null, "next"), cursors)
        assertEquals(listOf(Item("a"), Item("b", "new"), Item("c")), pager.state.value.items)
        assertFalse(pager.state.value.hasMore)
    }

    @Test fun failedAppendPreservesCardsAndRetriesTheSameCursor() = runTest {
        var fail = true
        val cursors = mutableListOf<String?>()
        val pager = CatalogPager<Item>({ it.id }) { cursor ->
            cursors.add(cursor)
            if (cursor == null) CatalogPage(listOf(Item("a")), "second")
            else { if (fail) throw TwitchException(TwitchError.NETWORK); CatalogPage(listOf(Item("b")), null) }
        }
        pager.loadNext(); pager.loadNext()
        assertEquals(listOf(Item("a")), pager.state.value.items)
        assertNotNull(pager.state.value.error)
        assertFalse(pager.state.value.loading)
        fail = false; pager.loadNext()
        assertEquals(listOf(null, "second", "second"), cursors)
        assertEquals(listOf(Item("a"), Item("b")), pager.state.value.items)
        assertNull(pager.state.value.error)
    }

    @Test fun concurrentLoadsIssueOnlyOneRequest() = runTest {
        var calls = 0
        val started = CompletableDeferred<Unit>(); val response = CompletableDeferred<Unit>()
        val pager = CatalogPager<Item>({ it.id }) {
            calls++; started.complete(Unit); response.await(); CatalogPage(listOf(Item("a")), null)
        }
        val first = launch { pager.loadNext() }; started.await()
        pager.loadNext()
        assertEquals(1, calls)
        response.complete(Unit); first.join()
        assertEquals(listOf(Item("a")), pager.state.value.items)
    }

    @Test fun cancelledOldResponseCannotOverwriteAResetCatalog() = runTest {
        var calls = 0
        val started = CompletableDeferred<Unit>(); val response = CompletableDeferred<Unit>()
        val pager = CatalogPager<Item>({ it.id }) {
            if (++calls == 1) withContext(NonCancellable) {
                started.complete(Unit); response.await(); CatalogPage(listOf(Item("old")), "old-cursor")
            } else CatalogPage(listOf(Item("new")), null)
        }
        val old = launch { pager.loadNext() }; started.await()
        pager.reset(); pager.loadNext()
        response.complete(Unit); old.join()
        assertEquals(listOf(Item("new")), pager.state.value.items)
        assertFalse(pager.state.value.hasMore)
    }

    @Test fun cancelPreservesEarlierPageAndAllowsRetry() = runTest {
        var calls = 0
        val started = CompletableDeferred<Unit>(); val blocked = CompletableDeferred<Unit>()
        val pager = CatalogPager<Item>({ it.id }) { cursor ->
            calls++
            if (cursor == null) CatalogPage(listOf(Item("a")), "next")
            else { if (calls == 2) { started.complete(Unit); blocked.await() }; CatalogPage(listOf(Item("b")), null) }
        }
        pager.loadNext()
        val job = launch { pager.loadNext() }; started.await(); job.cancel(); job.join()
        assertFalse(pager.state.value.loading)
        assertEquals(listOf(Item("a")), pager.state.value.items)
        pager.loadNext()
        assertEquals(listOf(Item("a"), Item("b")), pager.state.value.items)
    }

    @Test fun repeatedCursorIsAnErrorNotAnEndlessAutomaticLoad() = runTest {
        var calls = 0
        val pager = CatalogPager<Item>({ it.id }) {
            calls++; CatalogPage(listOf(Item("$calls")), "same")
        }
        pager.loadNext(); pager.loadNext()
        assertNotNull(pager.state.value.error)
        assertEquals(listOf(Item("1")), pager.state.value.items)
        assertFalse(pager.state.value.loading)
    }

    @Test fun memoryLimitIsExplicitAndStopsRequests() = runTest {
        var calls = 0
        val pager = CatalogPager<Item>({ it.id }, maxItems = 2) {
            calls++; CatalogPage(listOf(Item("a"), Item("b"), Item("c")), "more")
        }
        pager.loadNext(); pager.loadNext()
        assertEquals(1, calls)
        assertEquals(listOf(Item("a"), Item("b")), pager.state.value.items)
        assertTrue(pager.state.value.limitReached)
        assertFalse(pager.state.value.hasMore)
    }

    @Test fun skipsAnEmptyIntermediatePageWithoutLosingItsCursor() = runTest {
        val cursors = mutableListOf<String?>()
        val pager = CatalogPager<Item>({ it.id }) { cursor ->
            cursors.add(cursor)
            when (cursor) {
                null -> CatalogPage(listOf(Item("a")), "empty")
                "empty" -> CatalogPage(emptyList(), "third")
                else -> CatalogPage(listOf(Item("b")), null)
            }
        }
        pager.loadNext(); pager.loadNext()
        assertEquals(listOf(null, "empty", "third"), cursors)
        assertEquals(listOf(Item("a"), Item("b")), pager.state.value.items)
        assertNull(pager.state.value.error)
    }

    @Test fun repeatedEmptyPagesAreBoundedAndKeepTheOriginalRetryCursor() = runTest {
        var calls = 0
        val pager = CatalogPager<Item>({ it.id }) {
            calls++; CatalogPage(emptyList(), "cursor-$calls")
        }
        pager.loadNext()
        assertEquals(3, calls)
        assertNotNull(pager.state.value.error)
        assertTrue(pager.state.value.items.isEmpty())
    }

    @Test fun localFollowBatchesCanContinueBeyondEmptyChannelsWithoutAnInfiniteRequest() = runTest {
        val cursors = mutableListOf<String?>()
        val pager = CatalogPager<Item>({ it.id }, allowEmptyPageProgress = true) { cursor ->
            cursors += cursor
            val offset = cursor?.toInt() ?: 0
            if (offset < 3) CatalogPage(emptyList(), "${offset + 1}")
            else CatalogPage(listOf(Item("archive")), null)
        }
        pager.loadNext()
        assertEquals(listOf(null, "1", "2"), cursors)
        assertTrue(pager.state.value.initialized)
        assertTrue(pager.state.value.hasMore)
        assertNull(pager.state.value.error)
        pager.loadNext()
        assertEquals(listOf(Item("archive")), pager.state.value.items)
        assertFalse(pager.state.value.hasMore)
    }
}
