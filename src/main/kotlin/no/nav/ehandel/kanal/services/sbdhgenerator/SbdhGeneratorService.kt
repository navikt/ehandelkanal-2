package no.nav.ehandel.kanal.services.sbdhgenerator

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import javax.xml.bind.JAXB
import mu.KotlinLogging
import no.difi.commons.ubl21.jaxb.OrderType
import no.difi.vefa.peppol.common.model.Header
import no.nav.ehandel.kanal.common.models.ErrorMessage
import no.nav.ehandel.kanal.domain.documenttypes.order.mapToHeader

private val logger = KotlinLogging.logger { }

class SbdhGeneratorService {
    internal inline fun <reified T> generateSbdh(rawXmlPayload: String): Result<Header, ErrorMessage> =
        rawXmlPayload
            .parsePayload<T>()
            .andThen { payload -> payload.mapToSbdh() }

    companion object {
        const val ISO6323_1_ACTOR_ID_CODE = "9908" // todo: handle other actors?
    }
}

private inline fun <reified T> String.parsePayload(): Result<T, ErrorMessage> =
    runCatching {
        JAXB.unmarshal(this.byteInputStream(), T::class.java)
    }.fold(
        onSuccess = { Ok(it) },
        onFailure = { e ->
            logger.error(e) { "Could not unmarshal document payload" }
            Err(ErrorMessage.SbdhGenerator.CouldNotParseDocumentType)
        }
    )

private inline fun <reified T> T.mapToSbdh(): Result<Header, ErrorMessage> =
    runCatching {
        when (this) {
            is OrderType -> this.mapToHeader()
            else -> throw UnsupportedOperationException("Invalid DocumentType: '${T::class.simpleName}'")
        }
    }.fold(
        onSuccess = { Ok(it) },
        onFailure = { e ->
            logger.error(e) { "Could not map document payload to SBDH" }
            Err(ErrorMessage.SbdhGenerator.CouldNotMapPayloadToSbdh)
        }
    )
