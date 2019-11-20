package no.nav.ehandel.kanal.camel.processors

import java.io.InputStream
import javax.xml.bind.JAXB
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.difi.commons.ubl21.jaxb.CreditNoteType
import no.difi.commons.ubl21.jaxb.InvoiceType
import no.nav.ehandel.kanal.Metrics
import no.nav.ehandel.kanal.common.InvalidDocumentException
import no.nav.ehandel.kanal.common.constants.CamelHeader
import no.nav.ehandel.kanal.common.constants.CamelHeader.EHF_DOCUMENT_TYPE
import no.nav.ehandel.kanal.common.extensions.getBody
import no.nav.ehandel.kanal.common.extensions.getHeader
import no.nav.ehandel.kanal.common.functions.retry
import no.nav.ehandel.kanal.common.models.DocumentType
import no.nav.ehandel.kanal.services.report.CsvValues
import no.nav.ehandel.kanal.services.report.Report
import org.apache.camel.Exchange
import org.apache.camel.Exchange.FILE_NAME
import org.apache.camel.Processor
import org.joda.time.DateTime
import org.joda.time.LocalDateTime

private val LOGGER = KotlinLogging.logger { }

object InboundDataExtractor : Processor {

    override fun process(exchange: Exchange) {
        runBlocking {
            extractCsvValues(exchange)?.let { values ->
                try {
                    LOGGER.debug { "Attempting to insert entry: ${values.logString()}" }
                    retry(
                        callName = "Report Table Insert",
                        attempts = 100,
                        maxDelay = 60_000
                    ) {
                        Report.insert(values)
                    }
                    LOGGER.info { "Entry successfully inserted: ${values.logString()}" }
                } catch (e: Throwable) {
                    LOGGER.error(e) { "Could not insert new entry into report table" }
                    Metrics.exhaustedDeliveriesReport.inc()
                    throw e
                }
            }
        }
    }

    private fun extractCsvValues(exchange: Exchange): CsvValues? = try {
        when (val documentType = DocumentType.valueOfOrDefault(exchange.getHeader(EHF_DOCUMENT_TYPE))) {
            DocumentType.Invoice -> {
                JAXB.unmarshal(exchange.getBody<InputStream>(), InvoiceType::class.java).let { invoice ->
                    CsvValues(
                        fileName = exchange.getHeader(FILE_NAME),
                        type = documentType,
                        orgnummer = invoice?.accountingSupplierParty?.party?.endpointID?.value ?: exchange.getHeader(
                            CamelHeader.EHF_DOCUMENT_SENDER),
                        fakturanummer = invoice?.id?.value,
                        navn = invoice?.accountingSupplierParty?.party?.partyName?.firstOrNull()?.name?.value,
                        belop = invoice?.legalMonetaryTotal?.payableAmount?.value,
                        valuta = invoice?.legalMonetaryTotal?.payableAmount?.currencyID,
                        mottattDato = DateTime.now().withTimeAtStartOfDay(),
                        fakturaDato = LocalDateTime(invoice?.issueDate?.value?.toGregorianCalendar()?.timeInMillis).toDateTime()
                    )
                }
            }
            DocumentType.CreditNote -> {
                JAXB.unmarshal(exchange.getBody<InputStream>(), CreditNoteType::class.java).let { creditNote ->
                    CsvValues(
                        fileName = exchange.getHeader(FILE_NAME),
                        type = documentType,
                        orgnummer = creditNote?.accountingSupplierParty?.party?.endpointID?.value ?: exchange.getHeader(
                            CamelHeader.EHF_DOCUMENT_SENDER),
                        fakturanummer = creditNote?.id?.value,
                        navn = creditNote?.accountingSupplierParty?.party?.partyName?.firstOrNull()?.name?.value,
                        belop = creditNote?.legalMonetaryTotal?.payableAmount?.value,
                        valuta = creditNote?.legalMonetaryTotal?.payableAmount?.currencyID,
                        mottattDato = DateTime.now().withTimeAtStartOfDay(),
                        fakturaDato = LocalDateTime(creditNote?.issueDate?.value?.toGregorianCalendar()?.timeInMillis).toDateTime()
                    )
                }
            }
            else -> {
                LOGGER.info { "Unsupported document type: '${documentType.name}'" }
                null
            }
        }
    } catch (e: Throwable) {
        val message = "Failed to unmarshal and extract data from inbound exchange - likely invalid message"
        LOGGER.error(e) { message }
        Metrics.xmlParsingErrorsCounter.inc()
        throw InvalidDocumentException(message = message, cause = e)
    }
}
