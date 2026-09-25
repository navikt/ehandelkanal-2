# Plan: Oppdatering av dependencies fra PR #41 (dependabot, 35 updates)

## Problem
PR #41 er en dependabot-gruppe-PR som bumper 35 avhengigheter i `build.gradle.kts`,
inkludert Gradle wrapper, Kotlin-pluginet og flere biblioteker som hopper over
flere major-versjoner (Camel 2→3, JAXB 2→4, Flyway 7→13, Exposed 0.41→1.5, H2 1→2,
HikariCP 5→7, vault-driver 3→5, kotlin-logging 1→3, logstash-encoder 7→9,
kotlin-result 1→2, wiremock-jre8 2→3, junit-vintage 5→6, IBM MQ 9→10, Kotlin-plugin 1.9→2.4,
shadow-plugin 7→8, ben-manes-versions 0.51→0.64, gradle wrapper 7→9). Å ta denne PR-en
i én omgang er for risikabelt. Vi lukker/ikke-mergerer PR #41 direkte, og gjør i stedet
stegvise, verifiserbare oppdateringer med egne commits, gruppert etter risiko.

## Strategi
1. **Minor/patch-oppdateringer først** (lav risiko) — samles i én commit, kjøres
   gjennom full testsuite.
2. **Major-oppdateringer, én dependency per commit** — hver commit:
   - Oppdaterer kun én dependency (eller en tett koblet gruppe, f.eks. alle
     `exposed-*`-modulene eller alle `camel-*`-modulene sammen).
   - Der det er flere major-versjoner mellom nåværende og mål, oppgraderes
     **stegvis til hver mellomliggende major** (f.eks. Flyway 7→8→9→...→13),
     med egen commit og testkjøring per steg — ikke bare siste steg.
   - Sjekker CHANGELOG/release notes for breaking changes før oppgradering.
   - Kjører `./gradlew test` (og `./gradlew build`) etter hver commit.
3. **Gradle wrapper og byggeverktøy sist** i egen fase, siden dette påvirker
   selve byggeprosessen for alle andre steg.
4. Der eksisterende tester ikke dekker endret oppførsel (f.eks. H2 SQL-modus,
   Exposed DSL-endringer, Flyway-konfigurasjon), utvides testene før/sammen med
   oppgraderingen.
5. **Blokkerte oppgraderinger utsettes, de tvinges ikke gjennom.** Gjelder for
   ethvert steg i alle faser: hvis en dependency ikke lar seg oppgradere til
   mål-versjonen fordi den (transitivt) krever en nyere versjon av noe vi ennå
   ikke har oppgradert (typisk Kotlin-pluginet eller Gradle), gjør vi følgende:
   - Oppgrader til høyeste versjon som faktisk er kompatibel med dagens
     Kotlin-plugin/Gradle-versjon (verifisert med `./gradlew clean test`).
   - Noter den utsatte dependencyen i **«Utsatt til senere» (under)** med
     hvilken forutsetning som mangler.
   - Gå videre til neste dependency i planen — ikke la ett blokkert steg
     stoppe resten av rekkefølgen.
   - Når forutsetningen er oppfylt (f.eks. etter Kotlin-plugin- eller
     Gradle-oppgradering i Fase 3/4), samles de utsatte oppgraderingene i
     **Fase 5 — Oppfølging av utsatte oppgraderinger**.
6. **Ett delsteg av gangen, med pause for bekreftelse mellom hvert.** Når en
   dependency krysser flere mellomliggende major-versjoner (f.eks.
   kotlin-logging 1.x → 2.x → 3.x), skal hvert delsteg gjøres, testes og
   **committes/bekreftes av bruker før neste delsteg gjøres** — ikke flere
   versjonshopp i samme fil-endring uten opphold imellom. Dette gir bruker
   mulighet til å committe/pushe eller avbryte etter hvert delsteg, i tråd med
   samme prinsipp som mellom ulike dependencies. Det holder ikke å bare
   rapportere delstegene i samme svar; selve endringen i `build.gradle.kts`
   skal stoppe ved delsteget og vente på bekreftelse før filen endres videre.

## Rekkefølge og faser

### Fase 0 — Forarbeid
- Verifiser at `./gradlew test` er grønn på `main` før vi starter (baseline).
- Merk kjente rødsone-punkter (kjernelogikk/sikkerhet) som krever manuell
  forståelse, ikke bare blind bump: Vault-driver (secrets), JAXB/SBDH-parsing
  (kjernelogikk for e-handelsmeldinger), Flyway (databasemigrasjon).

### Fase 1 — Minor/patch (lav risiko, én samlet commit, evt. delt i 2-3 commits)
| Dependency | Fra | Til | Notat |
|---|---|---|---|
| jackson-databind / jackson-module-kotlin / jackson-datatype-joda | 2.17.2 | 2.22.2 | Samme major (2.x), API-stabil |
| logback-classic | 1.5.6 | 1.6.3 | Samme major |
| peppol-sbdh (difi) | 1.1.3 | 1.1.4 | Patch |
| postgresql (JDBC) | 42.7.4 | 42.7.13 | Patch |
| mockk | 1.13.12 | 1.14.11 | Testbibliotek, samme major |
| com.github.ben-manes.versions (plugin) | 0.51.0 | 0.64.0 | Kun rapporteringsplugin |
| prometheus simpleclient (common/hotspot) | 0.8.0 | 0.16.0 | 0.x-serie, sjekk deprecations men API stort sett stabilt |

Commit-forslag: `chore(deps): oppdater lavrisiko-avhengigheter (jackson, logback, postgres, mockk, difi, prometheus)`

**Status (gjennomført):** jackson, logback-classic, peppol-sbdh, postgresql og
prometheus simpleclient er oppdatert som planlagt. mockk og
com.github.ben-manes.versions-pluginet ble **utsatt** (se «Utsatt til senere»
under) — jackson ble også kun delvis oppdatert (til 2.19.4, ikke 2.22.2) av
samme årsak. `./gradlew clean test`: BUILD SUCCESSFUL, 40/40 tester grønne.

## Utsatt til senere (fylles ut fortløpende, følges opp i Fase 5)

| Dependency | Ønsket mål-versjon | Faktisk satt til | Blokkert av | Følges opp når |
|---|---|---|---|---|
| jackson-databind / jackson-module-kotlin / jackson-datatype-joda | 2.22.2 | 2.19.4 | `jackson-module-kotlin` ≥2.20 krever Kotlin-stdlib 2.0+/2.1+ | Kotlin-plugin oppgradert til 2.x (Fase 3) |
| mockk | 1.14.11 | 1.13.12 (uendret) | mockk ≥1.13.13 krever Kotlin-stdlib 2.0+ | Kotlin-plugin oppgradert til 2.x (Fase 3) |
| com.github.ben-manes.versions (plugin) | 0.64.0 | 0.51.0 (uendret) | Krever Gradle ≥8.4 | Gradle wrapper oppgradert til 8.x+ (Fase 4) |
| shadow-plugin | 8.1.1 | 7.1.2 (uendret, allerede siste 7.x-versjon) | Shadow ≥8.0 krever Gradle ≥8.0 | Gradle wrapper oppgradert til 8.x+ (Fase 4) |
| junit-vintage-engine | 6.1.3 | 5.11.4 (fra 5.10.2) | junit-vintage-engine ≥5.12.0 krever nyere `junit-platform-launcher` enn det Gradle 7.6.4 bundler internt («unaligned versions»-feil ved test-discovery) | Gradle wrapper oppgradert til 8.x+ (Fase 4) |
| logstash-logback-encoder | 9.0 | 8.1 (fra 7.4) | 9.0 migrerer til Jackson 3 (`tools.jackson.*`-groupId), inkompatibelt med vår Jackson 2.19.4 (selv låst pga. Kotlin-stdlib-kobling) | Jackson oppgradert til 3.x, som igjen krever Kotlin-plugin 2.x (Fase 3/5) |
| jaxb-runtime / jakarta.xml.bind-api | 4.0.x | 2.3.9 / 2.3.3 | `no.difi.commons:commons-ubl21:0.9.5`, `commons-sbdh:0.9.5` og `no.difi.vefa:peppol-sbdh:1.1.4` (alle siste versjoner) er kompilert mot `javax.xml.bind` – en jakarta-runtime gjenkjenner ikke annotasjonene deres. Oxalis-etterfølgere finnes for SBDH (`network.oxalis.vefa:peppol-sbdh` 4.x), men ingen for `commons-ubl21` | Egen oppgave: bytte difi-bibliotekene (Oxalis for SBDH, egne genererte UBL-klasser e.l.) – ikke en ren versjonsoppgradering |

### Fase 2 — Én major-versjon å krysse (egen commit hver)
| Dependency | Fra | Til | Breaking changes å sjekke |
|---|---|---|---|
| IBM MQ allclient | 9.1.3.0 | 10.0.0.5 | Sjekk MQ-klient-API/JMS-kompatibilitet mot Camel-JMS-oppsettet |
| shadow-plugin | 7.1.2 | 8.1.1 | Task-navn/konfig for shadowJar kan ha endret seg |
| wiremock-jre8 | 2.35.1 | 3.0.1 | Artefakt `wiremock-jre8` er avviklet i 3.x → vurder bytte til `wiremock` (og evt. `wiremock-standalone`); pakkenavn/API endret (`com.github.tomakehurst.wiremock` → `org.wiremock`) |
| junit-vintage-engine | 5.10.2 | 6.1.3 | JUnit 6 krever nyere JUnit Platform; sjekk at JUnit 4-testene (`ArchiveRequestTest` mfl. som evt. bruker JUnit4) fortsatt kjøres av vintage-engine |

**Status (gjennomført):**
- IBM MQ allclient → 10.0.0.5: fullført uendret API (fortsatt `javax.jms-api`).
- shadow-plugin: **utsatt**, se «Utsatt til senere» (krever Gradle ≥8.0).
- wiremock-jre8 → org.wiremock:wiremock 3.0.1: fullført, pakkenavn uendret
  (`com.github.tomakehurst.wiremock.*`), ingen kodeendring nødvendig.
- junit-vintage-engine → 5.11.4 (**delvis**, ikke 6.1.3): se «Utsatt til senere».

Commit per rad, f.eks. `chore(deps): oppgrader ibm mq client til 10.0.0.5`.

### Fase 3 — Flere major-versjoner å krysse (stegvis, egne commits per steg)

**kotlin-logging: 1.7.6 → 3.0.5**
- Steg 1: 1.7.6 → siste 1.x (om nyere finnes)
- Steg 2: → siste 2.x (2.x byttet artefakt-koordinat/pakke fra
  `io.github.microutils:kotlin-logging` med Kotlin/JVM-spesifikk publisering —
  sjekk om artifact-ID endres til `kotlin-logging-jvm`)
- Steg 3: → 3.0.5
- Kjør testene som logger (feilhåndteringsstier) etter hvert steg.

**logstash-logback-encoder: 7.4 → 9.0**
- Steg 1: 7.4 → 8.1 — **fullført**. Eneste breaking change i 8.0 gjelder
  `logback-access` (ikke i bruk her). Ingen kodeendring nødvendig.
- Steg 2: 8.1 → 9.0 — **utsatt**, se «Utsatt til senere». 9.0 migrerer til
  Jackson 3 (`tools.jackson.*`), som er inkompatibelt med vår Jackson 2.19.4.

**JAXB runtime: 2.4.0 → 4.0.9**
- **Rødsone / kjernelogikk**: SBDH/XML-parsing er kjernen i meldingsflyten.
- **Korrigert under gjennomføring**: navnerom-migreringen skjer allerede ved
  **3.0.x**, ikke ved 4.0 som først antatt. Verifisert direkte i JAR-innhold:
  `jakarta.xml.bind-api:2.3.3` → pakke fortsatt `javax.xml.bind`;
  `jakarta.xml.bind-api:3.0.1` → pakke byttet til `jakarta.xml.bind`.
- Steg 1: `jaxb-runtime` 2.4.0-beta → **2.3.9** (siste stabile `javax.xml.bind`-
  generasjon), `jaxb-api`-avhengigheten byttet fra `javax.xml.bind:jaxb-api`
  til `jakarta.xml.bind:jakarta.xml.bind-api:2.3.3` (kun gruppe-/artefakt-
  navnebytte, pakkenavn uendret) — **fullført**, ingen kodeendring nødvendig,
  alle 40 tester grønne.
- Steg 2: 2.3.9 → 3.0.2/4.0.x — dette er **jakarta-navnerom-migreringen**
  (`javax.xml.bind.*` → `jakarta.xml.bind.*`). Krever endring i importer i
  `AccessPointClient.kt`, `InboundDataExtractor.kt`,
  `StandardBusinessDocumentGenerator.kt` og evt. genererte SBDH/UBL-klasser.
  Dette er den mest risikable enkeltoppgraderingen i PR-en.
  **Utsatt**, se «Utsatt til senere»: difi-bibliotekene vi er avhengige av
  finnes bare i `javax.xml.bind`-varianter.
- Verifiser med `InboundSbdhMetaDataExtractorTest`, `InboundSbdhRemoverTest`,
  `StandardBusinessDocumentGeneratorTest`, `XmlDetectorTest` — utvid disse om
  de ikke dekker (de)serialisering etter namespace-bytte.

**HikariCP: 5.1.0 → 7.1.0**
- Steg 1: 5.1.0 → 6.3.3 — **fullført**, ingen kodeendring. 6.0 innførte
  atomisk credentials-håndtering (#2189); verifisert i kildekoden at
  `hikariConfigMXBean.setUsername/setPassword` (Vault-rotasjon i
  `Database.runRenewCredentialsTask`) fortsatt oppdaterer credentials som
  leses ved hver ny tilkobling. 40/40 tester grønne.
- Steg 2: 6.3.3 → 7.1.0 — **fullført**, ingen kodeendring. 7.0 la til
  `HikariCredentialsProvider`; når den ikke er satt, brukes samme
  credentials-sti som i 6.x (verifisert i kildekoden). 40/40 tester grønne.
- **Etterarbeid:** rotasjonsblokken i `runRenewCredentialsTask` er trukket ut
  til `HikariDataSource.rotateCredentials(...)` og dekket av
  `CredentialRotationTest` (H2 med to brukere; gammel bruker ugyldiggjøres
  etter rotasjon). Verifisert med mutasjonssjekk: uten
  `softEvictConnections()` feiler testen. Token-fornyingen i Vault
  (`lookupSelf`/`renewSelf`) er dekket av `VaultTokenRenewalTest` (WireMock).
  Dette erstatter å vente ~24 t på første credential-rotasjon i dev.
- Sjekk minimum-Java-krav per major (nyere HikariCP kan kreve nyere JDK-baseline —
  vi er på JDK 21 så bør være greit, men bekreft).

**vault-java-driver: 3.1.0 → 5.1.0**
- Steg 1: 3.1.0 → 4.1.0 — **fullført, med kodeendring**. 4.0 antar KV v2
  som standard og skriver om alle `logical()`-stier (`<mount>/creds/<role>`
  → `<mount>/data/creds/<role>`), noe som ville knekt henting av
  DB-credentials i prod. Løst med `Vault(config, 1)` i ny
  `createVaultClient(...)` i `Vault.kt`. Ny `VaultClientTest` (WireMock)
  verifiserer URL og token-header: grønn på 3.1.0, rød på 4.1.0 uten fiks,
  grønn med fiks. 41/41 tester grønne.
- Steg 2: 4.1.0 → 5.1.0 — **fullført, med kodeendring**. 5.0 endret
  `Logical.read()` til å returnere 4xx-svar i stedet for å kaste
  `VaultException`. Uten tiltak ville en 403 ført til
  `IllegalStateException("Username is not set…")` og 403-loggen i
  `Database.getNewCredentials` ville aldri slått til. Løst med
  `Vault.readSecret(path)` som kaster `VaultException(status)` ved ikke-2xx.
  `Auth` (lookupSelf/renewSelf) kaster fortsatt ved ikke-200, uendret.
  Retry-endringen i 5.0 påvirker oss ikke (vi bruker ikke `withRetries`,
  standard er 0). Ny 403-test i `VaultClientTest`: rød uten fiks, grønn med.
  42/42 tester grønne.
- **Rødsone**: dette er secrets-håndtering. Sjekk endringer i
  autentiseringsmetoder/timeout-/retry-oppførsel manuelt, ikke bare bump.

**flyway-core + flyway gradle-plugin: 7.15.0 → 13.7.0**
- **Rødsone**: databasemigrasjon, risiko for datatap ved feil migreringsoppførsel.
- Stegvis gjennom major-linjene: 7 → 8 → 9 → 10 → 11 → 12 → 13, én commit per
  major-hopp (evt. slå sammen der release notes viser ingen config-endringer).
- Kjente brytende endringer å sjekke pr. steg: Flyway 8 (redesignet
  konfigurasjons-API), Flyway 10 (lisens-/edition-splitt, enkelte
  databasemotorer flyttet til «Teams»-utgave — bekreft at PostgreSQL/H2 fortsatt
  er i community/OSS-utgaven), Flyway-kommandolinjeflagg/`flywayCleanDisabled`-
  defaults kan ha endret seg.
- Oppdater `flyway { locations = ... }`-blokken i `build.gradle.kts` om syntaks
  har endret seg mellom versjoner.
- **Forutsetning**: prod/dev kjører PostgreSQL 17.10 (bekreftet fra
  oppstartslogg i dev). Flyway 8 Community krever PG ≥10.
- Steg 7 → 8 (7.15.0 → 8.5.13, core + Gradle-plugin) — **fullført**, ingen
  kodeendring. H2-migreringene dekkes av `InboundIT` (`Database.initLocal`).
  Oppgradering simulert: schema-historikk laget med 7.15.0 validerer med
  8.5.13 (checksummer uendret, 0 pending). PostgreSQL-stien (`initRemote`)
  kan ikke testes lokalt — verifiseres ved deploy til dev. 42/42 tester grønne.
- Steg 8 → 9 (8.5.13 → 9.22.3) — **fullført, med kodeendring**. Flyway 9
  endret standard for `cleanDisabled` til `true`, slik at
  `cleanOnValidationError(true)` i `Database.initLocal` (kun lokal H2-profil)
  kastet `FlywayException` i stedet for å rense og migrere på nytt. Løst med
  `cleanDisabled(false)` i `initLocal`; `initRemote` bruker ikke clean og
  får nå en tryggere standard. Ny `DatabaseInitLocalTest`: rød uten fiks,
  grønn med. Schema-historikk fra 7.15.0 validerer med 9.22.3. 43/43 grønne.
- Steg 9 → 10 (9.22.3 → 10.22.0) — **fullført, med bygg-endringer**.
  - PostgreSQL-støtte er skilt ut i egen modul: lagt til
    `runtimeOnly("org.flywaydb:flyway-database-postgresql")`. Uten den feiler
    `initRemote` med «No database found to handle jdbc:postgresql». H2 er
    fortsatt i core. Ny `FlywayDatabaseSupportTest` (bruker internt API
    `DatabaseTypeRegister`, må trolig justeres ved senere Flyway-hopp) —
    rød uten modulen, grønn med.
  - Flyway 10 finner databasemoduler via `ServiceLoader`. Uten
    `mergeServiceFiles()` i shadowJar overskrev PG-modulens
    `META-INF/services/org.flywaydb.core.extensibility.Plugin` core sin
    (H2 m.fl. forsvant fra fat-JAR). Lagt til `mergeServiceFiles()`.
    Sideeffekter gjennomgått: JDBC-drivere (PG + H2), Jackson-moduler (ikke
    auto-registrert hos oss), JAXB (samme impl), Camel `TypeConverter`
    (`org.apache.camel.core` er en dummy-markør som filtreres bort) — ingen
    endret oppførsel.
  - `flyway*`-Gradle-tasks (kun manuell bruk) vil trenge PG-modulen på
    buildscript-classpath om de skal brukes mot PostgreSQL.
  - 45/45 tester grønne.
- Steg 10 → 11 (10.22.0 → 11.20.3) — **fullført, med kodeendring**.
  `cleanOnValidationError` er fjernet i 11 (metoden finnes, men `migrate()`
  kaster «cleanOnValidationError has been removed» om den er satt), så
  `initLocal` feilet alltid. Erstattet med eksplisitt
  `validateWithResult()` → `clean()` ved feil → `migrate()`. Kun lokal
  profil; `initRemote` er uendret og bruker aldri clean.

  `ignoreMigrationPatterns("*:pending", "*:future")` avgjør hva som regnes
  som valideringsfeil:
  - `*:pending` — migreringer som finnes i koden, men ikke er kjørt ennå.
    Uten dette ville hver nye migrering gitt valideringsfeil og tømt den
    lokale databasen, i stedet for å bare kjøre den nye migreringen.
  - `*:future` — migreringer i databasen som koden ikke kjenner (f.eks.
    etter bytte til en eldre branch). Dette er Flyways standard, men må
    settes eksplisitt fordi vi overstyrer mønsterlisten.

  Dermed tømmes databasen bare ved ekte avvik, som endret checksum på en
  migrering som allerede er kjørt — tilsvarende den gamle oppførselen.
  `DatabaseInitLocalTest` utvidet med test for at pending migreringer ikke
  sletter data. Schema-historikk fra 7.15.0 validerer med 11.20.3; begge
  database-typer er med i fat-JAR. 46/46 tester grønne.
- Steg 11 → 12 (11.20.3 → 12.11.0) — **fullført**, ingen kodeendring.
  Java 17-bytekode (vi kjører 21), H2 fortsatt i core, PG-støtte uendret
  (PG 17 innenfor støttet område). Schema-historikk fra 7.15.0 validerer med
  12.11.0; begge database-typer er med i fat-JAR. 46/46 tester grønne.
- Steg 12 → 13 (12.11.0 → 13.8.0, ikke 13.7.0 som først planlagt) —
  **fullført**, ingen kodeendring. Samme API og DB-støtte som 12.
  - `flyway-core` 13 drar inn `jackson-annotations:2.22` (resten av Jackson
    er 2.19.4). Fra 2.20 versjoneres annotations uten patch og er
    bakoverkompatible med eldre databind, så kombinasjonen er støttet.
    Justeres naturlig når Jackson oppgraderes i Fase 5. Ingen Jackson 3
    (`tools.jackson`) på classpath.
  - CockroachDB er skilt ut i `flyway-database-cockroachdb`, som dras inn
    transitivt av PG-modulen. Ingen endring nødvendig.
  - Schema-historikk fra 7.15.0 validerer med 13.8.0; begge database-typer
    er med i fat-JAR. 46/46 tester grønne.
  - **Flyway-oppgraderingen er ferdig.** Deploy til dev og verifiser
    `initRemote` mot PostgreSQL før neste dependency.

**h2database: 1.4.200 → 2.5.250**
- **Rødsone / testinfrastruktur**: H2 2.x har strengere SQL-kompatibilitetsmodus
  (identifikatorer, reserverte ord, `MODE=`-innstillinger) — dette er testdatabasen,
  så alle DB-relaterte tester må kjøres grundig og eventuelt migreringsskript
  i `db/migration` justeres.
- Ett steg er nok versjonsmessig (1.x → 2.x er én major), men testdekningen må
  utvides der testene i dag stoler på lempelig SQL-parsing.

**exposed (core/dao/jdbc/java-time/jodatime): 0.41.1 → 1.5.0**
- **Rødsone**: databasetilgangslag, kjernelogikk.
- JetBrains Exposed 1.0 er en større API-revisjon (DSL/DAO-endringer,
  transaksjonshåndtering). Sjekk om det finnes en anbefalt mellomversjon
  (f.eks. siste 0.5x før 1.0) og oppgrader stegvis dit før hopp til 1.5.0.
- Alle DB-repository-tester må kjøres og eventuelt utvides for å dekke
  endret query-bygging.

**kotlin-result: 1.1.6 → 2.3.1**
- Steg 1: 1.x → siste 1.1.x/2.0-forhåndsversjon om relevant
- Steg 2: → 2.3.1 — sjekk endringer i `Result`-typen/ekstensjonsfunksjoner
  som brukes i feilhåndteringskoden (kjernelogikk).

**camel-core/jms/ftp/jsonpath/test: 2.24.2 → 3.22.4**
- **Rødsone / kjernelogikk**: dette er ruting-motoren for hele meldingsflyten.
- Camel 3.0 modulariserte `camel-core` i mange JAR-er og flyttet komponenter
  (se migreringsguide). Sjekk at `camel-jaxb`-bruk, JMS- og FTP-endepunkter,
  og eventuelle Spring-koordinater ikke er berørt.
- Stegvis: 2.24 → siste 2.x → 3.0.x (mest brytende) → siste 3.22.x.
- Alle integrasjonstester (`AccessPointClientTest`, `AccessPointInboxSplitTest`,
  `RestArchiverTest` m.fl.) må kjøres etter hvert steg — disse tester trolig
  ruting/prosessering direkte.

**Kotlin-plugin (jvm): 1.9.24 → 2.4.20**
- **Rødsone / verktøykjede**: Kotlin 2.0 introduserer K2-kompilatoren.
- Steg 1: 1.9.24 → 2.0.x (bekreft kompatibilitet med Gradle-versjon i bruk,
  se Kotlin 2.0-notater om Gradle 6.8.3–8.5-støtte).
- Steg 2: 2.0.x → 2.4.20.
- Kompiler hele prosjektet og kjør full testsuite etter hvert steg — K2 kan
  gi andre kompileringsfeil enn K1.

### Fase 4 — Byggeverktøy sist
**Gradle wrapper: 7.6.4 → 9.7.1**
- Gjøres til slutt, siden Kotlin-plugin/shadow-plugin-versjoner må være
  kompatible med Gradle-versjonen.
- Stegvis: 7.6.4 → siste 8.x → 9.7.1, med `./gradlew wrapper --gradle-version <x>`
  og full build+test etter hvert steg.
- Sjekk kompatibilitetsmatrise for Kotlin-plugin, shadow-plugin og
  flyway-plugin mot hver Gradle-major.

### Fase 5 — Oppfølging av utsatte oppgraderinger (samlesteg)
Når Kotlin-pluginet (Fase 3) og Gradle wrapper (Fase 4) er oppgradert, går vi
tilbake til tabellen **«Utsatt til senere»** og fullfører de oppgraderingene
som da er blitt mulige, én commit per dependency som i de tidligere fasene:
- jackson (databind/module-kotlin/datatype-joda) → 2.22.2 (eller nyeste
  tilgjengelige på det tidspunktet)
- mockk → 1.14.11 (eller nyeste tilgjengelige)
- com.github.ben-manes.versions-plugin → 0.64.0 (eller nyeste tilgjengelige)

Kjør `./gradlew clean test` etter hver av disse også, selv om de er
"lavrisiko" — de er nettopp utsatt fordi de har en (nå oppfylt) avhengighet
til verktøykjeden.

## Testing per steg (gjelder for alle commits)
- `./gradlew clean test` — full testsuite.
- `./gradlew build` — sikre at shadowJar/distTar-tasks fortsatt fungerer.
- For DB-relaterte steg (H2, Exposed, Flyway): kjør migrasjoner mot en ren
  H2-instans og verifiser skjema.
- For JAXB/SBDH/Camel-steg: kjør de spesifikke XML/SBDH-testene og gjerne en
  manuell smoke-test av en representativ melding gjennom hele pipeline.
- Der eksisterende tester ikke dekker den endrede oppførselen, skriv nye
  tester **før** oppgraderingen låses inn i en commit (rød-grønn-verifisering).

## Leveranse
- Én PR-gren per fase (eller én lang gren med tydelig commit-historikk),
  med commit-meldinger som beskriver hva som er endret og hvilke breaking
  changes som er håndtert.
- Lukk PR #41 med referanse til den nye PR-rekken, siden vi ikke merger
  dependabot-gruppe-PR-en direkte.
