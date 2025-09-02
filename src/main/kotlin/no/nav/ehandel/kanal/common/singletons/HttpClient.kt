package no.nav.ehandel.kanal.common.singletons

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.BasicAuthCredentials
import io.ktor.client.features.auth.providers.basic
import no.nav.ehandel.kanal.ServiceUserProps

val httpClient: HttpClient =
    HttpClient(Apache) {
        engine {
            socketTimeout = 30_000
            connectTimeout = 30_000
            connectionRequestTimeout = 30_000
        }
        install(Auth) {
            basic {
                credentials {
                    BasicAuthCredentials(
                        username = ServiceUserProps.username,
                        password = ServiceUserProps.password
                    )
                }
                sendWithoutRequest { true }
            }
        }
    }
