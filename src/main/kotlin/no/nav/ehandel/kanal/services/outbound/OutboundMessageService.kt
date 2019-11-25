package no.nav.ehandel.kanal.services.outbound

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.mapBoth
import java.io.StringWriter
import javax.xml.bind.JAXB
import no.nav.ehandel.kanal.camel.processors.AccessPointClient
import no.nav.ehandel.kanal.services.sbdhgenerator.StandardBusinessDoumentProcessorService
import no.nav.ehandel.kanal.services.sbdhgenerator.mapToStandardBusinessDocument

class OutboundMessageService(
    private val accessPointClient: AccessPointClient,
    private val standardBusinessDoumentProcessorService: StandardBusinessDoumentProcessorService
) {

    internal suspend inline fun <reified T> processOutboundMessage(xmlPayload: String): Result<OutboundResponse, OutboundErrorResponse> =
        standardBusinessDoumentProcessorService
            .generateStandardBusinessDocumentHeader<T>(xmlPayload)
            .andThen { header ->
                header
                    .mapToStandardBusinessDocument(xmlPayload)
                    .andThen { standardBusinessDocument ->
                        accessPointClient.sendToOutbox(payload = standardBusinessDocument, header = header)
                    }
            }
            .andThen() { outboxPostResponse -> accessPointClient.transmitMessage(outboxPostResponse) }
            .mapBoth(
                success = { response ->
                    Ok(OutboundResponse(foo = response.mapJaxbObjectToString()))
                },
                failure = { errorMessage ->
                    Err(OutboundErrorResponse(foo = "bar", errorMessage = errorMessage))
                }
            )

    // todo
    //  - log to legal archive?
    //  - report entries?
}

private fun Any.mapJaxbObjectToString(): String = StringWriter().use { sw ->
    JAXB.marshal(this, sw)
    sw.toString()
}
