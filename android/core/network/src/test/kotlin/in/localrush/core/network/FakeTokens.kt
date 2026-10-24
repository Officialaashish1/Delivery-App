package `in`.localrush.core.network

import `in`.localrush.core.common.AppRole
import `in`.localrush.core.network.auth.TokenProvider
import java.util.concurrent.atomic.AtomicReference

class FakeTokens(access: String?, refresh: String?) : TokenProvider {
    private val pair = AtomicReference(access to refresh)
    val saves = java.util.concurrent.CopyOnWriteArrayList<Pair<String, String>>()
    var cleared = false
    override fun accessToken(): String? = pair.get().first
    override fun refreshToken(): String? = pair.get().second
    override fun saveTokens(access: String, refresh: String) { pair.set(access to refresh); saves += access to refresh }
    override fun clear() { pair.set(null to null); cleared = true }
}

fun testConfig(base: String, ws: String = base.replace("http", "ws")) =
    NetworkConfig(apiBaseUrl = base, wsBaseUrl = ws, isDebug = false, appVersion = "test", role = AppRole.CUSTOMER)
