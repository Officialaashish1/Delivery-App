package `in`.localrush.partner.data

import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.network.dto.PageDto
import `in`.localrush.core.network.dto.ReasonRequest
import `in`.localrush.core.network.util.apiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrdersRepository @Inject constructor(private val api: PartnerApi) {
    suspend fun list(scope: String, page: Int? = null, pageSize: Int? = null): AppResult<PageDto<PartnerOrderDto>> =
        apiCall { api.orders(scope = scope, page = page, pageSize = pageSize) }

    suspend fun detail(id: String): AppResult<PartnerOrderDto> = apiCall { api.order(id) }
    suspend fun accept(id: String, preparationMinutes: Int?): AppResult<PartnerOrderDto> = apiCall { api.accept(id, AcceptOrderDto(preparationMinutes)) }
    suspend fun reject(id: String, reason: String): AppResult<PartnerOrderDto> = apiCall { api.reject(id, ReasonRequest(reason)) }
    suspend fun preparing(id: String): AppResult<PartnerOrderDto> = apiCall { api.preparing(id) }
    suspend fun ready(id: String): AppResult<PartnerOrderDto> = apiCall { api.ready(id) }
    suspend fun cancel(id: String, reason: String): AppResult<PartnerOrderDto> = apiCall { api.cancel(id, ReasonRequest(reason)) }
}
