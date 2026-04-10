package no.nav.ehandel.kanal

import com.github.michaelbull.result.getErrorOrElse
import com.github.michaelbull.result.getOrElse
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.http.ContentTypeHeader
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import java.util.Date
import kotlinx.coroutines.runBlocking
import no.difi.vefa.peppol.common.model.DocumentTypeIdentifier
import no.difi.vefa.peppol.common.model.Header
import no.difi.vefa.peppol.common.model.InstanceIdentifier
import no.difi.vefa.peppol.common.model.InstanceType
import no.difi.vefa.peppol.common.model.ParticipantIdentifier
import no.difi.vefa.peppol.common.model.ProcessIdentifier
import no.difi.vefasrest.model.MessageMetaDataType
import no.difi.vefasrest.model.MessageType
import no.difi.vefasrest.model.OutboxPostResponseType
import no.nav.ehandel.kanal.auth.EntraIdTokenProvider
import no.nav.ehandel.kanal.camel.processors.AccessPointClient
import no.nav.ehandel.kanal.common.constants.CamelHeader.TRACE_ID
import no.nav.ehandel.kanal.common.models.ErrorMessage
import no.nav.ehandel.kanal.helpers.getResource
import no.nav.ehandel.kanal.helpers.shouldBeXmlEqualTo
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.apache.camel.Exchange
import org.apache.camel.Message
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

private const val MOCK_PORT = 20000
private const val MOCK_SERVER_PATH = "vefasrest"
private const val MOCK_SERVER_URL = "http://localhost:$MOCK_PORT/$MOCK_SERVER_PATH/"

class AccessPointClientTest {

    private val messageMock: Message = mockk(relaxed = true) {
        every { getHeader(TRACE_ID) } returns "1337"
    }
    private val exchange: Exchange = mockk(relaxed = true) {
        every { getIn() } returns messageMock
    }

    // Mock EntraIdTokenProvider
    private val mockTokenProvider: EntraIdTokenProvider = mockk {
        every { getToken() } returns "mock-bearer-token"
    }

    // Create AccessPointClient with mocked token provider
    private val vefaClient = AccessPointClient(mockTokenProvider)

    @Test
    fun `get inbox message headers`() {
        val url = "/$MOCK_SERVER_PATH/inbox/hent-uleste-meldinger"
        wireMockRule.accessPointStub(
            url = url,
            method = HttpMethod.Get,
            acceptHeaderValue = ContentType.Application.Json,
            body = StubBody.WithFile(path = "json/inbox-hent-uleste-meldinger.json")
        )
        val body = vefaClient.getInboxMessageHeaders(exchange)
        body shouldContain "\"meldinger\""
    }

    @ExperimentalStdlibApi
    @Test
    fun `download message payload`() {
        val url = "/$MOCK_SERVER_PATH/messages/xml-document/1"
        wireMockRule.accessPointStub(
            url = url,
            method = HttpMethod.Get,
            acceptHeaderValue = ContentType.Application.Xml,
            body = StubBody.WithFile(path = "message-faktura-invoice-ok.xml")
        )
        val body = vefaClient.downloadMessagePayload(exchange, "1")
        body shouldBeXmlEqualTo "/__files/message-faktura-invoice-ok.xml".getResource()
    }

    @Test
    fun `mark message as read`() {
        val url = "/$MOCK_SERVER_PATH/inbox/marker-som-lest/1"
        wireMockRule.accessPointStub(
            url = url,
            method = HttpMethod.Put,
            acceptHeaderValue = ContentType.Text.Plain,
            body = StubBody.WithContent("OK")
        )
        val body = vefaClient.markMessageAsRead("1")
        body shouldBeXmlEqualTo "OK"
    }

    companion object {
        @ClassRule
        @JvmField
        val wireMockRule = WireMockRule(wireMockConfig().port(MOCK_PORT).notifier(Slf4jNotifier(true)))

        @BeforeClass
        @JvmStatic
        fun setUp() {
            System.setProperty("vefasrest.inbox.url", MOCK_SERVER_URL + "inbox/")
            System.setProperty("vefasrest.messages.url", MOCK_SERVER_URL + "messages/")
        }
    }
}

private fun WireMockServer.accessPointStub(
    url: String,
    method: HttpMethod,
    acceptHeaderValue: ContentType = ContentType.Application.Xml,
    httpStatusCode: HttpStatusCode = HttpStatusCode.OK,
    body: StubBody
): StubMapping {
    return stubFor(
        when (method) {
            HttpMethod.Get -> get(urlPathEqualTo(url))
            HttpMethod.Post -> post(urlPathEqualTo(url))
            HttpMethod.Put -> put(urlPathEqualTo(url))
            else -> throw IllegalArgumentException("Unsupported Http Method")
        }.apply {
            withHeader(HttpHeaders.Authorization, equalTo("Bearer mock-bearer-token"))
            willReturn(
                aResponse()
                    .withStatus(httpStatusCode.value)
                    .withHeader(
                        ContentTypeHeader.KEY,
                        "$acceptHeaderValue; charset=utf-8"
                    )
                    .apply {
                        when (body) {
                            is StubBody.WithFile -> withBodyFile(body.path)
                            is StubBody.WithContent -> withBody(body.content)
                        }
                    }
            )
        }
    )
}

sealed class StubBody {
    data class WithContent(val content: String) : StubBody()
    data class WithFile(val path: String) : StubBody()
}
