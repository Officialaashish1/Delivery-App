package `in`.localrush.rider.data

import android.content.Context
import android.net.Uri
import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.network.api.TrackingApi
import `in`.localrush.core.network.dto.ChatMessageDto
import `in`.localrush.core.network.dto.ChatSendDto
import `in`.localrush.core.network.dto.PageDto
import `in`.localrush.core.network.dto.ReasonRequest
import `in`.localrush.core.network.dto.RouteDto
import `in`.localrush.core.network.util.Multipart
import `in`.localrush.core.network.util.apiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface DeliveryRepository {
    suspend fun order(id: String): AppResult<RiderOrderDto>
    suspend fun orders(scope: String?, page: Int?): AppResult<PageDto<RiderOrderDto>>
    suspend fun arrivedAtRestaurant(id: String): AppResult<RiderOrderDto>
    suspend fun pickup(id: String, otp: String): AppResult<RiderOrderDto>
    suspend fun deliver(id: String, otp: String, proof: Uri?): AppResult<RiderOrderDto>
    suspend fun deliveryFailed(id: String, reason: String): AppResult<RiderOrderDto>
    suspend fun customerContact(id: String): AppResult<CustomerContactDto>
    suspend fun route(id: String, lat: Double, lng: Double): AppResult<RouteDto>
    suspend fun chat(id: String): AppResult<List<ChatMessageDto>>
    suspend fun sendChat(id: String, text: String): AppResult<ChatMessageDto>
}

@Singleton
class RemoteDeliveryRepository @Inject constructor(
    private val api: RiderApi,
    private val tracking: TrackingApi,
    @ApplicationContext private val context: Context,
) : DeliveryRepository {
    override suspend fun order(id: String) = apiCall { api.order(id) }
    override suspend fun orders(scope: String?, page: Int?) = apiCall { api.orders(scope, page) }
    override suspend fun arrivedAtRestaurant(id: String) = apiCall { api.arrivedAtRestaurant(id) }
    override suspend fun pickup(id: String, otp: String) = apiCall { api.pickup(id, OtpActionDto(otp)) }
    override suspend fun deliver(id: String, otp: String, proof: Uri?) = apiCall {
        api.deliver(id, Multipart.text(otp), proof?.let { Multipart.uriPart(context, "proof", it, "proof.jpg") })
    }
    override suspend fun deliveryFailed(id: String, reason: String) = apiCall { api.deliveryFailed(id, ReasonRequest(reason)) }
    override suspend fun customerContact(id: String) = apiCall { api.customerContact(id) }
    override suspend fun route(id: String, lat: Double, lng: Double) = apiCall { api.route(id, lat, lng) }
    override suspend fun chat(id: String) = apiCall { tracking.chat(id) }
    override suspend fun sendChat(id: String, text: String) = apiCall { tracking.sendChat(id, ChatSendDto(text)) }
}
