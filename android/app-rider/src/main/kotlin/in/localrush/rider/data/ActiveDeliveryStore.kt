package `in`.localrush.rider.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.riderPrefs: DataStore<Preferences> by preferencesDataStore("rider_state")

data class ActiveDelivery(val orderId: String, val status: String, val arrivedAtRestaurant: Boolean)

/**
 * Persists the delivery the rider is currently working on (survives process death) so the location
 * service can tag fixes with `order_id`, the dashboard can restore the "current delivery" card even
 * while the order is still `preparing` (not in the backend's RIDER_ACTIVE_STATUSES), and the Arrived flag sticks.
 */
@Singleton
class ActiveDeliveryStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _active = MutableStateFlow<ActiveDelivery?>(null)
    val active: StateFlow<ActiveDelivery?> = _active

    private val _wantsOnline = MutableStateFlow(false)
    /** The rider's own intent: used to restart tracking after reboot/process death and to decide the idle interval. */
    val wantsOnline: StateFlow<Boolean> = _wantsOnline

    init {
        scope.launch {
            val p = context.riderPrefs.data.first()
            val id = p[KEY_ORDER]
            if (id != null) _active.value = ActiveDelivery(id, p[KEY_STATUS] ?: "", p[KEY_ARRIVED] ?: false)
            _wantsOnline.value = p[KEY_ONLINE] ?: false
        }
    }

    fun set(orderId: String, status: String, arrived: Boolean? = null) {
        val arrivedFlag = arrived ?: (_active.value?.takeIf { it.orderId == orderId }?.arrivedAtRestaurant ?: false)
        val next = ActiveDelivery(orderId, status, arrivedFlag)
        if (_active.value == next) return
        _active.value = next
        scope.launch { context.riderPrefs.edit { it[KEY_ORDER] = orderId; it[KEY_STATUS] = status; it[KEY_ARRIVED] = arrivedFlag } }
    }

    fun markArrived(orderId: String) { _active.value?.takeIf { it.orderId == orderId }?.let { set(orderId, it.status, true) } }

    fun clear() {
        _active.value = null
        scope.launch { context.riderPrefs.edit { it.remove(KEY_ORDER); it.remove(KEY_STATUS); it.remove(KEY_ARRIVED) } }
    }

    fun setWantsOnline(value: Boolean) {
        if (_wantsOnline.value == value) return
        _wantsOnline.value = value
        scope.launch { context.riderPrefs.edit { it[KEY_ONLINE] = value } }
    }

    private companion object {
        val KEY_ORDER = stringPreferencesKey("active_order_id")
        val KEY_STATUS = stringPreferencesKey("active_order_status")
        val KEY_ARRIVED = booleanPreferencesKey("active_order_arrived")
        val KEY_ONLINE = booleanPreferencesKey("wants_online")
    }
}
