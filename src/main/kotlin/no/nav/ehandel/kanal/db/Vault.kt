package no.nav.ehandel.kanal.db

import com.bettercloud.vault.SslConfig
import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import com.bettercloud.vault.VaultException
import com.bettercloud.vault.response.LogicalResponse
import java.io.File
import kotlinx.coroutines.delay
import mu.KotlinLogging
import no.nav.ehandel.kanal.common.models.ApplicationState

private val logger = KotlinLogging.logger { }

internal fun createVaultClient(address: String, token: String): Vault = Vault(
    VaultConfig()
        .address(address)
        .token(token)
        .openTimeout(5)
        .readTimeout(30)
        .sslConfig(SslConfig().build())
        .build(),
    1
)

internal fun Vault.readSecret(path: String): LogicalResponse {
    val response = logical().read(path)
    val status = response.restResponse.status
    if (status !in 200..299) {
        throw VaultException("Vault responded with HTTP status code: $status", status)
    }
    return response
}

object Vault {
    private const val MIN_REFRESH_MARGIN = 600_000L // 10 minutes
    private val vaultToken: String = System.getenv("VAULT_TOKEN")
        ?: getTokenFromFile()
        ?: throw RuntimeException("Neither VAULT_TOKEN or VAULT_TOKEN_PATH is set")
    val client: Vault = createVaultClient(
        address = System.getenv("VAULT_ADDR") ?: "https://vault.adeo.no",
        token = vaultToken
    )

    suspend fun renewVaultTokenTask(applicationState: ApplicationState) {
        val lookupSelf = client.auth().lookupSelf()
        if (lookupSelf.isRenewable) {
            delay(suggestedRefreshIntervalInMillis(lookupSelf.ttl * 1000))
            while (applicationState.running) {
                try {
                    logger.debug("Refreshing Vault token (old TTL: ${client.auth().lookupSelf().ttl} seconds)")
                    val response = client.auth().renewSelf()
                    logger.debug("Successfully refreshed Vault token (new TTL: ${client.auth().lookupSelf().ttl} seconds)")
                    delay(suggestedRefreshIntervalInMillis(response.authLeaseDuration * 1000))
                } catch (e: VaultException) {
                    logger.error(e) { "Could not refresh the Vault token" }
                    logger.warn { "Attempting to refresh Vault token in 5 seconds" }
                    delay(5_000L)
                }
            }
        } else {
            logger.warn { "Vault token is not renewable" }
        }
    }

    private fun getTokenFromFile(): String? =
        File(System.getenv("VAULT_TOKEN_PATH") ?: "/var/run/secrets/nais.io/vault/vault_token").let { file ->
            when (file.exists()) {
                true -> file.readText(Charsets.UTF_8).trim()
                false -> null
            }
        }

    // We should refresh tokens from Vault before they expire, so we add a MIN_REFRESH_MARGIN margin.
    // If the token is valid for less than MIN_REFRESH_MARGIN * 2, we use duration / 2 instead.
    fun suggestedRefreshIntervalInMillis(duration: Long): Long = when {
        duration < MIN_REFRESH_MARGIN * 2 -> duration / 2
        else -> duration - MIN_REFRESH_MARGIN
    }
}
