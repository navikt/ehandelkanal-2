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
            sender = "",
            receiver = "",
            documentId = "",
            processId = ""
        )
        val response: String = AccessPointClient.transmitMessage(msgNo)
        call.respond(status = HttpStatusCode.OK, message = response)
    }
}
