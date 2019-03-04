package no.nav.ehandel.kanal.db

import org.jetbrains.exposed.sql.Table

object ReportTable : Table() {
    val id = integer("id").autoIncrement("report").primaryKey()
    val fileName = varchar("file_name", length = 256)
    val documentType = varchar("document_type", length = 64)
    val orgNumber = integer("org_number").nullable()
    val invoiceNumber = varchar("invoice_number", length = 64).nullable()
    val partyName = varchar("party_name", length = 256).nullable()
    val amount = decimal(name = "amount", precision = 4, scale = 18).nullable()
    val currency = varchar("currency", length = 32).nullable()
    val receivedAt = date("received_at")
    val issuedAt = date("issued_at")
}
