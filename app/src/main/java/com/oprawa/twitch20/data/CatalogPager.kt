package com.oprawa.twitch20.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import com.oprawa.twitch20.auth.AuthException

data class CatalogPageState<T>(
    val items: List<T> = emptyList(),
    val initialized: Boolean = false,
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val limitReached: Boolean = false,
    val revision: Long = 0,
)

/** One retained catalog; callers own coroutine lifetime. */
class CatalogPager<T>(
    private val identity: (T) -> String,
    private val maxItems: Int = 1000,
    private val allowEmptyPageProgress: Boolean = false,
    private val fetch: suspend (String?) -> CatalogPage<T>,
) {
    private val mutableState = MutableStateFlow(CatalogPageState<T>())
    val state = mutableState.asStateFlow()
    private val gate = Any()
    private var generation = 0L
    private var cursor: String? = null
    private val cursors = mutableSetOf<String>()

    init { require(maxItems > 0) }

    suspend fun loadNext() {
        val request = synchronized(gate) {
            if (mutableState.value.loading || !mutableState.value.hasMore) return
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            ++generation to cursor
        }
        try {
            val original = mutableState.value.items
            val rows = linkedMapOf<String, T>()
            original.forEach { rows[identity(it)] = it }
            val visited = mutableSetOf<String>()
            var after = request.second
            var next: String?
            var scanned = 0
            while (true) {
                synchronized(gate) { if (generation != request.first) return }
                val page = fetch(after)
                scanned++
                next = page.nextCursor
                synchronized(gate) {
                    if (generation != request.first) return
                    if (next != null && (next.isBlank() || next == after || next in cursors || next in visited))
                        throw TwitchException(TwitchError.SERVICE)
                }
                page.items.forEach {
                    val id = identity(it)
                    if (id.isBlank()) throw TwitchException(TwitchError.SERVICE)
                    rows[id] = it
                }
                next?.let(visited::add)
                if (next == null || rows.size > original.size) break
                // Helix may return an empty/duplicate-only page with a continuation.
                // Scan a bounded number, then retain the original retry position.
                if (scanned >= 3) {
                    if (allowEmptyPageProgress) break
                    throw TwitchException(TwitchError.SERVICE)
                }
                after = next
            }
            synchronized(gate) {
                if (generation != request.first) return
                val limited = rows.size > maxItems || (rows.size == maxItems && next != null)
                cursor = if (limited) null else next
                cursors.addAll(visited)
                mutableState.value = CatalogPageState(rows.values.take(maxItems),
                    initialized = true, hasMore = cursor != null, limitReached = limited,
                    revision = mutableState.value.revision)
            }
        } catch (cancelled: CancellationException) {
            synchronized(gate) {
                if (generation == request.first) mutableState.value = mutableState.value.copy(loading = false)
            }
            throw cancelled
        } catch (error: Exception) {
            synchronized(gate) {
                if (generation == request.first) mutableState.value = mutableState.value.copy(
                    initialized = true, loading = false,
                    error = if (error is TwitchException || error is AuthException) error.message
                        else "Не удалось загрузить страницу. Проверьте сеть и повторите.",
                )
            }
        }
    }

    /** Invalidate late responses without losing already loaded cards. */
    fun cancel() = synchronized(gate) {
        generation++
        mutableState.value = mutableState.value.copy(loading = false)
    }

    fun reset() = synchronized(gate) {
        generation++; cursor = null; cursors.clear()
        mutableState.value = CatalogPageState(revision = generation)
    }
}
