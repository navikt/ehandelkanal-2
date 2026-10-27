package no.nav.ehandel.kanal.db

import org.jetbrains.exposed.v1.core.Table

object InboxDownloadStateTable : Table("inbox_download_state") {
    val msgNo = varchar("msg_no", length = 64)
    override val primaryKey = PrimaryKey(msgNo, name = "inbox_download_state_pkey")
    val failureCount = integer("failure_count")
    val nextAttemptMs = long("next_attempt_ms")
    val lastError = varchar("last_error", length = 128).nullable()
    val updatedMs = long("updated_ms")
}
