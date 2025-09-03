package no.nav.ehandel.kanal.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.jodatime.date

object ReportTable : Table() {
    val id = integer("id").autoIncrement("report")
    override val primaryKey = PrimaryKey(id, name = "PK_Report_ID")
    val fileName = varchar("file_name", length = 256)
    val documentType = varchar("document_type", length = 64)
    val orgNumber = varchar("org_number", length = 128).nullable()
    val invoiceNumber = varchar("invoice_number", length = 256).nullable()
    val partyName = varchar("party_name", length = 256).nullable()
    val amount = decimal(name = "amount", precision = 4, scale = 18).nullable()
    val currency = varchar("currency", length = 32).nullable()
    val receivedAt = date("received_at")
    val issuedAt = date("issued_at")
}
