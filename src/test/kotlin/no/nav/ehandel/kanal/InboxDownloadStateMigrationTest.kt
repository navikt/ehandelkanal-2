package no.nav.ehandel.kanal

import java.sql.DriverManager
import org.amshove.kluent.shouldBeEqualTo
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test

class InboxDownloadStateMigrationTest {
    private val url = "jdbc:h2:mem:migrationdata;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"

    private fun flyway(target: String) = Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration/common", "classpath:db/migration/h2")
        .target(target)
        .load()

    @Test
    fun `migration keeps existing report data untouched`() {
        flyway("1.3").migrate()
        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().execute(
                "INSERT INTO report(file_name, document_type, invoice_number, received_at, issued_at) " +
                    "VALUES ('f.xml', 'Invoice', 'INV-1', DATE '2026-10-01', DATE '2026-09-30')"
            )
        }

        flyway("latest").migrate()

        DriverManager.getConnection(url, "sa", "").use { connection ->
            val reports = connection.createStatement().executeQuery("SELECT file_name, invoice_number FROM report")
            reports.next()
            reports.getString("file_name") shouldBeEqualTo "f.xml"
            reports.getString("invoice_number") shouldBeEqualTo "INV-1"
            reports.next() shouldBeEqualTo false
            connection.createStatement().executeQuery("SELECT COUNT(*) FROM inbox_download_state").apply {
                next()
                getInt(1) shouldBeEqualTo 0
            }
        }
    }
}
