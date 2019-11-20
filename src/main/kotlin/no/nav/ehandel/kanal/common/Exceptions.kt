package no.nav.ehandel.kanal.common

import java.lang.RuntimeException

class LegalArchiveException(
    override val message: String,
    override val cause: Throwable
) : RuntimeException(message, cause)

class InvalidDocumentException(
    override val message: String,
    override val cause: Throwable
) : RuntimeException(message, cause)
