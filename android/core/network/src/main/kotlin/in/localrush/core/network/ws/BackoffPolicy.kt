package `in`.localrush.core.network.ws

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Exponential backoff with full jitter: delay = random(0, min(cap, base * 2^attempt)).
 * Pure so it can be unit-tested.
 */
class BackoffPolicy(
    val baseMs: Long = 1_000,
    val capMs: Long = 30_000,
    val maxAttempts: Int = Int.MAX_VALUE,
    private val random: Random = Random.Default,
) {
    fun delayFor(attempt: Int): Long {
        val exp = baseMs * 2.0.pow(attempt.coerceIn(0, 20)).toLong()
        val ceiling = min(capMs, exp)
        // Keep at least half of the ceiling so we don't hammer the server on retry 0.
        val floor = ceiling / 2
        return floor + if (ceiling > floor) random.nextLong(ceiling - floor) else 0
    }

    fun shouldRetry(attempt: Int): Boolean = attempt < maxAttempts
}
