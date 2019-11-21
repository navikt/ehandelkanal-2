package no.nav.ehandel.kanal.domain.documenttypes.order

import java.util.Date
import no.difi.commons.ubl21.jaxb.OrderType
import no.difi.vefa.peppol.common.model.DocumentTypeIdentifier
import no.difi.vefa.peppol.common.model.Header
import no.difi.vefa.peppol.common.model.InstanceIdentifier
import no.difi.vefa.peppol.common.model.InstanceType
import no.difi.vefa.peppol.common.model.ParticipantIdentifier
import no.difi.vefa.peppol.common.model.ProcessIdentifier
import no.nav.ehandel.kanal.common.functions.getCorrelationId
import no.nav.ehandel.kanal.services.sbdhgenerator.SbdhGeneratorService.Companion.ISO6323_1_ACTOR_ID_CODE

private const val DOCUMENT_TYPE_STANDARD = "urn:oasis:names:specification:ubl:schema:xsd:Order-2"
private const val DOCUMENT_TYPE = "Order"

internal fun OrderType.mapToHeader(): Header = Header.newInstance()
    .sender(ParticipantIdentifier.of("$ISO6323_1_ACTOR_ID_CODE::${this.buyerCustomerParty.party.endpointID.value}"))
    .receiver(ParticipantIdentifier.of("$ISO6323_1_ACTOR_ID_CODE::${this.sellerSupplierParty.party.endpointID.value}"))
    .process(ProcessIdentifier.of(this.profileID.value))
    .documentType(
        DocumentTypeIdentifier.of(
            "$DOCUMENT_TYPE_STANDARD::$DOCUMENT_TYPE##${this.customizationID.value}::${this.ublVersionID.value}"
        )
    )
    .instanceType(InstanceType.of(DOCUMENT_TYPE_STANDARD, DOCUMENT_TYPE, this.ublVersionID.value))
    .creationTimestamp(Date())
    .identifier(InstanceIdentifier.of(getCorrelationId()))
