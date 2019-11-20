package no.nav.ehandel.kanal.services.outbound

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import no.nav.ehandel.kanal.camel.processors.AccessPointClient
import no.nav.ehandel.kanal.common.models.ErrorMessage
import no.nav.ehandel.kanal.services.sbdhgenerator.SbdhGeneratorService

class OutboundMessageService(
    private val accessPointClient: AccessPointClient,
    private val sbdhGeneratorService: SbdhGeneratorService
) {

    internal inline fun <reified T> processOutboundMessage(xmlPayload: String): Result<OutboundResponse, OutboundErrorResponse> =
        runCatching {
            sbdhGeneratorService
                .generateSbdh<T>(xmlPayload)
            // prepend SBDH to document, creating an SBD
            // upload SBD to access point
            // trigger transmit
            // ?????????
            // profit
        }.fold(
            onSuccess = {
                Ok(OutboundResponse(foo = "bar"))
            },
            onFailure = {
                Err(OutboundErrorResponse(foo = "bar", errorMessage = ErrorMessage.InternalError))
            }
        )
}
