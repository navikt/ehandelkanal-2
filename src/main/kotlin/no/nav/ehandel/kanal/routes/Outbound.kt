package no.nav.ehandel.kanal.routes

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
import no.difi.commons.ubl21.jaxb.OrderType
import no.nav.ehandel.kanal.common.models.ErrorMessage
import no.nav.ehandel.kanal.services.outbound.OutboundErrorResponse
import no.nav.ehandel.kanal.services.outbound.OutboundMessageService

fun Route.outbound(outboundMessageService: OutboundMessageService) {
    route("/outbound") {
        post("/order") {
            call.handleOutboundRequest<OrderType>(outboundMessageService)
        }
    }
}

private suspend inline fun <reified T> ApplicationCall.handleOutboundRequest(outboundMessageService: OutboundMessageService) {
    this.receiveText()
        .let { payload -> outboundMessageService.processOutboundMessage<T>(payload) }
        .mapError(OutboundErrorResponse::toResponse)
        .mapBoth(
            success = { response ->
                respond(HttpStatusCode.OK, response)
            },
            failure = { (status, response) ->
                respond(status, response)
            }
        )
}

private fun OutboundErrorResponse.toResponse() = when (this.errorMessage) {
    ErrorMessage.InternalError,
    ErrorMessage.AccessPoint.TransmitError,
    ErrorMessage.AccessPoint.ServerResponseError,
    ErrorMessage.SbdhGenerator.CouldNotPrependSbdh ->
        Pair(HttpStatusCode.InternalServerError, this)

    ErrorMessage.DataBindError,
    ErrorMessage.SbdhGenerator.CouldNotMapPayloadToSbdh,
    ErrorMessage.SbdhGenerator.CouldNotParseDocumentType ->
        Pair(HttpStatusCode.BadRequest, this)
}
