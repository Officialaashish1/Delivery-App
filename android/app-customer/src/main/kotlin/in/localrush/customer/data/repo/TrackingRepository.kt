package `in`.localrush.customer.data.repo

import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.network.api.TrackingApi
import `in`.localrush.core.network.dto.ChatMessageDto
import `in`.localrush.core.network.dto.ChatSendDto
import `in`.localrush.core.network.dto.TrackingSnapshotDto
import `in`.localrush.core.network.util.apiCall
import `in`.localrush.core.network.ws.WebSocketClient
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot + chat over REST, and a socket factory for `/ws/orders/{id}/tracking/`. */
interface TrackingRepository {
    suspend fun snapshot(orderId: String): AppResult<TrackingSnapshotDto>
    suspend fun sendChat(orderId: String, text: String): AppResult<ChatMessageDto>
    fun socket(orderId: String): WebSocketClient
}

@Singleton
class TrackingRepositoryImpl @Inject constructor(
    private val api: TrackingApi,
    private val sockets: WebSocketClient.Factory,
) : TrackingRepository {
    override suspend fun snapshot(orderId: String) = apiCall { api.snapshot(orderId) }
    override suspend fun sendChat(orderId: String, text: String) = apiCall { api.sendChat(orderId, ChatSendDto(text)) }
    override fun socket(orderId: String): WebSocketClient = sockets.orderTracking(orderId)
}
