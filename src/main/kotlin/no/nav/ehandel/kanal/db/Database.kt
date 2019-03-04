package no.nav.ehandel.kanal.db

import com.bettercloud.vault.VaultException
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.ehandel.kanal.ApplicationState
import no.nav.ehandel.kanal.DatabaseProps
import no.nav.ehandel.kanal.appProfileLocal
import no.nav.ehandel.kanal.runRenewTokenTask
import no.nav.ehandel.kanal.suggestedRefreshIntervalInMillis
import no.nav.ehandel.kanal.vaultClient
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

private val logger = KotlinLogging.logger { }
private val dispatcher: CoroutineContext = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

private data class VaultCredentials(
    val leaseId: String,
    val leaseDuration: Long,
    val username: String,
    val password: String
)

private enum class Role {
    ADMIN, USER, READONLY;

    override fun toString() = name.toLowerCase()
}

class Database(private val applicationState: ApplicationState) {
    suspend fun init() {
        when (appProfileLocal) {
            true -> initLocal()
            false -> initRemote(applicationState)
        }
    }

    private fun initLocal() {
        Flyway.configure().run {
            dataSource(DatabaseProps.url, DatabaseProps.username, DatabaseProps.password)
            load().migrate()
        }
        Database.connect(HikariDataSource(HikariConfig().apply {
            jdbcUrl = DatabaseProps.url
            username = DatabaseProps.username
            password = DatabaseProps.password
            maximumPoolSize = 3
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }))
    }

    private suspend fun initRemote(applicationState: ApplicationState) {
        coroutineScope {
            launch { runRenewTokenTask() }
            Flyway.configure().run {
                val credentials = getNewCredentials(
                    mountPath = DatabaseProps.vaultMountPath,
                    databaseName = DatabaseProps.name,
                    role = Role.ADMIN
                )
                dataSource(DatabaseProps.url, credentials.username, credentials.password)
                initSql("SET ROLE \"${DatabaseProps.name}-${Role.ADMIN}\"") // required for assigning proper owners for the tables
                load().migrate()
            }
            val initialCredentials = getNewCredentials(
                mountPath = DatabaseProps.vaultMountPath,
                databaseName = DatabaseProps.name,
                role = Role.USER
            )
            val hikariDataSource = HikariDataSource(HikariConfig().apply {
                jdbcUrl = DatabaseProps.url
                username = initialCredentials.username
                password = initialCredentials.password
                maximumPoolSize = 2
                minimumIdle = 0
                idleTimeout = 10001
                connectionTimeout = 1000
                maxLifetime = 30001
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            })
            Database.connect(hikariDataSource)
            launch {
                delay(suggestedRefreshIntervalInMillis(initialCredentials.leaseDuration * 1000))
                runRenewCredentialsTask(
                    dataSource = hikariDataSource,
                    mountPath = DatabaseProps.vaultMountPath,
                    databaseName = DatabaseProps.name,
                    role = Role.USER
                ) { applicationState.running }
            }
        }
    }
}

private fun getNewCredentials(mountPath: String, databaseName: String, role: Role): VaultCredentials {
    val path = "$mountPath/creds/$databaseName-$role"
    logger.debug("Getting database credentials for path '$path'")
    try {
        val response = vaultClient.logical().read(path)
        val username = checkNotNull(response.data["username"]) { "Username is not set in response from Vault" }
        val password = checkNotNull(response.data["password"]) { "Password is not set in response from Vault" }
        logger.debug("Got new credentials (username=$username, leaseDuration=${response.leaseDuration})")
        return VaultCredentials(response.leaseId, response.leaseDuration, username, password)
    } catch (e: VaultException) {
        when (e.httpStatusCode) {
            403 -> logger.error("Vault denied permission to fetch database credentials for path '$path'", e)
            else -> logger.error("Could not fetch database credentials for path '$path'", e)
        }
        throw e
    }
}

private suspend fun runRenewCredentialsTask(
    dataSource: HikariDataSource,
    mountPath: String,
    databaseName: String,
    role: Role,
    condition: () -> Boolean
) {
    while (condition()) {
        val credentials = getNewCredentials(mountPath, databaseName, role)
        dataSource.apply {
            hikariConfigMXBean.setUsername(credentials.username)
            hikariConfigMXBean.setPassword(credentials.password)
        }
        delay(suggestedRefreshIntervalInMillis(credentials.leaseDuration * 1000))
    }
}

suspend fun <T> dbQuery(block: () -> T): T = withContext(dispatcher) {
    transaction { block() }
}
