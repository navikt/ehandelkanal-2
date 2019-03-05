package no.nav.ehandel.kanal

import io.ktor.application.install
import io.ktor.features.CallId
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.features.callIdMdc
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.jackson.JacksonConverter
import io.ktor.request.path
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.ehandel.kanal.camel.processors.AccessPointClient
import no.nav.ehandel.kanal.camel.processors.InboundSbdhMetaDataExtractor
import no.nav.ehandel.kanal.camel.processors.InboundDataExtractor
import no.nav.ehandel.kanal.camel.routes.AccessPoint
import no.nav.ehandel.kanal.camel.routes.Inbound
import no.nav.ehandel.kanal.camel.routes.logAndSet
import no.nav.ehandel.kanal.db.Database
import no.nav.ehandel.kanal.log.InboundLogger
import no.nav.ehandel.kanal.routes.exceptionHandler
import no.nav.ehandel.kanal.routes.nais
import no.nav.ehandel.kanal.routes.notFoundHandler
import no.nav.ehandel.kanal.routes.report
import org.apache.camel.CamelContext
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.impl.SimpleRegistry
import org.apache.camel.spi.Registry
import org.slf4j.event.Level
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger { }
private val backgroundTaskContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

fun main() = runBlocking {
    val applicationState = ApplicationState()
    val camelContext = configureCamelContext(defaultRegistry())
    val server = createHttpServer(applicationState = applicationState)
    bootstrap(camelContext, server, applicationState)

    try {
        val job = launch {
            while (applicationState.running) {
                delay(100)
            }
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info { "Shutdown hook called, shutting down gracefully" }
            AccessPoint.readyToProcess.logAndSet(false)
            camelContext.stop()
            applicationState.fail()
            server.stop(5, 10, TimeUnit.SECONDS)
        })

        applicationState.initialized = true
        logger.info { "Application ready" }

        job.join()
    } finally {
        exitProcess(1)
    }
}

fun bootstrap(camelContext: CamelContext, server: ApplicationEngine, applicationState: ApplicationState) {
    DefaultExports.initialize()
    AccessPointClient.init()
    camelContext.start()
    server.start(wait = false)
    runBlocking {
        launch(backgroundTaskContext) {
            try {
                Database(applicationState).init()
            } catch (e: Throwable) {
                logger.error(e) { "Database jobs were cancelled, failing self tests" }
                applicationState.running = false
            }
        }
    }
}

fun defaultRegistry() = SimpleRegistry().apply {
    put("accessPointClient", AccessPointClient)
    put("inboundLogger", InboundLogger)
    put("inboundSbdhExtractor", InboundSbdhMetaDataExtractor)
    put("inboundDataExtractor", InboundDataExtractor)
    put("mqConnectionFactory", mqConnectionFactory)
}

fun configureCamelContext(registry: Registry) = DefaultCamelContext(registry).apply {
    shutdownStrategy.apply {
        timeout = 15
        timeUnit = TimeUnit.SECONDS
    }
    disableJMX()
    addRoutes(AccessPoint)
    addRoutes(Inbound)
    name = appName
}

fun createHttpServer(port: Int = 8080, applicationState: ApplicationState) = embeddedServer(Netty, port) {
    install(StatusPages) {
        notFoundHandler()
        exceptionHandler()
    }
    install(CallLogging) {
        level = Level.INFO
        filter { call -> !call.request.path().startsWith("/internal") }
        callIdMdc(MDC_CALL_ID)
    }
    install(CallId) {
        generate { randomUuid() }
        verify { callId: String -> callId.isNotEmpty() }
        header(HttpHeaders.XCorrelationId)
    }
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(objectMapper))
    }
    routing {
        nais(readinessCheck = { applicationState.initialized }, livenessCheck = { applicationState.running })
        report()
    }
}
