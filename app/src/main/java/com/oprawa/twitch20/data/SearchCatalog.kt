package com.oprawa.twitch20.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SearchCatalogType { Channels, Categories, Recordings }

data class SearchCatalogState internal constructor(
    internal val loaded: Map<SearchCatalogType, List<CatalogItem>> = emptyMap(),
    private val initialized: Boolean = false,
    private val loading: Boolean = false,
) {
    val failedTypes: Set<SearchCatalogType> = if (initialized) {
        SearchCatalogType.entries.filterNotTo(linkedSetOf(), loaded::containsKey)
    } else emptySet()

    val page = CatalogPageState(
        items = SearchCatalogType.entries.flatMap { loaded[it].orEmpty() },
        initialized = initialized,
        loading = loading,
        hasMore = false,
    )
}

/** Retains the successful sections of one query, including successful empty sections. */
class SearchCatalog internal constructor(private val fetch: suspend (SearchCatalogType) -> List<CatalogItem>) {
    private val lock = Mutex()
    private val mutableState = MutableStateFlow(SearchCatalogState())
    val state = mutableState.asStateFlow()

    suspend fun load() = lock.withLock {
        val previous = mutableState.value
        val pending = SearchCatalogType.entries.filterNot(previous.loaded::containsKey)
        if (pending.isEmpty()) return@withLock
        mutableState.value = previous.copy(loading = true)
        try {
            val recovered = coroutineScope {
                pending.map { type ->
                    async {
                        try { type to fetch(type).distinctBy(CatalogItem::key) }
                        catch (_: TwitchException) { null }
                    }
                }.mapNotNull { it.await() }.toMap()
            }
            currentCoroutineContext().ensureActive()
            mutableState.value = SearchCatalogState(previous.loaded + recovered, initialized = true)
        } finally {
            if (mutableState.value.page.loading) mutableState.value = previous
        }
    }
}
