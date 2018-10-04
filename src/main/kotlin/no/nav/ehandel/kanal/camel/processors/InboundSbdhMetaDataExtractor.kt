package no.nav.ehandel.kanal.camel.processors

import io.ktor.util.toLocalDateTime
import mu.KotlinLogging
import no.difi.vefa.peppol.common.model.Header
import no.difi.vefa.peppol.sbdh.SbdReader
import no.difi.vefa.peppol.sbdh.util.XMLStreamUtils
import no.nav.ehandel.kanal.CamelHeader.EHF_DOCUMENT_SENDER
import no.nav.ehandel.kanal.CamelHeader.EHF_DOCUMENT_TYPE
import no.nav.ehandel.kanal.Metrics.messagesReceived
import no.nav.ehandel.kanal.getBody
import no.nav.ehandel.kanal.getHeader
import no.nav.ehandel.kanal.log.InboundLogger
import org.apache.camel.Exchange
import org.apache.camel.Exchange.FILE_NAME
import org.apache.camel.Processor
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter

private val LOGGER = KotlinLogging.logger { }

object InboundSbdhMetaDataExtractor : Processor {

    const val CAMEL_XML_PROPERTY = "XML"

    override fun process(exchange: Exchange) {
        try {
            SbdReader.newInstance(exchange.getBody<InputStream>()).use { sbdReader ->
                val reader = sbdReader.xmlReader()
                extractSbdhMetadata(exchange, sbdReader.header, reader.localName)

                ByteArrayOutputStream().use { outputStream ->
                    XMLStreamUtils.copy(reader, outputStream)
                    exchange.getIn().body = outputStream.toString(StandardCharsets.UTF_8.name())
                }
            }
            LOGGER.debug { "Exchange body contains XML. Setting exchangeproperty '$CAMEL_XML_PROPERTY' to true." }
            exchange.setProperty(CAMEL_XML_PROPERTY, "true")
            messagesReceived.labels(exchange.getHeader(EHF_DOCUMENT_TYPE)).inc()
            InboundLogger.fileReceived(exchange)
        } catch (e: Exception) {
            LOGGER.error(e) { "Error occurred during parsing of exchange body" }
            LOGGER.debug { "Exchange body probably does not contain XML. Setting exchangeproperty '$CAMEL_XML_PROPERTY' to false." }
            exchange.setProperty(CAMEL_XML_PROPERTY, "false")
        }
    }

    private fun extractSbdhMetadata(exchange: Exchange, header: Header, documentType: String) {
        val documentId = header.identifier.identifier.substringAfterLast(":")
        val creationDateAndTime = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS")
            .format(header.creationTimestamp.toLocalDateTime())
        val sender = header.sender.identifier.substringAfterLast(":")
        val declaredDocumentType = header.instanceType.type

        val fileName = "$creationDateAndTime-$documentId.xml"

        exchange.getIn().setHeader(EHF_DOCUMENT_SENDER, sender)
        if (documentType != declaredDocumentType) {
            LOGGER.error { "Actual DocumentType ($documentType) does not match declared DocumentType in SBDH ($declaredDocumentType)" }
            exchange.getIn().setHeader(EHF_DOCUMENT_TYPE, "Unknown")
        } else {
            exchange.getIn().setHeader(EHF_DOCUMENT_TYPE, documentType)
        }
        exchange.getIn().setHeader(FILE_NAME, fileName)
    }
}
