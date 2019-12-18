package no.nav.ehandel.kanal.domain.documenttypes.invoice

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import java.util.Date
import mu.KotlinLogging
import no.difi.commons.ubl21.jaxb.InvoiceType
import no.difi.vefa.peppol.common.model.DocumentTypeIdentifier
import no.difi.vefa.peppol.common.model.Header
import no.difi.vefa.peppol.common.model.InstanceIdentifier
import no.difi.vefa.peppol.common.model.InstanceType
import no.difi.vefa.peppol.common.model.ParticipantIdentifier
import no.difi.vefa.peppol.common.model.ProcessIdentifier
import no.nav.ehandel.kanal.common.functions.getCorrelationId
import no.nav.ehandel.kanal.common.models.ErrorMessage
import no.nav.ehandel.kanal.domain.documenttypes.DOCUMENT_TYPE_VERSION
import no.nav.ehandel.kanal.domain.peppol.iso6523.v2.Code

private val logger = KotlinLogging.logger { }

private const val PEPPOL_ROOT_NAMESPACE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
private const val PEPPOL_LOCAL_NAME = "Invoice"

internal fun InvoiceType.mapToHeader(): Result<Header, ErrorMessage> {
    return runCatching {
        val senderSchemeId: String = Code
            .findCodeForSchemeId(this.accountingSupplierParty.party.endpointID.schemeID)
            .getOrElse { return Err(it) }
        val receiverSchemeId: String = Code
            .findCodeForSchemeId(this.accountingCustomerParty.party.endpointID.schemeID)
            .getOrElse { return Err(it) }
        Header.newInstance()
            .sender(
                ParticipantIdentifier.of("$senderSchemeId:${this.accountingSupplierParty.party.endpointID.value}")
            )
            .receiver(
                ParticipantIdentifier.of("$receiverSchemeId:${this.accountingCustomerParty.party.endpointID.value}")
            )
            .process(ProcessIdentifier.of(this.profileID.value))
            .documentType(
                DocumentTypeIdentifier.of(
                    "$PEPPOL_ROOT_NAMESPACE::$PEPPOL_LOCAL_NAME##${this.customizationID.value}::$DOCUMENT_TYPE_VERSION"
                )
            )
            .instanceType(InstanceType.of(PEPPOL_ROOT_NAMESPACE, PEPPOL_LOCAL_NAME, DOCUMENT_TYPE_VERSION))
            .creationTimestamp(Date())
            .identifier(InstanceIdentifier.of(getCorrelationId()))
    }.fold(
        onSuccess = { header -> Ok(header) },
        onFailure = { e ->
            logger.error(e) { "Could not generate SBDH for invoice document" }
            Err(ErrorMessage.StandardBusinessDocument.MissingRequiredValuesFromDocument)
        }
    )
}
