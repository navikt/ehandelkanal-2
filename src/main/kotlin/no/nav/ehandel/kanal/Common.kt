package no.nav.ehandel.kanal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.auth.basic.BasicAuth
import io.ktor.features.origin
import io.ktor.request.ApplicationRequest
import mu.KotlinLogging
import org.apache.camel.Exchange
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.slf4j.MDC
import java.util.UUID

private val logger = KotlinLogging.logger { }

internal const val MDC_CALL_ID = "callId"

// Constants for setting values in the header of the Camel exchange
object CamelHeader {
    const val EHF_DOCUMENT_TYPE = "EHF_DOCUMENT_TYPE"
    const val EHF_DOCUMENT_SENDER = "EHF_DOCUMENT_SENDER"
    const val TRACE_ID = "MSG_UUID"
    const val MSG_NO = "MSG_NO"
}

val httpClient: HttpClient = HttpClient(Apache) {
    install(BasicAuth) {
        username = ServiceUserProps.username
        password = ServiceUserProps.password
    }
    engine {
        socketTimeout = 30_000
        connectTimeout = 30_000
        connectionRequestTimeout = 30_000
    }
}

val objectMapper = ObjectMapper()
    .registerKotlinModule()
    .registerModule(JodaModule())
    .configure(SerializationFeature.INDENT_OUTPUT, true)

internal fun randomUuid() = UUID.randomUUID().toString()
internal fun getCorrelationId(): String = MDC.get(MDC_CALL_ID)

fun DateTime.formatDate(): String = DateTimeFormat.forPattern("yyyy-MM-dd").print(this)

fun Long.humanReadableByteCount(): String {
    val unit = 1000
    if (this < unit) return "$this B"
    val exp = (Math.log(this.toDouble()) / Math.log(unit.toDouble())).toInt()
    val pre = "kMGTPE"[exp - 1]
    return String.format("%.1f %sB", this / Math.pow(unit.toDouble(), exp.toDouble()), pre)
}
internal fun ApplicationRequest.url(): String {
    val port = when (origin.port) {
        in listOf(80, 443) -> ""
        else -> ":${origin.port}"
    }
    return "${origin.scheme}://${origin.host}$port${origin.uri}"
}

inline fun <reified T> Exchange.getBody(): T = getIn().getBody(T::class.java)
inline fun <reified T> Exchange.getHeader(name: String): T = getIn().getHeader(name, T::class.java)

enum class DocumentType {
    Invoice, CreditNote, OrderResponse, Catalogue, Unknown;

    companion object {
        fun valueOfOrDefault(value: String, defaultValue: DocumentType = Unknown): DocumentType =
            try {
                DocumentType.valueOf(value)
            } catch (e: Throwable) {
                defaultValue
            }
    }
}

data class RouteId(val id: String, val uri: String)

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false) {
    fun fail() {
        this.running = false
        this.initialized = false
        logger.warn { "Application self tests failing - expect imminent shutdown" }
    }
}
