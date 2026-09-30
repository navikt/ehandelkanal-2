package no.nav.ehandel.kanal

import no.nav.ehandel.kanal.camel.routes.ebasysConnectionTest
import no.nav.ehandel.kanal.camel.routes.ebasysInbound
import no.nav.ehandel.kanal.camel.routes.ebasysInboundUnknownFiles
import no.nav.ehandel.kanal.camel.routes.mqInbound
import org.amshove.kluent.shouldBeEqualTo
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.DefaultRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class EndpointUriTest {

    private val context = DefaultCamelContext(DefaultRegistry().apply { bind("mqConnectionFactory", mqConnectionFactory) })

    private fun String.withScheme(scheme: String) = replaceFirst(Regex("^s?ftp://"), "$scheme://")

    @AfterEach
    fun tearDown() {
        context.stop()
    }

    @Test
    fun `ebasys producer endpoints accept all parameters for ftp and sftp`() {
        listOf("ftp", "sftp").forEach { scheme ->
            listOf(ebasysInbound, ebasysInboundUnknownFiles).forEach { uri ->
                context.getEndpoint(uri.withScheme(scheme)).createProducer()
            }
        }
    }

    @Test
    fun `ebasys connection test consumer accepts all parameters for ftp and sftp`() {
        listOf("ftp", "sftp").forEach { scheme ->
            context.getEndpoint(ebasysConnectionTest.withScheme(scheme)).createConsumer { }
        }
    }

    @Test
    fun `mq endpoint resolves connection factory from registry`() {
        context.getEndpoint(mqInbound).endpointUri.startsWith("jms://queue:") shouldBeEqualTo true
    }
}
