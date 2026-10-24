package `in`.localrush.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import `in`.localrush.core.network.auth.TokenProvider
import `in`.localrush.core.network.dto.UserDto
import kotlinx.serialization.json.Json

/**
 * Encrypted at-rest storage for the JWT pair and the cached user. Backed by
 * EncryptedSharedPreferences (AES-256-GCM, key in Android Keystore). Falls back to plain
 * SharedPreferences only if the Keystore is unusable (rare broken OEM builds) so the app still works.
 *
 * All methods are synchronous and cheap: they're called from OkHttp interceptors.
 */
class TokenStore(context: Context, private val json: Json) : TokenProvider {

    private val prefs: SharedPreferences = createPrefs(context)

    @Volatile private var cachedAccess: String? = prefs.getString(KEY_ACCESS, null)
    @Volatile private var cachedRefresh: String? = prefs.getString(KEY_REFRESH, null)

    override fun accessToken(): String? = cachedAccess
    override fun refreshToken(): String? = cachedRefresh

    @Synchronized
    override fun saveTokens(access: String, refresh: String) {
        cachedAccess = access
        cachedRefresh = refresh
        prefs.edit(commit = true) { putString(KEY_ACCESS, access); putString(KEY_REFRESH, refresh) }
    }

    @Synchronized
    override fun clear() {
        cachedAccess = null
        cachedRefresh = null
        prefs.edit(commit = true) { remove(KEY_ACCESS); remove(KEY_REFRESH); remove(KEY_USER) }
    }

    fun hasSession(): Boolean = cachedAccess != null && cachedRefresh != null

    fun saveUser(user: UserDto) = prefs.edit { putString(KEY_USER, json.encodeToString(UserDto.serializer(), user)) }

    fun user(): UserDto? = prefs.getString(KEY_USER, null)?.let { runCatching { json.decodeFromString(UserDto.serializer(), it) }.getOrNull() }

    fun saveFcmToken(token: String?) = prefs.edit { putString(KEY_FCM, token) }
    fun fcmToken(): String? = prefs.getString(KEY_FCM, null)

    /** Stable per-install id sent as `device_id` to `auth/devices/`. */
    fun deviceId(): String = prefs.getString(KEY_DEVICE_ID, null) ?: java.util.UUID.randomUUID().toString().also {
        prefs.edit { putString(KEY_DEVICE_ID, it) }
    }

    private fun createPrefs(context: Context): SharedPreferences = try {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Corrupt keyset or broken Keystore: wipe and retry once, then fall back to plain prefs.
        context.deleteSharedPreferences(FILE_NAME)
        try {
            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(context, FILE_NAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        } catch (e2: Exception) {
            context.getSharedPreferences("${FILE_NAME}_plain", Context.MODE_PRIVATE)
        }
    }

    private companion object {
        const val FILE_NAME = "localrush_session"
        const val KEY_ACCESS = "access"
        const val KEY_REFRESH = "refresh"
        const val KEY_USER = "user"
        const val KEY_FCM = "fcm_token"
        const val KEY_DEVICE_ID = "device_id"
    }
}
