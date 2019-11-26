package no.nav.ehandel.kanal.services.sbdhgenerator

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import java.io.ByteArrayOutputStream
import javax.xml.bind.JAXB
import mu.KotlinLogging
import no.difi.commons.ubl21.jaxb.OrderType
import no.difi.vefa.peppol.common.model.Header
import no.difi.vefa.peppol.sbdh.SbdWriter
import no.difi.vefa.peppol.sbdh.util.XMLStreamUtils
import no.nav.ehandel.kanal.common.models.ErrorMessage
import no.nav.ehandel.kanal.domain.documenttypes.order.mapToHeader

private val logger = KotlinLogging.logger { }

class StandardBusinessDoumentProcessorService {

    /**
     * returns a tuple consisting of the generated standard business document header as well as the string representation
     * of the generated standard business document with the header and provided payload contained
     */
    internal inline fun <reified T> generateStandardBusinessDocument(rawXmlPayload: String): Result<Pair<Header, String>, ErrorMessage> =
        rawXmlPayload
            .parsePayload<T>()
            .andThen { payload -> payload.mapToSbdh() }
            .andThen { header -> header.mapToStandardBusinessDocument(rawXmlPayload) }
}

private fun Header.mapToStandardBusinessDocument(rawXmlPayload: String): Result<Pair<Header, String>, ErrorMessage> =
    runCatching {
        ByteArrayOutputStream().use { outputStream ->
            SbdWriter.newInstance(outputStream, this).use { sbdWriter ->
                XMLStreamUtils.copy(rawXmlPayload.byteInputStream(Charsets.UTF_8), sbdWriter.xmlWriter())
            }
            outputStream.toString(Charsets.UTF_8)
        }
    }.fold(
        onSuccess = { Ok(Pair(this, it)) },
        onFailure = { e ->
            logger.error(e) { "Could not prepend SBDH to payload" }
            Err(ErrorMessage.StandardBusinessDocument.CouldNotPrependStandardBusinessDocument)
        }
    )

private inline fun <reified T> String.parsePayload(): Result<T, ErrorMessage> =
    runCatching {
        JAXB.unmarshal(this.byteInputStream(), T::class.java)
    }.fold(
        onSuccess = { Ok(it) },
        onFailure = { e ->
            logger.error(e) { "Could not unmarshal document payload" }
            Err(ErrorMessage.StandardBusinessDocument.CouldNotParseDocumentType)
        }
    )

private inline fun <reified T> T.mapToSbdh(): Result<Header, ErrorMessage> =
    when (this) {
        is OrderType -> this.mapToHeader()
        else -> {
            logger.error { "Received invalid DocumentType: '${T::class.simpleName}'" }
            Err(ErrorMessage.StandardBusinessDocument.InvalidDocumentType)
        }
    }
