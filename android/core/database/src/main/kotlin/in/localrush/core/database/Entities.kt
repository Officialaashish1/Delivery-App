package `in`.localrush.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Generic JSON cache: key = e.g. "home:12.97,77.59", payload = raw JSON, updatedAt = epoch ms. */
@Entity(tableName = "cached_json")
data class CachedJsonEntity(
    @PrimaryKey val key: String,
    val payload: String,
    val updatedAt: Long,
    val etag: String? = null,
)

/** Rider location fixes captured offline, replayed in batches when connectivity returns. */
@Entity(tableName = "pending_locations", indices = [Index("recordedAt")])
data class PendingLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String?,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val speedKmph: Float?,
    val bearing: Float?,
    /** ISO-8601 */
    val timestamp: String,
    val recordedAt: Long,
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long,
)
