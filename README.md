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
![Arkitekturdiagram](docs/illustration-architecture.png)

## Funkjsonalitet
Hver tiende sekund trigges følgende prosess:
1. Kall `GET /hent-uleste-meldinger` mot VefasRest Inbox API for å hente liste over nye EHF-fakturaer
2. Kall  `GET /xml-document/{msgNo}` mot VefasRest Message API for å hente meldingspayload i xml format
for de listede nye meldingene
3. Validerer xml 
4. Router meldinger basert på Dokumenttype og størrelse
5. Lagrer xml i juridisk logg gjennom access punkt (kun for Invoice/CreditNote under størrelsesbegrensning)
6. Kall `PUT /marker-som-lest/{msgNo}` mot VefasRest Inbox API for å markere at meldingen er blitt lest
7. Logger metadata om prosesserte meldinger til PostgreSQL-database on prem for rapportering.
Tjenesten eksponerer også et `/reports`-endepunkt for å hente rapporter over prosesserte meldinger.

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
- Størrelseskonstanter: `LEGAL_ARCHIVE_SIZE_LIMIT` (linje 30) og `catalogueSizeLimit` i `Configuration.kt` tj
## Avhengigheter
- **Filområde via FTP** - tidligere koblet sammen med EBASYS, brukes for å lagre Invoice, CreditNote og store Catalogue-filer
- **Meldingskø (MQ)** - brukes for OrderResponse og små Catalogue-meldinger
- **PostgreSQL database (on prem)** - lagrer metadata om prosesserte inbound i databasen og eksponerer disse via `/reports`-endepunktet
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
- **`/reports` endepunkt** - tjenesten eksponerer egne endepunkter for rapportering (beskyttet med Azure AD via NAIS):

## Hvordan kjøre lokalt
Applikasjonen kan bygges lokalt ved å kjøre `./gradlew clean build`

Det har ikke blitt satt opp noen enkel måte å kjøre tjenesten lokalt på, ettersom det krever kontakt med nødvendige
avhengigheter (filområde, meldingskø, access points).

Ny funksjonalitet testes i dev-fss etter merge til main. Dependabot bruker også denne testinstansen
midlertidig før merge. Avtal funksjonell testing i dev slik at den ikke kolliderer med bot-deployer.

## Testing
PR-er kjører `./gradlew --warning-mode all clean build shadowJar`, inkludert tester med mockede
integrasjoner. Image-røyktesten kontrollerer Java 21, `JDK_JAVA_OPTIONS`, Nais-truststore og Europe/Oslo.
Den kjøres før main-deploy og før Dependabot-deploy. Vanlige PR-er publiserer eller deployer ikke image;
Dependabot-jobben hoppes over og tar ikke dev-låsen for slike PR-er.

Vellykket utrulling er ikke bevis på at hele fakturaflyten fungerer. Henting av testfaktura,
routing til MQ/FTP/arkiv, databaseføring og lest-markering verifiseres separat i dev.

## Deploy
Utvikling skjer på feature-/fix-brancher med PR mot main. Bruk squash-merge og referer til Jira-saken.
Merge til main bygger og tester, publiserer image og deployer automatisk til dagens `ehandelkanal-2`
i dev-fss. Workflowen venter på utrulling og lagrer kjent fungerende image-digest, commit og
dev-konfigurasjon som artifactet `dev-main-baseline`.

Prodsetting startes manuelt med **Build, push, and deploy** på main og `deploy_prod=true`.
Standardverdien er `false`. Kjøringen verifiserer først en ny build i dev; prod-jobben venter deretter
på godkjenning i GitHub environmentet `prod-fss:team-oebs`. Prod bruker samme image-digest som
denne dev-kjøringen, uten nytt image-bygg. Kontroller commit og digest i kjøringens sammendrag,
og verifiser funksjonaliteten i dev før prod godkjennes. Push deployer aldri automatisk til prod.

### Dependabot

Workflowene følger mønsteret i [okonomimodell-api](https://github.com/navikt/okonomimodell-api/tree/main/.github/workflows),
tilpasset Gradle og FSS. Dependabot-PR-er mot main bygger, tester og kjører image-røyktesten før
PR-imaget deployes til **dagens dev-app med dagens testressurser**. Det opprettes ingen ekstra
app, identitet, database, kø eller filområde.

Main-deploy og Dependabot bruker samme dev-lås. Før PR-deploy hentes sist verifiserte main-grunnlag;
etter testen gjenopprettes dette imaget og den lagrede konfigurasjonen, også hvis PR-deploy feiler.
Dependabot-sjekken blir bare grønn når PR-testen og gjenopprettingen har lykkes.

Artifactene `dev-deploy-started`, `dev-main-baseline` og `dev-restored` registrerer deploy-tilstanden.
Hvis siste registrerte deploy ikke er fullført/gjenopprettet, eller main-grunnlaget mangler eller
er utløpt, stoppes neste PR-deploy før dev endres. Artifactene beholdes i 30 dager; kjør en ny
main-deploy hvis grunnlaget er utløpt. Ikke slett disse artifactene mens bot-deploy brukes.

Ved avbrudd eller feilet gjenoppretting: kontroller digest/commit i kjøringens sammendrag og artifact,
og kjør hovedworkflowen på gjeldende main med `deploy_prod=false` for å etablere kjent dev-tilstand
igjen. Ikke tving videre auto-merge eller slett tilstandsmarkørene for å omgå feilen. Ved behov for
eldre kode må den tas tilbake gjennom en gjennomgått main-endring. Kontroller databasekompatibilitet først.

Auto-merge kopierer referansens policy: **major, minor, patch og unknown**, med auto-approve,
squash og blackout-perioder. Dette er bevisst bredere enn bare patch/minor. Java 27 skal fortsatt
stoppes av Java 21-røyktesten. Overgang til Digestabot håndteres separat.

### Endringer som kan brekke

| Endring | Konsekvens |
|---|---|
| Dependabot erstatter dev-versjonen midlertidig | Manuell testing kan treffe en PR-versjon i stedet for main. |
| Dev bruker `Recreate` | Gammel pod avsluttes før ny starter, med nedetid under utrulling. Dette hindrer normal rolling-update-overlapp av pollere; prod-strategien er uendret. |
| PR-en kjører mot vanlige testressurser | Meldinger kan behandles og markeres lest; MQ-/FTP-/arkiv-/DB-effekter reverseres ikke av image-gjenoppretting. |
| Flyway kjøres ved oppstart | En databaseendring kan gjøre gammelt image inkompatibelt. Automatisk gjenoppretting er ikke en database-rollback. |
| Readiness blir grønn | Dette er en utrullingskontroll, ikke full kontroll av bakgrunnsinitialisering eller eksterne integrasjoner. |
| Runneren forsvinner eller workflowen avbrytes hardt | Cleanup kan utebli. Neste PR-deploy blokkeres til main har etablert kjent tilstand. |
| master/dev erstattes av main | Branch-filtre, bot-PR-er, Nais/OIDC og påkrevde sjekker må oppdateres samlet. |

### Oppsett og overgang til main

Repo-administrator må konfigurere dette før bot-auto-merge og prodsetting aktiveres:

- Installer en repo-avgrenset GitHub App med **Contents: read/write**, **Pull requests: read/write**,
  **Checks: read** og **Commit statuses: read**. Legg App-ID i variabelen `CLIENT_ID` og nøkkelen i
  secret `PRIVATE_KEY`. App-tokenet gjør at merge utløser vanlig main-deploy.
- Beskytt main med påkrevde sjekker **Run tests** og **Verify dependabot deploy**, oppdatert branch
  før merge og uten bot-bypass. Aktiver repoets **Allow auto-merge**.
- Lukk eller flytt gamle Dependabot-PR-er mot dev før auto-merge aktiveres. Auto-merge-workflowen
  stopper hvis slike PR-er finnes, siden referansens action ikke begrenser seg til main-PR-er.
- Konfigurer godkjennere og main-begrensning i `prod-fss:team-oebs`. Hindre egen godkjenning og
  administrator-bypass der dette støttes. YAML-en alene gir ingen menneskelig godkjenningsport.
- Verifiser Nais-/OIDC-tilgang for main og Dependabot-PR-er. `dev-fss:team-oebs` må tillate disse
  PR-deployene; PR-jobbene skal ikke ha prod-tilgang.

Ta workflow-endringene inn på master før navnebyttet: de nye deploy-jobbene kjører bare fra main,
så denne mergen deployer ikke til prod. PR-filteret støtter master midlertidig for migrerings-PR-en.
Kontroller gamle kjøringer først; nye workflow-filer stanser ikke allerede startede eller ventende
prod-deployer som bruker den gamle workflowen.
Bytt deretter standardbranch til main og kjør første vellykkede main-deploy til dev for å opprette
gjenopprettingsgrunnlaget. Håndter gamle dev-PR-er før auto-merge aktiveres. Verifiser Dependabot-deploy,
gjenoppretting og merge-trigger før `dev/ehandelkanal-2` slettes. Den gamle dev-branchen har fortsatt
sin gamle deploy-workflow til den avvikles; ikke push eller merge dit under overgangen.
Fjern master fra PR-filteret etter overgangen.

**Rød sone — forstå dette grundig:** delte fakturakilder, databaseoppstart, PR-rettigheter,
gjenoppretting og prod-godkjenning. Bruk bare avtalte testdata i dev.

## Alarmer i FSS

[oebs-alerts](https://github.com/navikt/oebs-alerts) har ansvar for alarmregler,
terskler, AlertmanagerConfig, Slack-ruting, tester, validering og deploy i dev-fss og prod-fss.
Endringer i alarmoppsettet gjøres der, ikke i app-repoet. Driftsrutiner og oppfølging av
varsler beskrives nedenfor.

Appen eksponerer fortsatt simpleclient-metrikker på `/internal/prometheus`.
Instrumentering og appens build/deploy, inkludert `deploy_prod`, forvaltes i dette repoet.

### Overføring av alarmansvar

Overtakelsen i dev-fss og prod-fss er bekreftet av ansvarlig:
reglene evaluerer med reelle data, og firing/resolved når riktig mottaker.
Gamle alarmkjøringer er avklart. Alarmvalidering håndheves i oebs-alerts.
Oppryddingen i Git skal ikke slette installerte alarm- eller rutingsressurser i clusterne.

### Betydningen av varsler

Se oebs-alerts for gjeldende alarmuttrykk, terskler og ventetider.
At en hendelsesalarm går til resolved betyr at hendelsen har falt ut av regelens tidsvindu,
**ikke** at dokumentet er rettet eller arkivert. Fravær av målinger påvirkes også av
Prometheus sin lookback-periode; feil i selve alarmmotoren må overvåkes av plattformen.

### Driftsrutiner for alarmer

Start med miljø og tidsrom fra alarmen. Kontroller deployment og logger i riktig FSS-cluster,
ikke en GCP-instans. Bruk godkjent logg-/fagsystem for å finne berørte meldinger.
Avtal mottakere, reaksjonstid og hvem som følger opp varsler utenfor arbeidstid.
Ved manglende Slack-varsler kontrollerer alarmansvarlig regler og ruting i oebs-alerts.
Prøv varslingskjeden med en avtalt testalarm, inkludert resolved; ikke stopp prod-appen
eller fremprovoser en fakturafeil for å teste rutingen.

| Type feil | Kontroller og følg opp |
|---|---|
| Utilgjengelig, restart eller OOM | Deployment, pod-events og forrige container-logg. FTP-test og Vault-/DB-bakgrunnsjobber kan stoppe appen. Se på minne og dokumentstørrelser før ressursendring. |
| Målinger mangler | Pod, `/internal/prometheus`, scrape-target og datakilde. JVM-målingen skal finnes også når det ikke kommer dokumenter. |
| Juridisk arkiv | Finn manglende arkivering og avklar videre oppfølging. Appen kan ha fortsatt levering etter arkivfeilen. Dokumenter over 20 MB hopper normalt over arkivet og utløser ikke denne telleren. |
| Manuell levering feilet | Kontroller FTP, rettigheter, kapasitet og faktisk innhold i manuellBehandling. Avklar om dokumentet fortsatt er ulest. |
| Rapportlagring | Kontroller PostgreSQL, credentials-fornyelse og resultatet av feilruten. Retries kan ta lang tid før telleren økes. |
| Manuell behandling / XML | Undersøk formatfeil og behandle dokumentet etter avtalt rutine. XML-telleren dekker ikke alle mulige XML-/SBDH-feil, og feil-telleren dekker ikke alle manuelle løp. |

**Rød sone — forstå dette grundig:** Levering til FTP/MQ/arkiv og markering som lest er
ikke én atomisk operasjon. Ikke marker dokumenter lest, send fakturaer på nytt eller start
parallell behandling uten å ha avklart leveringsstatus og duplikatfare. Slack-varsler og
metrikklabeller skal ikke inneholde XML, body, headers, fakturanummer, leverandørdata eller token.

### Målinger og oppsett som fortsatt bør forbedres

Alarmoppsettet er avhengig av appens målinger, som ikke dekker hele fakturaflyten.
Følgende forbedringer må vurderes i app-repoet og samordnes med alarmreglene i oebs-alerts:

- Mål vellykket polling, pågående arbeid, behandlingsfremdrift og restansealder. Varsle om
  stillstand med arbeid som venter, ikke om lav trafikk alene. Terskler må ta høyde for store batcher.
- Mål vedvarende nedlastingsfeil og feil ved markering som lest. Sistnevnte kan gi ny behandling
  av allerede leverte dokumenter. Bruk faste integrasjons-/operasjonslabels, ikke meldings-ID-er.
- Skill leveringsresultat for MQ, FTP, arkiv og manuell behandling. Dagens `messages_successful`
  økes før lest-markering og i enkelte avviksløp; den er ikke et ende-til-ende-SLO.
- Registrer meldingstellerne ved oppstart og vurder hvordan første feil skal fanges opp.
  `Metrics` initialiseres i dag ved første bruk. `increase` krever minst to observasjoner og
  kan ikke oppdage en feil som allerede inngår i første scrape eller skjer mellom scrapes før restart.
- Instrumenter HikariCP, vedvarende Vault-fornyelsesfeil og HTTP-klientresultater før slike
  regler legges til. Referansens `hikaricp_connections_pending`, `logback_events_total` og
  Spring HTTP-metrikker finnes ikke automatisk her.
- Avklar store kataloger: filskrivingen er kommentert ut i `Inbound.kt`, mens løypa logger
  vellykket levering til filområde. Dette må avklares som forretningslogikk, ikke løses med alarmtekst.
- Gjennomgå readiness etter asynkron DB-initialisering. Ikke gjør liveness avhengig av alle
  eksterne tjenester; det kan gi restartstormer uten å rette integrasjonsfeilen.
- Fjern unødvendig body-/header-logging før eventuelle loggbaserte alarmer innføres.

## Dokumentasjon
Det eksisterer ingen dokumentasjon på tjenesten utover denne readme-filen, så det bør gjøres oppdateringer her 
ved endring av funksjonalitet. 
