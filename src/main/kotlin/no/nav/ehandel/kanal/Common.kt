package no.nav.ehandel.kanal

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.auth.basic.BasicAuth
import io.prometheus.client.CollectorRegistry
import org.apache.camel.Exchange

// Constants for setting values in the header of the Camel exchange
object CamelHeader {
    const val EHF_DOCUMENT_TYPE = "EHF_DOCUMENT_TYPE"
    const val EHF_DOCUMENT_SENDER = "EHF_DOCUMENT_SENDER"
    const val TRACE_ID = "MSG_UUID"
    const val MSG_NO = "MSG_NO"
}

data class RouteId(val id: String, val uri: String)

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

val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
fun Long.humanReadableByteCount(): String {
    val unit = 1000
    if (this < unit) return this.toString() + " B"
    val exp = (Math.log(this.toDouble()) / Math.log(unit.toDouble())).toInt()
    val pre = "kMGTPE"[exp - 1]
    return String.format("%.1f %sB", this / Math.pow(unit.toDouble(), exp.toDouble()), pre)
}

inline fun <reified T> Exchange.getBody(): T = getIn().getBody(T::class.java)
inline fun <reified T> Exchange.getHeader(name: String): T = getIn().getHeader(name, T::class.java)
