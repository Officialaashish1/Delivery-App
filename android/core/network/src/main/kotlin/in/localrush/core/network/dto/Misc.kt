package `in`.localrush.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---- Notifications ----
@Serializable
data class NotificationDto(
    val id: String,
    val category: String = "system",
    val title: String = "",
    val body: String = "",
    val data: JsonObject? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class UnreadCountDto(val unread: Int = 0)

@Serializable
data class MarkReadDto(val ids: List<String>? = null)

// ---- Support ----
@Serializable
data class FaqDto(val id: Int, val question: String = "", val answer: String = "")

@Serializable
data class SupportContactDto(
    val phone: String = "",
    val email: String = "",
    val categories: List<KeyLabelDto> = emptyList(),
)

@Serializable
data class KeyLabelDto(val key: String, val label: String = "")

@Serializable
data class SupportTicketDto(
    val id: String,
    @SerialName("ticket_number") val ticketNumber: String = "",
    val order: String? = null,
    @SerialName("order_number") val orderNumber: String? = null,
    val category: String = "",
    val subject: String = "",
    val description: String = "",
    val status: String = "open",
    val priority: String = "normal",
    val resolution: String = "",
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("refund_amount") val refundAmount: DecimalString? = null,
    val messages: List<TicketMessageDto> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class TicketMessageDto(
    val id: String,
    @SerialName("sender_name") val senderName: String = "",
    @SerialName("is_staff_reply") val isStaffReply: Boolean = false,
    val text: String = "",
    val attachment: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreateTicketDto(
    val order: String? = null,
    val category: String,
    val subject: String,
    val description: String,
)

// ---- Reviews ----
@Serializable
data class ReviewDto(
    val id: String,
    val order: String? = null,
    @SerialName("order_number") val orderNumber: String? = null,
    @SerialName("customer_name") val customerName: String = "",
    val restaurant: String? = null,
    @SerialName("restaurant_rating") val restaurantRating: Int = 0,
    @SerialName("restaurant_comment") val restaurantComment: String = "",
    @SerialName("rider_rating") val riderRating: Int? = null,
    @SerialName("rider_comment") val riderComment: String = "",
    @SerialName("item_ratings") val itemRatings: Map<String, Int> = emptyMap(),
    val tags: List<String> = emptyList(),
    @SerialName("partner_reply") val partnerReply: String? = null,
    @SerialName("partner_replied_at") val partnerRepliedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreateReviewDto(
    @SerialName("order_id") val orderId: String,
    @SerialName("restaurant_rating") val restaurantRating: Int,
    @SerialName("restaurant_comment") val restaurantComment: String = "",
    @SerialName("rider_rating") val riderRating: Int? = null,
    @SerialName("rider_comment") val riderComment: String = "",
    @SerialName("item_ratings") val itemRatings: Map<String, Int> = emptyMap(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class ReplyDto(val reply: String)
