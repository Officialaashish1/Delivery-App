package `in`.localrush.rider.ui.common

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import `in`.localrush.core.location.LocationPermissions
import `in`.localrush.rider.R

/**
 * The permission choreography for "Go online":
 * 1. POST_NOTIFICATIONS (API 33+) so the foreground-service notification is visible.
 * 2. Precise foreground location.
 * 3. Play-compliant **prominent disclosure** dialog, then ACCESS_BACKGROUND_LOCATION (API 29+) as a separate request.
 * [onReady] fires once foreground location is granted (background is recommended, not mandatory).
 */
class OnlinePermissionFlow internal constructor(val start: () -> Unit)

@Composable
fun rememberOnlinePermissionFlow(onReady: (backgroundGranted: Boolean) -> Unit, onDenied: () -> Unit): OnlinePermissionFlow {
    val context = LocalContext.current
    val ready = rememberUpdatedState(onReady)
    val denied = rememberUpdatedState(onDenied)
    var showDisclosure by remember { mutableStateOf(false) }

    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        ready.value(LocationPermissions.state(context).background)
    }
    fun afterForeground() {
        val st = LocationPermissions.state(context)
        if (!st.hasForeground) { denied.value(); return }
        if (st.background || LocationPermissions.BACKGROUND == null) ready.value(true) else showDisclosure = true
    }
    val foregroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { afterForeground() }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (LocationPermissions.state(context).hasForeground) afterForeground() else foregroundLauncher.launch(LocationPermissions.FOREGROUND)
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false; ready.value(false) },
            title = { Text(stringResource(R.string.bg_location_title)) },
            text = { Text(stringResource(R.string.bg_location_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    val perm = LocationPermissions.BACKGROUND
                    if (perm != null) backgroundLauncher.launch(perm) else ready.value(true)
                }) { Text(stringResource(R.string.bg_location_allow)) }
            },
            dismissButton = { TextButton(onClick = { showDisclosure = false; ready.value(false) }) { Text(stringResource(R.string.bg_location_not_now)) } },
        )
    }

    return remember {
        OnlinePermissionFlow {
            val st = LocationPermissions.state(context)
            when {
                !st.notifications && Build.VERSION.SDK_INT >= 33 -> notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                !st.hasForeground -> foregroundLauncher.launch(LocationPermissions.FOREGROUND)
                else -> afterForeground()
            }
        }
    }
}
