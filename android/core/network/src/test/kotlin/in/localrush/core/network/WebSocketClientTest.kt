package `in`.localrush.core.network

import `in`.localrush.core.network.ws.BackoffPolicy
import `in`.localrush.core.network.ws.WebSocketClient
import `in`.localrush.core.network.ws.type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class WebSocketClientTest {
    private lateinit var server: MockWebServer
    private val okhttp = OkHttpClient.Builder().pingInterval(0, TimeUnit.SECONDS).build()

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { runCatching { server.shutdown() } }

    private suspend fun WebSocketClient.closeAndAwait() {
        close()
        withTimeout(3000) { state.first { it is WebSocketClient.State.Closed } }
        kotlinx.coroutines.delay(100)
    }

    private fun wsUrl(path: String) = server.url(path).toString().replaceFirst("http", "ws")

    @Test
    fun `backoff grows exponentially with jitter and caps`() {
        val policy = BackoffPolicy(baseMs = 1000, capMs = 8000, random = Random(42))
        val d0 = policy.delayFor(0); val d1 = policy.delayFor(1); val d2 = policy.delayFor(2); val d9 = policy.delayFor(9)
        assertTrue("d0=$d0", d0 in 500..1000)
        assertTrue("d1=$d1", d1 in 1000..2000)
        assertTrue("d2=$d2", d2 in 2000..4000)
        assertTrue("d9=$d9", d9 in 4000..8000)
        assertTrue(BackoffPolicy(maxAttempts = 3).shouldRetry(2))
        assertTrue(!BackoffPolicy(maxAttempts = 3).shouldRetry(3))
    }

    @Test
    fun `connects with token query, replies pong to ping and emits messages`() = runBlocking {
        val received = CountDownLatch(1)
        val serverGot = CopyOnWriteArrayList<String>()
        val paths = CopyOnWriteArrayList<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                paths += request.path!!
                return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        webSocket.send("""{"type":"ping"}""")
                        webSocket.send("""{"type":"snapshot","order":{"id":"o1"}}""")
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) { serverGot += text; received.countDown() }
                })
            }
        }
        val client = WebSocketClient.create(okhttp, wsUrl("/ws/orders/o1/tracking/"), { "tok123" })
        client.connect()
        val msg = withTimeout(5000) { client.messages.first() }
        assertEquals("snapshot", msg.type)
        assertTrue(received.await(3, TimeUnit.SECONDS))
        assertEquals("""{"type":"pong"}""", serverGot.first())
        assertTrue(paths.first(), paths.first().endsWith("?token=tok123"))

        client.send { put("type", "chat"); put("text", "hi") }
        withTimeout(3000) { while (serverGot.size < 2) kotlinx.coroutines.delay(20) }
        assertEquals("chat", kotlinx.serialization.json.Json.parseToJsonElement(serverGot[1]).let { (it as kotlinx.serialization.json.JsonObject)["type"]!!.jsonPrimitive.content })
        client.closeAndAwait()
        assertTrue(client.state.value is WebSocketClient.State.Closed)
    }

    @Test
    fun `reconnects after server closes and flushes queued messages`() = runBlocking {
        val connections = AtomicInteger()
        val secondGot = CopyOnWriteArrayList<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val n = connections.incrementAndGet()
                return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        if (n == 1) webSocket.close(1011, "restart") else webSocket.send("""{"type":"hello","n":$n}""")
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) { if (n >= 2) secondGot += text }
                })
            }
        }
        val client = WebSocketClient.create(okhttp, wsUrl("/ws/rider/"), { "t" }, backoff = BackoffPolicy(baseMs = 50, capMs = 200))
        val states = CopyOnWriteArrayList<WebSocketClient.State>()
        val job: Job = CoroutineScope(Dispatchers.Default).launch { client.state.collect { states += it } }
        client.connect()
        // Queue a message while the first socket is down; it must be flushed on the second connection.
        withTimeout(5000) { client.state.first { it is WebSocketClient.State.WaitingToReconnect } }
        client.send { put("type", "location"); put("latitude", 1.0) }
        val hello = withTimeout(8000) { client.messages.first() }
        assertEquals("hello", hello.type)
        assertTrue("connections=${connections.get()}", connections.get() >= 2)
        withTimeout(3000) { while (secondGot.isEmpty()) kotlinx.coroutines.delay(20) }
        assertTrue(secondGot.first().contains("\"location\""))
        assertTrue(states.any { it is WebSocketClient.State.WaitingToReconnect })
        job.cancel()
        client.closeAndAwait()
    }

    @Test
    fun `fatal close code 4401 stops reconnecting`() = runBlocking {
        val connections = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                connections.incrementAndGet()
                return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) { webSocket.close(4401, "unauthenticated") }
                })
            }
        }
        val client = WebSocketClient.create(okhttp, wsUrl("/ws/partner/"), { null }, backoff = BackoffPolicy(baseMs = 20, capMs = 50))
        client.connect()
        val closed = withTimeout(5000) { client.state.first { it is WebSocketClient.State.Closed } as WebSocketClient.State.Closed }
        assertEquals(4401, closed.code)
        kotlinx.coroutines.delay(300)
        assertEquals(1, connections.get())
        client.closeAndAwait()
    }
}
