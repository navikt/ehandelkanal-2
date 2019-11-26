package no.nav.ehandel.kanal.domain.peppol.iso6523.v2

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import mu.KotlinLogging
import no.nav.ehandel.kanal.common.models.ErrorMessage

private val logger = KotlinLogging.logger { }

enum class Code(val code: String, val schemeId: String) {
    FR_SIRENE("0002", "FR:SIRENE"),
    SE_ORGNR("0007", "SE:ORGNR"),
    FR_SIRET("0009", "FR:SIRET"),
    ISO6523("0028", "ISO6523"),
    FI_VT("0037", "FI:OVT"),
    DUNS("0060", "DUNS"),
    GLN("0088", "GLN"),
    DK_P("0096", "DK:P"),
    IT_FTI("0097", "IT:FTI"),
    NL_KVK("0106", "NL:KVK"),
    IT_SIA("0135", "IT:SIA"),
    IT_SECETI("0142", "IT:SECETI"),
    AU_ABN("0151", "AU:ABN"),
    DK_DIGST("0184", "DK:DIGST"),
    NL_OIN("0190", "NL:OIN"),
    EE_RIK("0191", "EE:RIK"),
    NO_ORG("0192", "NO:ORG"),
    UBLBE("0193", "UBLBE"),
    SG_UEN("0195", "SG:UEN"),
    IS_KTNR("0196", "IS:KTNR"),
    DK_CPR("9901", "DK:CPR"),
    DK_CVR("9902", "DK:CVR"),
    DK_SE("9904", "DK:SE"),
    DK_VANS("9905", "DK:VANS"),
    IT_VAT("9906", "IT:VAT"),
    IT_CF("9907", "IT:CF"),
    NO_ORGNR("9908", "NO:ORGNR"),
    NO_VA("9909", "NO:VA"),
    HU_VAT("9910", "HU:VAT"),
    EU_VAT("9912", "EU:VAT"),
    EU_REID("9913", "EU:REID"),
    AT_VAT("9914", "AT:VAT"),
    AT_GOV("9915", "AT:GOV"),
    AT_CID("9916", "AT:CID"),
    IS_KT("9917", "IS:KT"),
    IBAN("9918", "IBAN"),
    AT_KUR("9919", "AT:KUR"),
    ES_VAT("9920", "ES:VAT"),
    IT_IPA("9921", "IT:IPA"),
    AD_VAT("9922", "AD:VAT"),
    AL_VAT("9923", "AL:VAT"),
    BA_VAT("9924", "BA:VAT"),
    BE_VAT("9925", "BE:VAT"),
    BG_VAT("9926", "BG:VAT"),
    CH_VAT("9927", "CH:VAT"),
    CY_VAT("9928", "CY:VAT"),
    CZ_VAT("9929", "CZ:VAT"),
    DE_VAT("9930", "DE:VAT"),
    EE_VAT("9931", "EE:VAT"),
    GB_VAT("9932", "GB:VAT"),
    GR_VAT("9933", "GR:VAT"),
    HR_VAT("9934", "HR:VAT"),
    IE_VAT("9935", "IE:VAT"),
    LI_VAT("9936", "LI:VAT"),
    LT_VAT("9937", "LT:VAT"),
    LU_VAT("9938", "LU:VAT"),
    LV_VAT("9939", "LV:VAT"),
    MC_VAT("9940", "MC:VAT"),
    ME_VAT("9941", "ME:VAT"),
    MK_VAT("9942", "MK:VAT"),
    MT_VAT("9943", "MT:VAT"),
    NL_VAT("9944", "NL:VAT"),
    PL_VAT("9945", "PL:VAT"),
    PT_VAT("9946", "PT:VAT"),
    RO_VAT("9947", "RO:VAT"),
    RS_VAT("9948", "RS:VAT"),
    SI_VAT("9949", "SI:VAT"),
    SK_VAT("9950", "SK:VAT"),
    SM_VAT("9951", "SM:VAT"),
    TR_VAT("9952", "TR:VAT"),
    VA_VAT("9953", "VA:VAT"),
    SE_VAT("9955", "SE:VAT"),
    BE_CBE("9956", "BE:CBE"),
    FR_VAT("9957", "FR:VAT"),
    DE_LID("9958", "DE:LID");

    companion object {
        fun findCodeForSchemeId(schemeId: String): Result<String, ErrorMessage> = values()
            .find { code -> code.schemeId == schemeId }
            ?.let { code -> Ok(code.code) }
            ?: schemeId.let { id ->
                // assumes that the scheme ID matches the actual code for usage in PEPPOL BIS v3
                if (id.matches(Regex("^[0-9]{4}$"))) {
                    logger.warn { "Code: Could not find PEPPOL participant code for parsed scheme ID '$schemeId', using '$schemeId' as code" }
                    Ok(id)
                } else {
                    logger.error { "Code: Invalid scheme ID for participant ('$schemeId')" }
                    Err(ErrorMessage.StandardBusinessDocument.InvalidSchemeIdForParticipant)
                }
            }
    }
}
