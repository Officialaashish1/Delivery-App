package `in`.localrush.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET config/` */
@Serializable
data class AppConfigDto(
    @SerialName("map_style_url") val mapStyleUrl: String = "https://tiles.openfreemap.org/styles/liberty",
    @SerialName("map_attribution") val mapAttribution: String = "",
    val currency: String = "INR",
    @SerialName("cod_enabled") val codEnabled: Boolean = true,
    @SerialName("cod_max_order_value") val codMaxOrderValue: DecimalString = "0",
    @SerialName("min_app_version_customer") val minAppVersionCustomer: String = "0",
    @SerialName("min_app_version_rider") val minAppVersionRider: String = "0",
    @SerialName("min_app_version_partner") val minAppVersionPartner: String = "0",
    @SerialName("support_phone") val supportPhone: String = "",
    @SerialName("support_email") val supportEmail: String = "",
    @SerialName("payment_provider") val paymentProvider: String = "razorpay",
    @SerialName("razorpay_key_id") val razorpayKeyId: String = "",
    @SerialName("location_interval_seconds") val locationIntervalSeconds: Int = 5,
)

/** `GET config/geocode/search/` and `.../reverse/` */
@Serializable
data class GeocodeResultDto(
    @SerialName("place_id") val placeId: Long? = null,
    @SerialName("display_name") val displayName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val name: String = "",
    val road: String = "",
    val locality: String = "",
    val city: String = "",
    val state: String = "",
    val postcode: String = "",
    val country: String = "",
)
