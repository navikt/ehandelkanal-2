package no.nav.ehandel.kanal.common.models

sealed class ErrorMessage {
    sealed class StandardBusinessDocument : ErrorMessage() {
        object CouldNotParseDocumentType : StandardBusinessDocument() {
            override fun toString(): String = this.javaClass.simpleName
        }

        object CouldNotPrependStandardBusinessDocument : StandardBusinessDocument() {
            override fun toString(): String = this.javaClass.simpleName
        }

        object InvalidSchemeIdForParticipant : StandardBusinessDocument() {
            override fun toString(): String = this.javaClass.simpleName
        }

        object InvalidDocumentType : StandardBusinessDocument() {
            override fun toString(): String = this.javaClass.simpleName
        }

        object MissingRequiredValuesFromDocument : StandardBusinessDocument() {
            override fun toString(): String = this.javaClass.simpleName
        }
    }

    sealed class AccessPoint : ErrorMessage() {
        object ServerResponseError : AccessPoint() {
            override fun toString(): String = this.javaClass.simpleName
        }

        object TransmitError : AccessPoint() {
            override fun toString(): String = this.javaClass.simpleName
        }
    }

    object InternalError : ErrorMessage() {
        override fun toString(): String = this.javaClass.simpleName
    }

    object DataBindError : ErrorMessage() {
        override fun toString(): String = this.javaClass.simpleName
    }
}
