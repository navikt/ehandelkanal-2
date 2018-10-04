package no.nav.ehandel.kanal

import no.nav.ehandel.kanal.CamelHeader.EHF_DOCUMENT_SENDER
import no.nav.ehandel.kanal.CamelHeader.EHF_DOCUMENT_TYPE
import no.nav.ehandel.kanal.CamelHeader.TRACE_ID
import no.nav.ehandel.kanal.camel.processors.InboundSbdhMetaDataExtractor
import org.amshove.kluent.AnyException
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotContain
import org.amshove.kluent.shouldNotThrow
import org.apache.camel.Exchange
import org.apache.camel.Exchange.FILE_NAME
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.impl.DefaultExchange
import org.junit.Test
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private val camelContext = DefaultCamelContext()

class InboundSbdhMetaDataExtractorTest {

    @Test
    fun `valid SBDH should extract correct metadata`() {
        val sender = "9908:810418052"
        val documentId = UUID.randomUUID().toString()
        val declaredDocumentType = "Catalogue"
        val actualDocumentType = "Catalogue"
        val creationDateAndTime = currentTimestamp()
        val document = createDocumentFromTemplate(sender, documentId, declaredDocumentType, actualDocumentType, creationDateAndTime)
        val exchange = document.createExchangeWithBody()
        InboundSbdhMetaDataExtractor.process(exchange)

        exchange.getHeader<String>(EHF_DOCUMENT_SENDER) shouldEqual sender.substringAfter(":")
        exchange.getHeader<String>(EHF_DOCUMENT_TYPE) shouldEqual declaredDocumentType
        exchange.getHeader<String>(EHF_DOCUMENT_TYPE) shouldEqual actualDocumentType
        exchange.getHeader<String>(FILE_NAME) shouldEqual "${creationDateAndTime.formattedTimestamp()}-$documentId.xml"
        exchange.getProperty(InboundSbdhMetaDataExtractor.CAMEL_XML_PROPERTY, Boolean::class.java).shouldBeTrue()
    }

    @Test
    fun `documenttype mismatch should set type to unknown`() {
        val sender = "9908:810418052"
        val documentId = UUID.randomUUID().toString()
        val declaredDocumentType = "Catalogue"
        val actualDocumentType = "Invoice"
        val creationDateAndTime = currentTimestamp()
        val document = createDocumentFromTemplate(sender, documentId, declaredDocumentType, actualDocumentType, creationDateAndTime)
        val exchange = document.createExchangeWithBody()
        InboundSbdhMetaDataExtractor.process(exchange)

        exchange.getHeader<String>(EHF_DOCUMENT_SENDER) shouldEqual sender.substringAfter(":")
        exchange.getHeader<String>(EHF_DOCUMENT_TYPE) shouldEqual "Unknown"
        exchange.getHeader<String>(FILE_NAME) shouldEqual "${creationDateAndTime.formattedTimestamp()}-$documentId.xml"
        exchange.getProperty(InboundSbdhMetaDataExtractor.CAMEL_XML_PROPERTY, Boolean::class.java).shouldBeTrue()
    }

    @Test
    fun `SBD without whitespace between SBDH and document`() {
        val document = "/inbound-invoice-no-whitespace-sbd-document.xml".getResource<String>()
        val exchange = document.createExchangeWithBody();
        { InboundSbdhMetaDataExtractor.process(exchange) } shouldNotThrow AnyException
        exchange.getProperty(InboundSbdhMetaDataExtractor.CAMEL_XML_PROPERTY, Boolean::class.java).shouldBeTrue()
    }

    @Test
    fun `minified SBD (all in one line)`() {
        val document = "/inbound-invoice-minified.xml".getResource<String>()
        val exchange = document.createExchangeWithBody();
        { InboundSbdhMetaDataExtractor.process(exchange) } shouldNotThrow AnyException
        exchange.getProperty(InboundSbdhMetaDataExtractor.CAMEL_XML_PROPERTY, Boolean::class.java).shouldBeTrue()
    }

    @Test
    fun `SBDH with prefix on DocumentIdentifier UUID`() {
        val document = "/inbound-catalogue-special-uuid.xml".getResource<String>()
        val exchange = document.createExchangeWithBody();
        { InboundSbdhMetaDataExtractor.process(exchange) } shouldNotThrow AnyException
        exchange.getHeader<String>(FILE_NAME) shouldNotContain ":"
        exchange.getProperty(InboundSbdhMetaDataExtractor.CAMEL_XML_PROPERTY, Boolean::class.java).shouldBeTrue()
    }

    @Test
    fun `SBDH with different timestamp`() {
        val document = "/inbound-catalogue-different-timestamp.xml".getResource<String>()
        val exchange = document.createExchangeWithBody();
        { InboundSbdhMetaDataExtractor.process(exchange) } shouldNotThrow AnyException
        exchange.getProperty(InboundSbdhMetaDataExtractor.CAMEL_XML_PROPERTY, Boolean::class.java).shouldBeTrue()
    }

    @Test
    fun `SBDH with another different timestamp`() {
        val document = "/inbound-catalogue-different-timestamp-2.xml".getResource<String>()
        val exchange = document.createExchangeWithBody();
        { InboundSbdhMetaDataExtractor.process(exchange) } shouldNotThrow AnyException
        exchange.getProperty(InboundSbdhMetaDataExtractor.CAMEL_XML_PROPERTY, Boolean::class.java).shouldBeTrue()
    }

    private fun createDocumentFromTemplate(
        sender: String,
        documentId: String,
        declaredDocumentType: String,
        actualDocumentType: String,
        creationDateAndTime: String
    ): String =
            "/inbound-sbdh-template.xml"
                .getResource<String>()
                .replace("@@SENDER@@", sender)
                .replace("@@DOCUMENTID@@", documentId)
                .replace("@@DECLAREDDOCTYPE@@", declaredDocumentType)
                .replace("ACTUALDOCTYPE", actualDocumentType)
                .replace("@@CREATIONDATEANDTIME@@", creationDateAndTime)
    private fun currentTimestamp(): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now())
    private fun String.formattedTimestamp(): String = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS")
        .format(ZonedDateTime.parse(this))
    private fun String.createExchangeWithBody(): Exchange = DefaultExchange(camelContext).apply {
        getIn().body = this@createExchangeWithBody
        getIn().setHeader(TRACE_ID, UUID.randomUUID().toString())
    }
}
