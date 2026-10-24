package `in`.localrush.rider

import `in`.localrush.rider.data.RiderProfileDto
import `in`.localrush.rider.feature.onboarding.vm.Destination
import `in`.localrush.rider.feature.onboarding.vm.GateViewModel
import `in`.localrush.rider.feature.onboarding.vm.RegForm
import `in`.localrush.rider.feature.onboarding.vm.RegStep
import `in`.localrush.rider.feature.onboarding.vm.RegistrationViewModel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GateDecisionTest {
    private val complete = RiderProfileDto(
        fullName = "Ravi", vehicleNumber = "KA01AB1234", drivingLicenceNumber = "DL123456", upiId = "ravi@upi", emergencyContactPhone = "+919876543210",
        documentsSummary = buildJsonObject { listOf("driving_licence", "aadhaar", "pan", "vehicle_rc").forEach { put(it, JsonPrimitive("pending")) } },
    )

    @Test fun `approved riders go to main regardless of docs`() {
        assertEquals(Destination.MAIN, GateViewModel.decide(RiderProfileDto(status = "approved")))
    }

    @Test fun `incomplete profile goes to registration, complete pending profile waits`() {
        assertEquals(Destination.REGISTRATION, GateViewModel.decide(RiderProfileDto(status = "pending")))
        assertEquals(Destination.REGISTRATION, GateViewModel.decide(complete.copy(documentsSummary = null)))
        assertEquals(Destination.PENDING, GateViewModel.decide(complete.copy(status = "pending")))
        assertEquals(Destination.PENDING, GateViewModel.decide(RiderProfileDto(status = "rejected")))
        assertEquals(Destination.PENDING, GateViewModel.decide(RiderProfileDto(status = "suspended")))
    }

    @Test fun `documents_summary object is parsed into statuses`() {
        assertEquals("pending", complete.documentStatuses["pan"])
        assertTrue(RiderProfileDto(documentsSummary = JsonPrimitive("4 docs")).documentStatuses.isEmpty())
    }

    @Test fun `registration step validation`() {
        assertTrue(RegistrationViewModel.validate(RegStep.PERSONAL, RegForm(fullName = "R", city = "")).keys.containsAll(setOf("full_name", "city")))
        assertTrue(RegistrationViewModel.validate(RegStep.BANK, RegForm()).containsKey("upi_id"))
        assertTrue(RegistrationViewModel.validate(RegStep.BANK, RegForm(upiId = "ravi@upi")).isEmpty())
        assertTrue(RegistrationViewModel.validate(RegStep.BANK, RegForm(bankAccountName = "R", bankAccountNumber = "123456789", bankIfsc = "HDFC0001234")).isEmpty())
        assertTrue(RegistrationViewModel.validate(RegStep.BANK, RegForm(bankAccountNumber = "123", bankIfsc = "x")).keys.containsAll(setOf("bank_account_name", "bank_account_number", "bank_ifsc")))
        assertTrue(RegistrationViewModel.validate(RegStep.EMERGENCY, RegForm(emergencyName = "Ma", emergencyPhone = "9876543210")).isEmpty())
        assertTrue(RegistrationViewModel.validate(RegStep.VEHICLE, RegForm(vehicleNumber = "KA01AB1234", licenceNumber = "DL1234567")).isEmpty())
        // locked fields are not sent once approved
        val patch = RegistrationViewModel.patchFor(RegStep.VEHICLE, RegForm(vehicleNumber = "KA01AB1234", licenceNumber = "DL1"), locked = true)
        assertEquals(null, patch.vehicleNumber)
        assertEquals("bike", patch.vehicleType)
    }
}
