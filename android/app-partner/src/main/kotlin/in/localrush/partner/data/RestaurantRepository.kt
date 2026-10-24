package `in`.localrush.partner.data

import android.content.Context
import android.net.Uri
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.common.result.EmptyResult
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.network.api.ConfigApi
import `in`.localrush.core.network.dto.GeocodeResultDto
import `in`.localrush.core.network.util.Multipart
import `in`.localrush.core.network.util.apiCall
import `in`.localrush.core.network.util.apiCallUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MultipartBody
import javax.inject.Inject
import javax.inject.Singleton

/** Approval status of the owner's restaurant, as the partner app sees it. */
enum class RestaurantStatus(val wire: String) {
    PENDING("pending"), UNDER_REVIEW("under_review"), APPROVED("approved"), REJECTED("rejected"), SUSPENDED("suspended"), UNKNOWN("");

    companion object { fun fromWire(v: String?) = entries.firstOrNull { it.wire == v } ?: UNKNOWN }
}

/** Everything the owner can send when registering (multipart). */
data class RegistrationPayload(
    val name: String,
    val restaurantType: String,
    val description: String,
    val ownerName: String,
    val phone: String,
    val email: String,
    val addressLine1: String,
    val addressLine2: String,
    val landmark: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val latitude: Double,
    val longitude: Double,
    val deliveryRadiusKm: String,
    val fssaiNumber: String,
    val fssaiExpiry: String?,
    val gstNumber: String,
    val panNumber: String,
    val bankAccountName: String,
    val bankAccountNumber: String,
    val bankIfsc: String,
    val upiId: String,
    val cuisineIds: List<Int>,
    val categoryIds: List<Int>,
    val isPureVeg: Boolean,
    val minOrderAmount: String,
    val packagingCharge: String,
    val avgPreparationMinutes: Int,
    val costForTwo: String,
    val logo: Uri?,
    val cover: Uri?,
)

/**
 * Owner's restaurant: profile, status, toggles, hours, holidays, documents. Keeps the latest
 * [PartnerRestaurantDto] in [restaurant] so every screen (dashboard switch, settings, approval gate) shares it.
 */
@Singleton
class RestaurantRepository @Inject constructor(
    private val api: PartnerApi,
    private val configApi: ConfigApi,
    @ApplicationContext private val context: Context,
) {
    private val _restaurant = MutableStateFlow<PartnerRestaurantDto?>(null)
    val restaurant: StateFlow<PartnerRestaurantDto?> = _restaurant.asStateFlow()

    /** Cached lookups: cuisines/categories rarely change. */
    private var cuisines: List<CuisineDto>? = null
    private var categories: List<RestaurantCategoryDto>? = null

    fun clear() { _restaurant.value = null }

    /** `Ok(null)` means "no restaurant registered yet" (backend 404 not_found). */
    suspend fun load(): AppResult<PartnerRestaurantDto?> = when (val r = apiCall { api.restaurant() }) {
        is Result.Ok -> { _restaurant.value = r.value; Result.Ok(r.value) }
        is Result.Err -> if (r.error is AppError.Api && (r.error as AppError.Api).isNotFound) Result.Ok(null) else r
    }

    suspend fun cuisines(): AppResult<List<CuisineDto>> = cuisines?.let { Result.Ok(it) } ?: apiCall { api.cuisines() }.onSuccess { cuisines = it }
    suspend fun restaurantCategories(): AppResult<List<RestaurantCategoryDto>> =
        categories?.let { Result.Ok(it) } ?: apiCall { api.restaurantCategories() }.onSuccess { categories = it }

    suspend fun reverseGeocode(lat: Double, lng: Double): AppResult<GeocodeResultDto> = apiCall { configApi.reverseGeocode(lat, lng) }
    suspend fun geocodeSearch(q: String, lat: Double?, lng: Double?): AppResult<List<GeocodeResultDto>> = apiCall { configApi.geocodeSearch(q, lat, lng) }

    suspend fun register(p: RegistrationPayload): AppResult<PartnerRestaurantDto> = apiCall {
        val fields = Multipart.fields(
            "name" to p.name, "restaurant_type" to p.restaurantType, "description" to p.description, "owner_name" to p.ownerName,
            "phone" to p.phone, "email" to p.email.ifBlank { null },
            "address_line1" to p.addressLine1, "address_line2" to p.addressLine2, "landmark" to p.landmark, "city" to p.city,
            "state" to p.state, "postal_code" to p.postalCode, "latitude" to p.latitude, "longitude" to p.longitude,
            "delivery_radius_km" to p.deliveryRadiusKm, "fssai_number" to p.fssaiNumber, "fssai_expiry" to p.fssaiExpiry,
            "gst_number" to p.gstNumber, "pan_number" to p.panNumber, "bank_account_name" to p.bankAccountName,
            "bank_account_number" to p.bankAccountNumber, "bank_ifsc" to p.bankIfsc, "upi_id" to p.upiId,
            "is_pure_veg" to p.isPureVeg, "min_order_amount" to p.minOrderAmount, "packaging_charge" to p.packagingCharge,
            "avg_preparation_minutes" to p.avgPreparationMinutes, "cost_for_two" to p.costForTwo,
        )
        val lists = p.cuisineIds.map { MultipartBody.Part.createFormData("cuisine_ids", it.toString()) } +
            p.categoryIds.map { MultipartBody.Part.createFormData("category_ids", it.toString()) }
        api.register(
            fields, lists,
            logo = p.logo?.let { Multipart.uriPart(context, "logo", it) },
            cover = p.cover?.let { Multipart.uriPart(context, "cover_image", it) },
        )
    }.onSuccess { _restaurant.value = it }

    suspend fun patch(body: RestaurantPatchDto): AppResult<PartnerRestaurantDto> =
        apiCall { api.patchRestaurant(body) }.onSuccess { _restaurant.value = it }

    suspend fun updateImages(logo: Uri?, cover: Uri?): AppResult<PartnerRestaurantDto> = apiCall {
        api.patchRestaurantImages(
            logo = logo?.let { Multipart.uriPart(context, "logo", it) },
            cover = cover?.let { Multipart.uriPart(context, "cover_image", it) },
        )
    }.onSuccess { _restaurant.value = it }

    suspend fun toggle(isOpen: Boolean? = null, isAcceptingOrders: Boolean? = null, temporarilyClosedUntil: String? = null, clearClosure: Boolean = false): AppResult<PartnerRestaurantDto> =
        apiCall { api.toggle(ToggleDto(isOpen, isAcceptingOrders, if (clearClosure) "" else temporarilyClosedUntil)) }
            .onSuccess { _restaurant.value = it }

    suspend fun saveHours(hours: List<RestaurantHoursDto>): AppResult<List<RestaurantHoursDto>> =
        apiCall { api.putHours(HoursBulkDto(hours)) }.onSuccess { h -> _restaurant.value = _restaurant.value?.copy(hours = h) }

    suspend fun holidays(): AppResult<List<HolidayDto>> = apiCall { api.holidays() }
    suspend fun addHoliday(date: String, reason: String): AppResult<HolidayDto> = apiCall { api.addHoliday(HolidayDto(date = date, reason = reason)) }
    suspend fun deleteHoliday(id: Int): EmptyResult = apiCallUnit { api.deleteHoliday(id) }

    suspend fun documents(): AppResult<List<RestaurantDocumentDto>> = apiCall { api.documents() }
    suspend fun uploadDocument(docType: String, number: String, file: Uri): AppResult<RestaurantDocumentDto> = apiCall {
        api.uploadDocument(Multipart.text(docType), Multipart.text(number), Multipart.uriPart(context, "file", file))
    }
    suspend fun deleteDocument(id: String): EmptyResult = apiCallUnit { api.deleteDocument(id) }

    suspend fun dashboard(): AppResult<DashboardDto> = apiCall { api.dashboard() }
    suspend fun bestSellers(): AppResult<List<MenuItemDto>> = apiCall { api.bestSellers() }
}
