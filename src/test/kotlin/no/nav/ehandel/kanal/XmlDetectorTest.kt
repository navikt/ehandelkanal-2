package no.nav.ehandel.kanal

import no.nav.ehandel.kanal.camel.processors.InboundSbdhMetaDataExtractor
import no.nav.ehandel.kanal.camel.processors.InboundSbdhMetaDataExtractor.CAMEL_XML_PROPERTY
import org.apache.camel.EndpointInject
import org.apache.camel.Produce
import org.apache.camel.ProducerTemplate
import org.apache.camel.RoutesBuilder
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.impl.JndiRegistry
import org.apache.camel.test.junit4.CamelTestSupport
import org.junit.Test

class XmlDetectorTest : CamelTestSupport() {

    @Produce(uri = "direct:start")
    private lateinit var producer: ProducerTemplate
    @EndpointInject(uri = "mock:result")
    private lateinit var result: MockEndpoint

    @Test
    fun `not XML`() {
        result.expectedMessageCount(1)
        result.expectedPropertyReceived(CAMEL_XML_PROPERTY, "false")
        producer.sendBody("NONXML")
        result.assertIsSatisfied()
    }

    @Test
    fun `incomplete XML`() {
        result.expectedMessageCount(1)
        result.expectedPropertyReceived(CAMEL_XML_PROPERTY, "false")
        producer.sendBody("<roten><barna/>")
        result.assertIsSatisfied()
    }

    @Test
    fun `empty body`() {
        result.expectedMessageCount(1)
        result.expectedPropertyReceived(CAMEL_XML_PROPERTY, "false")
        producer.sendBody("")
        result.assertIsSatisfied()
    }

    @Test
    fun `null body`() {
        result.expectedMessageCount(1)
        result.expectedPropertyReceived(CAMEL_XML_PROPERTY, "false")
        producer.sendBody(null)
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

    override fun createRegistry(): JndiRegistry {
        return super.createRegistry().apply {
            bind("inboundSbdhExtractor", InboundSbdhMetaDataExtractor)
        }
    }
}
