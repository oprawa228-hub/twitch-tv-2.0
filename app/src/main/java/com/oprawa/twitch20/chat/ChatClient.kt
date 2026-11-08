package com.oprawa.twitch20.chat

import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.random.Random
import kotlin.coroutines.EmptyCoroutineContext

data class ChatMessage(
    val id: String,
    val nickname: String,
    val text: String,
    val color: String,
    val login: String = "",
    val userId: String? = null,
)

/** One read-only guest IRC WebSocket. No chat text or credentials are logged/persisted. */
class ChatClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).pingInterval(30, TimeUnit.SECONDS).build()
    private val lock = Any()
    private var socket: WebSocket? = null
    private var reconnect: Job? = null
    private var generation = 0L
    private var channel: String? = null
    private var attempts = 0
    private var released = false
    private val mutableMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val mutableStatus = MutableStateFlow("Чат выключен")
    val messages: StateFlow<List<ChatMessage>> = mutableMessages.asStateFlow()
    val status: StateFlow<String> = mutableStatus.asStateFlow()

    fun connect(login: String) {
        val normalized = login.lowercase()
        if (!normalized.matches(Regex("[a-z0-9_]{1,25}"))) {
            close()
            mutableStatus.value = "Неверное имя канала"
            return
        }
        val previous = synchronized(lock) {
            if (released) return
            if (channel == normalized && socket != null) return
            val detached = disconnectLocked()
            channel = normalized
            attempts = 0
            mutableMessages.value = emptyList()
            openLocked(generation, normalized)
            detached
        }
        cancelSocketOnIo(previous)
    }

    /** Reusable: close current channel; connect() may be called again afterwards. */
    fun close() {
        val previous = synchronized(lock) {
            if (released) return
            val detached = disconnectLocked()
            mutableMessages.value = emptyList()
            mutableStatus.value = "Чат выключен"
            detached
        }
        cancelSocketOnIo(previous)
    }

    /** Permanently dispose the owner, including its dispatcher and coroutine resources. */
    fun release() {
        val previous = synchronized(lock) {
            if (released) return
            released = true
            val detached = disconnectLocked()
            mutableMessages.value = emptyList()
            mutableStatus.value = "Чат закрыт"
            detached
        }
        // Independent of the cancelled owner scope: TLS close may write close_notify.
        // Dispatching directly to shared IO creates no persistent cleanup scope/executor.
        Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable {
            try {
                scope.cancel()
                previous?.cancel()
                client.dispatcher.cancelAll()
                client.connectionPool.evictAll()
            } finally {
                client.dispatcher.executorService.shutdown()
            }
        })
    }

    private fun disconnectLocked(): WebSocket? {
        generation++
        channel = null
        reconnect?.cancel(); reconnect = null
        val detached = socket
        socket = null
        return detached
    }

    private fun cancelSocketOnIo(previous: WebSocket?) {
        if (previous != null) Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable { previous.cancel() })
    }

    private fun openLocked(epoch: Long, login: String) {
        if (generation != epoch || channel != login) return
        mutableStatus.value = "Подключение к чату…"
        socket = client.newWebSocket(Request.Builder().url("wss://irc-ws.chat.twitch.tv:443").build(), object : WebSocketListener() {
            private var pending = ""

            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(lock) {
                    if (!current(epoch, webSocket)) { cancelSocketOnIo(webSocket); return }
                    val nick = "justinfan" + Random.nextInt(100000, 999999)
                    // Twitch WebSocket accepts one IRC command per text message.
                    // Batching these into one frame produced no welcome in a real probe.
                    listOf("CAP REQ :twitch.tv/tags twitch.tv/commands", "PASS SCHMOOPIIE", "NICK $nick", "JOIN #$login")
                        .forEach { webSocket.send("$it\r\n") }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                synchronized(lock) {
                    if (!current(epoch, webSocket)) return
                    if (text.length + pending.length > 65_536) {
                        webSocket.close(1009, "Frame too large")
                        retryLocked(epoch, webSocket)
                        return
                    }
                    pending += text
                    // Twitch normally sends complete CRLF lines; preserve a partial trailing line.
                    var index = pending.indexOf("\r\n")
                    while (index >= 0) {
                        val line = pending.substring(0, index)
                        pending = pending.substring(index + 2)
                        when (val event = IrcParser.parse(line)) {
                            IrcEvent.RoomState -> {
                                attempts = 0
                                mutableStatus.value = "Чат · только чтение"
                            }
                            IrcEvent.Reconnect -> {
                                cancelSocketOnIo(webSocket)
                                retryLocked(epoch, webSocket)
                                return
                            }
                            is IrcEvent.Rejected -> {
                                // A terminal JOIN/auth refusal is not a transport outage.
                                // Invalidate this socket and retries; an explicit connect may try again.
                                cancelSocketOnIo(disconnectLocked())
                                mutableStatus.value = event.message
                                return
                            }
                            else -> handleLocked(event, webSocket)
                        }
                        index = pending.indexOf("\r\n")
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                synchronized(lock) { retryLocked(epoch, webSocket) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                synchronized(lock) { retryLocked(epoch, webSocket) }
            }
        })
    }

    private fun current(epoch: Long, source: WebSocket): Boolean = epoch == generation && source === socket && channel != null

    private fun retryLocked(epoch: Long, source: WebSocket) {
        if (!current(epoch, source)) return
        socket = null
        if (reconnect?.isActive == true) return
        val login = channel ?: return
        val seconds = (1L shl attempts.coerceAtMost(5)).coerceAtMost(30L)
        attempts++
        mutableStatus.value = "Нет связи с чатом · повтор через $seconds с"
        reconnect = scope.launch {
            delay(seconds * 1000L)
            synchronized(lock) {
                reconnect = null
                if (epoch == generation && channel == login) openLocked(epoch, login)
            }
        }
    }

    private fun handleLocked(event: IrcEvent?, webSocket: WebSocket) {
        if (event is IrcEvent.Ping) webSocket.send("PONG :${event.payload}\r\n")
        else mutableMessages.value = ChatTimeline.apply(mutableMessages.value, event)
    }
}

internal sealed interface IrcEvent {
    data object Reconnect : IrcEvent
    data object RoomState : IrcEvent
    data class Message(val value: ChatMessage) : IrcEvent
    data class Ping(val payload: String) : IrcEvent
    data class Delete(val id: String) : IrcEvent
    data class Clear(val nickname: String?, val userId: String? = null) : IrcEvent
    data class Rejected(val message: String) : IrcEvent
}

internal object IrcParser {
    fun parse(line: String): IrcEvent? {
        if (line.isBlank()) return null
        var rest = line
        val tags = mutableMapOf<String, String>()
        if (rest.startsWith('@')) {
            val end = rest.indexOf(' ')
            if (end < 0) return null
            rest.substring(1, end).split(';').forEach { tag ->
                val pair = tag.split('=', limit = 2)
                tags[pair[0]] = unescape(pair.getOrElse(1) { "" })
            }
            rest = rest.substring(end + 1)
        }
        var prefix = ""
        if (rest.startsWith(':')) {
            val end = rest.indexOf(' ')
            if (end < 0) return null
            prefix = rest.substring(1, end)
            rest = rest.substring(end + 1)
        }
        val command = rest.substringBefore(' ')
        val trailing = rest.indexOf(" :").takeIf { it >= 0 }?.let { rest.substring(it + 2) }
        return when (command) {
            "RECONNECT" -> IrcEvent.Reconnect
            "ROOMSTATE" -> IrcEvent.RoomState
            "PING" -> trailing?.take(200)?.replace("\r", "")?.replace("\n", "")?.let(IrcEvent::Ping)
            "PRIVMSG", "USERNOTICE" -> {
                val body = trailing ?: return null
                if (body.isBlank()) return null
                // USERNOTICE has a server prefix; the author's login is in tags.
                val login = if (command == "USERNOTICE") tags["login"].orEmpty() else prefix.substringBefore('!')
                val name = tags["display-name"]?.takeIf { it.isNotBlank() } ?: login
                if (name.isBlank()) return null
                val normalized = if (body.startsWith("\u0001ACTION ") && body.endsWith('\u0001')) body.substring(8, body.length - 1) else body
                IrcEvent.Message(ChatMessage(tags["id"]?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                    name.take(64), normalized.take(500), tags["color"]?.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) } ?: "#A6B3C6",
                    login.take(64), tags["user-id"]?.takeIf { it.isNotBlank() }))
            }
            "CLEARMSG" -> tags["target-msg-id"]?.takeIf { it.isNotBlank() }?.let(IrcEvent::Delete)
            "CLEARCHAT" -> IrcEvent.Clear(trailing?.takeIf { it.isNotBlank() }, tags["target-user-id"]?.takeIf { it.isNotBlank() })
            "NOTICE" -> when {
                tags["msg-id"] == "msg_channel_suspended" -> IrcEvent.Rejected("Чат канала недоступен.")
                trailing == "Login authentication failed" || trailing == "Improperly formatted auth" ->
                    IrcEvent.Rejected("Twitch отклонил подключение к чату.")
                else -> null
            }
            else -> null
        }
    }

    private fun unescape(value: String): String {
        val out = StringBuilder()
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            if (char == '\\' && index < value.length) out.append(when (val escaped = value[index++]) {
                's' -> ' '; ':' -> ';'; 'r' -> '\r'; 'n' -> '\n'; else -> escaped
            }) else out.append(char)
        }
        return out.toString()
    }
}
