package no.nav.ehandel.kanal

import java.sql.DriverManager
import java.time.Duration
import no.nav.ehandel.kanal.db.InboxDownloadStateTable
import no.nav.ehandel.kanal.services.inbox.DownloadBackoffPolicy
import no.nav.ehandel.kanal.services.inbox.InboxDownloadBackoff
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InboxDownloadBackoffTest {
    private val url = "jdbc:h2:mem:backoff;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
    private var now = 1_000_000L
    private lateinit var database: Database
    private lateinit var backoff: InboxDownloadBackoff

    @BeforeEach
    fun setUp() {
        Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/migration/common", "classpath:db/migration/h2")
            .cleanDisabled(false)
            .load()
            .apply { clean(); migrate() }
        database = Database.connect({ DriverManager.getConnection(url, "sa", "") })
        backoff = InboxDownloadBackoff(database, clockMs = { now }, jitter = { 0.0 })
    }

    @Test
    fun `policy follows the agreed steps and is capped at 30 minutes`() {
        (1..8).map { DownloadBackoffPolicy.delayFor(it, 0.0).toMinutes() } shouldBeEqualTo
            listOf(1L, 2L, 4L, 8L, 16L, 30L, 30L, 30L)
        DownloadBackoffPolicy.delayFor(6, 0.10) shouldBeEqualTo Duration.ofMinutes(30)
        DownloadBackoffPolicy.delayFor(1, 0.10) shouldBeEqualTo Duration.ofSeconds(66)
        DownloadBackoffPolicy.delayFor(1, 5.0) shouldBeEqualTo Duration.ofSeconds(66)
    }

    @Test
    fun `unknown message is attempted`() {
        backoff.shouldAttempt("1") shouldBe true
    }

    @Test
    fun `failed message is deferred until the next attempt time`() {
        backoff.recordFailure("1", RuntimeException("x")) shouldBeEqualTo 1
        backoff.shouldAttempt("1") shouldBe false
        now += Duration.ofSeconds(59).toMillis()
        backoff.shouldAttempt("1") shouldBe false
        now += Duration.ofSeconds(1).toMillis()
        backoff.shouldAttempt("1") shouldBe true
    }

    @Test
    fun `other messages are not affected by a failing message`() {
        backoff.recordFailure("1", RuntimeException("x"))
        backoff.shouldAttempt("2") shouldBe true
    }

    @Test
    fun `failures increase the backoff and survive a new instance`() {
        repeat(3) {
            backoff.recordFailure("1", RuntimeException("x")) shouldBeEqualTo it + 1
            now += Duration.ofHours(1).toMillis()
        }
        backoff.recordFailure("1", RuntimeException("x")) shouldBeEqualTo 4
        val restarted = InboxDownloadBackoff(database, clockMs = { now }, jitter = { 0.0 })
        restarted.shouldAttempt("1") shouldBe false
        now += Duration.ofMinutes(8).toMillis()
        restarted.shouldAttempt("1") shouldBe true
    }

    @Test
    fun `only the error type is stored`() {
        backoff.recordFailure("1", IllegalStateException("secret detail"))
        transaction(database) {
            InboxDownloadStateTable.selectAll().single()[InboxDownloadStateTable.lastError]
        } shouldBeEqualTo "IllegalStateException"
    }

    @Test
    fun `clear removes only the given message`() {
        backoff.recordFailure("1", RuntimeException("x"))
        backoff.recordFailure("2", RuntimeException("x"))
        backoff.clear("1")
        backoff.shouldAttempt("1") shouldBe true
        backoff.shouldAttempt("2") shouldBe false
    }

    @Test
    fun `fails closed when state cannot be read`() {
        val broken = InboxDownloadBackoff(Database.connect({ throw java.sql.SQLException("down") }), clockMs = { now })
        broken.shouldAttempt("1") shouldBe false
        broken.recordFailure("1", RuntimeException("x")) shouldBe null
    }
}
