package no.nav.ehandel.kanal.common.singletons

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache

val httpClient: HttpClient =
    HttpClient(Apache) {
        engine {
            socketTimeout = 30_000
            connectTimeout = 30_000
            connectionRequestTimeout = 30_000
        }
    }
