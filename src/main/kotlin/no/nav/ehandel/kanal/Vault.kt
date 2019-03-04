package no.nav.ehandel.kanal

import com.bettercloud.vault.SslConfig
import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import com.bettercloud.vault.VaultException
import kotlinx.coroutines.delay
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger { }

private val vaultToken: String = System.getenv("VAULT_TOKEN")
    ?: getTokenFromFile()
    ?: throw RuntimeException("Neither VAULT_TOKEN or VAULT_TOKEN_PATH is set")

val vaultClient: Vault = Vault(
    VaultConfig()
        .address(System.getenv("VAULT_ADDR") ?: "https://vault.adeo.no")
        .token(vaultToken)
        .openTimeout(5)
        .readTimeout(30)
        .sslConfig(SslConfig().build())
        .build()
)

suspend fun runRenewTokenTask() {
    vaultClient.auth().lookupSelf().apply {
        delay(suggestedRefreshIntervalInMillis(this.ttl * 1000))
        while (isRenewable) {
            try {
                logger.debug("Refreshing Vault token (TTL: $ttl seconds)")
                delay(suggestedRefreshIntervalInMillis(vaultClient.auth().renewSelf().authLeaseDuration * 1000))
            } catch (e: VaultException) {
                logger.error("Could not refresh the Vault token", e)
            }
        }
        logger.debug { "Vault token is not renewable" }
    }
}

private fun getTokenFromFile(): String? =
    File(System.getenv("VAULT_TOKEN_PATH") ?: "/var/run/secrets/nais.io/vault/vault_token").let { file ->
        when (file.exists()) {
            true -> file.readText(Charsets.UTF_8).trim()
            false -> null
        }
    }

// We should refresh tokens from Vault before they expire, so we add 30 seconds margin.
// If the token is valid for less than 60 seconds, we use duration / 2 instead.
internal fun suggestedRefreshIntervalInMillis(millis: Long): Long = when {
    millis < 60000 -> millis / 2
    else -> millis - 30000
}
