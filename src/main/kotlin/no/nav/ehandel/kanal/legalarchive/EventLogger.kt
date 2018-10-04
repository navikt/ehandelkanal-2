package no.nav.ehandel.kanal.legalarchive

import mu.KotlinLogging
import org.apache.camel.Exchange

private val LOGGER = KotlinLogging.logger { }

object EventLogger {

    // TODO: Temporary 'interface' for future event logger
    fun logToEventLog(event: String, exchange: Exchange) {
        // val id: String = exchange.getHeader(CamelHeader.TRACE_ID)
        LOGGER.info { event }
    }
}
