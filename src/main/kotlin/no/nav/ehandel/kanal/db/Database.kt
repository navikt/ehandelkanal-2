package no.nav.ehandel.kanal.db

import com.bettercloud.vault.VaultException
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.ehandel.kanal.DatabaseProps
import no.nav.ehandel.kanal.db.Vault.suggestedRefreshIntervalInMillis
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger { }
private val dispatcher: CoroutineContext = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

private data class VaultCredentials(
    val leaseId: String,
    val leaseDuration: Long,
    val username: String,
    val password: String
)

data class RenewCredentialsTaskData(
    val initialDelay: Long,
    val dataSource: HikariDataSource,
    val mountPath: String,
    val databaseName: String,
    val role: Role
)

enum class Role {
    ADMIN, USER, READONLY;

    override fun toString() = name.lowercase()
}

object Database {

    fun initLocal() {
        Flyway.configure().run {
            dataSource(DatabaseProps.url, DatabaseProps.username, DatabaseProps.password)
            locations("classpath:db/migration/common", "classpath:db/migration/h2")
            cleanOnValidationError(true)
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

    fun initRemote(): RenewCredentialsTaskData {
        Flyway.configure().run {
            val credentials = getNewCredentials(
                mountPath = DatabaseProps.vaultMountPath,
                databaseName = DatabaseProps.name,
                role = Role.ADMIN
            )
            dataSource(DatabaseProps.url, credentials.username, credentials.password)
            locations("classpath:db/migration/common", "classpath:db/migration/postgresql")
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
        return RenewCredentialsTaskData(
            initialDelay = suggestedRefreshIntervalInMillis(initialCredentials.leaseDuration * 1000),
            dataSource = hikariDataSource,
            mountPath = DatabaseProps.vaultMountPath,
            databaseName = DatabaseProps.name,
            role = Role.USER
        )
    }

    private fun getNewCredentials(mountPath: String, databaseName: String, role: Role): VaultCredentials {
        val path = "$mountPath/creds/$databaseName-$role"
        logger.debug("Getting database credentials for path '$path'")
        try {
            val response = Vault.client.logical().read(path)
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

    suspend fun runRenewCredentialsTask(data: RenewCredentialsTaskData, condition: () -> Boolean) {
        delay(data.initialDelay)
        while (condition()) {
            val credentials = getNewCredentials(data.mountPath, data.databaseName, data.role)
            data.dataSource.apply {
                hikariConfigMXBean.setUsername(credentials.username)
                hikariConfigMXBean.setPassword(credentials.password)
                hikariPoolMXBean.softEvictConnections()
            }
            delay(suggestedRefreshIntervalInMillis(credentials.leaseDuration * 1000))
        }
    }
}

suspend fun <T> dbQuery(block: () -> T): T = withContext(dispatcher) {
    transaction { block() }
}
