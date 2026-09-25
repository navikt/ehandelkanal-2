package no.nav.ehandel.kanal

import com.bettercloud.vault.VaultException
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.any
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import no.nav.ehandel.kanal.db.createVaultClient
import org.amshove.kluent.shouldBeEqualTo
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

private const val LOOKUP_SELF = "/v1/auth/token/lookup-self"
private const val RENEW_SELF = "/v1/auth/token/renew-self"

class VaultTokenRenewalTest {

    private val server = WireMockServer(wireMockConfig().dynamicPort())

    @Before
    fun setup() {
        server.start()
    }

    @After
    fun teardown() {
        server.stop()
    }

    private fun stubJson(path: String, status: Int, body: String) {
        server.stubFor(
            any(urlPathEqualTo(path)).willReturn(
                aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body)
            )
        )
    }

    @Test
    fun `lookupSelf reads ttl and renewable from the token`() {
        stubJson(LOOKUP_SELF, 200, """{ "data": { "ttl": 3600, "renewable": true, "policies": ["default"] } }""")

        val lookup = createVaultClient(address = server.baseUrl(), token = "test-token").auth().lookupSelf()

        lookup.ttl shouldBeEqualTo 3600L
        lookup.isRenewable shouldBeEqualTo true
        server.verify(anyRequestedFor(urlPathEqualTo(LOOKUP_SELF)).withHeader("X-Vault-Token", equalTo("test-token")))
    }

    @Test
    fun `renewSelf reads the new lease duration`() {
        stubJson(
            RENEW_SELF,
            200,
            """
            {
              "request_id": "8f1b2c3d",
              "lease_id": "",
              "renewable": false,
              "lease_duration": 0,
              "data": null,
              "auth": {
                "client_token": "test-token",
                "accessor": "accessor-id",
                "policies": ["default", "ehandelkanal"],
                "token_policies": ["default", "ehandelkanal"],
                "metadata": null,
                "lease_duration": 7200,
                "renewable": true
              }
            }
            """.trimIndent()
        )

        val response = createVaultClient(address = server.baseUrl(), token = "test-token").auth().renewSelf()

        response.authLeaseDuration shouldBeEqualTo 7200L
        server.verify(anyRequestedFor(urlPathEqualTo(RENEW_SELF)).withHeader("X-Vault-Token", equalTo("test-token")))
    }

    @Test
    fun `renewSelf throws VaultException when the token can no longer be renewed`() {
        stubJson(RENEW_SELF, 403, """{ "errors": ["permission denied"] }""")

        val client = createVaultClient(address = server.baseUrl(), token = "test-token")
        val exception = assertThrows(VaultException::class.java) { client.auth().renewSelf() }

        exception.httpStatusCode shouldBeEqualTo 403
    }
}
