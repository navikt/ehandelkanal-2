package no.nav.ehandel.kanal

import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot.DefaultExports
import no.nav.ehandel.kanal.camel.processors.AccessPointClient
import no.nav.ehandel.kanal.camel.processors.InboundSbdhMetaDataExtractor
import no.nav.ehandel.kanal.camel.routes.AccessPoint
import no.nav.ehandel.kanal.camel.routes.Inbound
import no.nav.ehandel.kanal.camel.routes.logAndSet
import no.nav.ehandel.kanal.log.InboundLogger
import org.apache.camel.CamelContext
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.impl.SimpleRegistry
import org.apache.camel.spi.Registry
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.lens.string
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.ApacheServer
import org.http4k.server.Http4kServer
import org.http4k.server.asServer
import java.io.StringWriter
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    bootstrap(configureCamelContext(defaultRegistry()), createHttpServer())
}

fun bootstrap(camelContext: CamelContext, server: Http4kServer) {
    DefaultExports.initialize()
    AccessPointClient.init()
    camelContext.start()
    server.start()
    Runtime.getRuntime().addShutdownHook(Thread {
        AccessPoint.readyToProcess.logAndSet(false)
        camelContext.stop()
        server.stop()
    })
}

fun defaultRegistry() = SimpleRegistry().apply {
    put("accessPointClient", AccessPointClient)
    put("inboundLogger", InboundLogger)
    put("inboundSbdhExtractor", InboundSbdhMetaDataExtractor)
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

fun createHttpServer(port: Int = 8080) = routes(
    "/internal" bind routes(
        "/is_alive" bind Method.GET to { _ -> Response(Status.OK)
            .with(Body.string(ContentType.TEXT_PLAIN).toLens() of "I'm alive")
        },
        "/is_ready" bind Method.GET to { _ -> Response(Status.OK)
            .with(Body.string(ContentType.TEXT_PLAIN).toLens() of "I'm ready")
        },
        "/prometheus" bind Method.GET to { request ->
            val names = request.queries("name[]").toSet()
            val writer = StringWriter()
            TextFormat.write004(writer, collectorRegistry.filteredMetricFamilySamples(names))
            Response(Status.OK)
                .replaceHeader("Content-Type", TextFormat.CONTENT_TYPE_004)
                .body(writer.toString())
        }
    )
).asServer(ApacheServer(port))
