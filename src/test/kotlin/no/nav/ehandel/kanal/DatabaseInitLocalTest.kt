package no.nav.ehandel.kanal

import java.sql.DriverManager
import no.nav.ehandel.kanal.db.Database
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.Test

class DatabaseInitLocalTest {

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T =
        DriverManager.getConnection(DatabaseProps.url, DatabaseProps.username, DatabaseProps.password).use { conn ->
            conn.createStatement().use { stmt -> stmt.executeQuery(sql).use { rs -> rs.next(); read(rs) } }
        }

    private fun update(sql: String) {
        DriverManager.getConnection(DatabaseProps.url, DatabaseProps.username, DatabaseProps.password).use { conn ->
            conn.createStatement().use { it.executeUpdate(sql) }
        }
    }

    private fun checksumOfV1(): Int =
        query("""SELECT "checksum" FROM "flyway_schema_history" WHERE "version" = '1'""") { it.getInt(1) }

    private fun reportCount(): Int = query("SELECT COUNT(*) FROM report") { it.getInt(1) }

    @Test
    fun `initLocal cleans and re-migrates when schema history does not match the migrations`() {
        Database.initLocal()
        update("""UPDATE "flyway_schema_history" SET "checksum" = 1 WHERE "version" = '1'""")

        Database.initLocal()

        checksumOfV1() shouldNotBeEqualTo 1
    }

    @Test
    fun `initLocal keeps existing data when there are only pending migrations`() {
        Database.initLocal()
        update("DELETE FROM report")
        update(
            "INSERT INTO report (file_name, document_type, received_at, issued_at) " +
                "VALUES ('keep.xml', 'Invoice', CURRENT_DATE, CURRENT_DATE)"
        )
        update("""DELETE FROM "flyway_schema_history" WHERE "version" = '1.3'""")

        Database.initLocal()

        reportCount() shouldBeEqualTo 1
    }
}
