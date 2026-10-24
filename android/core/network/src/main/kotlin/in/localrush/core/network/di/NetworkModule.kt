package `in`.localrush.core.network.di

import `in`.localrush.core.network.BuildConfig
import `in`.localrush.core.network.NetworkConfig
import `in`.localrush.core.network.api.AddressApi
import `in`.localrush.core.network.api.AuthApi
import `in`.localrush.core.network.api.ConfigApi
import `in`.localrush.core.network.api.NotificationApi
import `in`.localrush.core.network.api.OrderApi
import `in`.localrush.core.network.api.ReviewApi
import `in`.localrush.core.network.api.SupportApi
import `in`.localrush.core.network.api.TrackingApi
import `in`.localrush.core.network.auth.AuthInterceptor
import `in`.localrush.core.network.auth.SessionInvalidator
import `in`.localrush.core.network.auth.TokenAuthenticator
import `in`.localrush.core.network.auth.TokenProvider
import `in`.localrush.core.network.ws.WebSocketClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class BareClient
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AuthedClient

/**
 * Apps must provide: [NetworkConfig], [TokenProvider], [SessionInvalidator] (the latter two come from core:auth).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    private fun baseBuilder(config: NetworkConfig): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(config.writeTimeoutSeconds, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)

    /** No auth interceptor/authenticator: used for token refresh and as the WebSocket client. */
    @Provides @Singleton @BareClient
    fun provideBareClient(config: NetworkConfig): OkHttpClient = baseBuilder(config)
        .apply { if (config.isDebug && BuildConfig.HTTP_LOGGING) addInterceptor(logging(HttpLoggingInterceptor.Level.BASIC)) }
        .build()

    @Provides @Singleton @AuthedClient
    fun provideAuthedClient(
        @BareClient bare: OkHttpClient,
        config: NetworkConfig,
        tokens: TokenProvider,
        invalidator: SessionInvalidator,
    ): OkHttpClient = baseBuilder(config)
        .connectionPool(bare.connectionPool)
        .dispatcher(bare.dispatcher)
        .addInterceptor(AuthInterceptor(tokens, config))
        .authenticator(TokenAuthenticator(tokens, config, invalidator, bare))
        .apply { if (config.isDebug && BuildConfig.HTTP_LOGGING) addInterceptor(logging(HttpLoggingInterceptor.Level.BODY)) }
        .build()

    @Provides @Singleton
    fun provideRetrofit(@AuthedClient client: OkHttpClient, config: NetworkConfig, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(config.apiBaseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json; charset=utf-8".toMediaType()))
        .build()

    @Provides @Singleton
    fun provideWebSocketClientFactory(@BareClient client: OkHttpClient, config: NetworkConfig, tokens: TokenProvider, json: Json): WebSocketClient.Factory =
        WebSocketClient.Factory(client, config, tokens, json)

    @Provides @Singleton fun authApi(r: Retrofit): AuthApi = r.create(AuthApi::class.java)
    @Provides @Singleton fun configApi(r: Retrofit): ConfigApi = r.create(ConfigApi::class.java)
    @Provides @Singleton fun addressApi(r: Retrofit): AddressApi = r.create(AddressApi::class.java)
    @Provides @Singleton fun orderApi(r: Retrofit): OrderApi = r.create(OrderApi::class.java)
    @Provides @Singleton fun trackingApi(r: Retrofit): TrackingApi = r.create(TrackingApi::class.java)
    @Provides @Singleton fun notificationApi(r: Retrofit): NotificationApi = r.create(NotificationApi::class.java)
    @Provides @Singleton fun supportApi(r: Retrofit): SupportApi = r.create(SupportApi::class.java)
    @Provides @Singleton fun reviewApi(r: Retrofit): ReviewApi = r.create(ReviewApi::class.java)

    private fun logging(level: HttpLoggingInterceptor.Level) = HttpLoggingInterceptor().apply {
        this.level = level
        redactHeader("Authorization")
    }
}
