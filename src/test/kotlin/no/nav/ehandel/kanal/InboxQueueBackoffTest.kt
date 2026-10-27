package no.nav.ehandel.kanal

import java.net.SocketTimeoutException
import java.sql.DriverManager
import java.time.Duration
import no.nav.ehandel.kanal.camel.routes.AccessPoint
import no.nav.ehandel.kanal.camel.routes.INBOUND_EHF
import no.nav.ehandel.kanal.camel.routes.INBOX_POLLER
import no.nav.ehandel.kanal.camel.routes.INBOX_QUEUE
import no.nav.ehandel.kanal.services.inbox.InboxDownloadBackoff
import no.nav.ehandel.kanal.services.inbox.inboxDownloadBackoff
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.apache.camel.CamelContext
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.DefaultRegistry
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StubAccessPointClient {
    var downloads = 0
    var reads = 0
    var failDownload = true

    fun getInboxMessageHeaders(exchange: Exchange) = """{"meldinger":[]}"""

    fun downloadMessagePayload(exchange: Exchange, msgNo: String): String {
        downloads++
        if (failDownload) throw SocketTimeoutException("timeout")
        return "<Invoice/>"
    }

    fun markMessageAsRead(msgNo: String): String {
        reads++
        return "OK"
    }
}

class InboxQueueBackoffTest {
    private val url = "jdbc:h2:mem:queuebackoff;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
    private val message = """{"msgNo":1,"messageUUID":"11111111-1111-1111-1111-111111111111"}"""
    private val client = StubAccessPointClient()
    private var now = 1_000_000L
    private lateinit var camelContext: CamelContext
    private lateinit var inbound: MockEndpoint
    private lateinit var previous: InboxDownloadBackoff

    @BeforeEach
    fun setUp() {
        Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/migration/common", "classpath:db/migration/h2")
            .cleanDisabled(false)
            .load()
            .apply { clean(); migrate() }
        val database = Database.connect({ DriverManager.getConnection(url, "sa", "") })
        previous = inboxDownloadBackoff
        inboxDownloadBackoff = InboxDownloadBackoff(database, clockMs = { now }, jitter = { 0.0 })

        camelContext = DefaultCamelContext(DefaultRegistry().apply { bind("accessPointClient", client) }).apply {
            addRoutes(AccessPoint)
            addRoutes(object : RouteBuilder() {
                override fun configure() {
                    from(INBOUND_EHF.uri).to("mock:inbound")
                }
            })
            removeRouteDefinition(getRouteDefinition(INBOX_POLLER.id))
            start()
        }
        inbound = camelContext.getEndpoint("mock:inbound", MockEndpoint::class.java)
    }

    @AfterEach
    fun tearDown() {
        camelContext.stop()
        inboxDownloadBackoff = previous
    }

    private fun send() = camelContext.createProducerTemplate().sendBody(INBOX_QUEUE.uri, message)

    @Test
    fun `failed download is not repeated before the backoff has passed`() {
        send()
        client.downloads shouldBeEqualTo 1
        inboxDownloadBackoff.shouldAttempt("1") shouldBe false

        repeat(5) { send() }
        client.downloads shouldBeEqualTo 1
        client.reads shouldBeEqualTo 0
        inbound.receivedCounter shouldBeEqualTo 0
    }

    @Test
    fun `message is downloaded and acknowledged when the backoff has passed and clears state`() {
        send()
        client.failDownload = false
        now += Duration.ofMinutes(1).toMillis()

        send()

        client.downloads shouldBeEqualTo 2
        client.reads shouldBeEqualTo 1
        inbound.receivedCounter shouldBeEqualTo 1
        inboxDownloadBackoff.shouldAttempt("1") shouldBe true
    }

    @Test
    fun `successful download of a new message is unaffected`() {
        client.failDownload = false

        send()

        client.downloads shouldBeEqualTo 1
        client.reads shouldBeEqualTo 1
        inbound.receivedCounter shouldBeEqualTo 1
    }
}
