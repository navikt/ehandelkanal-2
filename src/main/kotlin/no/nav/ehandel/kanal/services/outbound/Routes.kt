package no.nav.ehandel.kanal.services.outbound

import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapError
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route
import no.difi.commons.ubl21.jaxb.InvoiceType
import no.difi.commons.ubl21.jaxb.OrderType

fun Route.outbound(outboundMessageService: OutboundMessageService) {
    route("/outbound") {
        post("/order") {
            call.handleOutboundRequest<OrderType>(outboundMessageService)
        }
        post("/invoice") {
            call.handleOutboundRequest<InvoiceType>(outboundMessageService)
        }
    }
}

private suspend inline fun <reified T> ApplicationCall.handleOutboundRequest(outboundMessageService: OutboundMessageService) {
    this.receiveText()
        .let { payload -> outboundMessageService.processOutboundMessage<T>(payload) }
        .mapError(OutboundErrorResponse::toResponse)
        .mapBoth(
            success = { response -> respond(HttpStatusCode.OK, response) },
            failure = { (status, response) -> respond(status, response) }
        )
}

private fun OutboundErrorResponse.toResponse() = when (this.status) {
    Status.FAILED -> Pair(HttpStatusCode.InternalServerError, this)
    Status.BAD_REQUEST -> Pair(HttpStatusCode.BadRequest, this)
    Status.SUCCESS -> Pair(HttpStatusCode.OK, this)
}
