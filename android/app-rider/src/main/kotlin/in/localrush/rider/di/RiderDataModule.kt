package `in`.localrush.rider.di

import `in`.localrush.core.database.PendingLocationDao
import `in`.localrush.rider.data.DeliveryRepository
import `in`.localrush.rider.data.LocationUploader
import `in`.localrush.rider.data.EarningsRepository
import `in`.localrush.rider.data.LocationSink
import `in`.localrush.rider.data.OffersRepository
import `in`.localrush.rider.data.RemoteDeliveryRepository
import `in`.localrush.rider.data.RemoteEarningsRepository
import `in`.localrush.rider.data.RemoteOffersRepository
import `in`.localrush.rider.data.RestLocationSink
import `in`.localrush.rider.data.RiderApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RiderApiModule {
    @Provides @Singleton
    fun riderApi(retrofit: Retrofit): RiderApi = retrofit.create(RiderApi::class.java)

    @Provides @Singleton
    fun locationUploader(sink: LocationSink, dao: PendingLocationDao): LocationUploader = LocationUploader(sink, dao)

    @Provides @Singleton
    fun appScope(): AppScope = AppScope()
}

/** Application-wide coroutine scope for fire-and-forget work that must outlive a ViewModel/Service (e.g. going offline on Stop). */
class AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Module
@InstallIn(SingletonComponent::class)
abstract class RiderDataModule {
    @Binds abstract fun offers(impl: RemoteOffersRepository): OffersRepository
    @Binds abstract fun delivery(impl: RemoteDeliveryRepository): DeliveryRepository
    @Binds abstract fun earnings(impl: RemoteEarningsRepository): EarningsRepository
    @Binds abstract fun locationSink(impl: RestLocationSink): LocationSink
}
