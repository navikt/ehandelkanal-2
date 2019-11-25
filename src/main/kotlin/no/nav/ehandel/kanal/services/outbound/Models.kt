package no.nav.ehandel.kanal.services.outbound

import no.nav.ehandel.kanal.common.models.ErrorMessage

// todo
data class OutboundResponse(
    val foo: String
)

// todo
data class OutboundErrorResponse(
    val foo: String,
    val errorMessage: ErrorMessage
)
