package no.nav.ehandel.kanal.common.extensions

import io.ktor.features.origin
import io.ktor.request.ApplicationRequest

internal fun ApplicationRequest.url(): String {
    val port = when (origin.port) {
        in listOf(80, 443) -> ""
        else -> ":${origin.port}"
    }
    return "${origin.scheme}://${origin.host}$port${origin.uri}"
}
