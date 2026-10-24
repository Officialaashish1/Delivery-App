package `in`.localrush.rider.di

import `in`.localrush.core.common.AppRole
import `in`.localrush.core.common.util.AppDispatchers
import `in`.localrush.core.network.NetworkConfig
import `in`.localrush.rider.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** App-specific bindings required by the core modules. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun networkConfig(): NetworkConfig = NetworkConfig(
        apiBaseUrl = BuildConfig.API_BASE_URL,
        wsBaseUrl = BuildConfig.WS_BASE_URL,
        isDebug = BuildConfig.DEBUG,
        appVersion = BuildConfig.VERSION_NAME,
        role = AppRole.RIDER,
    )

    @Provides @Singleton
    fun appRole(): AppRole = AppRole.RIDER

    @Provides @Singleton
    fun dispatchers(): AppDispatchers = AppDispatchers()
}
