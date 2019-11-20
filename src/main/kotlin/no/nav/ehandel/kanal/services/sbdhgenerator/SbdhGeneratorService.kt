package no.nav.ehandel.kanal.services.sbdhgenerator

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import javax.xml.bind.JAXB
import no.difi.commons.sbdh.jaxb.Partner
import no.difi.commons.sbdh.jaxb.StandardBusinessDocumentHeader
import no.nav.ehandel.kanal.common.models.ErrorMessage

class SbdhGeneratorService {

    internal inline fun <reified T> generateSbdh(
        rawXmlPayload: String
    ): Result<StandardBusinessDocumentHeader, ErrorMessage> =
        rawXmlPayload
            .parsePayload<T>()
            .andThen { payload -> payload.mapToSbdh() }

    private inline fun <reified T> String.parsePayload(): Result<T, ErrorMessage> =
        runCatching {
            JAXB.unmarshal(this, T::class.java)
        }.fold(
            onSuccess = { Ok(it) },
            onFailure = { Err(ErrorMessage.SbdhGenerator.CouldNotParseDocumentType) }
        )
}

private inline fun <reified T> T.mapToSbdh(): Result<StandardBusinessDocumentHeader, ErrorMessage> =
    runCatching {
        StandardBusinessDocumentHeader().apply {
            headerVersion = ""
            sender.add(Partner())
            receiver.add(Partner())
            documentIdentification = null
            manifest = null
            businessScope = null
        } // todo
    }.fold(
        onSuccess = { Ok(it) },
        onFailure = { Err(ErrorMessage.SbdhGenerator.CouldNotMapPayloadToSbdh) }
    )
