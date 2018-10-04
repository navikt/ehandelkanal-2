package no.nav.ehandel.kanal.log

import no.nav.common.juridisklogg.client.LegalArchiveException
import no.nav.ehandel.kanal.CamelHeader
import no.nav.ehandel.kanal.CamelHeader.TRACE_ID
import no.nav.ehandel.kanal.LoggerProps
import no.nav.ehandel.kanal.catalogueSizeLimit
import no.nav.ehandel.kanal.getHeader
import no.nav.ehandel.kanal.legalarchive.EventLogger
import no.nav.ehandel.kanal.legalarchive.LegalArchiveLogger
import org.apache.camel.Exchange

object InboundLogger {
    fun downloadInboundMessage(exchange: Exchange, msgNo: String) {
        EventLogger.logToEventLog("Downloading payload of message with MsgNo $msgNo", exchange)
    }

    fun fileReceived(exchange: Exchange) {
        val senderOrgNo: String = exchange.getHeader(CamelHeader.EHF_DOCUMENT_SENDER)
        val documentType: String = exchange.getHeader(CamelHeader.EHF_DOCUMENT_TYPE)
        val fileName: String = exchange.getHeader(Exchange.FILE_NAME)

        EventLogger.logToEventLog("Received '$documentType' from access point", exchange)
        EventLogger.logToEventLog("Sender OrgNo is '$senderOrgNo'", exchange)
        EventLogger.logToEventLog("File name set to '$fileName'", exchange)
    }

    fun logFileToLegalArchive(exchange: Exchange) {
        val id: String = exchange.getHeader(TRACE_ID)
        EventLogger.logToEventLog("Attempting to log message to legal archive", exchange)
        try {
            val archiveId = LegalArchiveLogger.logArchive(exchange, id, LoggerProps.urnEhandel, LoggerProps.urnNav)
            EventLogger.logToEventLog("Successfully logged document to Legal Archive with archive ID: $archiveId", exchange)
        } catch (e: LegalArchiveException) {
            EventLogger.logToEventLog("Failed to log to legal archive: ${e.message}", exchange)
            throw e
        }
    }

    fun sentToFileArea(exchange: Exchange) {
        EventLogger.logToEventLog("Inbound EHF put on file area due to exceeding file size limits ($catalogueSizeLimit)", exchange)
        finishedProcessing(exchange)
    }

    fun sentToMq(exchange: Exchange) {
        EventLogger.logToEventLog("Inbound EHF sent via MQ to internal systems", exchange)
        finishedProcessing(exchange)
    }

    fun sentToEbasys(exchange: Exchange) {
        EventLogger.logToEventLog("Inbound message successfully sent to EBASYS via FTP", exchange)
        finishedProcessing(exchange)
    }

    private fun finishedProcessing(exchange: Exchange) {
        EventLogger.logToEventLog("Message processing successfully completed", exchange)
    }
}
