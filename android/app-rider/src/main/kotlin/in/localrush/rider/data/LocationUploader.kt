package `in`.localrush.rider.data

import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.database.PendingLocationDao
import `in`.localrush.core.database.PendingLocationEntity
import `in`.localrush.core.network.dto.LocationAckDto
import `in`.localrush.core.network.dto.LocationBatchDto
import `in`.localrush.core.network.dto.LocationFixDto
import `in`.localrush.core.network.util.apiCall
import javax.inject.Inject
import javax.inject.Singleton

/** Transport abstraction so the replay logic can be unit-tested with a fake. */
interface LocationSink {
    suspend fun single(fix: LocationFixDto): AppResult<LocationAckDto>
    suspend fun batch(fixes: List<LocationFixDto>): AppResult<LocationBatchResultDto>
}

@Singleton
class RestLocationSink @Inject constructor(private val api: RiderApi) : LocationSink {
    override suspend fun single(fix: LocationFixDto) = apiCall { api.postLocation(fix) }
    override suspend fun batch(fixes: List<LocationFixDto>) = apiCall { api.postLocationBatch(LocationBatchDto(fixes)) }
}

sealed interface UploadOutcome {
    /** Sent live (and any queued fixes replayed). */
    data class Sent(val accepted: Boolean, val reason: String?, val replayed: Int) : UploadOutcome
    /** Offline/timeout: the fix was queued for later replay. */
    data class Queued(val queueSize: Int) : UploadOutcome
    /** Server rejected the payload for good (4xx): nothing kept. */
    data class Rejected(val error: AppError) : UploadOutcome
}

/**
 * Sends one fix, replaying anything queued in Room first (as a `{locations:[...]}` batch). Retryable
 * failures (no network, timeout, 5xx) enqueue the fix; permanent 4xx failures drop the poisoned queue.
 */
class LocationUploader(
    private val sink: LocationSink,
    private val dao: PendingLocationDao,
    private val batchSize: Int = BATCH_SIZE,
    private val maxQueued: Int = MAX_QUEUED,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun upload(fix: LocationFixDto): UploadOutcome {
        val queued = dao.oldest(batchSize)
        if (queued.isEmpty()) {
            return when (val r = sink.single(fix)) {
                is `in`.localrush.core.common.result.Result.Ok -> UploadOutcome.Sent(r.value.accepted, r.value.reason, 0)
                is `in`.localrush.core.common.result.Result.Err -> onFailure(r.error, fix)
            }
        }
        val payload = queued.map { it.toDto() } + fix
        return when (val r = sink.batch(payload)) {
            is `in`.localrush.core.common.result.Result.Ok -> {
                dao.delete(queued.map { it.id })
                val last = r.value.results.lastOrNull()
                UploadOutcome.Sent(last?.accepted ?: true, last?.reason, queued.size)
            }
            is `in`.localrush.core.common.result.Result.Err -> {
                if (!r.error.isRetryable) dao.delete(queued.map { it.id }) // poison batch: never block the live stream
                onFailure(r.error, fix)
            }
        }
    }

    /** Replays the whole queue in batches without a new fix (e.g. when connectivity returns). */
    suspend fun flush(): Int {
        var total = 0
        while (true) {
            val queued = dao.oldest(batchSize)
            if (queued.isEmpty()) return total
            when (val r = sink.batch(queued.map { it.toDto() })) {
                is `in`.localrush.core.common.result.Result.Ok -> { dao.delete(queued.map { it.id }); total += queued.size }
                is `in`.localrush.core.common.result.Result.Err -> {
                    if (!r.error.isRetryable) { dao.delete(queued.map { it.id }); total += queued.size } else return total
                }
            }
        }
    }

    private suspend fun onFailure(error: AppError, fix: LocationFixDto): UploadOutcome {
        if (!error.isRetryable) return UploadOutcome.Rejected(error)
        dao.insert(fix.toEntity(now()))
        dao.trim(maxQueued)
        return UploadOutcome.Queued(dao.oldest(maxQueued).size)
    }

    companion object {
        const val BATCH_SIZE = 100 // backend caps a batch at the last 100 points
        const val MAX_QUEUED = 1500

        fun PendingLocationEntity.toDto() = LocationFixDto(orderId, latitude, longitude, accuracy, speedKmph, bearing, timestamp)
        fun LocationFixDto.toEntity(recordedAt: Long) = PendingLocationEntity(
            orderId = orderId, latitude = latitude, longitude = longitude, accuracy = accuracy, speedKmph = speed, bearing = bearing,
            timestamp = timestamp, recordedAt = recordedAt,
        )
    }
}
