package no.nav.ehandel.kanal.services.sbd

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import java.io.ByteArrayOutputStream
import javax.xml.bind.JAXB
import mu.KotlinLogging
import no.difi.commons.ubl21.jaxb.InvoiceType
import no.difi.commons.ubl21.jaxb.OrderType
import no.difi.vefa.peppol.common.model.Header
import no.difi.vefa.peppol.sbdh.SbdWriter
import no.difi.vefa.peppol.sbdh.util.XMLStreamUtils
import no.nav.ehandel.kanal.common.models.ErrorMessage
import no.nav.ehandel.kanal.domain.documenttypes.invoice.mapToHeader
import no.nav.ehandel.kanal.domain.documenttypes.order.mapToHeader

@PublishedApi
internal val logger = KotlinLogging.logger { }

class StandardBusinessDocumentGenerator {

    /**
     * returns a tuple consisting of the generated standard business document header as well as the string representation
     * of the generated standard business document with the header and provided payload contained
     */
    @PublishedApi
    internal inline fun <reified T> generateStandardBusinessDocument(rawXmlPayload: String): Result<Pair<Header, String>, ErrorMessage> =
        rawXmlPayload
            .parsePayload<T>()
            .andThen { payload -> payload.mapToSbdh() }
            .andThen { header -> header.mapToStandardBusinessDocument(rawXmlPayload) }
}
@PublishedApi
internal fun Header.mapToStandardBusinessDocument(rawXmlPayload: String): Result<Pair<Header, String>, ErrorMessage> =
    runCatching {
        ByteArrayOutputStream().use { outputStream ->
            SbdWriter.newInstance(outputStream, this).use { sbdWriter ->
                XMLStreamUtils.copy(rawXmlPayload.byteInputStream(Charsets.UTF_8), sbdWriter.xmlWriter())
            }
            outputStream.toString(Charsets.UTF_8)
        }
    }.fold(
        onSuccess = { standardBusinessDocument -> Ok(Pair(this, standardBusinessDocument)) },
        onFailure = { e ->
            logger.error(e) { "Could not prepend SBDH to payload" }
            Err(ErrorMessage.StandardBusinessDocument.FailedToPrependStandardBusinessDocumentHeader)
        }
    )
@PublishedApi
internal inline fun <reified T> String.parsePayload(): Result<T, ErrorMessage> =
    runCatching {
        JAXB.unmarshal(this.byteInputStream(), T::class.java)
    }.fold(
        onSuccess = { unmarshalledPayload -> Ok(unmarshalledPayload) },
        onFailure = { e ->
            logger.error(e) { "Could not unmarshal document payload" }
            Err(ErrorMessage.StandardBusinessDocument.FailedToParseDocumentType)
        }
    )
@PublishedApi
internal inline fun <reified T> T.mapToSbdh(): Result<Header, ErrorMessage> =
    when (this) {
        is OrderType -> this.mapToHeader()
        is InvoiceType -> this.mapToHeader()
        else -> {
            logger.error { "Received invalid DocumentType: '${T::class.simpleName}'" }
            Err(ErrorMessage.StandardBusinessDocument.InvalidDocumentType)
        }
    }
