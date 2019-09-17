package no.nav.ehandel.kanal.camel.routes

import kotlin.system.exitProcess
import mu.KotlinLogging
import no.nav.ehandel.kanal.CamelHeader
import no.nav.ehandel.kanal.CamelHeader.EHF_DOCUMENT_TYPE
import no.nav.ehandel.kanal.EbasysProps
import no.nav.ehandel.kanal.FileAreaProps
import no.nav.ehandel.kanal.InvalidDocumentException
import no.nav.ehandel.kanal.LegalArchiveException
import no.nav.ehandel.kanal.Metrics.exhaustedDeliveriesErrorHandler
import no.nav.ehandel.kanal.Metrics.exhaustedDeliveriesLegalarchive
import no.nav.ehandel.kanal.Metrics.messageSize
import no.nav.ehandel.kanal.Metrics.messagesFailed
import no.nav.ehandel.kanal.Metrics.messagesSuccessful
import no.nav.ehandel.kanal.QueueProps
import no.nav.ehandel.kanal.RouteId
import no.nav.ehandel.kanal.catalogueSizeLimit
import no.nav.ehandel.kanal.getHeader
import no.nav.ehandel.kanal.humanReadableByteCount
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder

private val LOGGER = KotlinLogging.logger { }
const val INBOUND_LOGGER_BEAN = "bean:inboundLogger"
const val INBOUND_SBDH_EXTRACTOR = "bean:inboundSbdhExtractor"
const val INBOUND_FTP_TEST_ROUTE = "inboundFtpConnectionTest"
const val INBOUND_DATA_EXTRACTOR = "bean:inboundDataExtractor"

val INBOUND_CATALOGUE = RouteId("catalogue", "direct:catalogue")
val INBOUND_EHF = RouteId("inboundEhf", "direct:inbound")
val INBOUND_EHF_ERROR = RouteId("inboundEhfError", "direct:inboundEhfError")

private val ebasysInbound = "${EbasysProps.url}?username=${EbasysProps.username}&password=${EbasysProps.password}" +
    "&binary=true&throwExceptionOnConnectFailed=true&tempFileName=\$simple{file:name}.inprogress&passiveMode=true"
private val ebasysInboundUnknownFiles = "${EbasysProps.url}/${EbasysProps.unknownFileDirectory}?username=${EbasysProps.username}" +
    "&password=${EbasysProps.password}&binary=true&throwExceptionOnConnectFailed=true" +
    "&tempFileName=\$simple{file:name}.inprogress&passiveMode=true&timeout=300000"
private val fileAreaInboundLargeCatalogues = "${FileAreaProps.eFaktura}/katalog" // TODO: Tilgang til filomraadet
private val mqInbound = "jms:queue:${QueueProps.inName}?connectionFactory=#mqConnectionFactory"

private fun Exchange.shutdown(errorMessage: String) {
    LOGGER.error { errorMessage }
    AccessPoint.readyToProcess.set(false)
    exitProcess(1)
}

object Inbound : RouteBuilder() {
    override fun configure() {
        // Loop for testing FTP connection
        from("$ebasysInbound&initialDelay=0&delay=600000&noop=true&download=false&consumer.bridgeErrorHandler=true")
            .startupOrder(1)
            .routeId(INBOUND_FTP_TEST_ROUTE)
            .onException(Exception::class.java)
                .handled(true)
                .process { exchange ->
                    exchange.context.apply {
                        shutdownStrategy.isLogInflightExchangesOnTimeout = false
                        shutdownStrategy.timeout = 1
                    }
                    exchange.shutdown(errorMessage = "FTP connection failed")
                }
            .end()
            .process { LOGGER.info { "Testing FTP connection - ${it.getHeader<String>(Exchange.FILE_NAME)}" } }

        // Errorhandler
        from(INBOUND_EHF_ERROR.uri).routeId(INBOUND_EHF_ERROR.id)
            .onException(Exception::class.java)
                .maximumRedeliveries(30)
                .redeliveryDelay(1000)
                .useExponentialBackOff()
                .maximumRedeliveryDelay(10000)
                .handled(true)
                .process {
                    LOGGER.error {
                        "Exhausted all attempts to deliver failed message to manuellBehandling - retrying on next poll"
                    }
                    exhaustedDeliveriesErrorHandler.inc()
                }
            .end()
            .to("log:no.nav.ehandel.kanal?level=ERROR&showCaughtException=true&showStackTrace=true&showBody=false&showBodyType=false")
            .process {
                LOGGER.error {
                    "Failed delivery of incoming EHF (msgNo ${it.getHeader<String>(CamelHeader.MSG_NO)}) " +
                    "- attempting to deliver file to manuellBehandling"
                }
            }
            .to(ebasysInboundUnknownFiles)
            .process {
                LOGGER.info { "File transferred to EBASYS (manuellBehandling)" }
                messagesFailed.inc()
            }
            .to(ACCESS_POINT_READ.uri)

        // Main Route
        from(INBOUND_EHF.uri).routeId(INBOUND_EHF.id)
            .errorHandler(deadLetterChannel(INBOUND_EHF_ERROR.uri)
                .maximumRedeliveries(30)
                .redeliveryDelay(1000)
                .useExponentialBackOff()
                .maximumRedeliveryDelay(10000))
            .onException(LegalArchiveException::class.java)
                .maximumRedeliveries(30)
                .redeliveryDelay(1000)
                .useExponentialBackOff()
                .maximumRedeliveryDelay(10000)
                .process {
                    LOGGER.error { "Exhausted all attempts to log message to legal archive, continuing..." }
                    exhaustedDeliveriesLegalarchive.inc()
                }
                .continued(true)
            .end()
            .onException(InvalidDocumentException::class.java)
                .maximumRedeliveries(0)
                .to(INBOUND_EHF_ERROR.uri)
                .handled(true)
            .end()
            .onException(OutOfMemoryError::class.java)
                .maximumRedeliveries(0)
                .process { exchange ->
                    exchange.shutdown(errorMessage = "Ran out of memory, shutting down to trigger restart")
                }
            .end()
            .setHeader(Exchange.FILE_LENGTH, simple("\${body.length()}"))
            .process {
                LOGGER.info { "Processing inbound file" }
                it.getHeader<Long>(Exchange.FILE_LENGTH).let { size ->
                    LOGGER.info { "Size before SBDH removal: ${size.humanReadableByteCount()}" }
                }
            }
            .to(INBOUND_SBDH_EXTRACTOR)
            .process { LOGGER.info { "SBDH removed" } }
            .setHeader(Exchange.FILE_LENGTH, simple("\${body.length()}"))
            .process {
                it.getHeader<Long>(Exchange.FILE_LENGTH).let { size ->
                    messageSize.observe(size.toDouble())
                    LOGGER.info { "Size after SBDH removal: ${size.humanReadableByteCount()}" }
                }
            }
            .to(INBOUND_DATA_EXTRACTOR)
            .choice() // Content based routing
                .`when`(header(EHF_DOCUMENT_TYPE).isEqualTo("Catalogue"))
                    .to(INBOUND_CATALOGUE.uri)
                .`when`(header(EHF_DOCUMENT_TYPE).`in`("Invoice", "CreditNote"))
                    .to("$INBOUND_LOGGER_BEAN?method=logFileToLegalArchive")
                    .to(ebasysInbound)
                    .to("$INBOUND_LOGGER_BEAN?method=sentToEbasys")
                .`when`(header(EHF_DOCUMENT_TYPE).isEqualTo("OrderResponse"))
                    .to(mqInbound)
                    .to("$INBOUND_LOGGER_BEAN?method=sentToMq")
                .otherwise()
                    .process { LOGGER.error { "Unknown message in file" } }
                    .to(ebasysInboundUnknownFiles)
                    .process { LOGGER.error { "File transferred to EBASYS (manuellBehandling)" } }
            .end()
            .process { messagesSuccessful.inc() }

        // Conditional handling of catalogues based on size
        from(INBOUND_CATALOGUE.uri).routeId(INBOUND_CATALOGUE.id)
            .choice()
                .`when`(simple("\${header.CamelFileLength} > $catalogueSizeLimit"))
                    .process { LOGGER.warn { "File too large - size ${it.getHeader<Long>(Exchange.FILE_LENGTH)
                        .humanReadableByteCount()}, sending to file area" }
                    }
                    // TODO: Filearea
                    // .to(fileAreaInboundLargeCatalogues)
                    .to("$INBOUND_LOGGER_BEAN?method=sentToFileArea")
                .otherwise()
                    .to(mqInbound)
                    .to("$INBOUND_LOGGER_BEAN?method=sentToMq")
            .endChoice()
    }
}
