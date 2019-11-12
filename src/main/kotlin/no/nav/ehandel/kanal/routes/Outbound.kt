package no.nav.ehandel.kanal.routes

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.ehandel.kanal.camel.processors.AccessPointClient

fun Route.outbound() {
    post("/outbound") {
        val payload: String = call.receiveText()
        val msgNo: String = AccessPointClient.sendToOutbox(
            payload = payload.toByteArray(),
            sender = "9908:889640782",
            receiver = "9908:889640782",
            documentId = "6d93a20f-496f-4856-a942-f87c8314e0a8",
            processId = "urn:www.cenbii.eu:profile:bii28:ver2.0"
        )
        val response: String = AccessPointClient.transmitMessage(msgNo)
        call.respond(status = HttpStatusCode.OK, message = response)
    }
    post("/outbound/transmit/{msgNo}") {
        val msgNo: String = requireNotNull(call.request.queryParameters["msgNo"]) {
            "Missing query parameter 'msgNo'"
        }
        val response: String = AccessPointClient.transmitMessage(msgNo)
        call.respond(response)
    }
}
