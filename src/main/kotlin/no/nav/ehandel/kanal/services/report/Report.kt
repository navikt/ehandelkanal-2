package no.nav.ehandel.kanal.services.report

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.StringJoiner
import no.nav.ehandel.kanal.common.extensions.formatDate
import no.nav.ehandel.kanal.common.models.DocumentType
import no.nav.ehandel.kanal.db.ReportTable
import no.nav.ehandel.kanal.db.dbQuery
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

object Report {

    suspend fun getAll(date: DateTime? = null) = dbQuery {
        val rows = date?.let {
            ReportTable.select {
                ReportTable.receivedAt.between(
                    date.withTimeAtStartOfDay(),
                    date.withHourOfDay(23)
                        .withMinuteOfHour(59)
                        .withSecondOfMinute(59)
                        .withMillisOfSecond(999)
                )
            }
        } ?: ReportTable.selectAll()
        rows.map(ResultRow::toCsvValues)
    }

    fun insert(csvValues: CsvValues) = transaction {
        ReportTable.insert {
            it[fileName] = csvValues.fileName
            it[documentType] = csvValues.type.name
            it[orgNumber] = csvValues.orgnummer
            it[invoiceNumber] = csvValues.fakturanummer
            it[partyName] = csvValues.navn
            it[amount] = csvValues.belop
            it[currency] = csvValues.valuta
            it[receivedAt] = csvValues.mottattDato
            it[issuedAt] = csvValues.fakturaDato
        }
    }

    suspend fun getAllUniqueDaysWithEntries(): List<DateTime> = dbQuery {
        ReportTable
            .slice(ReportTable.receivedAt)
            .selectAll()
            .orderBy(ReportTable.receivedAt to SortOrder.DESC)
            .withDistinct()
            .map { it[ReportTable.receivedAt] }
    }

    suspend fun getAllAsCsvFile(date: DateTime): ByteArray =
        StringBuilder(CsvValues.CSV_HEADER + "\n")
            .apply { getAll(date).forEach { value -> append(value.toString() + "\n") } }
            .toString()
            .toByteArray(Charsets.UTF_8)
}

private fun ResultRow.toCsvValues(): CsvValues =
    CsvValues(
        fileName = this[ReportTable.fileName],
        type = DocumentType.valueOfOrDefault(this[ReportTable.documentType]),
        orgnummer = this[ReportTable.orgNumber],
        fakturanummer = this[ReportTable.invoiceNumber],
        navn = this[ReportTable.partyName],
        belop = this[ReportTable.amount],
        valuta = this[ReportTable.currency],
        mottattDato = this[ReportTable.receivedAt],
        fakturaDato = this[ReportTable.issuedAt]
    )

data class CsvValues(
    val fileName: String,
    val type: DocumentType,
    val orgnummer: String?,
    val fakturanummer: String?,
    val navn: String?,
    val belop: BigDecimal?,
    val valuta: String?,
    val mottattDato: DateTime,
    val fakturaDato: DateTime
) {
    override fun toString(): String = StringJoiner(",")
        .add(fileName)
        .add("$type")
        .add(orgnummer)
        .add(fakturanummer.escapeQuotes())
        .add(navn.escapeQuotes())
        .add("${belop?.setScale(2, RoundingMode.HALF_EVEN)}")
        .add(valuta?.trim())
        .add(mottattDato.formatDate())
        .add(fakturaDato.formatDate())
        .toString()

    fun logString() = CSV_HEADER
        .split(",")
        .zip(this.toString().split(","))
        .toMap()

    companion object {
        const val CSV_HEADER = "fileName,type,orgnummer,fakturanummer,navn,belop,valuta,mottattDato,fakturaDato"
    }
}

private fun String?.escapeQuotes(): String = "\"$this\""
