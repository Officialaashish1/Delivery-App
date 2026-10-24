package `in`.localrush.core.auth.di

import android.content.Context
import `in`.localrush.core.auth.SessionManager
import `in`.localrush.core.auth.TokenStore
import `in`.localrush.core.network.auth.SessionInvalidator
import `in`.localrush.core.network.auth.TokenProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides @Singleton
    fun tokenStore(@ApplicationContext context: Context, json: Json): TokenStore = TokenStore(context, json)

    @Provides @Singleton
    fun tokenProvider(store: TokenStore): TokenProvider = store

    @Provides @Singleton
    fun sessionInvalidator(session: SessionManager): SessionInvalidator = session
}
