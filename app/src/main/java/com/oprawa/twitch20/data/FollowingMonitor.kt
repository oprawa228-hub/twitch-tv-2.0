package com.oprawa.twitch20.data

import com.oprawa.twitch20.auth.AuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

data class FollowingState(
    val channels: List<Channel> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val initialized: Boolean = false,
)

data class LiveStart(val channel: Channel, val observedAtMs: Long)

/** An interval clock; changing the TV's displayed time must not extend notification expiry. */
internal fun notificationClockMs(): Long = System.nanoTime() / 1_000_000L

/** One foreground owner supplies both sidebar/catalog data and start notifications. */
class FollowingMonitor(private val load: suspend () -> List<Channel>) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(FollowingState())
    val state = mutableState.asStateFlow()
    private val mutableStarts = MutableSharedFlow<LiveStart>(extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val starts = mutableStarts.asSharedFlow()
    private val tracker = LiveStartTracker()

    suspend fun refresh() {
        if (!mutex.tryLock()) return
        mutableState.value = mutableState.value.copy(loading = true)
        try {
            val channels = load().distinctBy { it.id }
            val starts = tracker.update(channels)
            mutableState.value = FollowingState(channels, initialized = true)
            val now = notificationClockMs()
            starts.forEach { mutableStarts.tryEmit(LiveStart(it, now)) }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            val message = when (error) {
                is AuthException -> error.message
                is TwitchException -> error.message
                else -> "Не удалось обновить ваши каналы. Повторите."
            }
            // A failed request is not an empty successful snapshot and must never reset starts.
            mutableState.value = mutableState.value.copy(error = message)
        } finally {
            mutableState.value = mutableState.value.copy(loading = false)
            mutex.unlock()
        }
    }
}

internal class LiveStartTracker {
    private var initialized = false
    private val seen = linkedSetOf<String>()
    fun update(channels: List<Channel>): List<Channel> {
        val live = channels.filter { it.live }
        val active = live.map { it.id + ":" + it.liveStreamId.ifBlank { "unknown" } }.toSet()
        val starts = live.filter { channel ->
            val identity = channel.id + ":" + channel.liveStreamId.ifBlank { "unknown" }
            seen.add(identity)
        }
        // A long-running live stream must not be mistaken for a new start when old history expires.
        val expired = seen.iterator()
        while (seen.size > 4_000 && expired.hasNext()) if (expired.next() !in active) expired.remove()
        if (!initialized) { initialized = true; return emptyList() }
        return starts
    }
}
