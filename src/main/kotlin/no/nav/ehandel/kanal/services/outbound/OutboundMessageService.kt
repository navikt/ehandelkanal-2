package no.nav.ehandel.kanal.services.outbound

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.mapBoth
import no.nav.ehandel.kanal.camel.processors.AccessPointClient
import no.nav.ehandel.kanal.common.functions.getCorrelationId
import no.nav.ehandel.kanal.common.models.ErrorMessage
import no.nav.ehandel.kanal.services.sbd.StandardBusinessDocumentGenerator

class OutboundMessageService(
    private val accessPointClient: AccessPointClient,
    private val standardBusinessDocumentGenerator: StandardBusinessDocumentGenerator
) {

    internal suspend inline fun <reified T> processOutboundMessage(xmlPayload: String): Result<OutboundResponse, OutboundErrorResponse> =
        standardBusinessDocumentGenerator
            .generateStandardBusinessDocument<T>(xmlPayload)
            .andThen { (header, document) -> accessPointClient.sendToOutbox(payload = document, header = header) }
            .andThen { outboxPostResponse -> accessPointClient.transmitMessage(outboxPostResponse) }
            .mapBoth(
                success = {
                    Ok(
                        OutboundResponse(
                            correlationId = getCorrelationId(),
                            status = Status.SUCCESS,
                            message = "Message successfully sent"
                        )
                    )
                },
                failure = { errorMessage ->
                    Err(errorMessage.mapToOutboundErrorResponse())
                }
            )

    // todo
    //  - log to legal archive?
    //  - report entries?
}

private fun ErrorMessage.mapToOutboundErrorResponse(): OutboundErrorResponse {
    val status = when (this) {
        ErrorMessage.InternalError,
        ErrorMessage.AccessPoint.TransmitError,
        ErrorMessage.AccessPoint.ServerResponseError,
        ErrorMessage.StandardBusinessDocument.FailedToPrependStandardBusinessDocumentHeader ->
            Status.FAILED

        ErrorMessage.AccessPoint.DataBindError,
        ErrorMessage.StandardBusinessDocument.FailedToParseDocumentType,
        ErrorMessage.StandardBusinessDocument.InvalidSchemeIdForParticipant,
        ErrorMessage.StandardBusinessDocument.InvalidDocumentType,
        ErrorMessage.StandardBusinessDocument.MissingRequiredValuesFromDocument ->
            Status.BAD_REQUEST
    }
    return OutboundErrorResponse(
        correlationId = getCorrelationId(),
        status = status,
        message = this.toString()
    )
}
