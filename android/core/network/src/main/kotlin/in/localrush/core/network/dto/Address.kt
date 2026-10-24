package `in`.localrush.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Read model for `customers/addresses/` (`lat`/`lng` on read). */
@Serializable
data class AddressDto(
    val id: String,
    val label: String = "other",
    val name: String = "",
    @SerialName("contact_name") val contactName: String = "",
    @SerialName("contact_phone") val contactPhone: String = "",
    @SerialName("address_line1") val addressLine1: String = "",
    @SerialName("address_line2") val addressLine2: String = "",
    val landmark: String = "",
    val city: String = "",
    val state: String = "",
    @SerialName("postal_code") val postalCode: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("delivery_instructions") val deliveryInstructions: String = "",
    val formatted: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)

/** Write model (`latitude`/`longitude` on write). Null fields are omitted (explicitNulls=false) → fine for PATCH. */
@Serializable
data class AddressWriteDto(
    val label: String? = null,
    val name: String? = null,
    @SerialName("contact_name") val contactName: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null,
    @SerialName("address_line1") val addressLine1: String? = null,
    @SerialName("address_line2") val addressLine2: String? = null,
    val landmark: String? = null,
    val city: String? = null,
    val state: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("is_default") val isDefault: Boolean? = null,
    @SerialName("delivery_instructions") val deliveryInstructions: String? = null,
)

@Serializable
data class CustomerProfileDto(
    val id: String? = null,
    val phone: String = "",
    @SerialName("full_name") val fullName: String = "",
    val email: String? = null,
    @SerialName("avatar") val avatar: String? = null,
    @SerialName("default_address") val defaultAddress: AddressDto? = null,
    @SerialName("veg_only") val vegOnly: Boolean = false,
    @SerialName("referral_code") val referralCode: String? = null,
)
