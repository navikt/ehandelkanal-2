package no.nav.ehandel.kanal.legalarchive

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
data class ArchiveRequest(
    @JsonProperty(value = "meldingsId", required = true) val messageId: String,
    @JsonProperty(value = "avsender", required = true) val sender: String,
    @JsonProperty(value = "mottaker", required = true) val receiver: String,
    @JsonProperty(value = "meldingsInnhold", required = true) var messageContent: ByteArray,
    @JsonProperty(value = "joarkRef") val joarkReference: String? = null,
    @JsonProperty(value = "antallAarLagres") val retentionInYears: Int? = null
)
