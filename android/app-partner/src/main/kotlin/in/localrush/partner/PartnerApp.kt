package `in`.localrush.partner

import android.app.Application
import `in`.localrush.core.common.AppRole
import `in`.localrush.core.push.NotificationChannels
import `in`.localrush.partner.alert.NewOrderAlerter
import `in`.localrush.partner.data.PartnerOrderFeed
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PartnerApp : Application() {
    @Inject lateinit var orderFeed: PartnerOrderFeed
    @Inject lateinit var alerter: NewOrderAlerter

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this, AppRole.PARTNER)
        // The live orders board + loud alert live for the whole process so a new order rings on any screen.
        orderFeed.start()
        alerter.start()
    }
}
