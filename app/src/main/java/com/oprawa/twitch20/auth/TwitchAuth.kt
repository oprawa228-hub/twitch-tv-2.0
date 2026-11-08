package com.oprawa.twitch20.auth

import android.content.Context
import android.os.SystemClock
import com.oprawa.twitch20.data.Channel
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.EmptyCoroutineContext

data class AuthState(val loggedIn: Boolean = false, val displayName: String = "", val error: String? = null)

class DeviceAuthorization internal constructor(
    val userCode: String,
    val verificationUri: String,
    internal val deviceCode: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
    internal val generation: Long,
    internal val issuedAtMillis: Long = 0,
) {
    override fun toString(): String = "DeviceAuthorization(redacted)"
}

class AuthException(message: String) : Exception(message)

/** Official public-client Device Code Flow. No secret or private web token required. */
class TwitchAuth(context: Context, private val clientId: String) {
    private val vault = TokenVault(context.applicationContext)
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val gate = Any()
    private val generation = AuthGeneration()
    private val closed = AtomicBoolean(false)
    private var session: StoredSession? = null
    private var loaded = false
    private val mutableState = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = mutableState.asStateFlow()

    init {
        scope.launch {
            runAuthValidationLoop(::validateSession, { load() != null && mutableState.value.error != null })
        }
    }

    suspend fun beginLogin(): DeviceAuthorization = withContext(Dispatchers.IO) {
        if (clientId.isBlank()) throw AuthException("Для входа нужен Client ID приложения Twitch. Гостевой режим уже доступен.")
        val epoch = generation.incrementAndGet()
        mutableState.value = mutableState.value.copy(error = null)
        try {
            val response = post("https://id.twitch.tv/oauth2/device", mapOf("client_id" to clientId, "scopes" to SCOPES))
            requireCurrent(epoch)
            if (response.status != 200) throw AuthException("Twitch не выдал код входа. Проверьте Client ID и тип Public в консоли приложения.")
            val uri = response.json.optString("verification_uri")
            val parsed = runCatching { uri.toHttpUrl() }.getOrNull()
            if (parsed?.isHttps != true || parsed.host !in setOf("www.twitch.tv", "twitch.tv")) throw AuthException("Twitch вернул неподходящую страницу входа.")
            val code = response.json.optString("device_code")
            val userCode = response.json.optString("user_code")
            if (code.isBlank() || userCode.isBlank()) throw AuthException("Twitch вернул неполный код входа. Повторите.")
            DeviceAuthorization(userCode, uri, code, response.json.optInt("expires_in", 600).coerceIn(30, 3600),
                response.json.optInt("interval", 5).coerceAtLeast(1), epoch, SystemClock.elapsedRealtime())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            val safe = safeError(error)
            if (generation.get() == epoch) mutableState.value = mutableState.value.copy(error = safe.message)
            throw safe
        }
    }

    suspend fun awaitLogin(device: DeviceAuthorization): Unit = withContext(Dispatchers.IO) {
        var interval = device.intervalSeconds.toLong().coerceAtLeast(1) * 1000L
        val deadline = device.issuedAtMillis + device.expiresInSeconds * 1000L
        try {
            while (SystemClock.elapsedRealtime() < deadline) {
                requireCurrent(device.generation)
                delay(interval)
                requireCurrent(device.generation)
                if (SystemClock.elapsedRealtime() >= deadline) break
                val response = post("https://id.twitch.tv/oauth2/token", mapOf(
                    "client_id" to clientId, "scopes" to SCOPES, "device_code" to device.deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                ))
                requireCurrent(device.generation)
                when (AuthProtocol.pollDecision(response.status, response.json)) {
                    PollDecision.PENDING -> Unit
                    PollDecision.SLOW_DOWN -> interval += 5000L
                    PollDecision.DENIED -> throw AuthException("Вход отменён на странице Twitch.")
                    PollDecision.EXPIRED -> throw AuthException("Код входа истёк. Получите новый код.")
                    PollDecision.FAILED -> throw AuthException("Twitch не завершил вход. Получите новый код и повторите.")
                    PollDecision.COMPLETE -> {
                        mutex.withLock {
                            val candidate = StoredSession(clientId, response.json.getString("access_token"), response.json.getString("refresh_token"))
                            save(candidate, device.generation)
                            val valid = checkSession(candidate, device.generation)
                            publish(valid, device.generation)
                        }
                        return@withContext
                    }
                }
            }
            throw AuthException("Код входа истёк. Получите новый код.")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            val safe = safeError(error)
            if (generation.get() == device.generation) mutableState.value = mutableState.value.copy(error = safe.message)
            throw safe
        }
    }

    /** Startup/hourly validation; network failure preserves encrypted credentials for retry. */
    suspend fun validateSession(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val epoch = generation.get()
            try {
                val current = load() ?: return@withLock
                if (current.clientId != clientId || clientId.isBlank()) {
                    clearLocal(epoch)
                    return@withLock
                }
                publish(checkSession(current, epoch), epoch)
            } catch (cancelled: CancellationException) {
                // Starting a newer login invalidates this request, but must not kill
                // the long-lived hourly validator. Real coroutine cancellation propagates.
                if (!currentCoroutineContext().isActive || generation.get() == epoch) throw cancelled
            }
            catch (error: Exception) {
                if (generation.get() == epoch) mutableState.value = mutableState.value.copy(error = safeError(error).message)
            }
        }
    }

    suspend fun followedStreams(): List<Channel> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val epoch = generation.get()
            val current = validatedAccount(epoch)
            followedPages("streams/followed", "user_id", current, epoch).map(FollowedCatalog::live)
        }
    }

    /** All followed channels, including offline. Maximum 2,000; larger lists fail explicitly. */
    suspend fun followedChannels(): List<Channel> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val epoch = generation.get()
            val current = validatedAccount(epoch)
            val followed = followedPages("channels/followed", "broadcaster_id", current, epoch)
            if (followed.isEmpty()) return@withLock emptyList()
            // A previous page may have rotated the token; always use the persisted latest pair.
            val latest = load() ?: throw AuthException("Войдите в Twitch снова.")
            val live = followedPages("streams/followed", "user_id", latest, epoch)
            requireCurrent(epoch)
            FollowedCatalog.merge(followed, live)
        }
    }

    private suspend fun validatedAccount(epoch: Long): StoredSession {
        val saved = load() ?: throw AuthException("Войдите в Twitch, чтобы увидеть отслеживаемые каналы.")
        if (saved.clientId != clientId) throw AuthException("Настройки входа изменились. Войдите снова.")
        return checkSession(saved, epoch).also { publish(it, epoch) }
    }

    /** Official authenticated catalog GET; credentials remain inside the account owner. */
    suspend fun helixGet(endpoint: String, parameters: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val url = HelixCatalogAccess.url(endpoint, parameters)
        mutex.withLock {
            val epoch = generation.get()
            var current = validatedAccount(epoch)
            HelixCatalogAccess.fetch(
                request = {
                    requireCurrent(epoch)
                    val response = get(url.toString(), current.accessToken, helix = true)
                    requireCurrent(epoch)
                    HelixReply(response.status, response.json)
                },
                refresh = {
                    current = refresh(current, epoch)
                    // Refresh preserves this validated account's identity. The GET retry
                    // validates the replacement access token without another refresh loop.
                },
                expire = { clearLocal(epoch) },
            ).also { publish(current, epoch) }
        }
    }

    /** Caller holds mutex, including refresh: a single-use refresh token cannot race pages. */
    private suspend fun followedPages(endpoint: String, identityField: String, initial: StoredSession, epoch: Long): List<JSONObject> {
        var current = initial
        val pages = HelixPageCollector(identityField)
        var cursor: String? = null
        do {
            requireCurrent(epoch)
            val url = "https://api.twitch.tv/helix/$endpoint".toHttpUrl().newBuilder()
                .addQueryParameter("user_id", current.userId).addQueryParameter("first", "100")
                .apply { cursor?.let { addQueryParameter("after", it) } }.build()
            var response = get(url.toString(), current.accessToken, helix = true)
            if (response.status == 401) {
                current = refresh(current, epoch)
                current = checkSession(current, epoch)
                publish(current, epoch)
                response = get(url.toString(), current.accessToken, helix = true)
            }
            requireCurrent(epoch)
            if (response.status == 401) {
                clearLocal(epoch)
                throw AuthException("Сессия Twitch истекла. Войдите снова.")
            }
            if (response.status != 200) throw AuthException(when (response.status) {
                403 -> "Twitch не разрешил прочитать список каналов. Войдите снова и подтвердите разрешение."
                429 -> "Twitch просит подождать. Повторите через минуту."
                else -> "Не удалось получить отслеживаемые каналы. Повторите."
            })
            cursor = pages.append(response.json)
        } while (cursor != null)
        return pages.items
    }

    fun logout() {
        synchronized(gate) {
            generation.incrementAndGet()
            session = null
            loaded = true
            vault.clear()
            mutableState.value = AuthState()
        }
    }

    /** Call when the owning ViewModel/activity is permanently disposed. */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        generation.incrementAndGet()
        // scope.cancel triggers HTTP cancellation handlers; these and TLS pool eviction
        // must run off main. This task does not belong to the scope being cancelled.
        Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable {
            try {
                scope.cancel()
                client.dispatcher.cancelAll()
                client.connectionPool.evictAll()
            } finally {
                client.dispatcher.executorService.shutdown()
            }
        })
    }

    private suspend fun checkSession(current: StoredSession, epoch: Long): StoredSession {
        var candidate = current
        var response = get("https://id.twitch.tv/oauth2/validate", candidate.accessToken)
        requireCurrent(epoch)
        if (response.status == 401) {
            candidate = refresh(candidate, epoch)
            response = get("https://id.twitch.tv/oauth2/validate", candidate.accessToken)
        }
        requireCurrent(epoch)
        if (response.status == 401) {
            clearLocal(epoch)
            throw AuthException("Сессия Twitch истекла. Войдите снова.")
        }
        if (response.status != 200) throw AuthException("Не удалось проверить сессию Twitch. Проверьте интернет.")
        val body = response.json
        val scopes = body.optJSONArray("scopes")
        val hasFollows = scopes != null && (0 until scopes.length()).any { scopes.optString(it) == "user:read:follows" }
        if (body.optString("client_id") != clientId || body.optString("user_id").isBlank() || !hasFollows) {
            clearLocal(epoch)
            throw AuthException("Для списка каналов нужно заново разрешить вход в Twitch.")
        }
        return candidate.copy(login = body.optString("login"), userId = body.getString("user_id")).also { save(it, epoch) }
    }

    private suspend fun refresh(current: StoredSession, epoch: Long): StoredSession = refreshTransaction {
        val response = post("https://id.twitch.tv/oauth2/token", mapOf("client_id" to clientId,
            "grant_type" to "refresh_token", "refresh_token" to current.refreshToken))
        requireCurrent(epoch)
        if (response.status == 400 || response.status == 401) {
            clearLocal(epoch)
            throw AuthException("Разрешение Twitch истекло или отозвано. Войдите снова.")
        }
        if (AuthProtocol.pollDecision(response.status, response.json) != PollDecision.COMPLETE) throw AuthException("Не удалось обновить сессию Twitch. Повторите после восстановления сети.")
        val rotated = current.copy(accessToken = response.json.getString("access_token"), refreshToken = response.json.getString("refresh_token"))
        save(rotated, epoch) // DCF refresh token is single-use: persist rotation before any next request.
        rotated
    }

    private fun load(): StoredSession? = synchronized(gate) {
        if (!loaded) { session = vault.read(); loaded = true }
        session
    }

    private fun save(value: StoredSession, epoch: Long) = synchronized(gate) {
        requireCurrent(epoch)
        vault.write(value)
        session = value
        loaded = true
    }

    private fun publish(value: StoredSession, epoch: Long) = synchronized(gate) {
        requireCurrent(epoch)
        mutableState.value = AuthState(true, value.login)
    }

    private fun clearLocal(epoch: Long) = synchronized(gate) {
        requireCurrent(epoch)
        session = null; loaded = true; vault.clear(); mutableState.value = AuthState()
    }

    private fun requireCurrent(epoch: Long) {
        generation.requireCurrent(epoch)
    }

    private suspend fun post(url: String, fields: Map<String, String>): AuthResponse {
        val body = FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build()
        return execute(Request.Builder().url(url).post(body).build())
    }

    private suspend fun get(url: String, token: String, helix: Boolean = false): AuthResponse = execute(
        Request.Builder().url(url).header("Authorization", "Bearer $token")
            .apply { if (helix) header("Client-Id", clientId) }.build(),
    )

    private suspend fun execute(request: Request): AuthResponse = suspendCancellableCoroutine { continuation ->
        if (closed.get()) {
            continuation.cancel(CancellationException("Authorization owner released"))
            return@suspendCancellableCoroutine
        }
        val call = client.newCall(request)
        continuation.invokeOnCancellation {
            Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable { call.cancel() })
        }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(AuthException("Нет связи с Twitch. Проверьте интернет и повторите."))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        val source = it.body?.source() ?: throw AuthException("Twitch вернул пустой ответ.")
                        source.request(1_048_577)
                        if (source.buffer.size > 1_048_576) throw AuthException("Ответ Twitch слишком большой.")
                        val result = AuthResponse(it.code, JSONObject(source.readUtf8()))
                        if (!continuation.isCancelled) continuation.resume(result)
                    }
                } catch (_: Exception) {
                    if (!continuation.isCancelled) continuation.resumeWithException(AuthException("Не удалось прочитать ответ Twitch. Повторите."))
                }
            }
        })
    }

    private fun safeError(error: Exception) = error as? AuthException ?: AuthException("Не удалось завершить вход. Повторите.")
    private data class AuthResponse(val status: Int, val json: JSONObject) {
        override fun toString(): String = "AuthResponse(status=$status, body=redacted)"
    }
    private companion object { const val SCOPES = "user:read:follows" }
}

internal enum class PollDecision { COMPLETE, PENDING, SLOW_DOWN, DENIED, EXPIRED, FAILED }

internal object AuthProtocol {
    fun pollDecision(status: Int, body: JSONObject): PollDecision {
        if (status == 200 && !body.isNull("access_token") && !body.isNull("refresh_token") &&
            body.optString("access_token").isNotBlank() && body.optString("refresh_token").isNotBlank()) return PollDecision.COMPLETE
        val codes = listOf(body.optString("error"), body.optString("message"))
        return when {
            "authorization_pending" in codes -> PollDecision.PENDING
            "slow_down" in codes || status == 429 -> PollDecision.SLOW_DOWN
            "access_denied" in codes -> PollDecision.DENIED
            "expired_token" in codes || "invalid_device_code" in codes -> PollDecision.EXPIRED
            else -> PollDecision.FAILED
        }
    }
}

/** Bounded pagination rejects cursor loops and never disguises an incomplete list as complete. */
internal class HelixPageCollector(private val identityField: String, private val maxEntries: Int = 2_000) {
    private val rows = linkedMapOf<String, JSONObject>()
    private val cursors = mutableSetOf<String>()
    private var pages = 0
    val items: List<JSONObject> get() = rows.values.toList()

    fun append(body: JSONObject): String? {
        pages++
        val data = body.optJSONArray("data") ?: throw AuthException("Twitch вернул неполный список каналов.")
        for (index in 0 until data.length()) {
            val row = data.optJSONObject(index) ?: throw AuthException("Twitch вернул неполные сведения о канале.")
            val id = FollowedCatalog.required(row, identityField)
            if (!rows.containsKey(id)) rows[id] = row
            if (rows.size > maxEntries) throw AuthException("Список содержит больше $maxEntries каналов. Такой размер пока не поддерживается.")
        }
        val pagination = body.optJSONObject("pagination")
        val cursor = if (pagination == null || pagination.isNull("cursor")) "" else pagination.optString("cursor", "")
        if (cursor.isBlank()) return null
        if (pages >= 40 || !cursors.add(cursor)) throw AuthException("Twitch повторил страницу списка каналов. Повторите загрузку.")
        return cursor
    }
}

internal object FollowedCatalog {
    fun merge(followed: List<JSONObject>, streams: List<JSONObject>): List<Channel> {
        val liveById = streams.map(::live).associateBy { it.id }
        return followed.map { row ->
            val id = required(row, "broadcaster_id")
            liveById[id] ?: Channel(id, required(row, "broadcaster_login"), required(row, "broadcaster_name"),
                "Не в эфире", "", "", viewers = 0, live = false)
        }.sortedWith(compareByDescending<Channel> { it.live }.thenByDescending { it.viewers }
            .thenBy { it.name.lowercase(Locale.ROOT) })
    }

    fun live(item: JSONObject): Channel = Channel(
        required(item, "user_id"), required(item, "user_login"), required(item, "user_name"),
        text(item, "title"), text(item, "game_name"),
        text(item, "thumbnail_url").replace("{width}", "640").replace("{height}", "360"),
        item.optInt("viewer_count", 0).coerceAtLeast(0), true,
    )

    private fun text(item: JSONObject, key: String): String = if (item.isNull(key)) "" else item.optString(key, "")
    fun required(item: JSONObject, key: String): String = text(item, key).takeIf(String::isNotBlank)
        ?: throw AuthException("Twitch вернул неполные сведения о канале.")
}
