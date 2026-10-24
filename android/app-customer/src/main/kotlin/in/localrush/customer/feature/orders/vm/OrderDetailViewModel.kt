package `in`.localrush.customer.feature.orders.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.network.dto.OrderDetailDto
import `in`.localrush.customer.data.repo.CartRepository
import `in`.localrush.customer.data.repo.OrdersRepository
import `in`.localrush.customer.data.repo.PaymentResult
import `in`.localrush.customer.data.repo.PaymentResultBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class OrderDetailUiState(
    val loading: Boolean = true,
    val order: OrderDetailDto? = null,
    val error: AppError? = null,
    val cancelling: Boolean = false,
    val reordering: Boolean = false,
    val invoiceLoading: Boolean = false,
    val cancelDialog: Boolean = false,
    /** Razorpay retry in flight (payment sheet open or verifying). */
    val payingAgain: Boolean = false,
) {
    val status: OrderStatus get() = OrderStatus.fromWire(order?.status)
    val canTrack: Boolean get() = status.isActive && status != OrderStatus.PENDING_PAYMENT
    val canRate: Boolean get() = status == OrderStatus.DELIVERED && order?.isReviewed == false
    val awaitingPayment: Boolean get() = status == OrderStatus.PENDING_PAYMENT || status == OrderStatus.PAYMENT_FAILED
}

sealed interface OrderDetailEvent {
    data class Message(val text: String) : OrderDetailEvent
    data class ItemsSkipped(val names: List<String>) : OrderDetailEvent
    data object PaymentUnavailable : OrderDetailEvent
    data object PaymentConfirmed : OrderDetailEvent
    data object GoToCart : OrderDetailEvent
    data class OpenInvoice(val file: File) : OrderDetailEvent
    data class RetryPayment(val payload: `in`.localrush.core.network.dto.RazorpayCheckoutDto) : OrderDetailEvent
}

/** Order detail, cancel, reorder, invoice download, payment retry. Shared with OrderSuccessScreen (both routes carry `orderId`). */
@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: OrdersRepository,
    private val cart: CartRepository,
    private val payments: PaymentResultBus,
) : ViewModel() {
    val orderId: String = savedState.get<String>("orderId") ?: ""
    private val _state = MutableStateFlow(OrderDetailUiState())
    val state: StateFlow<OrderDetailUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<OrderDetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<OrderDetailEvent> = _events.asSharedFlow()

    init {
        load()
        viewModelScope.launch { payments.results.collect(::onPaymentResult) }
    }

    fun load() {
        _state.update { it.copy(loading = it.order == null, error = null) }
        viewModelScope.launch {
            repo.detail(orderId).fold(
                onOk = { o -> _state.update { it.copy(loading = false, order = o) } },
                onErr = { e -> _state.update { it.copy(loading = false, error = e) } },
            )
        }
    }

    fun showCancel(show: Boolean) = _state.update { it.copy(cancelDialog = show) }

    fun cancel(reason: String) {
        _state.update { it.copy(cancelling = true, cancelDialog = false) }
        viewModelScope.launch {
            repo.cancel(orderId, reason).fold(
                onOk = { o -> _state.update { it.copy(cancelling = false, order = o) } },
                onErr = { e -> _state.update { it.copy(cancelling = false) }; _events.tryEmit(OrderDetailEvent.Message(e.message)) },
            )
        }
    }

    fun reorder() {
        _state.update { it.copy(reordering = true) }
        viewModelScope.launch {
            repo.reorder(orderId).fold(
                onOk = { r ->
                    r.cart?.let { cart.publish(it) } ?: cart.refresh()
                    _state.update { it.copy(reordering = false) }
                    if (r.skippedItems.isNotEmpty()) _events.tryEmit(OrderDetailEvent.ItemsSkipped(r.skippedItems))
                    _events.tryEmit(OrderDetailEvent.GoToCart)
                },
                onErr = { e -> _state.update { it.copy(reordering = false) }; _events.tryEmit(OrderDetailEvent.Message(e.message)) },
            )
        }
    }

    /** Invoice HTML → cache file (repository) → opened/shared via FileProvider by the screen. */
    fun invoice() {
        if (_state.value.invoiceLoading) return
        _state.update { it.copy(invoiceLoading = true) }
        viewModelScope.launch {
            repo.downloadInvoice(orderId, _state.value.order?.orderNumber ?: "").fold(
                onOk = { file ->
                    _state.update { it.copy(invoiceLoading = false) }
                    _events.tryEmit(OrderDetailEvent.OpenInvoice(file))
                },
                onErr = { e -> _state.update { it.copy(invoiceLoading = false) }; _events.tryEmit(OrderDetailEvent.Message(e.message)) },
            )
        }
    }

    /** `POST payments/orders/{id}/retry/` → open Razorpay; the result comes back through [onPaymentResult]. */
    fun retryPayment() {
        if (_state.value.payingAgain) return
        _state.update { it.copy(payingAgain = true) }
        viewModelScope.launch {
            repo.retryPayment(orderId).fold(
                onOk = { p ->
                    if (p != null) _events.tryEmit(OrderDetailEvent.RetryPayment(p))
                    else { _state.update { it.copy(payingAgain = false) }; _events.tryEmit(OrderDetailEvent.PaymentUnavailable) }
                },
                onErr = { e -> _state.update { it.copy(payingAgain = false) }; _events.tryEmit(OrderDetailEvent.Message(e.message)) },
            )
        }
    }

    private fun onPaymentResult(result: PaymentResult) {
        if (!_state.value.payingAgain) return
        payments.consume()
        when (result) {
            is PaymentResult.Success -> viewModelScope.launch {
                repo.verifyPayment(`in`.localrush.core.network.dto.PaymentVerifyDto(orderId, result.razorpayOrderId, result.paymentId, result.signature)).fold(
                    onOk = { o -> _state.update { it.copy(payingAgain = false, order = o) }; _events.tryEmit(OrderDetailEvent.PaymentConfirmed) },
                    onErr = { e -> _state.update { it.copy(payingAgain = false) }; _events.tryEmit(OrderDetailEvent.Message(e.message)); load() },
                )
            }
            is PaymentResult.Failure -> { _state.update { it.copy(payingAgain = false) }; _events.tryEmit(OrderDetailEvent.Message(result.description)); load() }
        }
    }
}
