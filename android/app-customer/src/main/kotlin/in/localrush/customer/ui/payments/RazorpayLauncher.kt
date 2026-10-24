package `in`.localrush.customer.ui.payments

import android.app.Activity
import com.razorpay.Checkout
import `in`.localrush.core.network.dto.RazorpayCheckoutDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.json.JSONObject

/**
 * Opens Razorpay Checkout with the `payment` payload from `POST orders/`. Card data never touches the app:
 * the SDK renders its own UI and only hands back ids + signature (verified server-side via `payments/verify/`).
 */
object RazorpayLauncher {
    fun open(activity: Activity, payload: RazorpayCheckoutDto, methodHint: String?) {
        val checkout = Checkout().apply { setKeyID(payload.keyId) }
        val options = JSONObject().apply {
            put("key", payload.keyId)
            put("name", payload.name)
            put("description", payload.description)
            put("order_id", payload.providerOrderId)
            put("amount", payload.amountPaise)
            put("currency", payload.currency)
            put("theme", JSONObject().put("color", payload.themeColor))
            put("retry", JSONObject().put("enabled", false)) // retries go through payments/orders/{id}/retry/
            put("prefill", JSONObject().apply {
                payload.prefill?.forEach { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { put(k, it) } }
                if (methodHint != null && methodHint != "cod") put("method", methodHint)
            })
            put("notes", payload.notes.toJSONObject())
            put("send_sms_hash", true)
        }
        checkout.open(activity, options)
    }

    private fun JsonObject?.toJSONObject(): JSONObject = JSONObject().also { o -> this?.forEach { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { o.put(k, it) } } }
}
