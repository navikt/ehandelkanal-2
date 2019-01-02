package no.nav.ehandel.kanal.camel.processors

import io.ktor.util.toLocalDateTime
import mu.KotlinLogging
import no.difi.vefa.peppol.common.model.Header
import no.difi.vefa.peppol.sbdh.SbdReader
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
import java.time.format.DateTimeFormatter
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader
import javax.xml.stream.XMLStreamWriter

private val LOGGER = KotlinLogging.logger { }

object InboundSbdhMetaDataExtractor : Processor {

    const val CAMEL_XML_PROPERTY = "XML"

    override fun process(exchange: Exchange) {
        try {
            SbdReader.newInstance(exchange.getBody<InputStream>()).use { sbdReader ->
                val reader = sbdReader.xmlReader()
                extractSbdhMetadata(exchange, sbdReader.header, reader.localName)

                ByteArrayOutputStream().use { outputStream ->
                    val xmlStreamWriter = XMLOutputFactory.newFactory()
                        .createXMLStreamWriter(outputStream, reader.encoding)
                    copy(reader, xmlStreamWriter)
                    xmlStreamWriter.close()
                    exchange.getIn().body = outputStream.toString(reader.encoding)
                }
            }
            println(exchange.getBody<String>())
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

    private fun copy(reader: XMLStreamReader, writer: XMLStreamWriter) {
        var hasNext: Boolean
        do {
            when (reader.eventType) {
                XMLStreamConstants.START_DOCUMENT -> writer.writeStartDocument(reader.encoding, reader.version)
                XMLStreamConstants.END_DOCUMENT -> writer.writeEndDocument()
                XMLStreamConstants.START_ELEMENT -> {
                    writer.writeStartElement(reader.prefix, reader.localName, reader.namespaceURI)

                    for (i in 0 until reader.namespaceCount)
                        writer.writeNamespace(reader.getNamespacePrefix(i), reader.getNamespaceURI(i))
                    for (i in 0 until reader.attributeCount) {
                        val prefix = reader.getAttributePrefix(i)
                        if (prefix == null || "" == prefix)
                            writer.writeAttribute(reader.getAttributeLocalName(i), reader.getAttributeValue(i))
                        else
                            writer.writeAttribute(
                                prefix, reader.getAttributeNamespace(i),
                                reader.getAttributeLocalName(i), reader.getAttributeValue(i)
                            )
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
