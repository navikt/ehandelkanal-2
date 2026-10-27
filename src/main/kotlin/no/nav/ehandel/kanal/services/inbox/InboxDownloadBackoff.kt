package no.nav.ehandel.kanal.services.inbox

import java.time.Duration
import kotlin.random.Random
import mu.KotlinLogging
import no.nav.ehandel.kanal.Metrics
import no.nav.ehandel.kanal.db.InboxDownloadStateTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

private val LOGGER = KotlinLogging.logger { }

object DownloadBackoffPolicy {
    const val ALERT_THRESHOLD = 5
    private const val MAX_JITTER = 0.10
    private val steps = listOf(1L, 2L, 4L, 8L, 16L, 30L).map(Duration::ofMinutes)
    val maxDelay: Duration = steps.last()

    fun delayFor(failureCount: Int, jitter: Double): Duration {
        require(failureCount >= 1) { "failureCount must be at least 1" }
        val base = steps[minOf(failureCount, steps.size) - 1]
        val withJitter = Duration.ofMillis((base.toMillis() * (1 + jitter.coerceIn(0.0, MAX_JITTER))).toLong())
        return minOf(withJitter, maxDelay)
    }
}

class InboxDownloadBackoff(
    private val database: Database? = null,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val jitter: () -> Double = { Random.nextDouble(0.0, 0.10) }
) {

    /** Fails closed: if the retry state cannot be read the message is not downloaded in this poll. */
    fun shouldAttempt(msgNo: String): Boolean = try {
        val nextAttemptMs = transaction(database) {
            InboxDownloadStateTable
                .selectAll().where { InboxDownloadStateTable.msgNo eq msgNo }
                .singleOrNull()
                ?.get(InboxDownloadStateTable.nextAttemptMs)
        }
        val due = nextAttemptMs == null || clockMs() >= nextAttemptMs
        if (!due) Metrics.downloadsDeferred.inc()
        due
    } catch (e: Exception) {
        LOGGER.warn(e) { "Could not read download state for MsgNo $msgNo - skipping download in this poll" }
        Metrics.downloadStateErrors.inc()
        false
    }

    fun recordFailure(msgNo: String, error: Throwable): Int? = try {
        val now = clockMs()
        val errorType = error.javaClass.simpleName.take(128)
        val failureCount = transaction(database) {
            val previous = InboxDownloadStateTable
                .selectAll().where { InboxDownloadStateTable.msgNo eq msgNo }
                .singleOrNull()
                ?.get(InboxDownloadStateTable.failureCount) ?: 0
            val count = previous + 1
            val nextAttempt = now + DownloadBackoffPolicy.delayFor(count, jitter()).toMillis()
            if (previous == 0) {
                InboxDownloadStateTable.insert {
                    it[InboxDownloadStateTable.msgNo] = msgNo
                    it[failureCount] = count
                    it[nextAttemptMs] = nextAttempt
                    it[lastError] = errorType
                    it[updatedMs] = now
                }
            } else {
                InboxDownloadStateTable.update({ InboxDownloadStateTable.msgNo eq msgNo }) {
                    it[failureCount] = count
                    it[nextAttemptMs] = nextAttempt
                    it[lastError] = errorType
                    it[updatedMs] = now
                }
            }
            count
        }
        Metrics.downloadsFailed.inc()
        if (failureCount == DownloadBackoffPolicy.ALERT_THRESHOLD) Metrics.downloadAlertThresholdReached.inc()
        val delay = DownloadBackoffPolicy.delayFor(failureCount, 0.0)
        LOGGER.warn {
            "Download of MsgNo $msgNo failed ($errorType), failure $failureCount - " +
                "next attempt in about ${delay.toMinutes()} min"
        }
        failureCount
    } catch (e: Exception) {
        LOGGER.error(e) { "Could not record download failure for MsgNo $msgNo" }
        Metrics.downloadStateErrors.inc()
        null
    }

    fun clear(msgNo: String) {
        try {
            transaction(database) {
                InboxDownloadStateTable.deleteWhere { InboxDownloadStateTable.msgNo eq msgNo }
            }
        } catch (e: Exception) {
            LOGGER.warn(e) { "Could not clear download state for MsgNo $msgNo" }
            Metrics.downloadStateErrors.inc()
        }
    }
}

internal var inboxDownloadBackoff = InboxDownloadBackoff()
