package `in`.localrush.core.network

import `in`.localrush.core.network.auth.AuthInterceptor
import `in`.localrush.core.network.auth.LogoutReason
import `in`.localrush.core.network.auth.TokenAuthenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TokenAuthenticatorTest {
    private lateinit var server: MockWebServer
    private lateinit var tokens: FakeTokens
    private val logoutReasons = java.util.concurrent.CopyOnWriteArrayList<String>()
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        tokens = FakeTokens("old-access", "old-refresh")
        val config = testConfig(server.url("/api/v1/").toString())
        val bare = OkHttpClient()
        client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokens, config))
            .authenticator(TokenAuthenticator(tokens, config, { logoutReasons += it }, bare))
            .build()
    }

    @After fun tearDown() = server.shutdown()

    private fun get(path: String) = client.newCall(Request.Builder().url(server.url(path)).build()).execute()

    @Test
    fun `401 triggers refresh and retries with new token`() {
        val refreshCalls = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.endsWith("auth/token/refresh/") -> {
                    refreshCalls.incrementAndGet()
                    assertTrue(request.body.readUtf8().contains("old-refresh"))
                    MockResponse().setBody("""{"access":"new-access","refresh":"new-refresh"}""")
                }
                request.getHeader("Authorization") == "Bearer old-access" ->
                    MockResponse().setResponseCode(401).setBody("""{"error":{"code":"token_not_valid","message":"expired"}}""")
                request.getHeader("Authorization") == "Bearer new-access" -> MockResponse().setBody("""{"ok":true}""")
                else -> MockResponse().setResponseCode(500)
            }
        }

        val resp = get("/api/v1/auth/me/")
        assertEquals(200, resp.code)
        assertEquals(1, refreshCalls.get())
        assertEquals("new-access", tokens.accessToken())
        assertEquals("new-refresh", tokens.refreshToken())
        assertTrue(logoutReasons.isEmpty())
    }

    @Test
    fun `concurrent 401s refresh only once (single flight)`() {
        val refreshCalls = AtomicInteger()
        val gate = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.endsWith("auth/token/refresh/") -> {
                    refreshCalls.incrementAndGet()
                    gate.await(2, TimeUnit.SECONDS)
                    MockResponse().setBody("""{"access":"new-access","refresh":"new-refresh"}""")
                }
                request.getHeader("Authorization") == "Bearer old-access" ->
                    MockResponse().setResponseCode(401).setBody("""{"error":{"code":"token_not_valid","message":"expired"}}""")
                else -> MockResponse().setBody("""{"ok":true}""")
            }
        }
        val pool = Executors.newFixedThreadPool(4)
        val futures = (1..4).map { i -> pool.submit<Int> { get("/api/v1/orders/$i/").code } }
        Thread.sleep(300)
        gate.countDown()
        val codes = futures.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(listOf(200, 200, 200, 200), codes)
        assertEquals(1, refreshCalls.get())
        assertEquals(1, tokens.saves.size)
    }

    @Test
    fun `token_revoked forces logout without refresh`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"token_revoked","message":"Revoked"}}"""))
        val resp = get("/api/v1/auth/me/")
        assertEquals(401, resp.code)
        assertTrue(tokens.cleared)
        assertEquals(listOf(LogoutReason.TOKEN_REVOKED), logoutReasons)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `account_suspended forces logout`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"account_suspended","message":"Suspended"}}"""))
        get("/api/v1/auth/me/")
        assertEquals(listOf(LogoutReason.ACCOUNT_SUSPENDED), logoutReasons)
    }

    @Test
    fun `refresh rejected forces logout`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.endsWith("auth/token/refresh/") ->
                    MockResponse().setResponseCode(401).setBody("""{"error":{"code":"token_not_valid","message":"blacklisted"}}""")
                else -> MockResponse().setResponseCode(401).setBody("""{"error":{"code":"token_not_valid","message":"expired"}}""")
            }
        }
        val resp = get("/api/v1/auth/me/")
        assertEquals(401, resp.code)
        assertTrue(tokens.cleared)
        assertNull(tokens.accessToken())
        assertEquals(listOf(LogoutReason.REFRESH_FAILED), logoutReasons)
    }

    @Test
    fun `no refresh token forces logout`() {
        tokens.saveTokens("x", "")
        tokens = FakeTokens("x", null)
        setUpWith(tokens)
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"token_not_valid","message":"expired"}}"""))
        get("/api/v1/auth/me/")
        assertTrue(tokens.cleared)
        assertEquals(listOf(LogoutReason.REFRESH_FAILED), logoutReasons)
    }

    @Test
    fun `server error during refresh does not log out`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.endsWith("auth/token/refresh/") -> MockResponse().setResponseCode(503)
                else -> MockResponse().setResponseCode(401).setBody("""{"error":{"code":"token_not_valid","message":"expired"}}""")
            }
        }
        val resp = get("/api/v1/auth/me/")
        assertEquals(401, resp.code)
        assertFalse(tokens.cleared)
        assertTrue(logoutReasons.isEmpty())
    }

    @Test
    fun `no-auth requests skip bearer and never refresh`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"otp_invalid","message":"x"}}"""))
        client.newCall(Request.Builder().url(server.url("/api/v1/auth/otp/verify/")).header(AuthInterceptor.NO_AUTH_HEADER, "true").post("{}".toRequestBody(null)).build()).execute()
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
        assertNull(recorded.getHeader(AuthInterceptor.NO_AUTH_HEADER))
        assertEquals(1, server.requestCount)
    }

    private fun setUpWith(t: FakeTokens) {
        val config = testConfig(server.url("/api/v1/").toString())
        client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(t, config))
            .authenticator(TokenAuthenticator(t, config, { logoutReasons += it }, OkHttpClient()))
            .build()
    }
}
