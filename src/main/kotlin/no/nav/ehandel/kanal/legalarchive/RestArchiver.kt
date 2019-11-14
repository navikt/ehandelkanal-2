package no.nav.ehandel.kanal.legalarchive

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.basic
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.lang.Exception
import kotlinx.coroutines.runBlocking
import no.nav.ehandel.kanal.LegalArchiveException
import no.nav.ehandel.kanal.objectMapper

class RestArchiver(private val username: String, private val password: String, private val url: String) {
    private val httpClient: HttpClient = HttpClient(Apache) {
        engine {
            socketTimeout = 30_000
            connectTimeout = 30_000
            connectionRequestTimeout = 30_000
        }
        install(JsonFeature) {
            serializer = JacksonSerializer { objectMapper }
        }
        install(Auth) {
            basic {
                username = this@RestArchiver.username
                password = this@RestArchiver.password
                sendWithoutRequest = true
            }
        }
    }

    /**
     * Archives the document to Legal Archive by sending a POST request with the given ArchiveRequest instance,
     * returning the ID of the archive as a String if successful.
     * The method will throw an exception if the request resulted in an error
     * @param request an instance of ArchiveRequest - the body to be sent
     * @return the ID of the archive if the operation was successful
     * @throws LegalArchiveException if the request resulted in an error response from the endpoint
     */

    @Throws(Exception::class)
    fun archiveDocument(archiveRequest: ArchiveRequest): String {
        try {
            val response = runBlocking {
                httpClient.post<String> {
                    url(this@RestArchiver.url)
                    contentType(ContentType.Application.Json)
                    body = archiveRequest
                }
            }
            return objectMapper.readTree(response).get("id").asText()
        } catch (e: Throwable) {
            throw LegalArchiveException(
                e.localizedMessage ?: e.message ?: "Unknown error during logging to legal archive", e
            )
        }
    }
}
