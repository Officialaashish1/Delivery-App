package `in`.localrush.customer.data.repo

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Razorpay Checkout result, delivered by MainActivity (the SDK calls back on the Activity) to the Checkout ViewModel. */
sealed interface PaymentResult {
    data class Success(val razorpayOrderId: String, val paymentId: String, val signature: String) : PaymentResult
    data class Failure(val code: Int, val description: String) : PaymentResult
}

@Singleton
class PaymentResultBus @Inject constructor() {
    private val _results = MutableSharedFlow<PaymentResult>(replay = 1, extraBufferCapacity = 4)
    val results: SharedFlow<PaymentResult> = _results.asSharedFlow()
    fun publish(result: PaymentResult) { _results.tryEmit(result) }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consume() { _results.resetReplayCache() }
}
