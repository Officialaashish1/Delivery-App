package `in`.localrush.rider.data

import android.content.Context
import android.net.Uri
import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.common.result.EmptyResult
import `in`.localrush.core.network.util.Multipart
import `in`.localrush.core.network.util.apiCall
import `in`.localrush.core.network.util.apiCallUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Profile, documents, availability and dashboard. Caches the last profile in memory for instant screens. */
@Singleton
class RiderRepository @Inject constructor(
    private val api: RiderApi,
    @ApplicationContext private val context: Context,
) {
    private val _profile = MutableStateFlow<RiderProfileDto?>(null)
    val profile: StateFlow<RiderProfileDto?> = _profile

    suspend fun refreshProfile(): AppResult<RiderProfileDto> = apiCall { api.profile() }.onSuccess { _profile.value = it }

    suspend fun updateProfile(patch: RiderProfilePatchDto, photo: Uri? = null): AppResult<RiderProfileDto> {
        val result = if (photo == null) {
            apiCall { api.patchProfile(patch) }
        } else {
            val fields = Multipart.fields(
                "full_name" to patch.fullName, "email" to patch.email, "date_of_birth" to patch.dateOfBirth,
                "address" to patch.address, "city" to patch.city, "vehicle_type" to patch.vehicleType,
                "vehicle_number" to patch.vehicleNumber, "driving_licence_number" to patch.drivingLicenceNumber,
                "driving_licence_expiry" to patch.drivingLicenceExpiry, "aadhaar_last4" to patch.aadhaarLast4,
                "pan_number" to patch.panNumber, "bank_account_name" to patch.bankAccountName,
                "bank_account_number" to patch.bankAccountNumber, "bank_ifsc" to patch.bankIfsc, "upi_id" to patch.upiId,
                "emergency_contact_name" to patch.emergencyContactName, "emergency_contact_phone" to patch.emergencyContactPhone,
            )
            apiCall { api.patchProfileMultipart(fields, Multipart.uriPart(context, "photo", photo, "photo.jpg")) }
        }
        return result.onSuccess { _profile.value = it }
    }

    suspend fun documents(): AppResult<List<RiderDocumentDto>> = apiCall { api.documents() }

    suspend fun uploadDocument(type: DocType, number: String, file: Uri): AppResult<RiderDocumentDto> = apiCall {
        api.uploadDocument(Multipart.text(type.wire), Multipart.text(number), Multipart.uriPart(context, "file", file))
    }

    suspend fun deleteDocument(id: String): EmptyResult = apiCallUnit { api.deleteDocument(id) }

    suspend fun setOnline(online: Boolean, lat: Double?, lng: Double?): AppResult<RiderProfileDto> =
        apiCall { api.setOnline(OnlineToggleDto(online, lat, lng)) }.onSuccess { _profile.value = it }

    suspend fun dashboard(): AppResult<RiderDashboardDto> = apiCall { api.dashboard() }

    fun clearCache() { _profile.value = null }
}
