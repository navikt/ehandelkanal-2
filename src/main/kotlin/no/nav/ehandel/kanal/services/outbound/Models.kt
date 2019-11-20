package no.nav.ehandel.kanal.services.outbound

import no.nav.ehandel.kanal.common.models.ErrorMessage

data class OutboundRequest(
    val payload: ByteArray,
    val sender: String,
    val receiver: String,
    val documentId: String,
    val processId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OutboundRequest

        if (!payload.contentEquals(other.payload)) return false
        if (sender != other.sender) return false
        if (receiver != other.receiver) return false
        if (documentId != other.documentId) return false
        if (processId != other.processId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = payload.contentHashCode()
        result = 31 * result + sender.hashCode()
        result = 31 * result + receiver.hashCode()
        result = 31 * result + documentId.hashCode()
        result = 31 * result + processId.hashCode()
        return result
    }
}

data class OutboundResponse(
    val foo: String
)

data class OutboundErrorResponse(
    val foo: String,
    val errorMessage: ErrorMessage
)
