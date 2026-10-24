package `in`.localrush.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import `in`.localrush.core.common.Constants
import `in`.localrush.core.common.geo.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Thin coroutine/Flow wrapper over the fused location provider. Callers must check permissions first. */
@Singleton
class LocationClient @Inject constructor(@ApplicationContext private val context: Context) {

    private val fused: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    /** Last known fix (fast, may be null/stale). */
    @SuppressLint("MissingPermission")
    suspend fun lastKnown(): Location? {
        if (!LocationPermissions.hasForeground(context)) return null
        return runCatching { fused.lastLocation.await() }.getOrNull()
    }

    /** One-shot fresh fix with a timeout; falls back to last known. */
    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = 8_000, highAccuracy: Boolean = true): Location? {
        if (!LocationPermissions.hasForeground(context)) return null
        val request = CurrentLocationRequest.Builder()
            .setPriority(if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setDurationMillis(timeoutMs)
            .setMaxUpdateAgeMillis(30_000)
            .build()
        val fresh = withTimeoutOrNull(timeoutMs + 500) { runCatching { fused.getCurrentLocation(request, null).await() }.getOrNull() }
        return fresh ?: lastKnown()
    }

    /**
     * Continuous updates. Cancelling the collecting coroutine removes the callback.
     * @param intervalMs desired interval; @param minDistanceM minimum displacement between updates.
     */
    @SuppressLint("MissingPermission")
    fun locationUpdates(
        intervalMs: Long = Constants.LOCATION_INTERVAL_MOVING_MS,
        minDistanceM: Float = 5f,
        highAccuracy: Boolean = true,
        allowApproximate: Boolean = true,
    ): Flow<Location> = callbackFlow {
        if (!LocationPermissions.hasForeground(context)) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }
        val request = LocationRequest.Builder(if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMinUpdateDistanceMeters(minDistanceM)
            .setMaxUpdateDelayMillis(intervalMs * 2)
            .setGranularity(if (allowApproximate) Granularity.GRANULARITY_PERMISSION_LEVEL else Granularity.GRANULARITY_FINE)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { close(it) }
        awaitClose { fused.removeLocationUpdates(callback) }
    }
}

fun Location.toLatLng() = LatLng(latitude, longitude)
