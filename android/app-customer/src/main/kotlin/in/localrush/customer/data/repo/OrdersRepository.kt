package `in`.localrush.customer.data.repo

import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.network.api.OrderApi
import `in`.localrush.core.network.api.ReviewApi
import `in`.localrush.core.network.dto.CreateReviewDto
import `in`.localrush.core.network.dto.OrderDetailDto
import `in`.localrush.core.network.dto.OrderListDto
import `in`.localrush.core.network.dto.PageDto
import `in`.localrush.core.network.dto.PaymentVerifyDto
import `in`.localrush.core.network.dto.PlaceOrderDto
import `in`.localrush.core.network.dto.PlaceOrderResponseDto
import `in`.localrush.core.network.dto.RazorpayCheckoutDto
import `in`.localrush.core.network.dto.ReasonRequest
import `in`.localrush.core.network.dto.ReviewDto
import `in`.localrush.core.network.dto.RiderContactDto
import `in`.localrush.core.network.util.apiCall
import `in`.localrush.customer.data.api.CustomerApi
import `in`.localrush.customer.data.dto.ReorderResponseDto
import javax.inject.Inject
import javax.inject.Singleton

/** Customer orders & payments. Interface so the checkout state machine can be unit-tested with a fake. */
interface OrdersRepository {
    suspend fun list(scope: String, page: Int? = null): AppResult<PageDto<OrderListDto>>
    suspend fun detail(id: String): AppResult<OrderDetailDto>
    suspend fun place(body: PlaceOrderDto): AppResult<PlaceOrderResponseDto>
    suspend fun verifyPayment(body: PaymentVerifyDto): AppResult<OrderDetailDto>
    suspend fun retryPayment(orderId: String): AppResult<RazorpayCheckoutDto?>
    suspend fun cancel(id: String, reason: String): AppResult<OrderDetailDto>
    suspend fun reorder(id: String): AppResult<ReorderResponseDto>
    /** `GET orders/{id}/invoice/` (HTML, authenticated) saved under cacheDir/invoices for sharing via FileProvider. */
    suspend fun downloadInvoice(id: String, orderNumber: String): AppResult<java.io.File>
    suspend fun riderContact(id: String): AppResult<RiderContactDto>
    suspend fun review(body: CreateReviewDto): AppResult<ReviewDto>
}

@Singleton
class OrdersRepositoryImpl @Inject constructor(
    private val orderApi: OrderApi,
    private val customerApi: CustomerApi,
    private val reviewApi: ReviewApi,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : OrdersRepository {
    override suspend fun list(scope: String, page: Int?) = apiCall { orderApi.list(scope = scope, page = page) }
    override suspend fun detail(id: String) = apiCall { orderApi.detail(id) }
    override suspend fun place(body: PlaceOrderDto) = apiCall { orderApi.place(body) }
    override suspend fun verifyPayment(body: PaymentVerifyDto) = apiCall { orderApi.verifyPayment(body) }
    // The backend answers `{payment}` only (no `order`), hence the app-local DTO instead of OrderApi.retryPayment.
    override suspend fun retryPayment(orderId: String) = apiCall { customerApi.retryPayment(orderId).payment }
    override suspend fun cancel(id: String, reason: String) = apiCall { orderApi.cancel(id, ReasonRequest(reason)) }
    override suspend fun reorder(id: String) = apiCall { customerApi.reorder(id) }
    override suspend fun downloadInvoice(id: String, orderNumber: String): AppResult<java.io.File> = apiCall {
        val html = customerApi.invoice(id).string()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir = java.io.File(context.cacheDir, "invoices").apply { mkdirs() }
            java.io.File(dir, "invoice-${orderNumber.ifBlank { id }}.html").apply { writeText(html) }
        }
    }
    override suspend fun riderContact(id: String) = apiCall { orderApi.riderContact(id) }
    override suspend fun review(body: CreateReviewDto) = apiCall { reviewApi.create(body) }
}
