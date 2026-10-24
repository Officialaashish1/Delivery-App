package `in`.localrush.rider

import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.database.PendingLocationDao
import `in`.localrush.core.database.PendingLocationEntity
import `in`.localrush.core.network.dto.LocationAckDto
import `in`.localrush.core.network.dto.LocationFixDto
import `in`.localrush.rider.data.LocationBatchResultDto
import `in`.localrush.rider.data.LocationResultDto
import `in`.localrush.rider.data.LocationSink
import `in`.localrush.rider.data.LocationUploader
import `in`.localrush.rider.data.UploadOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationUploaderTest {

    private class FakeDao : PendingLocationDao {
        val rows = linkedMapOf<Long, PendingLocationEntity>()
        private var nextId = 1L
        override suspend fun insert(entity: PendingLocationEntity): Long { val id = nextId++; rows[id] = entity.copy(id = id); return id }
        override suspend fun oldest(limit: Int): List<PendingLocationEntity> = rows.values.sortedBy { it.recordedAt }.take(limit)
        override fun count(): Flow<Int> = flowOf(rows.size)
        override suspend fun delete(ids: List<Long>) { ids.forEach { rows.remove(it) } }
        override suspend fun trim(keep: Int) { rows.values.sortedByDescending { it.recordedAt }.drop(keep).forEach { rows.remove(it.id) } }
        override suspend fun clear() = rows.clear()
    }

    private class FakeSink : LocationSink {
        var online = true
        val singles = mutableListOf<LocationFixDto>()
        val batches = mutableListOf<List<LocationFixDto>>()
        var batchError: AppError? = null
        override suspend fun single(fix: LocationFixDto): AppResult<LocationAckDto> {
            if (!online) return Result.Err(AppError.Network())
            singles += fix; return Result.Ok(LocationAckDto(accepted = true, orderTracked = fix.orderId != null))
        }
        override suspend fun batch(fixes: List<LocationFixDto>): AppResult<LocationBatchResultDto> {
            batchError?.let { return Result.Err(it) }
            if (!online) return Result.Err(AppError.Network())
            batches += fixes; return Result.Ok(LocationBatchResultDto(fixes.map { LocationResultDto(accepted = true) }))
        }
    }

    private fun fix(i: Int, orderId: String? = null) = LocationFixDto(orderId, 12.9 + i * 0.001, 77.5, 10f, 20f, 90f, "2026-10-24T10:00:0${i}Z")

    @Test fun `online fix is sent live as a single point`() = runTest {
        val sink = FakeSink(); val dao = FakeDao()
        val out = LocationUploader(sink, dao).upload(fix(0, "o1"))
        assertEquals(UploadOutcome.Sent(accepted = true, reason = null, replayed = 0), out)
        assertEquals(1, sink.singles.size)
        assertEquals("o1", sink.singles.single().orderId)
        assertTrue(dao.rows.isEmpty())
    }

    @Test fun `offline fixes are queued and replayed as one batch when back online`() = runTest {
        var now = 1_000L
        val sink = FakeSink().apply { online = false }; val dao = FakeDao()
        val uploader = LocationUploader(sink, dao, now = { now++ })
        assertEquals(UploadOutcome.Queued(1), uploader.upload(fix(0)))
        assertEquals(UploadOutcome.Queued(2), uploader.upload(fix(1)))
        assertEquals(UploadOutcome.Queued(3), uploader.upload(fix(2)))
        assertEquals(3, dao.rows.size)
        assertTrue(sink.singles.isEmpty())

        sink.online = true
        val out = uploader.upload(fix(3, "o1"))
        assertEquals(UploadOutcome.Sent(accepted = true, reason = null, replayed = 3), out)
        assertEquals(1, sink.batches.size)
        assertEquals(listOf(fix(0), fix(1), fix(2), fix(3, "o1")), sink.batches.single()) // oldest first, live fix last
        assertTrue(dao.rows.isEmpty())
        assertTrue(sink.singles.isEmpty())
    }

    @Test fun `queue is bounded`() = runTest {
        var now = 0L
        val sink = FakeSink().apply { online = false }; val dao = FakeDao()
        val uploader = LocationUploader(sink, dao, maxQueued = 5, now = { now++ })
        repeat(8) { uploader.upload(fix(it)) }
        assertEquals(5, dao.rows.size)
        assertEquals(fix(3).timestamp, dao.oldest(10).first().timestamp) // oldest dropped first
    }

    @Test fun `server rejection of a live point is not queued`() = runTest {
        val dao = FakeDao()
        val sink = object : LocationSink {
            override suspend fun single(fix: LocationFixDto): AppResult<LocationAckDto> = Result.Err(AppError.Api("validation_error", "bad", 400))
            override suspend fun batch(fixes: List<LocationFixDto>): AppResult<LocationBatchResultDto> = Result.Ok(LocationBatchResultDto())
        }
        val out = LocationUploader(sink, dao).upload(fix(0))
        assertTrue(out is UploadOutcome.Rejected)
        assertTrue(dao.rows.isEmpty())
    }

    @Test fun `a poisoned batch is dropped so live tracking resumes`() = runTest {
        val sink = FakeSink().apply { online = false }; val dao = FakeDao()
        val uploader = LocationUploader(sink, dao)
        uploader.upload(fix(0)); uploader.upload(fix(1))
        sink.online = true
        sink.batchError = AppError.Api("validation_error", "bad batch", 400)
        val out = uploader.upload(fix(2))
        assertTrue(out is UploadOutcome.Rejected)
        assertTrue("poisoned queue must be cleared", dao.rows.isEmpty())
        sink.batchError = null
        assertEquals(UploadOutcome.Sent(true, null, 0), uploader.upload(fix(3)))
    }

    @Test fun `5xx while replaying keeps the queue and enqueues the new fix`() = runTest {
        val sink = FakeSink().apply { online = false }; val dao = FakeDao()
        val uploader = LocationUploader(sink, dao)
        uploader.upload(fix(0))
        sink.online = true
        sink.batchError = AppError.Http(503, "down")
        assertEquals(UploadOutcome.Queued(2), uploader.upload(fix(1)))
        assertEquals(2, dao.rows.size)
    }

    @Test fun `flush replays in batches of batchSize and reports the count`() = runTest {
        var now = 0L
        val sink = FakeSink().apply { online = false }; val dao = FakeDao()
        val uploader = LocationUploader(sink, dao, batchSize = 2, now = { now++ })
        repeat(5) { uploader.upload(fix(it)) }
        sink.online = true
        assertEquals(5, uploader.flush())
        assertEquals(listOf(2, 2, 1), sink.batches.map { it.size })
        assertTrue(dao.rows.isEmpty())
        assertEquals(0, uploader.flush())
    }
}
