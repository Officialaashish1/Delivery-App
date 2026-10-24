package `in`.localrush.customer

import android.app.Application
import `in`.localrush.core.common.AppRole
import `in`.localrush.core.push.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CustomerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this, AppRole.CUSTOMER)
    }
}
