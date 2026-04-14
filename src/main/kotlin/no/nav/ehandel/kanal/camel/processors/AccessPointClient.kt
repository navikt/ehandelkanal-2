package no.nav.ehandel.kanal.camel.processors

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.ResponseException
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.http.headersOf
import javax.xml.bind.DataBindingException
import javax.xml.bind.JAXB
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.difi.vefa.peppol.common.model.Header
import no.difi.vefasrest.model.OutboxPostResponseType
import no.difi.vefasrest.model.QueuedMessagesSendResultType
import no.nav.ehandel.kanal.AccessPointProps
import no.nav.ehandel.kanal.auth.EntraIdTokenProvider
import no.nav.ehandel.kanal.common.functions.retry
import no.nav.ehandel.kanal.common.models.ErrorMessage
import no.nav.ehandel.kanal.common.singletons.httpClient
import no.nav.ehandel.kanal.services.log.InboundLogger
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.language.NamespacePrefix
import org.apache.camel.language.XPath

private val LOGGER = KotlinLogging.logger { }

class AccessPointClient(
    private val entraIdTokenProvider: EntraIdTokenProvider
) : Processor {

    init {
        LOGGER.apply {
            info { "Vefa Inbox URL: ${AccessPointProps.inbox.url}" }
            info { "Vefa Messages URL: ${AccessPointProps.messages.url}" }
        }
    }

    fun getInboxMessageHeaders(exchange: Exchange): String = runBlocking {
        LOGGER.trace { "Getting inbox messageheaders" }
        httpClient.get<String> {
            url("${AccessPointProps.inbox.url}/hent-uleste-meldinger")
            header("Authorization", "Bearer ${entraIdTokenProvider.getToken()}")
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }.also {
            LOGGER.trace { "Inbox messages: $it" }
        }
    }

    @ExperimentalStdlibApi
    fun downloadMessagePayload(exchange: Exchange, msgNo: String): String {
        val payload = runBlocking {
            InboundLogger.downloadInboundMessage(exchange, msgNo)
            httpClient.get<ByteArray> {
                url("${AccessPointProps.messages.url}/xml-document/$msgNo")
                header("Authorization", "Bearer ${entraIdTokenProvider.getToken()}")
                header(HttpHeaders.Accept, ContentType.Application.Xml)
            }
        }
        return try {
            payload.decodeToString(throwOnInvalidSequence = true)
        } catch (e: Throwable) {
            LOGGER.info(e) { "Could not parse downloaded payload as UTF-8, attempting to parse as ISO 8859-1" }
            payload.toString(Charsets.ISO_8859_1)
                .also { LOGGER.info { "Successfully parsed downloaded payload as ISO 8859-1" } }
        }
    }

    fun markMessageAsRead(msgNo: String): String = runBlocking {
        LOGGER.trace { "Marking MsgNo $msgNo as read" }
        try {
            httpClient.put<String> {
                url("${AccessPointProps.inbox.url}/marker-som-lest/$msgNo")
                header("Authorization", "Bearer ${entraIdTokenProvider.getToken()}")
                header(HttpHeaders.Accept, ContentType.Text.Plain)
            }.also {
                LOGGER.info { "Successfully marked MsgNo $msgNo as read" }
            }
        } catch (e: Throwable) {
            LOGGER.error(e) { "Could not mark MsgNo $msgNo as read" }
            throw e
        }
    }

    // Used during resubmit to set original in body as the body to used when resubmitted
    override fun process(exchange: Exchange) {
        LOGGER.apply {
            error { "OnPrepareResubmit" }
            error { "Current Body: ${exchange.getIn().body}" }
            error { "Exchangeheaders: ${exchange.getIn().headers}" }
        }
        exchange.getIn().body = exchange.unitOfWork.originalInMessage.body
    }

}
