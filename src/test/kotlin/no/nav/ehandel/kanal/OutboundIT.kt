package no.nav.ehandel.kanal

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.http.ContentTypeHeader
import com.github.tomakehurst.wiremock.junit.WireMockRule
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.coEvery
import io.mockk.mockk
import no.difi.vefasrest.model.MessageMetaDataType
import no.difi.vefasrest.model.MessageType
import no.difi.vefasrest.model.OutboxPostResponseType
import no.difi.vefasrest.model.QueuedMessagesSendResultType
import no.nav.ehandel.kanal.camel.processors.AccessPointClient
import no.nav.ehandel.kanal.common.models.ErrorMessage
import no.nav.ehandel.kanal.common.singletons.objectMapper
import no.nav.ehandel.kanal.helpers.getResource
import no.nav.ehandel.kanal.services.outbound.IOutboundResponse
import no.nav.ehandel.kanal.services.outbound.OutboundErrorResponse
import no.nav.ehandel.kanal.services.outbound.OutboundMessageService
import no.nav.ehandel.kanal.services.outbound.OutboundResponse
import no.nav.ehandel.kanal.services.outbound.Status
import no.nav.ehandel.kanal.services.sbd.StandardBusinessDocumentGenerator
import org.amshove.kluent.`should be`
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

private const val URI_ORDER = "/api/v1/outbound/order"
private const val URI_INVOICE = "/api/v1/outbound/invoice"
private const val MOCK_PORT = 20001

class OutboundIT {

    private val accessPointClientMock = mockk<AccessPointClient>()
    private val outboundMessageService = OutboundMessageService(
        accessPointClient = accessPointClientMock,
        standardBusinessDocumentGenerator = StandardBusinessDocumentGenerator()
    )

    @Before
    fun setup() {
        coEvery { accessPointClientMock.sendToOutbox(any(), any()) } returns Ok(
            OutboxPostResponseType().apply {
                message = MessageType().apply {
                    messageMetaData = MessageMetaDataType().apply {
                        this.msgNo = "1"
                    }
                }
            })
        coEvery { accessPointClientMock.transmitMessage(any()) } returns Ok(QueuedMessagesSendResultType())
    }

    @Test
    fun `given a valid order payload and access point is available, should return success`() =
        runTestRequestWIthPayload<OutboundResponse>(
            bodyPath = "/outbound/outbound-valid-order-no-sbdh.xml"
        ) { response ->
            response.status `should be` Status.SUCCESS
        }

    @Test
    fun `given a valid invoice payload and access point is available, should return success`() =
        runTestRequestWIthPayload<OutboundResponse>(
            bodyPath = "/outbound/outbound-valid-invoice-no-sbdh.xml",
            uri = URI_INVOICE
        ) { response ->
            response.status `should be` Status.SUCCESS
        }

    @Test
    fun `given a valid payload and the access point is unavailable, it should return failure`() {
        coEvery {
            accessPointClientMock.sendToOutbox(any(), any())
        } returns Err(ErrorMessage.AccessPoint.ServerResponseError)
        runTestRequestWIthPayload<OutboundErrorResponse>(
            bodyPath = "/outbound/outbound-valid-order-no-sbdh.xml",
            expectedHttpStatusCode = HttpStatusCode.InternalServerError
        ) { response ->
            response.status `should be` Status.FAILED
        }
    }

    @Test
    fun `given an invalid payload, it should return failure`() =
        runTestRequestWIthPayload<OutboundErrorResponse>(
            bodyPath = "/outbound/outbound-invalid-payload.json",
            expectedHttpStatusCode = HttpStatusCode.BadRequest
        ) { response ->
            response.status `should be` Status.BAD_REQUEST
        }

    @Test
    fun `given an invalid payload with invalid schemeid, it should return failure`() =
        runTestRequestWIthPayload<OutboundErrorResponse>(
            bodyPath = "/outbound/outbound-invalid-order-invalid-schemeid-no-sbdh.xml",
            expectedHttpStatusCode = HttpStatusCode.BadRequest
        ) { response ->
            response.status `should be` Status.BAD_REQUEST
        }

    @Test
    fun `given an invalid payload with missing required values, it should return failure`() =
        runTestRequestWIthPayload<OutboundErrorResponse>(
            bodyPath = "/outbound/outbound-invalid-order-missing-required-values-no-sbdh.xml",
            expectedHttpStatusCode = HttpStatusCode.BadRequest
        ) { response ->
            response.status `should be` Status.BAD_REQUEST
        }

    @Test // TODO
    fun `given successful processing of a message, it should insert entry into report`() =
        runTestRequestWIthPayload<OutboundResponse>(
            bodyPath = "/outbound/outbound-valid-order-no-sbdh.xml"
        ) { response ->
            response.status `should be` Status.SUCCESS
        }

    @Test // TODO
    fun `given failed processing of a message, it should not insert entry into report`() =
        runTestRequestWIthPayload<OutboundResponse>(
            bodyPath = "/outbound/outbound-valid-order-no-sbdh.xml"
        ) { response ->
            response.status `should be` Status.SUCCESS
        }

    private inline fun <reified T : IOutboundResponse> runTestRequestWIthPayload(
        bodyPath: String,
        uri: String = URI_ORDER,
        expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.OK,
        noinline assertions: (T) -> Unit
    ): Unit = withTestApplication(moduleFunction = { main(outboundMessageService = outboundMessageService) }) {
        handleRequest(HttpMethod.Post, uri) {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
            setBody(bodyPath.getResource<String>())
        }.run {
            this.response.status()!! `should be` expectedHttpStatusCode
            with(objectMapper.readValue<T>(this.response.content!!)) {
                assertions(this)
            }
        }
    }

    companion object {
        @ClassRule
        @JvmField
        val outboundWireMockRule =
            WireMockRule(WireMockConfiguration.wireMockConfig().port(MOCK_PORT).notifier(Slf4jNotifier(true)))

        @BeforeClass
        @JvmStatic
        fun setupClass() {
            System.setProperty("sts.well-known.url", "http://localhost:$MOCK_PORT")
            stubFor(
                get(urlEqualTo("/"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(ContentTypeHeader.KEY, "application/json; charset=utf-8")
                            .withBodyFile("sts-rest-well-known.json")
                    )
            )
        }
    }
}
