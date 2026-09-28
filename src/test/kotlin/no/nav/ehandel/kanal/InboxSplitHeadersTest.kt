package no.nav.ehandel.kanal

import java.util.concurrent.Executors
import no.nav.ehandel.kanal.common.constants.CamelHeader
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContainSame
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.impl.DefaultCamelContext
import org.junit.After
import org.junit.Test

class InboxSplitHeadersTest {

    private val camelContext = DefaultCamelContext()
    private val threadPool = Executors.newFixedThreadPool(6)

    @After
    fun tearDown() {
        camelContext.stop()
        threadPool.shutdown()
    }

    private fun inbox(vararg msgNos: Int) = msgNos.joinToString(",", """{"meldinger":[""", "]}") { no ->
        """{"msgNo":$no,"messageUUID":"uuid-$no","direction":"IN","delivered":null}"""
    }

    private fun splitAndExtract(json: String): MockEndpoint {
        camelContext.addRoutes(object : RouteBuilder() {
            override fun configure() {
                from("direct:inbox")
                    .split()
                        .jsonpath("$.meldinger[*]")
                        .streaming()
                        .executorService(threadPool)
                        .to("direct:inboxQueue")
                    .end()

                from("direct:inboxQueue")
                    .setHeader(CamelHeader.TRACE_ID, jsonpath("$.messageUUID"))
                    .setHeader(CamelHeader.MSG_NO, jsonpath("$.msgNo"))
                    .to("mock:result")
            }
        })
        camelContext.start()
        val result = camelContext.getEndpoint("mock:result", MockEndpoint::class.java)
        camelContext.createProducerTemplate().sendBody("direct:inbox", json)
        return result
    }

    private fun MockEndpoint.headers(name: String): List<String> =
        receivedExchanges.map { it.getIn().getHeader(name, String::class.java) }

    @Test
    fun `single message in inbox is split into one exchange with msgNo and uuid`() {
        val result = splitAndExtract(inbox(1))

        result.headers(CamelHeader.MSG_NO) shouldContainSame listOf("1")
        result.headers(CamelHeader.TRACE_ID) shouldContainSame listOf("uuid-1")
    }

    @Test
    fun `several messages in inbox are split into one exchange each`() {
        val result = splitAndExtract(inbox(1, 2))

        result.headers(CamelHeader.MSG_NO) shouldContainSame listOf("1", "2")
        result.headers(CamelHeader.TRACE_ID) shouldContainSame listOf("uuid-1", "uuid-2")
    }

    @Test
    fun `empty inbox produces no exchanges`() {
        val result = splitAndExtract(inbox())

        result.receivedCounter shouldBeEqualTo 0
    }
}
