package `in`.localrush.customer.data.repo

import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.common.result.EmptyResult
import `in`.localrush.core.network.api.NotificationApi
import `in`.localrush.core.network.api.SupportApi
import `in`.localrush.core.network.dto.CreateTicketDto
import `in`.localrush.core.network.dto.FaqDto
import `in`.localrush.core.network.dto.MarkReadDto
import `in`.localrush.core.network.dto.NotificationDto
import `in`.localrush.core.network.dto.PageDto
import `in`.localrush.core.network.dto.SupportContactDto
import `in`.localrush.core.network.dto.SupportTicketDto
import `in`.localrush.core.network.util.Multipart
import `in`.localrush.core.network.util.apiCall
import `in`.localrush.core.network.util.apiCallUnit
import `in`.localrush.customer.data.api.CustomerApi
import `in`.localrush.customer.data.dto.ProfileDto
import `in`.localrush.customer.data.dto.ProfilePatchDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationsRepository @Inject constructor(private val api: NotificationApi) {
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread

    suspend fun refreshUnread(): AppResult<Int> = apiCall { api.unreadCount().unread }.onSuccess { _unread.value = it }
    suspend fun list(page: Int? = null): AppResult<PageDto<NotificationDto>> = apiCall { api.list(page = page, pageSize = 50) }
    suspend fun markAllRead(): EmptyResult = apiCallUnit { api.markRead(MarkReadDto()) }.onSuccess { _unread.value = 0 }
    suspend fun markRead(ids: List<String>): EmptyResult = apiCallUnit { api.markRead(MarkReadDto(ids)) }.onSuccess { refreshUnread() }
    fun reset() { _unread.value = 0 }
}

@Singleton
class SupportRepository @Inject constructor(private val api: SupportApi) {
    suspend fun faqs(): AppResult<List<FaqDto>> = apiCall { api.faqs("customer") }
    suspend fun contact(): AppResult<SupportContactDto> = apiCall { api.contact() }
    suspend fun tickets(): AppResult<PageDto<SupportTicketDto>> = apiCall { api.tickets() }
    suspend fun ticket(id: String): AppResult<SupportTicketDto> = apiCall { api.ticket(id) }
    suspend fun create(orderId: String?, category: String, subject: String, description: String): AppResult<SupportTicketDto> =
        apiCall { api.createTicket(CreateTicketDto(orderId, category, subject, description)) }
    /** The reply endpoint returns the new message, not the ticket, so re-fetch the thread afterwards. */
    suspend fun reply(id: String, text: String): AppResult<SupportTicketDto> =
        apiCall { api.reply(id, Multipart.text(text)) }.let { r -> if (r.isOk) ticket(id) else r }
    suspend fun close(id: String): AppResult<SupportTicketDto> = apiCall { api.close(id) }
}

@Singleton
class ProfileRepository @Inject constructor(private val api: CustomerApi) {
    suspend fun profile(): AppResult<ProfileDto> = apiCall { api.profile() }
    suspend fun update(fullName: String?, email: String?, vegOnly: Boolean?): AppResult<ProfileDto> =
        apiCall { api.patchProfile(ProfilePatchDto(fullName, email, vegOnly)) }
}
