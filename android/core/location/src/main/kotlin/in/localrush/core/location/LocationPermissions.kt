package `in`.localrush.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class LocationAccuracy { None, Approximate, Precise }

data class LocationPermissionState(
    val foreground: LocationAccuracy,
    /** Always true below API 29 (background permission didn't exist). */
    val background: Boolean,
    val notifications: Boolean,
) {
    val hasForeground: Boolean get() = foreground != LocationAccuracy.None
    val isPrecise: Boolean get() = foreground == LocationAccuracy.Precise
}

object LocationPermissions {
    val FOREGROUND = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Only meaningful on API 29+; request it in a *separate* step after foreground is granted (Play policy). */
    val BACKGROUND: String? = if (Build.VERSION.SDK_INT >= 29) Manifest.permission.ACCESS_BACKGROUND_LOCATION else null

    val NOTIFICATIONS: String? = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null

    fun state(context: Context): LocationPermissionState {
        val fine = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        val fg = when {
            fine -> LocationAccuracy.Precise
            coarse -> LocationAccuracy.Approximate
            else -> LocationAccuracy.None
        }
        val bg = BACKGROUND?.let { granted(context, it) } ?: (fg != LocationAccuracy.None)
        val notif = NOTIFICATIONS?.let { granted(context, it) } ?: true
        return LocationPermissionState(fg, bg, notif)
    }

    fun hasForeground(context: Context) = state(context).hasForeground
    fun hasBackground(context: Context) = state(context).background

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
