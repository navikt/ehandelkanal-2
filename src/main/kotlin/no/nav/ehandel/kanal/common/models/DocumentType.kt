package no.nav.ehandel.kanal.common.models

enum class DocumentType {
    Invoice, CreditNote, OrderResponse, Catalogue, Unknown;

    companion object {
        fun valueOfOrDefault(value: String, defaultValue: DocumentType = Unknown): DocumentType =
            try {
                DocumentType.valueOf(value)
            } catch (e: Throwable) {
                defaultValue
            }
    }
}
