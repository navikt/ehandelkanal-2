package no.nav.ehandel.kanal.common.models

sealed class ErrorMessage {
    sealed class SbdhGenerator : ErrorMessage() {
        object CouldNotParseDocumentType : SbdhGenerator()
        object CouldNotMapPayloadToSbdh : SbdhGenerator()
        object CouldNotPrependSbdh : SbdhGenerator()
    }

    sealed class AccessPoint : ErrorMessage() {
        object ServerResponseError : AccessPoint()
        object TransmitError : AccessPoint()
    }

    object InternalError : ErrorMessage()
    object DataBindError : ErrorMessage()
}
