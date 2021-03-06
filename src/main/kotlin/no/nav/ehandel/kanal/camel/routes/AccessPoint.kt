package no.nav.ehandel.kanal.camel.routes

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import mu.KotlinLogging
import no.nav.ehandel.kanal.Metrics.messagesReceivedTotal
import no.nav.ehandel.kanal.common.constants.CamelHeader
import no.nav.ehandel.kanal.common.extensions.getBody
import no.nav.ehandel.kanal.common.extensions.getHeader
import no.nav.ehandel.kanal.common.models.RouteId
import org.apache.camel.LoggingLevel
import org.apache.camel.builder.RouteBuilder
import org.slf4j.MDC

private val LOGGER = KotlinLogging.logger { }
private val threadPool = Executors.newFixedThreadPool(6)

const val ACCESS_POINT_CLIENT = "bean:accessPointClient"

val INBOX_QUEUE = RouteId("inboxQueue", "direct:inboxQueue")
val INBOX_POLLER =
    RouteId("inboxPoller", "timer://inboxpoller?period=10s")
val ACCESS_POINT_READ =
    RouteId("accessPointRead", "direct:accessPointRead")

fun AtomicBoolean.logAndSet(newValue: Boolean) {
    if (newValue) LOGGER.debug { "Enable inbox poller" }
    else LOGGER.debug { "Disable inbox poller" }
    set(newValue)
}

object AccessPoint : RouteBuilder() {
    val readyToProcess = AtomicBoolean(true)

    override fun configure() {
        // Timer based polling of accessPoint inbox for this applications serviceuser
        // Routed to in-memory queue for further processing
        from(INBOX_POLLER.uri).routeId(INBOX_POLLER.id)
            .startupOrder(8)
            .filter { readyToProcess.get() }
            .to("$ACCESS_POINT_CLIENT?method=getInboxMessageHeaders(*)")
            .process { readyToProcess.logAndSet(false) }
            .split()
                .tokenizeXML("message")
                .streaming()
                .executorService(threadPool)
                .process { LOGGER.info { it.getBody<String>() } }
                .to(INBOX_QUEUE.uri)
            .end()
            .process { readyToProcess.logAndSet(true) }

        // Download the payload of a message, extract SBDH payload and route to existing route for further processing
        // Message is marked as read at the end of processing
        from(INBOX_QUEUE.uri).routeId(INBOX_QUEUE.id)
            .onException(Exception::class.java)
                .to("log:inboxQueue?level=INFO&showCaughtException=true&showStackTrace=true")
                .process { LOGGER.info { "Exception during download, likely network issues - silently ignore and retry on next poll" } }
                .handled(true)
            .end()
            .setHeader(CamelHeader.TRACE_ID, xpath("/message/message-meta-data/uuid/text()", String::class.java))
            .setHeader(CamelHeader.MSG_NO, xpath("/message/message-meta-data/msg-no/text()", String::class.java))
            .process { MDC.put("callId", it.getHeader(CamelHeader.TRACE_ID)) }
            .to("$ACCESS_POINT_CLIENT?method=downloadMessagePayload(*, \${header.MSG_NO})")
            .process { messagesReceivedTotal.inc() }
            .to(INBOUND_EHF.uri)
            .to(ACCESS_POINT_READ.uri)
            .process { MDC.clear() }

        // Separate route for marking message as read with redelivery in case of failures to prevent reprocessing a message
        from(ACCESS_POINT_READ.uri).routeId(ACCESS_POINT_READ.id)
            .errorHandler(defaultErrorHandler().logHandled(true)
                .retryAttemptedLogLevel(LoggingLevel.ERROR)
                .maximumRedeliveries(300)
                .redeliveryDelay(5000)
                .useExponentialBackOff()
                .maximumRedeliveryDelay(300000)
            )
            .to("$ACCESS_POINT_CLIENT?method=markMessageAsRead(\${header.MSG_NO})")
    }
}
