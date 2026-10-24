package `in`.localrush.customer.data.di

import `in`.localrush.customer.data.api.CustomerApi
import `in`.localrush.customer.data.repo.CartRepository
import `in`.localrush.customer.data.repo.CartRepositoryImpl
import `in`.localrush.customer.data.repo.OrdersRepository
import `in`.localrush.customer.data.repo.OrdersRepositoryImpl
import `in`.localrush.customer.data.repo.TrackingRepository
import `in`.localrush.customer.data.repo.TrackingRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CustomerApiModule {
    @Provides @Singleton fun customerApi(retrofit: Retrofit): CustomerApi = retrofit.create(CustomerApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CustomerRepoModule {
    @Binds @Singleton abstract fun cart(impl: CartRepositoryImpl): CartRepository
    @Binds @Singleton abstract fun orders(impl: OrdersRepositoryImpl): OrdersRepository
    @Binds @Singleton abstract fun tracking(impl: TrackingRepositoryImpl): TrackingRepository
}
