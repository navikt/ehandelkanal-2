package no.nav.ehandel.kanal.common.models

sealed class ErrorMessage {
    sealed class SbdhGenerator : ErrorMessage() {
        object CouldNotParseDocumentType : SbdhGenerator()
        object CouldNotMapPayloadToSbdh : SbdhGenerator()
    }

    sealed class AccessPoint : ErrorMessage() {
        object ServerResponseError : AccessPoint()
        object ClientRequestError : AccessPoint()
        object TransmitError : AccessPoint()
    }

    object InternalError : ErrorMessage()
    object DataBindError : ErrorMessage()
}