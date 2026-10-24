package `in`.localrush.core.common

import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.ErrorCodes
import `in`.localrush.core.common.result.ErrorEnvelopeParser
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorEnvelopeParserTest {

    @Test
    fun `parses standard envelope with fields and extras`() {
        val body = """{"error":{"code":"otp_invalid","message":"Wrong code","fields":{"code":["Invalid OTP"]},"attempts_left":2}}"""
        val err = ErrorEnvelopeParser.parse(400, body)
        assertTrue(err is AppError.Api)
        err as AppError.Api
        assertEquals(ErrorCodes.OTP_INVALID, err.code)
        assertEquals("Wrong code", err.message)
        assertEquals(400, err.httpStatus)
        assertEquals(listOf("Invalid OTP"), err.fields["code"])
        assertEquals("Invalid OTP", err.fieldError("code"))
        assertEquals(2, err.extra!!["attempts_left"]!!.jsonPrimitive.int)
    }

    @Test
    fun `message falls back to first field error`() {
        val body = """{"error":{"code":"validation_error","fields":{"phone":["Enter a valid phone"]}}}"""
        val err = ErrorEnvelopeParser.parse(400, body) as AppError.Api
        assertEquals("Enter a valid phone", err.message)
        assertTrue(err.isValidation)
        assertNull(err.extra)
    }

    @Test
    fun `token_revoked forces logout`() {
        val err = ErrorEnvelopeParser.parse(401, """{"error":{"code":"token_revoked","message":"Revoked"}}""") as AppError.Api
        assertTrue(err.forcesLogout)
        assertTrue(err.isUnauthorized)
    }

    @Test
    fun `conflict extras preserved`() {
        val body = """{"error":{"code":"cart_restaurant_conflict","message":"x","current_restaurant":{"id":"abc","name":"Dosa Hub"}}}"""
        val err = ErrorEnvelopeParser.parse(409, body) as AppError.Api
        assertEquals(ErrorCodes.CART_RESTAURANT_CONFLICT, err.code)
        assertEquals("abc", err.extra!!["current_restaurant"]!!.let { (it as kotlinx.serialization.json.JsonObject)["id"]!!.jsonPrimitive.content })
    }

    @Test
    fun `drf detail fallback`() {
        val err = ErrorEnvelopeParser.parse(401, """{"detail":"Given token not valid","code":"token_not_valid"}""") as AppError.Api
        assertEquals("token_not_valid", err.code)
        assertEquals("Given token not valid", err.message)
    }

    @Test
    fun `drf field dict fallback`() {
        val err = ErrorEnvelopeParser.parse(400, """{"phone":["This field is required."]}""") as AppError.Api
        assertEquals(ErrorCodes.VALIDATION_ERROR, err.code)
        assertEquals("This field is required.", err.message)
    }

    @Test
    fun `non json body becomes Http error`() {
        val err = ErrorEnvelopeParser.parse(502, "<html>Bad gateway</html>")
        assertTrue(err is AppError.Http)
        assertEquals(502, (err as AppError.Http).httpStatus)
        assertTrue(err.isRetryable)
    }

    @Test
    fun `empty body becomes Http error with default message`() {
        val err = ErrorEnvelopeParser.parse(404, "")
        assertTrue(err is AppError.Http)
        assertEquals("Not found", err.message)
    }
}
