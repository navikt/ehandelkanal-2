package no.nav.ehandel.kanal

import com.github.tomakehurst.wiremock.client.BasicCredentials
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.http.ContentTypeHeader
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.nhaarman.mockitokotlin2.mock
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.engine.ApplicationEngine
import java.util.concurrent.TimeUnit
import no.nav.ehandel.kanal.camel.processors.InboundSbdhMetaDataExtractor
import no.nav.ehandel.kanal.camel.routes.ACCESS_POINT_CLIENT
import no.nav.ehandel.kanal.camel.routes.ACCESS_POINT_READ
import no.nav.ehandel.kanal.camel.routes.INBOUND_EHF
import no.nav.ehandel.kanal.camel.routes.INBOUND_FTP_TEST_ROUTE
import no.nav.ehandel.kanal.camel.routes.INBOUND_LOGGER_BEAN
import no.nav.ehandel.kanal.camel.routes.INBOUND_SBDH_EXTRACTOR
import no.nav.ehandel.kanal.camel.routes.INBOX_QUEUE
import no.nav.ehandel.kanal.db.Database
import no.nav.ehandel.kanal.legalarchive.LEGAL_ARCHIVE_CAMEL_HEADER
import org.apache.camel.builder.AdviceWithRouteBuilder
import org.apache.camel.builder.NotifyBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.h2.tools.DeleteDbFiles
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test

private const val juridiskLoggUrl = "/juridisklogg/api/rest/logg"
private const val inboxCountUrl = "/vefasrest/inbox/count"
private const val inboxMessagesUrl = "/vefasrest/inbox/"
private const val inboxReadUrl = "/vefasrest/inbox/1/read"
private const val inboxMessageXmlDocUrl = "/vefasrest/messages/1/xml-document"

private val server: ApplicationEngine = mock()
private val camelContext = configureCamelContext(defaultRegistry()).apply {
    routeDefinitions[0].adviceWith(this, object : AdviceWithRouteBuilder() {
        override fun configure() {
            mockEndpointsAndSkip("^(jms|ftp).*")
            mockEndpoints("^(?!(jms|ftp)).*")
        }
    })
    removeRouteDefinition(getRouteDefinition(INBOUND_FTP_TEST_ROUTE))
}

private fun getMockEndpoint(name: String): MockEndpoint = camelContext.getEndpoint(name, MockEndpoint::class.java)

class InboundIT {
    private val accessPointClient = getMockEndpoint("mock:$ACCESS_POINT_CLIENT")
    private val inboxQueue = getMockEndpoint("mock:${INBOX_QUEUE.uri.substringBefore("?")}")
    private val inboundRoute = getMockEndpoint("mock:${INBOUND_EHF.uri}")
    private val inboundLogger = getMockEndpoint("mock:$INBOUND_LOGGER_BEAN")
    private val inboundSbdhMetaDataExtractor = getMockEndpoint("mock:$INBOUND_SBDH_EXTRACTOR")
    private val ebasys = getMockEndpoint("mock:ftp:localhost:20000/ftpeFaktura")
    private val ebasysUnknownFiles = getMockEndpoint("mock:ftp:localhost:20000/ftpeFaktura/manuellBehandling")
    private val inboundMq = getMockEndpoint("mock:jms:queue:IN")
    private val fileAreaCatalogue = getMockEndpoint("mock:file:./efakturaavtale/katalog")
    private val mockEndpoints = listOf(
        accessPointClient, inboxQueue, inboundRoute,
        ebasys, ebasysUnknownFiles, inboundMq, fileAreaCatalogue
    )

    @Before
    fun setUp() {
        camelContext.start()
    }

    @After
    fun tearDown() {
        verifyAccessPointRequests()
        wireMockRule.resetRequests()
        camelContext.stop()
        DeleteDbFiles.execute("./", "integrationtestdb", true)
    }

    @Test
    fun `valid invoice`() {
        setUpStubs("message-faktura-invoice-ok.xml")
        setUpInboundValidExpectations()
        ebasys.run {
            expectedMessageCount(1) // Expect that message is sent to Ebasys
            expectedHeaderReceived(
                LEGAL_ARCHIVE_CAMEL_HEADER,
                1
            ) // Expect that the message has legal archive header set
        }
        NotifyBuilder(camelContext).wereSentTo(ACCESS_POINT_READ.uri).whenExactlyCompleted(1).create()
            .matches(3, TimeUnit.SECONDS)
        verify(exactly(1), postRequestedFor(urlEqualTo(juridiskLoggUrl)))
        assertMockEndpointsSatisfied()
    }

    @Test
    fun `valid credit note`() {
        setUpStubs("message-faktura-creditnote-ok.xml")
        setUpInboundValidExpectations()
        ebasys.run {
            expectedMessageCount(1) // Expect that message is sent to Ebasys
            expectedHeaderReceived(
                LEGAL_ARCHIVE_CAMEL_HEADER,
                1
            ) // Expect that the message has legal archive header set
        }
        NotifyBuilder(camelContext).wereSentTo(ACCESS_POINT_READ.uri).whenExactlyCompleted(1).create()
            .matches(3, TimeUnit.SECONDS)
        verify(exactly(1), postRequestedFor(urlEqualTo(juridiskLoggUrl)))
        assertMockEndpointsSatisfied()
    }

    @Test
    fun `valid catalogue with size smaller than set limit`() {
        setUpStubs("message-faktura-catalogue-small-ok.xml")
        setUpInboundValidExpectations()
        inboundMq.expectedMessageCount(1) // Expect that the message is sent to the inbound MQ queue

        NotifyBuilder(camelContext).fromRoute(ACCESS_POINT_READ.id).whenExactlyCompleted(1).create()
            .matches(3, TimeUnit.SECONDS)
        verify(exactly(0), postRequestedFor(urlEqualTo(juridiskLoggUrl)))
        assertMockEndpointsSatisfied()
    }

    @Test
    @Ignore // TODO
    fun `valid catalogue with size larger than set limit`() {
        setUpStubs("message-faktura-catalogue-large-ok.xml")
        setUpInboundValidExpectations()
        fileAreaCatalogue.expectedMessageCount(1) // Expect that the message is put on the file area

        NotifyBuilder(camelContext).fromRoute(ACCESS_POINT_READ.id).whenExactlyCompleted(1).create()
            .matches(3, TimeUnit.SECONDS)
        verify(exactly(0), postRequestedFor(urlEqualTo(juridiskLoggUrl)))
        assertMockEndpointsSatisfied()
    }

    @Test
    fun `invalid message with not well formed XML`() {
        setUpStubs("message-not-well-formed-xml-invalid.xml")
        setUpAccessPointExpectations()
        ebasysUnknownFiles.run {
            expectedPropertyReceived(
                InboundSbdhMetaDataExtractor.CAMEL_XML_PROPERTY,
                false
            ) // Expect that the XMLDetector has set the property
            expectedMessageCount(1) // Expect that the message is sent to Ebasys (manual processing folder)
        }
        NotifyBuilder(camelContext).fromRoute(INBOUND_EHF.id).whenExactlyCompleted(1).create()
            .matches(3, TimeUnit.SECONDS)
        verify(exactly(0), postRequestedFor(urlEqualTo(juridiskLoggUrl)))
        assertMockEndpointsSatisfied()
    }

    @Test
    fun `invalid message with unknown document type`() {
        setUpStubs("message-unknown-document-type-invalid.xml")
        setUpInboundValidExpectations()
        ebasysUnknownFiles.expectedMessageCount(1) // Expect that the message is sent to Ebasys (manual processing folder)

        NotifyBuilder(camelContext).fromRoute(ACCESS_POINT_READ.id).whenExactlyCompleted(1).create()
            .matches(3, TimeUnit.SECONDS)
        verify(exactly(0), postRequestedFor(urlEqualTo(juridiskLoggUrl)))
        assertMockEndpointsSatisfied()
    }

    @Test
    fun `valid invoice with ISO-8859-1 encoding`() {
        setUpStubs("message-faktura-invoice-iso8859-1-ok.xml")
        setUpInboundValidExpectations()
        ebasys.run {
            expectedMessageCount(1) // Expect that message is sent to Ebasys
            expectedHeaderReceived(
                LEGAL_ARCHIVE_CAMEL_HEADER,
                1
            ) // Expect that the message has legal archive header set
        }
        NotifyBuilder(camelContext).wereSentTo(ACCESS_POINT_READ.uri).whenExactlyCompleted(1).create()
            .matches(3, TimeUnit.SECONDS)
        verify(exactly(1), postRequestedFor(urlEqualTo(juridiskLoggUrl)))
        assertMockEndpointsSatisfied()
    }

    private fun assertMockEndpointsSatisfied() {
        mockEndpoints.map { it.assertIsSatisfied() }
    }

    private fun setUpAccessPointExpectations() {
        accessPointClient.expectedMessageCount(3) // Expect: 1 - fetch inbox, 2 - fetch message, 3 - mark as read
        inboxQueue.expectedMessageCount(1) // Expect that the message is sent to the inbox queue

        // Expect that the inbound route has received the message
        inboundRoute.run {
            expectedMessageCount(1)
            expectedHeaderReceived(CamelHeader.MSG_NO, "1")
        }
    }

    private fun setUpInboundValidExpectations() {
        setUpAccessPointExpectations()
        inboundSbdhMetaDataExtractor.expectedMessageCount(1) // Expect that XSLT is performed (EHF extracted)
        inboundLogger.run {
            expectedMessageCount(2) // Expect that the event loggers have been invoked
            expectedPropertyReceived(
                InboundSbdhMetaDataExtractor.CAMEL_XML_PROPERTY,
                true
            ) // Expect that the XMLDetector has validated the file
        }
    }

    private fun setUpStubs(messageFileName: String) {
        stubFor(
            get(urlEqualTo(inboxMessageXmlDocUrl))
                .withHeader(AccessPointProps.messages.header, equalTo(AccessPointProps.messages.apiKey))
                .withBasicAuth(ServiceUserProps.username, ServiceUserProps.password)
                .withHeader(HttpHeaders.Accept, equalTo(ContentType.Application.Xml.toString()))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader(ContentTypeHeader.KEY, "application/xml; charset=utf-8")
                        .withBodyFile(messageFileName)
                )
        )
    }

    private fun verifyAccessPointRequests() {
        verify(
            exactly(1), getRequestedFor(urlEqualTo(inboxMessagesUrl))
                .withHeader(AccessPointProps.inbox.header, equalTo(AccessPointProps.inbox.apiKey))
                .withBasicAuth(BasicCredentials(ServiceUserProps.username, ServiceUserProps.password))
                .withHeader(HttpHeaders.Accept, equalTo(ContentType.Application.Xml.toString()))
        )

        verify(
            exactly(1), getRequestedFor(urlEqualTo(inboxMessageXmlDocUrl))
                .withHeader(AccessPointProps.messages.header, equalTo(AccessPointProps.messages.apiKey))
                .withBasicAuth(BasicCredentials(ServiceUserProps.username, ServiceUserProps.password))
                .withHeader(HttpHeaders.Accept, equalTo(ContentType.Application.Xml.toString()))
        )

        verify(
            exactly(1), postRequestedFor(urlEqualTo(inboxReadUrl))
                .withHeader(AccessPointProps.inbox.header, equalTo(AccessPointProps.inbox.apiKey))
                .withBasicAuth(BasicCredentials(ServiceUserProps.username, ServiceUserProps.password))
                .withHeader(HttpHeaders.Accept, equalTo(ContentType.Application.Xml.toString()))
        )
    }

    companion object {
        @ClassRule
        @JvmField
        val wireMockRule = WireMockRule(wireMockConfig().port(20000).notifier(Slf4jNotifier(true)))

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            stubFor(
                get(urlEqualTo(inboxMessagesUrl))
                    .withHeader(AccessPointProps.inbox.header, equalTo(AccessPointProps.inbox.apiKey))
                    .withBasicAuth(ServiceUserProps.username, ServiceUserProps.password)
                    .withHeader(HttpHeaders.Accept, equalTo(ContentType.Application.Xml.toString()))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(ContentTypeHeader.KEY, "application/xml; charset=utf-8")
                            .withBodyFile("inbox-message-headers-ok.xml")
                    )
            )
            stubFor(
                post(urlEqualTo(inboxReadUrl))
                    .withHeader(AccessPointProps.inbox.header, equalTo(AccessPointProps.inbox.apiKey))
                    .withBasicAuth(ServiceUserProps.username, ServiceUserProps.password)
                    .withHeader(HttpHeaders.Accept, equalTo(ContentType.Application.Xml.toString()))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(ContentTypeHeader.KEY, "application/xml; charset=utf-8")
                            .withBodyFile("inbox-message-read-ok.xml")
                    )
            )
            stubFor(
                get(urlEqualTo(inboxCountUrl))
                    .withHeader(AccessPointProps.inbox.header, equalTo(AccessPointProps.inbox.apiKey))
                    .withHeader(HttpHeaders.Accept, equalTo(ContentType.Text.Plain.toString()))
                    .withBasicAuth(ServiceUserProps.username, ServiceUserProps.password)
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(ContentTypeHeader.KEY, "text/plain; charset=utf-8")
                            .withBody("1")
                    )
            )
            stubFor(
                post(urlEqualTo(juridiskLoggUrl))
                    .withBasicAuth(ServiceUserProps.username, ServiceUserProps.password)
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(ContentTypeHeader.KEY, "application/json; charset=utf-8")
                            .withBody("{\"id\":\"1\"}")
                    )
            )
            bootstrap(camelContext, server)
            Database.initLocal()
        }
    }
}
