package no.nav.ehandel.kanal.services.outbound

internal interface IOutboundResponse {
    val correlationId: String
    val status: Status
    val message: String
}

internal enum class Status {
    SUCCESS, FAILED, BAD_REQUEST
}

internal data class OutboundResponse(
    override val correlationId: String,
    override val status: Status,
    override val message: String
) : IOutboundResponse

internal data class OutboundErrorResponse(
    override val correlationId: String,
    override val status: Status,
    override val message: String
) : IOutboundResponse
