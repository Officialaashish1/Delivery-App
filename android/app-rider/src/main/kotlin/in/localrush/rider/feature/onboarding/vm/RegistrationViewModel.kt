package `in`.localrush.rider.feature.onboarding.vm

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.util.PhoneUtils
import `in`.localrush.rider.R
import `in`.localrush.rider.data.DocStatus
import `in`.localrush.rider.data.DocType
import `in`.localrush.rider.data.RiderDocumentDto
import `in`.localrush.rider.data.RiderProfileDto
import `in`.localrush.rider.data.RiderProfilePatchDto
import `in`.localrush.rider.data.RiderRepository
import `in`.localrush.rider.data.VehicleType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RegStep { PERSONAL, VEHICLE, DOCUMENTS, BANK, EMERGENCY }

data class RegForm(
    val fullName: String = "",
    val email: String = "",
    val dateOfBirth: String = "",
    val address: String = "",
    val city: String = "",
    val photoUrl: String? = null,
    val photoUri: Uri? = null,
    val vehicleType: VehicleType = VehicleType.BIKE,
    val vehicleNumber: String = "",
    val licenceNumber: String = "",
    val licenceExpiry: String = "",
    val aadhaarLast4: String = "",
    val panNumber: String = "",
    val bankAccountName: String = "",
    val bankAccountNumber: String = "",
    val bankIfsc: String = "",
    val upiId: String = "",
    val emergencyName: String = "",
    val emergencyPhone: String = "",
)

data class DocRow(val type: DocType, val document: RiderDocumentDto?, val uploading: Boolean = false, val error: String? = null) {
    val status: DocStatus get() = document?.let { DocStatus.fromWire(it.status) } ?: DocStatus.MISSING
}

data class RegistrationUiState(
    val loading: Boolean = true,
    val loadError: AppError? = null,
    val step: RegStep = RegStep.PERSONAL,
    val form: RegForm = RegForm(),
    /** Server-side field errors (already human readable). */
    val fieldErrors: Map<String, String> = emptyMap(),
    /** Local validation errors as string resource ids. */
    val localErrors: Map<String, Int> = emptyMap(),
    val saving: Boolean = false,
    val generalError: AppError? = null,
    val docs: List<DocRow> = DocType.uploadable.map { DocRow(it, null) },
    val docsLoading: Boolean = false,
    val approvedLocked: Boolean = false,
) {
    val stepIndex: Int get() = step.ordinal
    val requiredDocsDone: Boolean get() = docs.filter { it.type.required }.all { it.document != null && it.status != DocStatus.REJECTED }
}

sealed interface RegistrationEvent { data object Finished : RegistrationEvent }

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val repo: RiderRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(RegistrationUiState(step = RegStep.entries.getOrElse(savedState.get<Int>("step") ?: 0) { RegStep.PERSONAL }))
    val state: StateFlow<RegistrationUiState> = _state
    private val _events = MutableSharedFlow<RegistrationEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<RegistrationEvent> = _events

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            repo.refreshProfile().fold(
                onOk = { p -> _state.update { it.copy(loading = false, form = p.toForm(), approvedLocked = p.isApproved) }; loadDocuments() },
                onErr = { e -> _state.update { it.copy(loading = false, loadError = e) } },
            )
        }
    }

    fun update(transform: RegForm.() -> RegForm) = _state.update { it.copy(form = it.form.transform(), fieldErrors = emptyMap(), localErrors = emptyMap(), generalError = null) }
    fun setPhoto(uri: Uri) = update { copy(photoUri = uri) }
    fun goTo(step: RegStep) = _state.update { it.copy(step = step, fieldErrors = emptyMap(), localErrors = emptyMap(), generalError = null) }
    fun back() { val s = _state.value.step; if (s.ordinal > 0) goTo(RegStep.entries[s.ordinal - 1]) }
    fun clearError() = _state.update { it.copy(generalError = null) }

    /** Validates + saves the current step, then advances (or finishes). */
    fun next() {
        val s = _state.value
        if (s.saving) return
        val errors = validate(s.step, s.form)
        if (errors.isNotEmpty()) { _state.update { it.copy(localErrors = errors) }; return }
        if (s.step == RegStep.DOCUMENTS) { advance(); return }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repo.updateProfile(patchFor(s.step, s.form, s.approvedLocked), photo = if (s.step == RegStep.PERSONAL) s.form.photoUri else null).fold(
                onOk = { p -> _state.update { it.copy(saving = false, form = p.toForm().copy(photoUri = null)) }; advance() },
                onErr = { e ->
                    val fields = (e as? AppError.Api)?.fields?.mapValues { it.value.first() } ?: emptyMap()
                    _state.update { it.copy(saving = false, fieldErrors = fields, generalError = if (fields.isEmpty()) e else null) }
                },
            )
        }
    }

    private fun advance() {
        val s = _state.value.step
        if (s == RegStep.EMERGENCY) _events.tryEmit(RegistrationEvent.Finished) else goTo(RegStep.entries[s.ordinal + 1])
    }

    fun loadDocuments() {
        _state.update { it.copy(docsLoading = true) }
        viewModelScope.launch {
            repo.documents().fold(
                onOk = { list -> _state.update { st -> st.copy(docsLoading = false, docs = DocType.uploadable.map { t -> DocRow(t, list.filter { it.docType == t.wire }.maxByOrNull { it.createdAt ?: "" }) }) } },
                onErr = { e -> _state.update { it.copy(docsLoading = false, generalError = e) } },
            )
        }
    }

    fun uploadDocument(type: DocType, number: String, uri: Uri) {
        _state.update { st -> st.copy(docs = st.docs.map { if (it.type == type) it.copy(uploading = true, error = null) else it }) }
        viewModelScope.launch {
            repo.uploadDocument(type, number, uri).fold(
                onOk = { doc -> _state.update { st -> st.copy(docs = st.docs.map { if (it.type == type) DocRow(type, doc) else it }) } },
                onErr = { e -> _state.update { st -> st.copy(docs = st.docs.map { if (it.type == type) it.copy(uploading = false, error = e.message) else it }) } },
            )
        }
    }

    fun deleteDocument(type: DocType) {
        val id = _state.value.docs.firstOrNull { it.type == type }?.document?.id ?: return
        viewModelScope.launch {
            repo.deleteDocument(id).fold(
                onOk = { _state.update { st -> st.copy(docs = st.docs.map { if (it.type == type) DocRow(type, null) else it }) } },
                onErr = { e -> _state.update { st -> st.copy(docs = st.docs.map { if (it.type == type) it.copy(error = e.message) else it }) } },
            )
        }
    }

    companion object {
        /** Local validation → string resource ids (the UI resolves them); server field errors are plain strings. */
        fun validate(step: RegStep, f: RegForm): Map<String, Int> {
            val e = mutableMapOf<String, Int>()
            when (step) {
                RegStep.PERSONAL -> {
                    if (f.fullName.trim().length < 2) e["full_name"] = R.string.err_full_name
                    if (f.email.isNotBlank() && !f.email.contains("@")) e["email"] = R.string.err_email
                    if (f.dateOfBirth.isNotBlank() && !f.dateOfBirth.matches(DATE)) e["date_of_birth"] = R.string.err_date_format
                    if (f.city.isBlank()) e["city"] = R.string.err_city
                }
                RegStep.VEHICLE -> {
                    if (f.vehicleType != VehicleType.BICYCLE) {
                        if (f.vehicleNumber.replace(" ", "").length < 6) e["vehicle_number"] = R.string.err_vehicle_number
                        if (f.licenceNumber.trim().length < 6) e["driving_licence_number"] = R.string.err_licence_number
                        if (f.licenceExpiry.isNotBlank() && !f.licenceExpiry.matches(DATE)) e["driving_licence_expiry"] = R.string.err_date_format
                    }
                }
                RegStep.DOCUMENTS -> Unit
                RegStep.BANK -> {
                    val hasBank = f.bankAccountNumber.isNotBlank() || f.bankIfsc.isNotBlank() || f.bankAccountName.isNotBlank()
                    if (!hasBank && f.upiId.isBlank()) e["upi_id"] = R.string.err_bank_or_upi
                    if (hasBank) {
                        if (f.bankAccountName.isBlank()) e["bank_account_name"] = R.string.err_account_name
                        if (f.bankAccountNumber.length < 9) e["bank_account_number"] = R.string.err_account_number
                        if (!f.bankIfsc.matches(IFSC)) e["bank_ifsc"] = R.string.err_ifsc
                    }
                    if (f.upiId.isNotBlank() && !f.upiId.contains("@")) e["upi_id"] = R.string.err_upi
                    if (f.aadhaarLast4.isNotBlank() && f.aadhaarLast4.length != 4) e["aadhaar_last4"] = R.string.err_aadhaar_last4
                    if (f.panNumber.isNotBlank() && !f.panNumber.matches(PAN)) e["pan_number"] = R.string.err_pan
                }
                RegStep.EMERGENCY -> {
                    if (f.emergencyName.isBlank()) e["emergency_contact_name"] = R.string.err_emergency_name
                    if (!PhoneUtils.isValidIndian(f.emergencyPhone)) e["emergency_contact_phone"] = R.string.err_phone
                }
            }
            return e
        }

        private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
        private val IFSC = Regex("^[A-Za-z]{4}0[A-Za-z0-9]{6}$")
        private val PAN = Regex("^[A-Za-z]{5}\\d{4}[A-Za-z]$")

        fun patchFor(step: RegStep, f: RegForm, locked: Boolean): RiderProfilePatchDto = when (step) {
            RegStep.PERSONAL -> RiderProfilePatchDto(fullName = f.fullName.trim(), email = f.email.trim().ifBlank { null }, dateOfBirth = f.dateOfBirth.ifBlank { null }, address = f.address.trim(), city = f.city.trim())
            RegStep.VEHICLE -> RiderProfilePatchDto(
                vehicleType = f.vehicleType.wire,
                vehicleNumber = if (locked) null else f.vehicleNumber.uppercase().replace(" ", ""),
                drivingLicenceNumber = if (locked) null else f.licenceNumber.uppercase().trim(),
                drivingLicenceExpiry = f.licenceExpiry.ifBlank { null },
            )
            RegStep.DOCUMENTS -> RiderProfilePatchDto()
            RegStep.BANK -> RiderProfilePatchDto(
                aadhaarLast4 = f.aadhaarLast4.ifBlank { null },
                panNumber = if (locked) null else f.panNumber.uppercase().ifBlank { null },
                bankAccountName = f.bankAccountName.trim().ifBlank { null },
                bankAccountNumber = if (locked) null else f.bankAccountNumber.trim().ifBlank { null },
                bankIfsc = if (locked) null else f.bankIfsc.uppercase().ifBlank { null },
                upiId = f.upiId.trim().ifBlank { null },
            )
            RegStep.EMERGENCY -> RiderProfilePatchDto(emergencyContactName = f.emergencyName.trim(), emergencyContactPhone = PhoneUtils.normalizeIndian(f.emergencyPhone) ?: f.emergencyPhone)
        }

        fun RiderProfileDto.toForm() = RegForm(
            fullName = fullName, email = email ?: "", dateOfBirth = dateOfBirth ?: "", address = address, city = city, photoUrl = photo,
            vehicleType = VehicleType.fromWire(vehicleType), vehicleNumber = vehicleNumber, licenceNumber = drivingLicenceNumber, licenceExpiry = drivingLicenceExpiry ?: "",
            aadhaarLast4 = aadhaarLast4, panNumber = panNumber, bankAccountName = bankAccountName, bankAccountNumber = bankAccountNumber, bankIfsc = bankIfsc, upiId = upiId,
            emergencyName = emergencyContactName, emergencyPhone = emergencyContactPhone.let { PhoneUtils.localPart(it) },
        )
    }
}
