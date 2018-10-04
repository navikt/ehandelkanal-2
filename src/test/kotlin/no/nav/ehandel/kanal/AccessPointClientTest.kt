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
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.nhaarman.mockitokotlin2.mock
import no.nav.ehandel.kanal.CamelHeader.TRACE_ID
import no.nav.ehandel.kanal.camel.processors.AccessPointClient
import org.apache.camel.Exchange
import org.apache.camel.Message
import org.assertj.core.api.Assertions.assertThat
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

private val username = ServiceUserProps.username
private val password = ServiceUserProps.password
private val inboxApiKey = AccessPointProps.inbox.apiKey
private val messagesApiKey = AccessPointProps.messages.apiKey
private val API_KEY_HEADER = AccessPointProps.inbox.header
private val basicCredentials = BasicCredentials(username, password)

private const val mockPort = 20000
private const val mockServerUrl = "http://localhost:$mockPort/vefasrest/"

class AccessPointClientTest {

    private val message: Message = mock {
        on { getHeader(TRACE_ID) }.thenReturn("1337")
    }
    private val exchange: Exchange = mock {
        onGeneric { getIn() }.thenReturn(message)
    }

    private val vefaClient = AccessPointClient

    @Test
    fun `get inbox count`() {
        val url = "/vefasrest/inbox/count"
        stubFor(
            get(urlEqualTo(url))
                .withHeader(API_KEY_HEADER, equalTo(inboxApiKey))
                .withHeader("Accept", equalTo("text/plain"))
                .withBasicAuth(username, password)
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader(ContentTypeHeader.KEY, "text/plain; charset=utf-8")
                        .withBody("10")
                )
        )

        val count = vefaClient.getInboxCount()
        verifyRequest(getRequestedFor(urlEqualTo(url)), inboxApiKey)
        assertThat(count).isEqualTo(10)
    }

    @Test
    fun `get inbox message headers`() {
        val url = "/vefasrest/inbox/"
        stubFor(
            get(urlEqualTo(url))
                .withHeader(API_KEY_HEADER, equalTo(inboxApiKey))
                .withHeader("Accept", equalTo("application/xml"))
                .withBasicAuth(username, password)
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader(ContentTypeHeader.KEY, "application/xml; charset=utf-8")
                        .withBodyFile("inbox-message-headers-ok.xml")
                )
        )
        val body = vefaClient.getInboxMessageHeaders(exchange)
        verifyRequest(getRequestedFor(urlEqualTo(url)), inboxApiKey)
        assertThat(body).isXmlEqualToContentOf("/__files/inbox-message-headers-ok.xml".getResource())
    }

    @Test
    fun `download message payload`() {
        val url = "/vefasrest/messages/1/xml-document"
        stubFor(
            get(urlEqualTo(url))
                .withHeader(API_KEY_HEADER, equalTo(messagesApiKey))
                .withHeader("Accept", equalTo("application/xml"))
                .withBasicAuth(username, password)
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader(ContentTypeHeader.KEY, "application/xml; charset=utf-8")
                        .withBodyFile("message-faktura-invoice-ok.xml")
                )
        )
        val body = vefaClient.downloadMessagePayload(exchange, "1")
        verifyRequest(getRequestedFor(urlEqualTo(url)), messagesApiKey)
        assertThat(body).isXmlEqualToContentOf("/__files/message-faktura-invoice-ok.xml".getResource())
    }

    @Test
    fun `mark message as read`() {
        val url = "/vefasrest/inbox/1/read"
        stubFor(
            post(urlEqualTo(url))
                .withHeader(API_KEY_HEADER, equalTo(inboxApiKey))
                .withHeader("Accept", equalTo("application/xml"))
                .withBasicAuth(username, password)
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader(ContentTypeHeader.KEY, "application/xml; charset=utf-8")
                        .withBodyFile("inbox-message-read-ok.xml")
                )
        )
        val body = vefaClient.markMessageAsRead("1")
        verifyRequest(postRequestedFor(urlEqualTo(url)), inboxApiKey)
        assertThat(body).isXmlEqualToContentOf("/__files/inbox-message-read-ok.xml".getResource())
    }

    // TODO: Add case for failing read (also in IT)

    private fun verifyRequest(request: RequestPatternBuilder, apiKey: String) {
        verify(exactly(1), request
            .withHeader(API_KEY_HEADER, equalTo(apiKey))
            .withBasicAuth(basicCredentials)
        )
    }

    companion object {
        @ClassRule @JvmField
        val wireMockRule = WireMockRule(wireMockConfig().port(mockPort).notifier(Slf4jNotifier(true)))

        @BeforeClass @JvmStatic
        fun setUp() {
            System.setProperty("vefasrest.outbox.url", mockServerUrl + "outbox/")
            System.setProperty("vefasrest.inbox.url", mockServerUrl + "inbox/")
            System.setProperty("vefasrest.messages.url", mockServerUrl + "messages/")
            System.setProperty("vefasrest.transmit.url", mockServerUrl + "transmit/")
        }
    }
}
