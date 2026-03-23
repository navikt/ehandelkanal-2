ehandelkanal-2
==============

[![Github Actions](https://github.com/navikt/ehandelkanal-2/workflows/Build,%20push,%20and%20deploy/badge.svg)](https://github.com/navikt/ehandelkanal-2/actions?query=workflow%3A%22Build%2C+push%2C+and+deploy%22)

Tjenesten håndterer EHFs mottatt fra NAV's PEPPOL ved å pulle et aksesspunkt fra
[VefasRest Inbox API ](#avhengigheter) hver tiende sekund, og route tilhørende meldinger til enten et [filområde](#avhengigheter)
eller en [MQ meldingskø](#avhengigheter) basert på dokumenttype og størrelse.
Fra kø og filområde hentes meldingene av en annen tjeneste og leverer de til eyeshare.

Tjenesten benytter mye utdatert teknologi ettersom det er blitt gjort minimalt med vedlikehold siden 2019.
Det er forventet at tjenesten skal fases ut etter at det blir gjort anskaffelse på en ny tjeneste som skal håndtere mottak
og håndtering av EHF-fakturaer. Det er uklart når dette vil skje.

## Arkitektur 
Legg inn en arkitekturtegning her når den er tilgjengelig.

## Funkjsonalitet
Hver tiende sekund trigges følgende prosess:
1. Kall `GET /hent-uleste-meldinger` mot VefasRest Inbox API for å hente liste over nye EHF-fakturaer
2. Kall  `GET /xml-document/{msgNo}` mot VefasRest Message API for å hente meldingspayload i xml format
for de listede nye meldingene
3. Validerer xml 
4. Router meldinger basert på Dokumenttype og størrelse
5. Lagrer xml i juridisk logg gjennom access punkt (kun for Invoice/CreditNote under størrelsesbegrensning)
6. Kall `PUT /marker-som-lest/{msgNo}` mot VefasRest Inbox API for å markere at meldingen er blitt lest

### Routing av meldinger (punkt 4)
Meldinger routes ulikt avhengig av dokumenttype og filstørrelse:

| Dokumenttype | Filstørrelse | Destinasjon | Juridisk logg? |
|---|---|---|---|
| **Invoice** | ≤ 20 MB | Filområde via FTP | ✅ Ja |
| **Invoice** | > 20 MB | Filområde via FTP | ❌ Nei |
| **CreditNote** | ≤ 20 MB | Filområde via FTP | ✅ Ja |
| **CreditNote** | > 20 MB | Filområde via FTP | ❌ Nei |
| **OrderResponse** | Alle størrelser | MQ | ❌ Nei |
| **Catalogue** | ≤ 100 MB | MQ | ❌ Nei |
| **Catalogue** | > 100 MB | Filområde via FTP | ❌ Nei |
| **Unknown/Andre** | Alle størrelser | Eget filområde via FTP (manuellBehandling) | ❌ Nei |

**Størrelsesgrenser:**
- Juridisk logg: 20 MB (20 000 000 bytes)
- Catalogue MQ-grense: 100 MB (100 000 000 bytes)

**Referanse i koden:**
- Routing-logikk: `src/main/kotlin/no/nav/ehandel/kanal/camel/routes/Inbound.kt` (linje 145-175)
- Størrelseskonstanter: `LEGAL_ARCHIVE_SIZE_LIMIT` (linje 30) og `catalogueSizeLimit` i `Configuration.kt`

## Avhengigheter
- **Filområde via FTP** - tidligere koblet sammen med EBASYS, brukes for å lagre Invoice, CreditNote og store Catalogue-filer
- **Meldingskø (MQ)** - brukes for OrderResponse og små Catalogue-meldinger
- **VefasRest Inbox API** - brukes for å sjekke etter nye meldinger og markere dem som lest
  - Swagger (dev): https://sokos-vefasrest.intern.dev.nav.no/api/inbox/docs
  - Endepunkter: 
    - `GET /hent-uleste-meldinger` - Henter liste over uleste meldinger
    - `PUT /marker-som-lest/{msgNo}` - Markerer melding som lest
- **VefasRest Messages API** - brukes for å hente meldingspayload (XML)
  - Swagger (dev): https://sokos-vefasrest.intern.dev.nav.no/api/messages/docs
  - Endepunkter:
    - `GET /xml-document/{msgNo}` - Henter XML-payload for en melding
- **Juridisk logg** - brukes for å lagre Invoice og CreditNote under 20 MB for etterfølgende tilgang ved nedetid

## Hvordan kjøre lokalt
Applikasjonen kan bygges lokalt ved å kjøre `./gradlew clean build`

Det har ikke blitt satt opp noen enkel måte å kjøre tjenesten lokalt på, ettersom det krever kontakt med nødvendige
avhengigheter (filområde, meldingskø, access points).

Ved test av ny funksjonalitet anbefales det derfor å deploye til dev/ehandelskanal-2 branchen som kjører i dev-miljøet,
og teste funksjonaliteten der. Det er ingen andre som benytter data fra testmiljøet, så man kan trygt teste
funksjonalitet der uten å påvirke andre.

## Testing
Det er satt opp enhetstester for tjenesten, men ingen integrasjonstester eller ende til ende tester.

## Deploy
Utvikling bør skje på egen branch, hvor det opprettes en pull request til dev/ehandelkanal-2 branchen for testing i dev-miljøet.
Ved merge bør commitene squashes for å holde historikken ryddig, og pull requesten bør referere til jira saken endringen kommer fra.
Før deploy til prod bør det være verifisert at funksjonaliteten fungerer som forventet i dev-miljøet.

## Dokumentasjon
Det eksisterer ingen dokumentasjon på tjenesten utover denne readme-filen, så det bør gjøres oppdateringer her 
ved endring av funksjonalitet. 
