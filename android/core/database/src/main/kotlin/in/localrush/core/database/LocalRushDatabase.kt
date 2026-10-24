package `in`.localrush.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * App-local cache. Each app gets its own database file (see [DatabaseModule]). Add entities here
 * and bump [VERSION]; use destructive migration during development only.
 *
 * Sample: [CachedJsonEntity] is a generic key→JSON cache (home feed, config, last orders) so feature
 * engineers can cache responses without a dedicated table; [PendingLocationEntity] is the rider's
 * offline location queue for `POST rider/location/ {locations:[...]}` replay.
 */
@Database(
    entities = [CachedJsonEntity::class, PendingLocationEntity::class, RecentSearchEntity::class],
    version = LocalRushDatabase.VERSION,
    exportSchema = true,
)
abstract class LocalRushDatabase : RoomDatabase() {
    abstract fun cachedJsonDao(): CachedJsonDao
    abstract fun pendingLocationDao(): PendingLocationDao
    abstract fun recentSearchDao(): RecentSearchDao

    companion object {
        const val VERSION = 1
    }
}
