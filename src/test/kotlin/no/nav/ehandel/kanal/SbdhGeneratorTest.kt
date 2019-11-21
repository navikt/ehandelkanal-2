package no.nav.ehandel.kanal

import com.github.michaelbull.result.getErrorOrElse
import com.github.michaelbull.result.getOrElse
import no.difi.commons.ubl21.jaxb.OrderType
import no.nav.ehandel.kanal.common.constants.MDC_CALL_ID
import no.nav.ehandel.kanal.common.functions.randomUuid
import no.nav.ehandel.kanal.common.models.ErrorMessage
import no.nav.ehandel.kanal.helpers.getResource
import no.nav.ehandel.kanal.services.sbdhgenerator.SbdhGeneratorService
import org.amshove.kluent.`should equal`
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.slf4j.MDC

class SbdhGeneratorTest {

    private val sbdhGeneratorService = SbdhGeneratorService()
    private val callId = randomUuid()

    @Before
    fun setup() {
        MDC.put(MDC_CALL_ID, callId)
    }

    @Test
    fun `given a valid Order payload, should generate valid SBDH`() {
        val payload: String = "/outbound/outbound-valid-order-no-sbdh.xml".getResource()
        sbdhGeneratorService
            .generateSbdh<OrderType>(payload)
            .getOrElse { throw IllegalStateException("should not return error") }
            .run {
                sender.identifier shouldEqual "9908::889640782"
                receiver.identifier shouldEqual "9908::889640782"
                process.identifier shouldEqual "urn:www.cenbii.eu:profile:bii28:ver2.0"
                documentType.identifier shouldEqual "urn:oasis:names:specification:ubl:schema:xsd:Order-2::Order##urn:www.cenbii.eu:transaction:biitrns001:ver2.0:extended:urn:www.peppol.eu:bis:peppol28a:ver1.0:extended:urn:www.difi.no:ehf:ordre:ver1.0::2.1"
                with(instanceType) {
                    standard shouldEqual "urn:oasis:names:specification:ubl:schema:xsd:Order-2"
                    type shouldEqual "Order"
                    version shouldEqual "2.1"
                }
                identifier.identifier shouldEqual callId
            }
    }

    @Test
    fun `given an invalid payload, should return parse error message`() {
        val payload = """{ "payload": "I can't believe it's not XML""}"""
        sbdhGeneratorService
            .generateSbdh<OrderType>(payload)
            .getErrorOrElse { throw IllegalStateException("should return error") }
            .`should equal`(ErrorMessage.SbdhGenerator.CouldNotParseDocumentType)
    }

    @Test
    fun `given a non-matching document type, should return invalid document message`() {
        val payload: String = "/outbound/outbound-valid-invoice-no-sbdh.xml".getResource()
        sbdhGeneratorService
            .generateSbdh<OrderType>(payload)
            .getErrorOrElse { throw IllegalStateException("should return error") }
            .`should equal`(ErrorMessage.SbdhGenerator.CouldNotMapPayloadToSbdh)
    }
}
