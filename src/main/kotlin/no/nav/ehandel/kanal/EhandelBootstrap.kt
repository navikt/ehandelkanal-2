package no.nav.ehandel.kanal

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.jwt
import io.ktor.features.CallId
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.features.callIdMdc
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.jackson.JacksonConverter
import io.ktor.request.path
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.prometheus.client.hotspot.DefaultExports
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.ehandel.kanal.camel.processors.AccessPointClient
import no.nav.ehandel.kanal.camel.processors.InboundDataExtractor
import no.nav.ehandel.kanal.camel.processors.InboundSbdhMetaDataExtractor
import no.nav.ehandel.kanal.camel.routes.AccessPoint
import no.nav.ehandel.kanal.camel.routes.Inbound
import no.nav.ehandel.kanal.camel.routes.logAndSet
import no.nav.ehandel.kanal.common.constants.MDC_CALL_ID
import no.nav.ehandel.kanal.common.functions.randomUuid
import no.nav.ehandel.kanal.common.functions.retry
import no.nav.ehandel.kanal.common.models.ApplicationState
import no.nav.ehandel.kanal.common.models.JwtConfig
import no.nav.ehandel.kanal.common.singletons.objectMapper
import no.nav.ehandel.kanal.db.Database
import no.nav.ehandel.kanal.db.Vault
import no.nav.ehandel.kanal.routes.exceptionHandler
import no.nav.ehandel.kanal.routes.nais
import no.nav.ehandel.kanal.routes.notFoundHandler
import no.nav.ehandel.kanal.routes.report
import no.nav.ehandel.kanal.services.log.InboundLogger
import no.nav.ehandel.kanal.services.outbound.OutboundMessageService
import no.nav.ehandel.kanal.services.outbound.outbound
import no.nav.ehandel.kanal.services.sbd.StandardBusinessDocumentGenerator
import org.apache.camel.CamelContext
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.impl.SimpleRegistry
import org.apache.camel.spi.Registry
import org.slf4j.event.Level

private val logger = KotlinLogging.logger { }
private val backgroundTaskContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

fun main() = runBlocking {
    val applicationState = ApplicationState()
    val camelContext = configureCamelContext(defaultRegistry())
    val server = createHttpServer(applicationState = applicationState)
    bootstrap(camelContext, server)

    when (appProfileLocal) {
        true -> Database.initLocal()
        false -> {
            launchBackgroundTask(
                applicationState = applicationState,
                callName = "Vault - Token Renewal Task",
                maxDelay = 60_000L,
                attempts = 10
            ) {
                Vault.renewVaultTokenTask(applicationState)
            }
            launchBackgroundTask(
                applicationState = applicationState,
                callName = "DB - Credentials Renewal Task",
                maxDelay = 60_000L,
                attempts = 10
            ) {
                Database.runRenewCredentialsTask(Database.initRemote()) { applicationState.running }
            }
        }
    }

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

fun bootstrap(camelContext: CamelContext, server: ApplicationEngine) {
    DefaultExports.initialize()
    AccessPointClient.init()
    camelContext.start()
    server.start(wait = false)
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

fun createHttpServer(port: Int = 8080, applicationState: ApplicationState) =
    embeddedServer(Netty, port, module = { main(applicationState) })

fun Application.main(
    applicationState: ApplicationState = ApplicationState(running = true, initialized = true),
    outboundMessageService: OutboundMessageService = OutboundMessageService(
        AccessPointClient, StandardBusinessDocumentGenerator()
    )
) {
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
    install(Authentication) {
        jwt {
            val jwtConfig = JwtConfig(SecurityTokenServiceProps)
            skipWhen { appProfileLocal }
            realm = jwtConfig.realm
            verifier(jwtConfig.jwkProvider, jwtConfig.openIdConfig.issuer)
            validate { credentials -> jwtConfig.validate(credentials) }
        }
    }
    routing {
        nais(readinessCheck = { applicationState.initialized }, livenessCheck = { applicationState.running })
        report()
        authenticate {
            route("/api/v1") {
                outbound(outboundMessageService = outboundMessageService)
            }
        }
    }
}

private fun CoroutineScope.launchBackgroundTask(
    applicationState: ApplicationState,
    callName: String,
    attempts: Int,
    maxDelay: Long,
    block: suspend () -> Any
) {
    launch(backgroundTaskContext) {
        try {
            retry(
                callName = callName,
                attempts = attempts,
                maxDelay = maxDelay
            ) {
                block()
            }
        } catch (e: Throwable) {
            logger.error(e) { "Background task '$callName' was cancelled, failing self tests" }
            applicationState.running = false
        }
    }
}
