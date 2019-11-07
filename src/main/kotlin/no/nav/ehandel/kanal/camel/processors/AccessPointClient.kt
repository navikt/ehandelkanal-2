package no.nav.ehandel.kanal.camel.processors

import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import java.io.IOException
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.ehandel.kanal.AccessPointProps
import no.nav.ehandel.kanal.getBody
import no.nav.ehandel.kanal.httpClient
import no.nav.ehandel.kanal.log.InboundLogger
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
            header(HttpHeaders.Accept, ContentType.Text.Plain.toString())
        }).also {
            LOGGER.trace { "Inbox count: $it" }
        }
    }

    fun getInboxMessageHeaders(exchange: Exchange): String = runBlocking {
        LOGGER.trace { "Getting inbox messageheaders" }
        httpClient.get<String> {
            url("${AccessPointProps.inbox.url}/")
            header(AccessPointProps.inbox.header, AccessPointProps.inbox.apiKey)
            header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
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
                header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
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
                header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
            }.also {
                LOGGER.info { "Successfully marked MsgNo $msgNo as read" }
            }
        } catch (e: Throwable) {
            LOGGER.error(e) { "Could not mark MsgNo $msgNo as read" }
            throw e
        }
    }

    @Throws(IOException::class)
    fun sendToOutbox(exchange: Exchange, sender: String, receiver: String, documentId: String, processId: String): String =
        runBlocking {
            LOGGER.info { "Sending new message to outbox" }
            val response = httpClient.submitFormWithBinaryData<String> {
                url("${AccessPointProps.outbox.url}/")
                header(AccessPointProps.outbox.header, AccessPointProps.outbox.apiKey)
                header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
                formData {
                    append("file", exchange.getBody<ByteArray>())
                    append("SenderID", sender)
                    append("RecipientID", receiver)
                    append("DocumentID", documentId)
                    append("ProcessID", processId)
                }
            }
            LOGGER.info { "Post response: $response" }
            response
        }

    fun transmitMessage(msgNo: String): String = runBlocking {
        LOGGER.info { "Transmitting MsgNo $msgNo to external" }
        httpClient.get<String> {
            url("${AccessPointProps.transmit.url}/$msgNo")
            header(AccessPointProps.transmit.header, AccessPointProps.transmit.apiKey)
            header(HttpHeaders.Accept, ContentType.Application.Xml.toString())
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

    fun calculateDocumentID(
        @XPath(value = "namespace-uri(/*)") rootNamespace: String,
        @XPath(value = "local-name(/*)") localName: String,
        customizationId: String,
        @XPath(value = "/*/cbc:UBLVersionID/text()", namespaces = [(NamespacePrefix(prefix = "cbc", uri = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"))])
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
