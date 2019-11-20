package no.nav.ehandel.kanal.services.legalarchive

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.ehandel.kanal.LegalArchiveProps
import no.nav.ehandel.kanal.ServiceUserProps
import no.nav.ehandel.kanal.common.extensions.getBody
import org.apache.camel.Exchange

private val LOGGER = KotlinLogging.logger { }
private val archiver = RestArchiver(ServiceUserProps.username, ServiceUserProps.password, LegalArchiveProps.endpointUrl)
const val LEGAL_ARCHIVE_CAMEL_HEADER = "LEGAL_ARCHIVE_LOG_ID"

// Logs entire files to Legal Archive
object LegalArchiveLogger {

    private fun setArchiveId(exchangeIn: Exchange, archiveId: String) {
        LOGGER.trace { "Setting $LEGAL_ARCHIVE_CAMEL_HEADER with value $archiveId" }
        exchangeIn.getIn().setHeader(LEGAL_ARCHIVE_CAMEL_HEADER, archiveId)
    }

    fun logArchive(exchange: Exchange, messageId: String, sender: String, receiver: String): String {
        val request = ArchiveRequest(
            messageId = messageId,
            messageContent = exchange.getBody(),
            sender = sender,
            receiver = receiver
        )
        val archiveId = runBlocking { archiver.archiveDocument(request) }
        setArchiveId(exchange, archiveId)
        return archiveId
    }
}
