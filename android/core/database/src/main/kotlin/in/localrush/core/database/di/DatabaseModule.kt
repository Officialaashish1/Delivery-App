package `in`.localrush.core.database.di

import android.content.Context
import androidx.room.Room
import `in`.localrush.core.database.CachedJsonDao
import `in`.localrush.core.database.LocalRushDatabase
import `in`.localrush.core.database.PendingLocationDao
import `in`.localrush.core.database.RecentSearchDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): LocalRushDatabase =
        Room.databaseBuilder(context, LocalRushDatabase::class.java, "localrush.db")
            // Cache-only data: safe to drop on schema change. Replace with real migrations once tables hold user data.
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun cachedJsonDao(db: LocalRushDatabase): CachedJsonDao = db.cachedJsonDao()
    @Provides fun pendingLocationDao(db: LocalRushDatabase): PendingLocationDao = db.pendingLocationDao()
    @Provides fun recentSearchDao(db: LocalRushDatabase): RecentSearchDao = db.recentSearchDao()
}
