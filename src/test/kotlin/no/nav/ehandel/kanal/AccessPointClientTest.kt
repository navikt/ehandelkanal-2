package no.nav.ehandel.kanal

import com.github.michaelbull.result.getErrorOrElse
import com.github.michaelbull.result.getOrElse
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.BasicCredentials
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.http.ContentTypeHeader
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.ktor.http.ContentType
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
import no.nav.ehandel.kanal.camel.processors.AccessPointClient
import no.nav.ehandel.kanal.common.constants.CamelHeader.TRACE_ID
import no.nav.ehandel.kanal.common.models.ErrorMessage
import no.nav.ehandel.kanal.helpers.getResource
import no.nav.ehandel.kanal.helpers.shouldBeXmlEqualTo
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.apache.camel.Exchange
import org.apache.camel.Message
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

private val username = ServiceUserProps.username
private val password = ServiceUserProps.password
private val inboxApiKey = AccessPointProps.inbox.apiKey
private val messagesApiKey = AccessPointProps.messages.apiKey
private val outboxApiKey = AccessPointProps.outbox.apiKey
private val transmitApiKey = AccessPointProps.transmit.apiKey
private val API_KEY_HEADER = AccessPointProps.inbox.header
private val basicCredentials = BasicCredentials(username, password)

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

    private val vefaClient = AccessPointClient

    @Test
    fun `get inbox count`() {
        val url = "/$MOCK_SERVER_PATH/inbox/count"
        wireMockRule.accessPointStub(
            url = url,
            method = HttpMethod.Get,
            apiKey = inboxApiKey,
            acceptHeaderValue = ContentType.Text.Plain,
            body = StubBody.WithContent("10")
        )
        val count = vefaClient.getInboxCount()
        verifyRequest(getRequestedFor(urlEqualTo(url)), inboxApiKey)
        count shouldBeEqualTo 10
    }

    @Test
    fun `get inbox message headers`() {
        val url = "/$MOCK_SERVER_PATH/inbox/"
        wireMockRule.accessPointStub(
            url = url,
            method = HttpMethod.Get,
            apiKey = inboxApiKey,
            acceptHeaderValue = ContentType.Application.Xml,
            body = StubBody.WithFile(path = "inbox-message-headers-ok.xml")
        )
        val body = vefaClient.getInboxMessageHeaders(exchange)
        verifyRequest(getRequestedFor(urlEqualTo(url)), inboxApiKey)
        body shouldBeXmlEqualTo "/__files/inbox-message-headers-ok.xml".getResource()
    }

    @ExperimentalStdlibApi
    @Test
    fun `download message payload`() {
        val url = "/$MOCK_SERVER_PATH/messages/1/xml-document"
        wireMockRule.accessPointStub(
            url = url,
            method = HttpMethod.Get,
            apiKey = messagesApiKey,
            acceptHeaderValue = ContentType.Application.Xml,
            body = StubBody.WithFile(path = "message-faktura-invoice-ok.xml")
        )
        val body = vefaClient.downloadMessagePayload(exchange, "1")
        verifyRequest(getRequestedFor(urlEqualTo(url)), messagesApiKey)
        body shouldBeXmlEqualTo "/__files/message-faktura-invoice-ok.xml".getResource()
    }

    @Test
    fun `mark message as read`() {
        val url = "/$MOCK_SERVER_PATH/inbox/1/read"
        wireMockRule.accessPointStub(
            url = url,
            method = HttpMethod.Post,
            apiKey = inboxApiKey,
            acceptHeaderValue = ContentType.Application.Xml,
            body = StubBody.WithFile(path = "inbox-message-read-ok.xml")
        )
        val body = vefaClient.markMessageAsRead("1")
        verifyRequest(postRequestedFor(urlEqualTo(url)), inboxApiKey)
        body shouldBeXmlEqualTo "/__files/inbox-message-read-ok.xml".getResource()
    }

    @Test
    fun `given a valid request, when sending to outbox, it should succeed`() {
        val url = "/$MOCK_SERVER_PATH/outbox"
        wireMockRule.accessPointStub(
            url = url,
            method = HttpMethod.Post,
            apiKey = outboxApiKey,
            acceptHeaderValue = ContentType.Application.Xml,
            body = StubBody.WithFile(path = "outbox-message-ok.xml")
        )
        val response = runBlocking {
            val header = Header.newInstance()
                .sender(ParticipantIdentifier.of("test"))
                .receiver(ParticipantIdentifier.of("test"))
                .process(ProcessIdentifier.of("test"))
                .documentType(DocumentTypeIdentifier.of("test"))
                .instanceType(InstanceType.of("test", "test", "test"))
                .creationTimestamp(Date())
                .identifier(InstanceIdentifier.of("test"))
            vefaClient.sendToOutbox(payload = "test", header = header, attempts = 1)
        }
        response
            .getOrElse { throw IllegalStateException("should return error") }
            .run {
                message.messageMetaData.msgNo.toInt() shouldBeEqualTo 1
            }
    }

    @Test
    fun `given an invalid request, when sending to outbox, it should fail`() {
        val url = "/$MOCK_SERVER_PATH/outbox"
        wireMockRule.accessPointStub(
            url = url,
            method = HttpMethod.Post,
            apiKey = outboxApiKey,
            acceptHeaderValue = ContentType.Application.Xml,
            httpStatusCode = HttpStatusCode.BadRequest,
            body = StubBody.WithContent("")
        )
        val response = runBlocking {
            val header = Header.newInstance()
                .sender(ParticipantIdentifier.of("test"))
                .receiver(ParticipantIdentifier.of("test"))
                .process(ProcessIdentifier.of("test"))
                .documentType(DocumentTypeIdentifier.of("test"))
                .instanceType(InstanceType.of("test", "test", "test"))
                .creationTimestamp(Date())
                .identifier(InstanceIdentifier.of("test"))
            vefaClient.sendToOutbox(payload = "test", header = header, attempts = 1)
        }
        response
            .getErrorOrElse { throw IllegalStateException("should return error") }
            .`should be equal to`(ErrorMessage.AccessPoint.ServerResponseError)
    }

    @Test
    fun `given a valid msgNo, when attempting to mark an outbox message for transmission, it should succeed`() {
        val msgNo = 1
        val outboxPostResponse = msgNo.mapToOutboxResponse()
        val url = "/$MOCK_SERVER_PATH/transmit/$msgNo"
        wireMockRule.accessPointStub(
            url = url,
            method = HttpMethod.Get,
            apiKey = transmitApiKey,
            acceptHeaderValue = ContentType.Application.Xml,
            body = StubBody.WithFile("transmit-message-ok.xml")
        )
        val response = runBlocking {
            vefaClient.transmitMessage(outboxResponse = outboxPostResponse, attempts = 1)
        }
        response
            .getOrElse { throw IllegalStateException("should not return error") }
            .run {
                succeededCount `should be equal to` 1
            }
    }

    @Test
    fun `given an invalid msgNo, when attempting to mark an outbox message for transmission, it should fail`() {
        val msgNo = 2
        val outboxPostResponse = msgNo.mapToOutboxResponse()
        val url = "/$MOCK_SERVER_PATH/transmit/$msgNo"
        wireMockRule.accessPointStub(
            url = url,
            method = HttpMethod.Get,
            apiKey = transmitApiKey,
            acceptHeaderValue = ContentType.Application.Xml,
            httpStatusCode = HttpStatusCode.BadRequest,
            body = StubBody.WithContent("")
        )
        val response = runBlocking {
            vefaClient.transmitMessage(outboxResponse = outboxPostResponse, attempts = 1)
        }
        response
            .getErrorOrElse { throw IllegalStateException("should return error") }
            .`should be equal to`(ErrorMessage.AccessPoint.ServerResponseError)
    }

    @Test
    fun `given a valid msgNo, when attempting to mark an outbox message for transmission and it fails, it should return a failure message`() {
        val msgNo = 3
        val outboxPostResponse = msgNo.mapToOutboxResponse()
        val url = "/$MOCK_SERVER_PATH/transmit/$msgNo"
        wireMockRule.accessPointStub(
            url = url,
            method = HttpMethod.Get,
            apiKey = transmitApiKey,
            acceptHeaderValue = ContentType.Application.Xml,
            body = StubBody.WithFile("transmit-message-failed.xml")
        )
        val response = runBlocking {
            vefaClient.transmitMessage(outboxResponse = outboxPostResponse, attempts = 1)
        }
        response
            .getErrorOrElse { throw IllegalStateException("should return error") }
            .`should be equal to`(ErrorMessage.AccessPoint.TransmitError)
    }

    @Test
    fun `given a valid msgNo, when attempting to mark an outbox message for transmission and the access point is unavailable, it should return a failure message`() {
        val msgNo = 4
        val outboxPostResponse = msgNo.mapToOutboxResponse()
        val url = "/$MOCK_SERVER_PATH/transmit/$msgNo"
        wireMockRule.accessPointStub(
            url = url,
            method = HttpMethod.Get,
            apiKey = transmitApiKey,
            acceptHeaderValue = ContentType.Application.Xml,
            httpStatusCode = HttpStatusCode.InternalServerError,
            body = StubBody.WithContent("")
        )
        val response = runBlocking {
            vefaClient.transmitMessage(outboxResponse = outboxPostResponse, attempts = 1)
        }
        response
            .getErrorOrElse { throw IllegalStateException("should return error") }
            .`should be equal to`(ErrorMessage.AccessPoint.ServerResponseError)
    }

    private fun verifyRequest(request: RequestPatternBuilder, apiKey: String) {
        verify(
            exactly(1), request
                .withHeader(API_KEY_HEADER, equalTo(apiKey))
                .withBasicAuth(basicCredentials)
        )
    }

    companion object {
        @ClassRule
        @JvmField
        val wireMockRule = WireMockRule(wireMockConfig().port(MOCK_PORT).notifier(Slf4jNotifier(true)))

        @BeforeClass
        @JvmStatic
        fun setUp() {
            System.setProperty("vefasrest.outbox.url", MOCK_SERVER_URL + "outbox/")
            System.setProperty("vefasrest.inbox.url", MOCK_SERVER_URL + "inbox/")
            System.setProperty("vefasrest.messages.url", MOCK_SERVER_URL + "messages/")
            System.setProperty("vefasrest.transmit.url", MOCK_SERVER_URL + "transmit/")
        }
    }
}

private fun Int.mapToOutboxResponse() = OutboxPostResponseType().apply {
    message = MessageType().apply {
        messageMetaData = MessageMetaDataType().apply {
            this.msgNo = this@mapToOutboxResponse.toString()
        }
    }
}

private fun WireMockServer.accessPointStub(
    url: String,
    method: HttpMethod,
    apiKey: String,
    acceptHeaderValue: ContentType = ContentType.Application.Xml,
    httpStatusCode: HttpStatusCode = HttpStatusCode.OK,
    body: StubBody
): StubMapping {
    return stubFor(
        when (method) {
            HttpMethod.Get -> get(urlPathEqualTo(url))
            HttpMethod.Post -> post(urlPathEqualTo(url))
            else -> throw IllegalArgumentException("Unsupported Http Method")
        }.apply {
            withHeader(API_KEY_HEADER, equalTo(apiKey))
            withHeader("Accept", equalTo("$acceptHeaderValue"))
            withBasicAuth(username, password)
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

private sealed class StubBody {
    data class WithContent(val content: String) : StubBody()
    data class WithFile(val path: String) : StubBody()
}
