package `in`.localrush.core.network.ws

import `in`.localrush.core.common.Constants
import `in`.localrush.core.network.NetworkConfig
import `in`.localrush.core.network.auth.TokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Resilient wrapper over OkHttp WebSocket for the `/ws/...` endpoints.
 *
 * - Auth: `?token=<access>` appended at (re)connect time (fresh token each attempt).
 * - Auto-reconnect with exponential backoff + jitter; stops on 4401/4403/4404 and after [close].
 * - Replies `{"type":"pong"}` to server `{"type":"ping"}` (ping/pong not emitted to consumers).
 * - [messages]: hot flow of parsed JSON objects (replay 0; collect before [connect] or use [state]).
 * - [send]: queues while disconnected (bounded), flushes on open.
 */
class WebSocketClient internal constructor(
    private val client: OkHttpClient,
    private val url: String,
    private val tokenProvider: () -> String?,
    private val json: Json,
    private val backoff: BackoffPolicy = BackoffPolicy(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val maxQueued: Int = 32,
) {
    sealed interface State {
        data object Idle : State
        data class Connecting(val attempt: Int) : State
        data object Connected : State
        data class WaitingToReconnect(val attempt: Int, val delayMs: Long) : State
        /** Terminal: server refused (4401/4403/4404) or [close] called. */
        data class Closed(val code: Int, val reason: String) : State
    }

    private val _messages = MutableSharedFlow<JsonObject>(extraBufferCapacity = 256)
    val messages: Flow<JsonObject> = _messages.asSharedFlow()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val socket = AtomicReference<WebSocket?>(null)
    private val attempt = AtomicInteger(0)
    private val closedByUser = AtomicBoolean(false)
    private val outbox = ArrayDeque<String>()
    private var reconnectJob: Job? = null

    val isConnected: Boolean get() = _state.value is State.Connected

    fun connect() {
        if (closedByUser.get()) return
        if (socket.get() != null) return
        open()
    }

    private fun open() {
        val token = tokenProvider()
        val full = if (token != null) {
            url + (if ('?' in url) "&" else "?") + "token=$token"
        } else url
        _state.value = State.Connecting(attempt.get())
        val request = Request.Builder().url(full).build()
        socket.set(client.newWebSocket(request, listener))
    }

    /** Sends a JSON object (or queues it until connected). Returns false if the client is closed. */
    fun send(message: JsonObject): Boolean = sendRaw(message.toString())

    fun send(build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): Boolean = send(buildJsonObject(build))

    fun sendRaw(text: String): Boolean {
        if (closedByUser.get()) return false
        val ws = socket.get()
        if (ws != null && isConnected) {
            return ws.send(text)
        }
        synchronized(outbox) {
            if (outbox.size >= maxQueued) outbox.removeFirst()
            outbox.addLast(text)
        }
        return true
    }

    /** Permanently closes; the instance cannot be reused. */
    fun close(code: Int = 1000, reason: String = "client closed") {
        if (!closedByUser.compareAndSet(false, true)) return
        reconnectJob?.cancel()
        socket.getAndSet(null)?.close(code, reason)
        _state.value = State.Closed(code, reason)
        scope.cancel()
    }

    /** Drops the current socket and reconnects immediately (e.g. after network change). */
    fun reconnectNow() {
        if (closedByUser.get()) return
        reconnectJob?.cancel()
        attempt.set(0)
        socket.getAndSet(null)?.cancel()
        open()
    }

    private fun scheduleReconnect() {
        if (closedByUser.get()) return
        val n = attempt.getAndIncrement()
        if (!backoff.shouldRetry(n)) {
            _state.value = State.Closed(-1, "max attempts reached")
            return
        }
        val delayMs = backoff.delayFor(n)
        _state.value = State.WaitingToReconnect(n + 1, delayMs)
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!closedByUser.get()) open()
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempt.set(0)
            _state.value = State.Connected
            val pending: List<String>
            synchronized(outbox) { pending = outbox.toList(); outbox.clear() }
            pending.forEach { webSocket.send(it) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            val type = (obj["type"] as? JsonPrimitive)?.contentOrNull
            if (type == Constants.PING_TYPE) {
                webSocket.send(PONG)
                return
            }
            if (type == Constants.PONG_TYPE) return
            _messages.tryEmit(obj)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (socket.get() !== webSocket && socket.get() != null) return
            socket.set(null)
            if (closedByUser.get()) return
            if (code in FATAL_CODES) {
                _state.value = State.Closed(code, reason)
                return
            }
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (socket.get() !== webSocket && socket.get() != null) return
            socket.set(null)
            if (closedByUser.get()) return
            // HTTP-level rejection during handshake (e.g. 401/403) is fatal only for auth codes.
            if (response?.code == 401 || response?.code == 403) {
                _state.value = State.Closed(response.code, response.message)
                return
            }
            scheduleReconnect()
        }
    }

    /** Hilt-provided factory; apps build endpoint URLs relative to [NetworkConfig.wsBaseUrl]. */
    class Factory(
        private val client: OkHttpClient,
        private val config: NetworkConfig,
        private val tokens: TokenProvider,
        private val json: Json,
    ) {
        /** [path] relative to ws base, e.g. `orders/{id}/tracking/`, `rider/`, `partner/`. */
        fun create(path: String, backoff: BackoffPolicy = BackoffPolicy()): WebSocketClient =
            WebSocketClient(client, config.wsBaseUrl + path.removePrefix("/"), { tokens.accessToken() }, json, backoff)

        fun orderTracking(orderId: String) = create("orders/$orderId/tracking/")
        fun rider() = create("rider/")
        fun partner() = create("partner/")
    }

    companion object {
        val FATAL_CODES = setOf(Constants.WS_CLOSE_UNAUTHENTICATED, Constants.WS_CLOSE_FORBIDDEN, Constants.WS_CLOSE_NOT_FOUND)
        private const val PONG = """{"type":"pong"}"""

        /** Test/manual constructor without Hilt. */
        fun create(client: OkHttpClient, url: String, token: () -> String?, json: Json = Json { ignoreUnknownKeys = true }, backoff: BackoffPolicy = BackoffPolicy()) =
            WebSocketClient(client, url, token, json, backoff)
    }
}

/** Convenience: `type` of a WS message. */
val JsonObject.type: String? get() = (this["type"] as? JsonPrimitive)?.contentOrNull
