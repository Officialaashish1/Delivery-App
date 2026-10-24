package `in`.localrush.core.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import `in`.localrush.core.auth.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the current FCM token and registers it after login. Safe to call when Firebase isn't
 * configured (no google-services.json): it logs and returns false.
 */
@Singleton
class PushTokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
) {
    val isAvailable: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    suspend fun syncToken(): Boolean {
        if (!isAvailable) {
            Log.i(TAG, "Firebase not configured; skipping FCM registration")
            return false
        }
        val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrElse {
            Log.w(TAG, "FCM token fetch failed", it); return false
        }
        return authRepository.registerDevice(token).isOk
    }

    suspend fun deleteToken() {
        if (!isAvailable) return
        runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
    }

    private companion object { const val TAG = "PushTokenManager" }
}
