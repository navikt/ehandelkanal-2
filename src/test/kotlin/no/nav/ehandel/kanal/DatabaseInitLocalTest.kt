package no.nav.ehandel.kanal

import java.sql.DriverManager
import no.nav.ehandel.kanal.db.Database
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.Test

class DatabaseInitLocalTest {

    private fun checksumOfV1(): Int =
        DriverManager.getConnection(DatabaseProps.url, DatabaseProps.username, DatabaseProps.password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("""SELECT "checksum" FROM "flyway_schema_history" WHERE "version" = '1'""").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    @Test
    fun `initLocal cleans and re-migrates when schema history does not match the migrations`() {
        Database.initLocal()
        DriverManager.getConnection(DatabaseProps.url, DatabaseProps.username, DatabaseProps.password).use { conn ->
            conn.createStatement().use {
                it.executeUpdate("""UPDATE "flyway_schema_history" SET "checksum" = 1 WHERE "version" = '1'""")
            }
        }

        Database.initLocal()

        checksumOfV1() shouldNotBeEqualTo 1
    }
}
