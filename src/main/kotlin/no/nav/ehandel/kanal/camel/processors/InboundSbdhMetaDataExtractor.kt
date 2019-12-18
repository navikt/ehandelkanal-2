package no.nav.ehandel.kanal.camel.processors

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader
import javax.xml.stream.XMLStreamWriter
import mu.KotlinLogging
import no.difi.vefa.peppol.common.model.Header
import no.difi.vefa.peppol.sbdh.SbdReader
import no.nav.ehandel.kanal.Metrics.messagesReceived
import no.nav.ehandel.kanal.common.constants.CamelHeader.EHF_DOCUMENT_SENDER
import no.nav.ehandel.kanal.common.constants.CamelHeader.EHF_DOCUMENT_TYPE
import no.nav.ehandel.kanal.common.extensions.getBody
import no.nav.ehandel.kanal.common.extensions.getHeader
import no.nav.ehandel.kanal.services.log.InboundLogger
import org.apache.camel.Exchange
import org.apache.camel.Exchange.FILE_NAME
import org.apache.camel.Processor

private val LOGGER = KotlinLogging.logger { }

object InboundSbdhMetaDataExtractor : Processor {

    const val CAMEL_XML_PROPERTY = "XML"

    override fun process(exchange: Exchange) {
        try {
            SbdReader.newInstance(exchange.getBody<InputStream>()).use { sbdReader ->
                val reader = sbdReader.xmlReader()
                extractSbdhMetadata(exchange, sbdReader.header, reader.localName)

                ByteArrayOutputStream().use { outputStream ->
                    XMLOutputFactory.newFactory().createXMLStreamWriter(outputStream, reader.encoding).run {
                        copy(reader, this)
                        close()
                    }
                    exchange.getIn().body = outputStream.toString(reader.encoding)
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
            exchange.getIn().setHeader(EHF_DOCUMENT_TYPE, "Unknown")
        }
    }

    private fun extractSbdhMetadata(exchange: Exchange, header: Header, documentType: String) {
        val documentId = header.identifier.identifier.substringAfterLast(":")
        val creationDateAndTime = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS")
            .format(header.creationTimestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
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

    private fun copy(reader: XMLStreamReader, writer: XMLStreamWriter) {
        var hasNext: Boolean
        do {
            when (reader.eventType) {
                XMLStreamConstants.START_DOCUMENT -> writer.writeStartDocument(reader.encoding, reader.version)
                XMLStreamConstants.END_DOCUMENT -> writer.writeEndDocument()
                XMLStreamConstants.START_ELEMENT -> {
                    writer.writeStartElement(reader.prefix, reader.localName, reader.namespaceURI)
                    repeat(reader.namespaceCount) { i ->
                        writer.writeNamespace(reader.getNamespacePrefix(i), reader.getNamespaceURI(i))
                    }
                    repeat(reader.attributeCount) { i ->
                        when (val prefix = reader.getAttributePrefix(i)) {
                            null, "" -> writer.writeAttribute(reader.getAttributeLocalName(i), reader.getAttributeValue(i))
                            else -> writer.writeAttribute(
                                prefix, reader.getAttributeNamespace(i),
                                reader.getAttributeLocalName(i), reader.getAttributeValue(i)
                            )
                        }
                    }
                }
                XMLStreamConstants.END_ELEMENT -> writer.writeEndElement()
                XMLStreamConstants.CHARACTERS -> writer.writeCharacters(reader.text)
                XMLStreamConstants.CDATA -> writer.writeCData(reader.text)
            }

            hasNext = reader.hasNext()
            if (hasNext) reader.next()
        } while (hasNext)
    }
}
