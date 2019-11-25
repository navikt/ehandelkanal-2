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
import io.ktor.client.request.post
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
import no.nav.ehandel.kanal.common.functions.retry
import no.nav.ehandel.kanal.common.models.ErrorMessage
import no.nav.ehandel.kanal.common.singletons.httpClient
import no.nav.ehandel.kanal.services.log.InboundLogger
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.language.NamespacePrefix
import org.apache.camel.language.XPath

private val LOGGER = KotlinLogging.logger { }

object AccessPointClient : Processor {

    fun init() {
        LOGGER.apply {
            info { "Vefa Inbox URL: ${AccessPointProps.inbox.url}" }
            info { "Vefa Outbox URL: ${AccessPointProps.outbox.url}" }
            info { "Vefa Messages URL: ${AccessPointProps.messages.url}" }
            info { "Vefa Transmit URL: ${AccessPointProps.transmit.url}" }
        }
    }

    fun getInboxCount(): Int = runBlocking {
        LOGGER.trace { "Checking inbox count" }
        Integer.valueOf(httpClient.get<String> {
            url("${AccessPointProps.inbox.url}/count")
            header(AccessPointProps.inbox.header, AccessPointProps.inbox.apiKey)
            header(HttpHeaders.Accept, ContentType.Text.Plain)
        }).also {
            LOGGER.trace { "Inbox count: $it" }
        }
    }

    fun getInboxMessageHeaders(exchange: Exchange): String = runBlocking {
        LOGGER.trace { "Getting inbox messageheaders" }
        httpClient.get<String> {
            url("${AccessPointProps.inbox.url}/")
            header(AccessPointProps.inbox.header, AccessPointProps.inbox.apiKey)
            header(HttpHeaders.Accept, ContentType.Application.Xml)
        }.also {
            LOGGER.trace { "Inbox messages: $it" }
        }
    }

    @ExperimentalStdlibApi
    fun downloadMessagePayload(exchange: Exchange, msgNo: String): String {
        val payload = runBlocking {
            InboundLogger.downloadInboundMessage(exchange, msgNo)
            httpClient.get<ByteArray> {
                url("${AccessPointProps.messages.url}/$msgNo/xml-document")
                header(AccessPointProps.messages.header, AccessPointProps.messages.apiKey)
                header(HttpHeaders.Accept, ContentType.Application.Xml)
            }
        }
        return try {
            payload.decodeToString(throwOnInvalidSequence = true)
        } catch (e: Throwable) {
            LOGGER.error(e) { "Could not parse downloaded payload as UTF-8, attempting to parse as ISO 8859-1" }
            payload.toString(Charsets.ISO_8859_1)
                .also { LOGGER.info { "Successfully parsed downloaded payload as ISO 8859-1" } }
        }
    }

    fun markMessageAsRead(msgNo: String): String = runBlocking {
        LOGGER.trace { "Marking MsgNo $msgNo as read" }
        try {
            httpClient.post<String> {
                url("${AccessPointProps.inbox.url}/$msgNo/read")
                header(AccessPointProps.inbox.header, AccessPointProps.inbox.apiKey)
                header(HttpHeaders.Accept, ContentType.Application.Xml)
            }.also {
                LOGGER.info { "Successfully marked MsgNo $msgNo as read" }
            }
        } catch (e: Throwable) {
            LOGGER.error(e) { "Could not mark MsgNo $msgNo as read" }
            throw e
        }
    }

    suspend fun sendToOutbox(
        payload: String,
        header: Header,
        attempts: Int = 10
    ): Result<OutboxPostResponseType, ErrorMessage> =
        runCatching {
            retry(
                callName = "Access Point - Outbound",
                attempts = attempts,
                illegalExceptions = *arrayOf(ClientRequestException::class)
            ) {
                LOGGER.info { "SendToOutbox - Sending new message to outbox" }
                httpClient.submitFormWithBinaryData<String>(formData = mapToFormData(payload, header)) {
                    url(AccessPointProps.outbox.url)
                    header(AccessPointProps.outbox.header, AccessPointProps.outbox.apiKey)
                    header(HttpHeaders.Accept, ContentType.Application.Xml)
                }
            }.let { response ->
                LOGGER.debug { "SendToOutbox - Payload: '$response'" }
                JAXB.unmarshal(response.byteInputStream(), OutboxPostResponseType::class.java)
            }
        }.fold(
            onSuccess = { response -> Ok(response) },
            onFailure = { e -> e.toErrorMessage() }
        )

    suspend fun transmitMessage(
        outboxResponse: OutboxPostResponseType,
        attempts: Int = 10
    ): Result<QueuedMessagesSendResultType, ErrorMessage> =
        runCatching {
            val msgNo = outboxResponse.message.messageMetaData.msgNo
            LOGGER.info { "Transmitting MsgNo '$msgNo' to external party" }
            retry(
                callName = "Access Point - Transmit",
                attempts = attempts,
                illegalExceptions = *arrayOf(ClientRequestException::class)
            ) {
                httpClient.get<String> {
                    url("${AccessPointProps.transmit.url}/$msgNo")
                    header(AccessPointProps.transmit.header, AccessPointProps.transmit.apiKey)
                    header(HttpHeaders.Accept, ContentType.Application.Xml)
                }
            }.let { response ->
                LOGGER.debug { "SendToOutbox - Payload: '$response'" }
                JAXB.unmarshal(response.byteInputStream(), QueuedMessagesSendResultType::class.java)
            }
        }.fold(
            onSuccess = { response ->
                when {
                    response.succeededCount != 1 -> Err(ErrorMessage.AccessPoint.TransmitError)
                    else -> Ok(response)
                }
            },
            onFailure = { e -> e.toErrorMessage() }
        )

    // Used during resubmit to set original in body as the body to used when resubmitted
    override fun process(exchange: Exchange) {
        LOGGER.apply {
            error { "OnPrepareResubmit" }
            error { "Current Body: ${exchange.getIn().body}" }
            error { "Exchangeheaders: ${exchange.getIn().headers}" }
        }
        exchange.getIn().body = exchange.unitOfWork.originalInMessage.body
    }

    fun calculateDocumentID(
        @XPath(value = "namespace-uri(/*)") rootNamespace: String,
        @XPath(value = "local-name(/*)") localName: String,
        customizationId: String,
        @XPath(
            value = "/*/cbc:UBLVersionID/text()",
            namespaces = [(NamespacePrefix(
                prefix = "cbc",
                uri = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
            ))]
        )
        ublVersion: String
    ): String {
        LOGGER.apply {
            info { "rootNamespace: $rootNamespace" }
            info { "localName: $localName" }
            info { "customizationId: $customizationId" }
            info { "ublVersion: $ublVersion" }
        }
        return "$rootNamespace::$localName##$customizationId::$ublVersion"
    }
}

private inline fun <reified T> Throwable.toErrorMessage(): Result<T, ErrorMessage> =
    Err(
        error = when (this) {
            is ResponseException -> ErrorMessage.AccessPoint.ServerResponseError
            is DataBindingException -> ErrorMessage.DataBindError
            else -> ErrorMessage.InternalError
        }
    )

private fun mapToFormData(payload: String, header: Header): List<PartData> = formData {
    append("file", payload, headersOf(HttpHeaders.ContentType, "${ContentType.Application.Xml}"))
    append("SenderID", header.sender.identifier, headersOf(HttpHeaders.ContentType, "${ContentType.Text.Plain}"))
    append("RecipientID", header.receiver.identifier, headersOf(HttpHeaders.ContentType, "${ContentType.Text.Plain}"))
    append("DocumentID", header.identifier.identifier, headersOf(HttpHeaders.ContentType, "${ContentType.Text.Plain}"))
    append("ProcessID", header.process.identifier, headersOf(HttpHeaders.ContentType, "${ContentType.Text.Plain}"))
}
