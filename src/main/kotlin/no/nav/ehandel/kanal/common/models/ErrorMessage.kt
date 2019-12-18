package no.nav.ehandel.kanal.common.models

sealed class ErrorMessage {
    sealed class StandardBusinessDocument : ErrorMessage() {
        object FailedToParseDocumentType : StandardBusinessDocument() {
            override fun toString(): String = "Failed to parse document type"
        }

        object FailedToPrependStandardBusinessDocumentHeader : StandardBusinessDocument() {
            override fun toString(): String = "Failed to preprend SBDH to document payload"
        }

        object InvalidSchemeIdForParticipant : StandardBusinessDocument() {
            override fun toString(): String = "Invalid schemeId for party endpoint"
        }

        object InvalidDocumentType : StandardBusinessDocument() {
            override fun toString(): String = "Could not unmarshal document. Invalid document type"
        }

        object MissingRequiredValuesFromDocument : StandardBusinessDocument() {
            override fun toString(): String = "Could not generate SBDH. Missing required values from document"
        }
    }

    sealed class AccessPoint : ErrorMessage() {
        object ServerResponseError : AccessPoint() {
            override fun toString(): String = "Access Point responded with an HTTP error"
        }

        object TransmitError : AccessPoint() {
            override fun toString(): String = "Failed to trigger transmission of message from Access Point"
        }

        object DataBindError : AccessPoint() {
            override fun toString(): String = "Failed to parse response from Access Point"
        }
    }

    object InternalError : ErrorMessage() {
        override fun toString(): String = "An unknown error occurred"
    }
}
