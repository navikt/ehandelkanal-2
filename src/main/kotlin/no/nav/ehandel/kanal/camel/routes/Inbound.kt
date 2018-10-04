package no.nav.ehandel.kanal.camel.routes

import mu.KotlinLogging
import no.nav.ehandel.kanal.CamelHeader
import no.nav.ehandel.kanal.CamelHeader.EHF_DOCUMENT_TYPE
import no.nav.ehandel.kanal.EbasysProps
import no.nav.ehandel.kanal.FileAreaProps
import no.nav.ehandel.kanal.Metrics.messageSize
import no.nav.ehandel.kanal.Metrics.messagesFailed
import no.nav.ehandel.kanal.Metrics.messagesSuccessful
import no.nav.ehandel.kanal.QueueProps
import no.nav.ehandel.kanal.RouteId
import no.nav.ehandel.kanal.catalogueSizeLimit
import no.nav.ehandel.kanal.getHeader
import no.nav.ehandel.kanal.humanReadableByteCount
import org.apache.camel.Exchange
import org.apache.camel.LoggingLevel
import org.apache.camel.builder.RouteBuilder

private val LOGGER = KotlinLogging.logger { }
const val INBOUND_LOGGER_BEAN = "bean:inboundLogger"
const val INBOUND_SBDH_EXTRACTOR = "bean:inboundSbdhExtractor"
const val INBOUND_FTP_TEST_ROUTE = "inboundFtpConnectionTest"

val INBOUND_CATALOGUE = RouteId("catalogue", "direct:catalogue")
val INBOUND_EHF = RouteId("inboundEhf", "direct:inbound")
val INBOUND_EHF_ERROR = RouteId("inboundEhfError", "direct:inboundEhfError")

private val ebasysInbound = "${EbasysProps.url}?username=${EbasysProps.username}&password=${EbasysProps.password}" +
    "&binary=true&throwExceptionOnConnectFailed=true&tempFileName=\$simple{file:name}.inprogress&passiveMode=true"
private val ebasysInboundUnknownFiles = "${EbasysProps.url}/${EbasysProps.unknownFileDirectory}?username=${EbasysProps.username}" +
    "&password=${EbasysProps.password}&binary=true&throwExceptionOnConnectFailed=true&tempFileName=\$simple{file:name}.inprogress&passiveMode=true"
private val fileAreaInboundLargeCatalogues = "${FileAreaProps.eFaktura}/katalog" // TODO: Tilgang til filomraadet
private val mqInbound = "jms:queue:${QueueProps.inName}?connectionFactory=#mqConnectionFactory"

object Inbound : RouteBuilder() {
    override fun configure() {
        // Loop for testing FTP connection
        from("$ebasysInbound&initialDelay=0&delay=600000&noop=true&download=false&consumer.bridgeErrorHandler=true")
            .startupOrder(1)
            .routeId(INBOUND_FTP_TEST_ROUTE)
            .onException(Exception::class.java)
                .handled(true)
                .process { ex ->
                    LOGGER.error { "FTP connection failed" }
                    AccessPoint.readyToProcess.set(false)
                    ex.context.apply {
                        shutdownStrategy.isLogInflightExchangesOnTimeout = false
                        shutdownStrategy.timeout = 1
                    }
                    System.exit(1)
                }
            .end()
            .process { LOGGER.info { "Testing FTP connection - ${it.getHeader<String>(Exchange.FILE_NAME)}" } }

        // Errorhandler
        from(INBOUND_EHF_ERROR.uri).routeId(INBOUND_EHF_ERROR.id)
            .onException(Exception::class.java)
                .continued(true)
            .end()
            .to("log:no.nav.ehandel.kanal?level=ERROR&showCaughtException=true&showStackTrace=true&showBody=false&showBodyType=false")
            .process { LOGGER.error(it.exception) { "Failed delivery of incoming EHF (msgNo ${it.getHeader<String>(CamelHeader.MSG_NO)}" } }
            .to(ebasysInboundUnknownFiles)
            .process {
                LOGGER.info { "File transferred to EBASYS (manuellBehandling)" }
                messagesFailed.inc()
            }
            .to(ACCESS_POINT_READ.uri)

        // Main Route
        from(INBOUND_EHF.uri).routeId(INBOUND_EHF.id)
            .errorHandler(deadLetterChannel(INBOUND_EHF_ERROR.uri)
                .maximumRedeliveries(5)
                .redeliveryDelay(1000)
                .maximumRedeliveryDelay(5000)
                .retryAttemptedLogLevel(LoggingLevel.DEBUG))
            .process { LOGGER.info { "Processing inbound file" } }
            .to(INBOUND_SBDH_EXTRACTOR)
            .process { LOGGER.info { "SBDH removed" } }
            .setHeader(Exchange.FILE_LENGTH, simple("\${body.length()}"))
            .process {
                it.getHeader<Long>(Exchange.FILE_LENGTH).let { size ->
                    messageSize.observe(size.toDouble())
                    LOGGER.info { "Size: ${size.humanReadableByteCount()}" }
                }
            }
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
