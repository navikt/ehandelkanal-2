package no.nav.ehandel.kanal

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.DriverManager
import no.nav.ehandel.kanal.db.rotateCredentials
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

private const val URL = "jdbc:h2:mem:credential-rotation;DB_CLOSE_DELAY=-1"

class CredentialRotationTest {

    private fun Connection.currentUser(): String = metaData.userName

    private fun asAdmin(sql: String) {
        DriverManager.getConnection(URL, "sa", "").use { conn -> conn.createStatement().use { it.execute(sql) } }
    }

    @Test
    fun `rotating credentials makes the pool use the new user and retire connections using the old one`() {
        asAdmin("CREATE USER IF NOT EXISTS alice PASSWORD 'alice-password' ADMIN")
        asAdmin("CREATE USER IF NOT EXISTS bob PASSWORD 'bob-password' ADMIN")

        HikariDataSource(HikariConfig().apply {
            jdbcUrl = URL
            username = "alice"
            password = "alice-password"
            maximumPoolSize = 2
        }).use { dataSource ->
            val connectionInUseDuringRotation = dataSource.connection
            connectionInUseDuringRotation.currentUser() shouldBeEqualTo "ALICE"

            dataSource.rotateCredentials(username = "bob", password = "bob-password")
            asAdmin("ALTER USER alice SET PASSWORD 'revoked'")

            connectionInUseDuringRotation.currentUser() shouldBeEqualTo "ALICE"
            connectionInUseDuringRotation.close()

            repeat(3) {
                dataSource.connection.use { it.currentUser() shouldBeEqualTo "BOB" }
            }
        }
    }
}
