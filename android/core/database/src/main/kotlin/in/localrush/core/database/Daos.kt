package `in`.localrush.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedJsonDao {
    @Query("SELECT * FROM cached_json WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): CachedJsonEntity?

    @Query("SELECT * FROM cached_json WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<CachedJsonEntity?>

    @Upsert
    suspend fun put(entity: CachedJsonEntity)

    @Query("DELETE FROM cached_json WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM cached_json WHERE `key` LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)

    @Query("DELETE FROM cached_json WHERE updatedAt < :olderThan")
    suspend fun evict(olderThan: Long)

    @Query("DELETE FROM cached_json")
    suspend fun clear()
}

@Dao
interface PendingLocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingLocationEntity): Long

    @Query("SELECT * FROM pending_locations ORDER BY recordedAt ASC LIMIT :limit")
    suspend fun oldest(limit: Int = 50): List<PendingLocationEntity>

    @Query("SELECT COUNT(*) FROM pending_locations")
    fun count(): Flow<Int>

    @Query("DELETE FROM pending_locations WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    /** Keep the queue bounded (e.g. last 2 hours at 5s = 1440 rows). */
    @Query("DELETE FROM pending_locations WHERE id NOT IN (SELECT id FROM pending_locations ORDER BY recordedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int = 1500)

    @Query("DELETE FROM pending_locations")
    suspend fun clear()
}

@Dao
interface RecentSearchDao {
    @Query("SELECT * FROM recent_searches ORDER BY searchedAt DESC LIMIT :limit")
    fun recent(limit: Int = 10): Flow<List<RecentSearchEntity>>

    @Upsert
    suspend fun add(entity: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE `query` = :query")
    suspend fun remove(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clear()
}
