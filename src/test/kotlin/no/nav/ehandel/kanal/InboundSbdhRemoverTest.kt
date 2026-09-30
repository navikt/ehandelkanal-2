package no.nav.ehandel.kanal

import no.nav.ehandel.kanal.camel.processors.InboundSbdhMetaDataExtractor
import no.nav.ehandel.kanal.common.extensions.getBody
import no.nav.ehandel.kanal.helpers.getResource
import no.nav.ehandel.kanal.helpers.shouldBeXmlEqualTo
import org.apache.camel.EndpointInject
import org.apache.camel.Produce
import org.apache.camel.ProducerTemplate
import org.apache.camel.RoutesBuilder
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.spi.Registry
import org.apache.camel.test.junit5.CamelTestSupport
import org.junit.jupiter.api.Test

class InboundSbdhRemoverTest : CamelTestSupport() {

    @Produce("direct:start")
    private lateinit var producer: ProducerTemplate
    @EndpointInject("mock:result")
    private lateinit var result: MockEndpoint

    @Test
    fun `valid file should have SBDH removed`() {
        result.expectedMessageCount(1)
        val input: String = "/inbound/inbound-catalogue-ok.xml".getResource()
        producer.sendBody(input)
        val exchange = result.assertExchangeReceived(0)
        exchange.getBody<String>() shouldBeXmlEqualTo "/inbound/inbound-catalogue-ok-sbdh-removed.xml".getResource()
        result.assertIsSatisfied()
    }

    override fun createRouteBuilder(): RoutesBuilder {
        return object : RouteBuilder() {
            override fun configure() {
                from("direct:start")
                    .bean("inboundSbdhExtractor")
                    .to("mock:result")
            }
        }
    }

    override fun bindToRegistry(registry: Registry) {
        registry.bind("inboundSbdhExtractor", InboundSbdhMetaDataExtractor)
    }
}
