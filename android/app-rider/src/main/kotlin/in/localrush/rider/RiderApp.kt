package `in`.localrush.rider

import android.app.Application
import `in`.localrush.core.common.AppRole
import `in`.localrush.core.push.NotificationChannels
import `in`.localrush.rider.data.AppForeground
import `in`.localrush.rider.data.RiderRealtime
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RiderApp : Application() {
    @Inject lateinit var foreground: AppForeground
    @Inject lateinit var realtime: RiderRealtime

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this, AppRole.RIDER)
        foreground.install()
        realtime.start()
    }
}
