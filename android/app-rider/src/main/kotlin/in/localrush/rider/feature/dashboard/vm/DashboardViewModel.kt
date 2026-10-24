package `in`.localrush.rider.feature.dashboard.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.auth.SessionManager
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.location.LocationClient
import `in`.localrush.rider.data.ActiveDeliveryStore
import `in`.localrush.rider.data.DeliveryRepository
import `in`.localrush.rider.data.LocationBus
import `in`.localrush.rider.data.NetworkMonitor
import `in`.localrush.rider.data.RiderDashboardDto
import `in`.localrush.rider.data.RiderOrderDto
import `in`.localrush.rider.data.RiderRealtime
import `in`.localrush.rider.data.RiderRepository
import `in`.localrush.rider.data.RiderStatus
import `in`.localrush.rider.data.TrackingController
import `in`.localrush.rider.data.isRiderActive
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: AppError? = null,
    val data: RiderDashboardDto? = null,
    /** Current delivery (from the dashboard, or restored from the local store while the order is still preparing). */
    val currentOrder: RiderOrderDto? = null,
    val isOnline: Boolean = false,
    val toggling: Boolean = false,
    val riderStatus: RiderStatus = RiderStatus.UNKNOWN,
    val riderName: String = "",
    val networkOnline: Boolean = true,
    val queuedFixes: Int = 0,
    val trackingRunning: Boolean = false,
    val backgroundPermission: Boolean = true,
) {
    val approved: Boolean get() = riderStatus == RiderStatus.APPROVED
}

sealed interface DashboardEvent {
    data class Message(val resId: Int) : DashboardEvent
    data class Error(val error: AppError) : DashboardEvent
    /** Tell the UI to run the permission flow, then call [DashboardViewModel.goOnline]. */
    data object RequestPermissions : DashboardEvent
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: RiderRepository,
    private val delivery: DeliveryRepository,
    private val store: ActiveDeliveryStore,
    private val tracking: TrackingController,
    private val locationClient: LocationClient,
    private val realtime: RiderRealtime,
    network: NetworkMonitor,
    bus: LocationBus,
    session: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState(riderName = session.currentUser?.fullName ?: ""))
    val state: StateFlow<DashboardUiState> = _state
    private val _events = MutableSharedFlow<DashboardEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<DashboardEvent> = _events
    private var polling: Job? = null

    init {
        viewModelScope.launch { network.isOnline.collect { on -> _state.update { it.copy(networkOnline = on) }; if (on) refresh(silent = true) } }
        viewModelScope.launch { bus.queued.collect { q -> _state.update { it.copy(queuedFixes = q) } } }
        viewModelScope.launch { bus.serviceRunning.collect { r -> _state.update { it.copy(trackingRunning = r) } } }
        viewModelScope.launch { realtime.orderEvents.collect { refresh(silent = true) } }
        viewModelScope.launch { store.active.collect { refresh(silent = true) } }
        tracking.ensureRunningIfWanted()
        refresh()
    }

    fun onResume() { refreshPermissionState(); startPolling(); refresh(silent = true) }
    fun onPause() { polling?.cancel() }

    private fun startPolling() {
        polling?.cancel()
        polling = viewModelScope.launch { while (isActive) { delay(30_000); refresh(silent = true) } }
    }

    fun refreshPermissionState() = _state.update { it.copy(backgroundPermission = tracking.hasBackgroundPermission()) }

    fun refresh(silent: Boolean = false) {
        if (!silent) _state.update { it.copy(refreshing = it.data != null, loading = it.data == null, error = null) }
        viewModelScope.launch {
            repo.dashboard().fold(
                onOk = { d ->
                    val current = d.currentOrder ?: restoreActive()
                    syncStore(current)
                    _state.update {
                        it.copy(loading = false, refreshing = false, error = null, data = d, currentOrder = current, isOnline = d.isOnline, riderStatus = RiderStatus.fromWire(d.status))
                    }
                    when {
                        d.isOnline && tracking.hasForegroundPermission() -> { store.setWantsOnline(true); tracking.start() }
                        !d.isOnline && current == null -> { store.setWantsOnline(false); tracking.stop() }
                    }
                },
                onErr = { e ->
                    val hadData = _state.value.data != null
                    _state.update { it.copy(loading = false, refreshing = false, error = if (hadData) null else e) }
                    if (hadData && !silent) _events.tryEmit(DashboardEvent.Error(e))
                },
            )
        }
    }

    /** The backend's `current_order` only covers RIDER_ACTIVE_STATUSES; an accepted order still `preparing` is restored locally. */
    private suspend fun restoreActive(): RiderOrderDto? {
        val active = store.active.value ?: return null
        return delivery.order(active.orderId).fold(
            onOk = { o -> if (OrderStatus.fromWire(o.status).isRiderActive()) o else { store.clear(); null } },
            onErr = { null },
        )
    }

    private fun syncStore(order: RiderOrderDto?) {
        if (order == null) {
            // Only forget the stored order when the server says there is none and we could verify it.
            return
        }
        store.set(order.id, order.status)
    }

    /** Step 1 of going online: ask the UI to sort out permissions. */
    fun requestOnline() {
        val s = _state.value
        if (s.toggling) return
        if (!s.approved) { _events.tryEmit(DashboardEvent.Message(`in`.localrush.rider.R.string.dash_not_approved)); return }
        _events.tryEmit(DashboardEvent.RequestPermissions)
    }

    /** Step 2: permissions granted (foreground at least) → POST is_online=true with the current fix, start tracking. */
    fun goOnline() {
        if (_state.value.toggling) return
        _state.update { it.copy(toggling = true) }
        viewModelScope.launch {
            val fix = locationClient.current(timeoutMs = 6_000)
            repo.setOnline(true, fix?.latitude, fix?.longitude).fold(
                onOk = { p ->
                    store.setWantsOnline(true)
                    tracking.start()
                    _state.update { it.copy(toggling = false, isOnline = p.isOnline, riderStatus = RiderStatus.fromWire(p.status)) }
                    refreshPermissionState()
                    refresh(silent = true)
                },
                onErr = { e -> _state.update { it.copy(toggling = false) }; _events.tryEmit(DashboardEvent.Error(e)) },
            )
        }
    }

    fun permissionDenied() { _events.tryEmit(DashboardEvent.Message(`in`.localrush.rider.R.string.dash_location_required)) }

    fun goOffline() {
        if (_state.value.toggling) return
        _state.update { it.copy(toggling = true) }
        viewModelScope.launch {
            repo.setOnline(false, null, null).fold(
                onOk = { p ->
                    store.setWantsOnline(false)
                    tracking.stop()
                    _state.update { it.copy(toggling = false, isOnline = p.isOnline) }
                    refresh(silent = true)
                },
                onErr = { e -> _state.update { it.copy(toggling = false) }; _events.tryEmit(DashboardEvent.Error(e)) },
            )
        }
    }
}
