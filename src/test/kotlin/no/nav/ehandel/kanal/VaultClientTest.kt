package no.nav.ehandel.kanal

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.bettercloud.vault.VaultException
import no.nav.ehandel.kanal.db.createVaultClient
import no.nav.ehandel.kanal.db.readSecret
import org.amshove.kluent.shouldBeEqualTo
import org.junit.AfterClass
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.junit.Test

private const val CREDS_PATH = "postgresql/preprod-fss/creds/ehandelkanal-user"
private const val FORBIDDEN_PATH = "postgresql/preprod-fss/creds/ehandelkanal-admin"

class VaultClientTest {

    companion object {
        private val server = WireMockServer(wireMockConfig().dynamicPort())

        @BeforeClass
        @JvmStatic
        fun setup() {
            server.start()
            server.stubFor(
                get(urlPathEqualTo("/v1/$CREDS_PATH")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "lease_id": "$CREDS_PATH/abc123",
                              "lease_duration": 3600,
                              "renewable": true,
                              "data": { "username": "db-user", "password": "db-password" }
                            }
                            """.trimIndent()
                        )
                )
            )
            server.stubFor(
                get(urlPathEqualTo("/v1/$FORBIDDEN_PATH")).willReturn(
                    aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{ "errors": ["permission denied"] }""")
                )
            )
        }

        @AfterClass
        @JvmStatic
        fun teardown() {
            server.stop()
        }
    }

    @Test
    fun `reading database credentials uses the path unchanged and sends the token`() {
        val client = createVaultClient(address = server.baseUrl(), token = "test-token")

        val response = client.readSecret(CREDS_PATH)

        response.data["username"] shouldBeEqualTo "db-user"
        response.data["password"] shouldBeEqualTo "db-password"
        response.leaseDuration shouldBeEqualTo 3600L
        server.verify(
            1,
            getRequestedFor(urlPathEqualTo("/v1/$CREDS_PATH"))
                .withHeader("X-Vault-Token", equalTo("test-token"))
        )
    }

    @Test
    fun `reading a secret without permission throws VaultException with status 403`() {
        val client = createVaultClient(address = server.baseUrl(), token = "test-token")

        val exception = assertThrows(VaultException::class.java) { client.readSecret(FORBIDDEN_PATH) }

        exception.httpStatusCode shouldBeEqualTo 403
    }
}
