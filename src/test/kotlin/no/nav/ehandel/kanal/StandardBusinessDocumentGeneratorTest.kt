package no.nav.ehandel.kanal

import com.github.michaelbull.result.getErrorOrElse
import com.github.michaelbull.result.getOrElse
import no.difi.commons.ubl21.jaxb.ForecastType
import no.difi.commons.ubl21.jaxb.OrderType
import no.difi.vefa.peppol.common.model.Header
import no.nav.ehandel.kanal.common.constants.MDC_CALL_ID
import no.nav.ehandel.kanal.common.functions.randomUuid
import no.nav.ehandel.kanal.common.models.ErrorMessage
import no.nav.ehandel.kanal.helpers.getResource
import no.nav.ehandel.kanal.services.sbd.StandardBusinessDocumentGenerator
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.junit.Before
import org.junit.Test
import org.slf4j.MDC

class StandardBusinessDocumentGeneratorTest {

    private val sbdGenerator = StandardBusinessDocumentGenerator()
    private val callId = randomUuid()

    @Before
    fun setup() {
        MDC.put(MDC_CALL_ID, callId)
    }

    @Test
    fun `given a valid document, should generate valid SBD`() {
        sbdGenerator.runPositiveTest<OrderType>(
            "/outbound/outbound-valid-order-no-sbdh.xml"
        ) { (header, document) ->
            with(header) {
                sender.identifier shouldBeEqualTo "9908:889640782"
                receiver.identifier shouldBeEqualTo "9908:889640782"
                process.identifier shouldBeEqualTo "urn:www.cenbii.eu:profile:bii28:ver2.0"
                documentType.identifier shouldBeEqualTo "urn:oasis:names:specification:ubl:schema:xsd:Order-2::Order##urn:www.cenbii.eu:transaction:biitrns001:ver2.0:extended:urn:www.peppol.eu:bis:peppol28a:ver1.0:extended:urn:www.difi.no:ehf:ordre:ver1.0::2.1"
                with(instanceType) {
                    standard shouldBeEqualTo "urn:oasis:names:specification:ubl:schema:xsd:Order-2"
                    type shouldBeEqualTo "Order"
                    version shouldBeEqualTo "2.1"
                }
                identifier.identifier shouldBeEqualTo callId
            }
            document.shouldNotBeEmpty()
        }
    }

    @Test
    fun `given a valid document with v3 scheme ID, should return a valid SBD`() {
        sbdGenerator.runPositiveTest<OrderType>(
            "/outbound/outbound-valid-order-schemeid-v3-no-sbdh.xml"
        ) { (header, document) ->
            with(header) {
                sender.identifier shouldBeEqualTo "0192:889640782"
                receiver.identifier shouldBeEqualTo "0192:889640782"
                process.identifier shouldBeEqualTo "urn:www.cenbii.eu:profile:bii28:ver2.0"
                documentType.identifier shouldBeEqualTo "urn:oasis:names:specification:ubl:schema:xsd:Order-2::Order##urn:www.cenbii.eu:transaction:biitrns001:ver2.0:extended:urn:www.peppol.eu:bis:peppol28a:ver1.0:extended:urn:www.difi.no:ehf:ordre:ver1.0::2.1"
                with(instanceType) {
                    standard shouldBeEqualTo "urn:oasis:names:specification:ubl:schema:xsd:Order-2"
                    type shouldBeEqualTo "Order"
                    version shouldBeEqualTo "2.1"
                }
                identifier.identifier shouldBeEqualTo callId
            }
            document.shouldNotBeEmpty()
        }
    }

    @Test
    fun `given an invalid payload, should return parse error message`() {
        sbdGenerator.runNegativeTest<OrderType>(
            payloadPath = "/outbound/outbound-invalid-payload.json",
            expectedErrorMessage = ErrorMessage.StandardBusinessDocument.FailedToParseDocumentType
        )
    }

    @Test
    fun `given an unhandled document type, should return invalid document message`() {
        sbdGenerator.runNegativeTest<ForecastType>(
            payloadPath = "/outbound/outbound-valid-invoice-no-sbdh.xml",
            expectedErrorMessage = ErrorMessage.StandardBusinessDocument.InvalidDocumentType
        )
    }

    @Test
    fun `given an invalid document with missing required values, should return error message`() {
        sbdGenerator.runNegativeTest<OrderType>(
            payloadPath = "/outbound/outbound-invalid-order-missing-required-values-no-sbdh.xml",
            expectedErrorMessage = ErrorMessage.StandardBusinessDocument.MissingRequiredValuesFromDocument
        )
    }

    @Test
    fun `given a document with an invalid scheme ID, should return error message`() {
        sbdGenerator.runNegativeTest<OrderType>(
            payloadPath = "/outbound/outbound-invalid-order-invalid-schemeid-no-sbdh.xml",
            expectedErrorMessage = ErrorMessage.StandardBusinessDocument.InvalidSchemeIdForParticipant
        )
    }

    private inline fun <reified T> StandardBusinessDocumentGenerator.runPositiveTest(
        payloadPath: String,
        validate: (Pair<Header, String>) -> Unit
    ) {
        this.generateStandardBusinessDocument<T>(payloadPath.getResource())
            .getOrElse { throw IllegalStateException("should not return error") }
            .run { validate(this) }
    }

    private inline fun <reified T> StandardBusinessDocumentGenerator.runNegativeTest(
        payloadPath: String,
        expectedErrorMessage: ErrorMessage
    ) {
        this.generateStandardBusinessDocument<T>(payloadPath.getResource())
            .getErrorOrElse { throw IllegalStateException("should return error") }
            .`should be equal to`(expectedErrorMessage)
    }
}
