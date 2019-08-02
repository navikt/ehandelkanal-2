package no.nav.ehandel.kanal

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.client.BasicCredentials
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockRule
import no.nav.ehandel.kanal.legalarchive.ArchiveRequest
import no.nav.ehandel.kanal.legalarchive.RestArchiver
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.withMessage
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

private val mapper = jacksonObjectMapper()
private const val port = 20000
private const val username = "username"
private const val password = "password"
private const val wrongPassword = "wrong"
private const val url = "http://localhost:$port/archive"

class RestArchiverTest {
    val request = ArchiveRequest(
        messageId = "1", messageContent = "hello".toByteArray(), sender = "sender",
        receiver = "receiver"
    )

    @Test
    fun `a valid request with valid credentials should succeed`() {
        val archiver = RestArchiver(username, password, url)
        val archiveId = archiver.archiveDocument(request)
        verify(
            1, WireMock.postRequestedFor(WireMock.urlEqualTo("/archive"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withBasicAuth(BasicCredentials(username, password))
                .withRequestBody(WireMock.equalToJson(mapper.writeValueAsString(request)))
        )
        archiveId shouldEqual "1"
    }

    @Test
    fun `a valid request with invalid credentials should fail`() {
        val archiver = RestArchiver(username, wrongPassword, url)
        val archiveId = { archiver.archiveDocument(request) }
        val expectedErrorMessage = "HTTP Exception 401 Unauthorized"
        archiveId shouldThrow Exception::class withMessage expectedErrorMessage
        verify(
            1, WireMock.postRequestedFor(WireMock.urlEqualTo("/archive"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withBasicAuth(BasicCredentials(username, wrongPassword))
                .withRequestBody(WireMock.equalToJson(mapper.writeValueAsString(request)))
        )
    }

    companion object {
        @ClassRule
        @JvmField
        val wireMockRule =
            WireMockRule(WireMockConfiguration.wireMockConfig().port(20000).notifier(Slf4jNotifier(true)))

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            stubFor(
                WireMock.post(WireMock.urlEqualTo("/archive"))
                    .atPriority(1)
                    .withBasicAuth(username, password)
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"id\":\"1\"}")
                    )
            )
            stubFor(
                WireMock.post(WireMock.urlEqualTo("/archive"))
                    .atPriority(2)
                    .withBasicAuth(username, wrongPassword)
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(401)
                            .withStatusMessage("Unauthorized")
                    )
            )
        }
    }
}
