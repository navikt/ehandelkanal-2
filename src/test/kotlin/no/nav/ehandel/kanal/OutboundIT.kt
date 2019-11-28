package no.nav.ehandel.kanal

import com.github.michaelbull.result.Ok
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationCall
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
import no.nav.ehandel.kanal.helpers.getResource
import no.nav.ehandel.kanal.services.outbound.OutboundMessageService
import no.nav.ehandel.kanal.services.sbd.StandardBusinessDocumentGenerator
import org.amshove.kluent.`should be`
import org.junit.Before
import org.junit.Test

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

    @Test // todo
    fun `given a valid payload and access point is available, should return success`() =
        testApp { call ->
            call.response.status()!! `should be` HttpStatusCode.OK
        }

    @Test // todo
    fun `given a valid payload and the access point is unavailable, it should return failure`() =
        testApp { call ->
            call.response.status()!! `should be` HttpStatusCode.OK
        }

    @Test // todo
    fun `given an invalid payload, it should return failure`() =
        testApp("/outbound/outbound-invalid-payload.json") { call ->
            call.response.status()!! `should be` HttpStatusCode.BadRequest
        }

    @Test // todo
    fun `given an invalid payload with invalid schemeid, it should return failure`() =
        testApp("/outbound/outbound-invalid-order-invalid-schemeid-no-sbdh.xml") { call ->
            call.response.status()!! `should be` HttpStatusCode.BadRequest
        }

    @Test // todo
    fun `given an invalid payload with missing required values, it should return failure`() =
        testApp("/outbound/outbound-invalid-order-missing-required-values-no-sbdh.xml") { call ->
            call.response.status()!! `should be` HttpStatusCode.BadRequest
        }

    @Test // todo
    fun `given successful processing of a message, it should insert entry into report`() =
        testApp { call ->
            call.response.status()!! `should be` HttpStatusCode.OK
        }

    @Test // todo
    fun `given failed processing of a message, it should not insert entry into report`() =
        testApp { call ->
            call.response.status()!! `should be` HttpStatusCode.OK
        }

    private fun testApp(
        bodyPath: String = "/outbound/outbound-valid-order-no-sbdh.xml",
        assertions: (TestApplicationCall) -> Unit
    ): Unit =
        withTestApplication(moduleFunction = { main(outboundMessageService = outboundMessageService) }) {
            handleRequest(HttpMethod.Post, "/api/v1/outbound/order") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
                setBody(bodyPath.getResource<String>())
            }.run {
                assertions(this)
            }
        }
}
