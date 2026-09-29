package no.nav.ehandel.kanal

import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import no.nav.ehandel.kanal.common.models.DocumentType
import no.nav.ehandel.kanal.db.Database
import no.nav.ehandel.kanal.db.ReportTable
import no.nav.ehandel.kanal.services.report.CsvValues
import no.nav.ehandel.kanal.services.report.Report
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContainSame
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.junit.Before
import org.junit.Test

class ReportTest {

    private val jan10 = DateTime(2024, 1, 10, 0, 0)
    private val jan11 = DateTime(2024, 1, 11, 0, 0)

    private fun csvValues(fileName: String, receivedAt: DateTime, amount: BigDecimal? = null) = CsvValues(
        fileName = fileName,
        type = DocumentType.Invoice,
        orgnummer = "889640782",
        fakturanummer = "INV-1",
        navn = "Leverandør AS",
        belop = amount,
        valuta = " NOK ",
        mottattDato = receivedAt,
        fakturaDato = DateTime(2024, 1, 5, 0, 0)
    )

    @Before
    fun setUp() {
        Database.initLocal()
        transaction { ReportTable.deleteAll() }
        Report.insert(csvValues("a.xml", jan10, BigDecimal("1234.5678")))
        Report.insert(csvValues("b.xml", jan10.withHourOfDay(23).withMinuteOfHour(59)))
        Report.insert(csvValues("c.xml", jan11))
    }

    @Test
    fun `getAll without date returns all entries`() {
        runBlocking { Report.getAll() }.map { it.fileName } shouldContainSame listOf("a.xml", "b.xml", "c.xml")
    }

    @Test
    fun `getAll with date returns only entries received that day`() {
        runBlocking { Report.getAll(jan10) }.map { it.fileName } shouldContainSame listOf("a.xml", "b.xml")
        runBlocking { Report.getAll(jan11) }.map { it.fileName } shouldBeEqualTo listOf("c.xml")
    }

    @Test
    fun `getAllUniqueDaysWithEntries returns distinct days newest first`() {
        runBlocking { Report.getAllUniqueDaysWithEntries() }.map { it.toLocalDate().toString() } shouldBeEqualTo
            listOf("2024-01-11", "2024-01-10")
    }

    @Test
    fun `getAllAsCsvFile returns header and rows for the given day`() {
        val csv = runBlocking { Report.getAllAsCsvFile(jan11) }.toString(Charsets.UTF_8)

        csv shouldBeEqualTo CsvValues.CSV_HEADER + "\n" +
            "c.xml,Invoice,889640782,\"INV-1\",\"Leverandør AS\",null,NOK,2024-01-11,2024-01-05\n"
    }

    @Test
    fun `amount is stored and read back`() {
        runBlocking { Report.getAll(jan10) }.first { it.fileName == "a.xml" }.belop!!.compareTo(BigDecimal("1234.5678")) shouldBeEqualTo 0
    }
}
