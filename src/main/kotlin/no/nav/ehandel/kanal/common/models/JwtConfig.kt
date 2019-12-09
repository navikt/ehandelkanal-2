package no.nav.ehandel.kanal.common.models

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.ehandel.kanal.SecurityTokenServiceProps
import no.nav.ehandel.kanal.common.singletons.objectMapper

private val logger = KotlinLogging.logger { }
private val httpClient: HttpClient =
    HttpClient(Apache) {
        engine {
            socketTimeout = 30_000
            connectTimeout = 30_000
            connectionRequestTimeout = 30_000
        }
        install(JsonFeature) {
            serializer = JacksonSerializer { objectMapper }
        }
    }

class JwtConfig(private val stsProps: SecurityTokenServiceProps) {
    val openIdConfig: OpenIdConfiguration = runBlocking {
        httpClient.get<OpenIdConfiguration>(stsProps.wellKnownUrl)
    }
    val realm = "NAV"
    val jwkProvider: JwkProvider = JwkProviderBuilder(URL(openIdConfig.jwksUri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    fun validate(credentials: JWTCredential): JWTPrincipal? =
        try {
            requireNotNull(credentials.payload.audience) { "Auth: Missing audience in token" }
            require(stsProps.acceptedAudiences.any { allowedAudience ->
                credentials.payload.audience.contains(allowedAudience)
            }) { "Auth: Valid audience not found in claims" }
            logger.debug {
                "Auth: Resource requested by '${credentials.payload.audience.joinToString()}'"
            }
            JWTPrincipal(credentials.payload)
        } catch (e: Throwable) {
            logger.error(e) { "Auth: Token validation failed: ${e.message}" }
            null
        }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenIdConfiguration(
    @JsonProperty(value = "issuer", required = true)
    val issuer: String,
    @JsonProperty(value = "jwks_uri", required = true)
    val jwksUri: String
)
