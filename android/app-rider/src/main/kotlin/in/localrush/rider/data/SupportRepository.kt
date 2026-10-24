package `in`.localrush.rider.data

import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.network.api.SupportApi
import `in`.localrush.core.network.dto.CreateTicketDto
import `in`.localrush.core.network.dto.FaqDto
import `in`.localrush.core.network.dto.PageDto
import `in`.localrush.core.network.dto.SupportContactDto
import `in`.localrush.core.network.dto.SupportTicketDto
import `in`.localrush.core.network.util.Multipart
import `in`.localrush.core.network.util.apiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupportRepository @Inject constructor(private val api: SupportApi) {
    suspend fun faqs(): AppResult<List<FaqDto>> = apiCall { api.faqs(audience = "rider") }
    suspend fun contact(): AppResult<SupportContactDto> = apiCall { api.contact() }
    suspend fun tickets(page: Int? = null): AppResult<PageDto<SupportTicketDto>> = apiCall { api.tickets(page) }
    suspend fun ticket(id: String): AppResult<SupportTicketDto> = apiCall { api.ticket(id) }
    suspend fun create(orderId: String?, category: String, subject: String, description: String): AppResult<SupportTicketDto> =
        apiCall { api.createTicket(CreateTicketDto(order = orderId, category = category, subject = subject, description = description)) }
    suspend fun reply(id: String, text: String): AppResult<SupportTicketDto> = apiCall { api.reply(id, Multipart.text(text), null) }
    suspend fun close(id: String): AppResult<SupportTicketDto> = apiCall { api.close(id) }
}
