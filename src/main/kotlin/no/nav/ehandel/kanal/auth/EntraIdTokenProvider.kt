package no.nav.ehandel.kanal.auth

import no.nav.ehandel.kanal.common.singletons.objectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class EntraIdTokenProvider(
    private val tokenTarget: String) {

    private val tokenEndpoint: String = System.getenv("NAIS_TOKEN_ENDPOINT") ?: "http://token-provider/token"
    private val client = HttpClient.newHttpClient()

    fun getToken(): String {
        val requestBody = "{\"identity_provider\": \"entra_id\", \"target\": \"$tokenTarget\"}"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val json = objectMapper.readTree(response.body())
        return json.get("access_token").asText()
    }
}
