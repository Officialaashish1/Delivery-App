package `in`.localrush.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import `in`.localrush.core.common.AppRole
import `in`.localrush.core.common.Constants.Channels

data class ChannelSpec(
    val id: String,
    val nameRes: Int,
    val descRes: Int,
    val importance: Int,
    val vibrate: Boolean = true,
    /** `android.resource://<pkg>/raw/<name>` or null for default. */
    val sound: Uri? = null,
    val bypassDnd: Boolean = false,
)

/** Creates the per-app channel set. Idempotent; call from Application.onCreate. */
object NotificationChannels {

    fun specsFor(role: AppRole, context: Context): List<ChannelSpec> {
        val high = if (Build.VERSION.SDK_INT >= 24) NotificationManager.IMPORTANCE_HIGH else 4
        val default = if (Build.VERSION.SDK_INT >= 24) NotificationManager.IMPORTANCE_DEFAULT else 3
        return when (role) {
            AppRole.CUSTOMER -> listOf(
                ChannelSpec(Channels.CUSTOMER_ORDERS, R.string.push_channel_orders, R.string.push_channel_orders_desc, high),
                ChannelSpec(Channels.CUSTOMER_PROMOS, R.string.push_channel_promos, R.string.push_channel_promos_desc, default, vibrate = false),
            )
            AppRole.RIDER -> listOf(
                ChannelSpec(
                    Channels.RIDER_OFFERS, R.string.push_channel_rider_offers, R.string.push_channel_rider_offers_desc, high,
                    vibrate = true, sound = rawSound(context, "rider_offer"), bypassDnd = true,
                ),
                ChannelSpec(Channels.RIDER_ORDERS, R.string.push_channel_rider_orders, R.string.push_channel_rider_orders_desc, high),
            )
            AppRole.PARTNER -> listOf(
                ChannelSpec(
                    Channels.PARTNER_ORDERS, R.string.push_channel_partner_orders, R.string.push_channel_partner_orders_desc, high,
                    vibrate = true, sound = rawSound(context, "new_order"), bypassDnd = true,
                ),
            )
        }
    }

    fun create(context: Context, role: AppRole) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        specsFor(role, context).forEach { spec ->
            val channel = NotificationChannel(spec.id, context.getString(spec.nameRes), spec.importance).apply {
                description = context.getString(spec.descRes)
                enableVibration(spec.vibrate)
                if (spec.vibrate) vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 600)
                enableLights(true)
                setShowBadge(true)
                setBypassDnd(spec.bypassDnd)
                spec.sound?.let {
                    setSound(it, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                }
            }
            nm.createNotificationChannel(channel)
        }
    }

    /** Channel for a payload given the app role. */
    fun channelFor(role: AppRole, payload: PushPayload): String = when (role) {
        AppRole.CUSTOMER -> if (payload.isPromo) Channels.CUSTOMER_PROMOS else Channels.CUSTOMER_ORDERS
        AppRole.RIDER -> if (payload.isRiderOffer) Channels.RIDER_OFFERS else Channels.RIDER_ORDERS
        AppRole.PARTNER -> Channels.PARTNER_ORDERS
    }

    /** Returns the `raw/<name>` sound if the app bundles one, else null (system default). */
    private fun rawSound(context: Context, name: String): Uri? {
        val id = context.resources.getIdentifier(name, "raw", context.packageName)
        return if (id != 0) Uri.parse("android.resource://${context.packageName}/raw/$name") else null
    }
}
