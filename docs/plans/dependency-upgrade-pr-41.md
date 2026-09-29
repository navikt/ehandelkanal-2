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
| jackson-databind / jackson-module-kotlin / jackson-datatype-joda | 2.22.2 | ✅ 2.22.3 i Fase 5 steg 1 (fra 2.19.4) | `jackson-module-kotlin` ≥2.20 krever Kotlin-stdlib 2.0+/2.1+ | Kotlin-plugin oppgradert til 2.x (Fase 3) |
| mockk | 1.14.11 | ✅ 1.14.11 i Fase 5 steg 2 (fra 1.13.12) | mockk ≥1.13.13 krever Kotlin-stdlib 2.0+ | Kotlin-plugin oppgradert til 2.x (Fase 3) |
| com.github.ben-manes.versions (plugin) | 0.64.0 | ✅ 0.64.0 i Fase 4 steg 3a (ny plugin-id `io.github.ben-manes.versions`) | Krever Gradle ≥8.4 | Gradle wrapper oppgradert til 8.x+ (Fase 4) |
| shadow-plugin | 8.1.1 | ✅ `com.gradleup.shadow` 9.2.2 i Fase 4 steg 2, 9.6.1 i steg 4 (Gradle 9.7.1) | Shadow ≥8.0 krever Gradle ≥8.0 | Gradle wrapper oppgradert til 8.x+ (Fase 4) |
| junit-vintage-engine | 6.1.3 | ✅ 6.1.3 i Fase 4 steg 3b (via `junit-bom`) | junit-vintage-engine ≥5.12.0 krever nyere `junit-platform-launcher` enn det Gradle 7.6.4 bundler internt («unaligned versions»-feil ved test-discovery) | Gradle wrapper oppgradert til 8.x+ (Fase 4) |
| logstash-logback-encoder | 9.0 | 8.1 (fra 7.4) | 9.0 migrerer til Jackson 3 (`tools.jackson.*`-groupId), inkompatibelt med vår Jackson 2.19.4 (selv låst pga. Kotlin-stdlib-kobling) | Jackson oppgradert til 3.x, som igjen krever Kotlin-plugin 2.x (Fase 3/5) |
| jaxb-runtime / jakarta.xml.bind-api | 4.0.x | 2.3.9 / 2.3.3 | `no.difi.commons:commons-ubl21:0.9.5`, `commons-sbdh:0.9.5` og `no.difi.vefa:peppol-sbdh:1.1.4` (alle siste versjoner) er kompilert mot `javax.xml.bind` – en jakarta-runtime gjenkjenner ikke annotasjonene deres. Oxalis-etterfølgere finnes for SBDH (`network.oxalis.vefa:peppol-sbdh` 4.x), men ingen for `commons-ubl21` | Egen oppgave: bytte difi-bibliotekene (Oxalis for SBDH, egne genererte UBL-klasser e.l.) – ikke en ren versjonsoppgradering |
| exposed (core/dao/jdbc/java-time/jodatime) | 1.5.0 | ✅ 1.5.0 i Fase 5 steg 3 (coroutines låst til 1.8.1 pga. Ktor 1.6.8) | Exposed 0.54–0.61 er bygget med Kotlin-stdlib 2.0, og 1.x med 2.2+/2.3 (1.0 flytter også pakkene til `org.jetbrains.exposed.v1.*`) | Kotlin-plugin oppgradert til 2.x (Fase 3) |
| kotlin-result | 2.3.1 | ✅ 2.3.1 i Fase 5 steg 4 (fra 2.0.1) | kotlin-result ≥2.0.2 er bygget med Kotlin-stdlib 2.2+ | Kotlin-plugin oppgradert til 2.x (Fase 3) |
| camel-test (JUnit 4, `CamelTestSupport`) | camel-test-junit5 | ✅ camel-test-junit5 3.22.4 i Fase 4 steg 3b | `XmlDetectorTest` og `InboundSbdhRemoverTest` var JUnit 4-tester og ble kjørt av junit-vintage | Sammen med junit-vintage i Fase 4 (Camel 4 fjerner JUnit 4-støtten helt) |

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

**h2database: 1.4.200 → 2.5.252** (ikke 2.5.250 som først planlagt) — **fullført**, ingen kodeendring
- **Rødsone / testinfrastruktur**: H2 2.x har strengere SQL-kompatibilitetsmodus
  (identifikatorer, reserverte ord, `MODE=`-innstillinger) — dette er testdatabasen,
  så alle DB-relaterte tester må kjøres grundig og eventuelt migreringsskript
  i `db/migration` justeres.
- Ett steg er nok versjonsmessig (1.x → 2.x er én major), men testdekningen må
  utvides der testene i dag stoler på lempelig SQL-parsing.
- Resultat: alle H2-migreringer (`common` + `h2/V1.3`) kjører på 2.5.252 i
  `MODE=PostgreSQL`, og ingen skript måtte endres. `InboundIT` kjører
  `Database.initLocal()` og `Report.insert` (tre inserts, `Entry successfully
  inserted`). `DatabaseInitLocalTest` og `CredentialRotationTest` er grønne.
  50/50 tester grønne.
- H2 ligger i `implementation` og følger med i fat-JAR-en. Oppgraderingen
  fjerner derfor også kjente sårbarheter i 1.4.200 (bl.a. CVE-2021-42392 og
  CVE-2022-23221), selv om prod bare bruker PostgreSQL.
- **Lokalt**: filformatet i H2 2.x er ikke kompatibelt med 1.4. Gamle
  `./test.mv.db`/`./integrationtestdb.mv.db` må slettes før lokal kjøring
  (filene er i `.gitignore`).

**Etterarbeid (fra dev-logg etter Flyway 13)**
- Flyway logger `initSql is deprecated` for `SET ROLE` i `initRemote`.
  Virker fortsatt, men bør flyttes til en `afterConnect`-callback før
  Flyway fjerner `initSql`.

**exposed (core/dao/jdbc/java-time/jodatime): 0.41.1 → 1.5.0**
- **Rødsone**: databasetilgangslag, kjernelogikk.
- JetBrains Exposed 1.0 er en større API-revisjon (DSL/DAO-endringer,
  transaksjonshåndtering). Sjekk om det finnes en anbefalt mellomversjon
  (f.eks. siste 0.5x før 1.0) og oppgrader stegvis dit før hopp til 1.5.0.
- Alle DB-repository-tester må kjøres og eventuelt utvides for å dekke
  endret query-bygging.
- Steg 1 (0.41.1 → 0.53.0) — **fullført**. 0.53.0 er siste versjon bygget
  med Kotlin 1.9. Resten er **utsatt** til etter Kotlin 2.x (se «Utsatt til
  senere»).
  - Breaking changes 0.42–0.53 gjennomgått. Ingen treffer vår bruk (bare
    `Table`, `insert`, `select`/`selectAll`, jodatime `date`, `transaction`,
    `Database.connect`). Det som var nærmest: endret jodatime-formattering
    for `date` (0.48) og bevaring av store/små bokstaver i
    nøkkelord-identifikatorer (0.46). Ingen av kolonnene våre er nøkkelord.
  - Ny `ReportTest` (H2 via `initLocal`) dekker lesestiene som ikke var
    testet: `getAll` med og uten dato (inkludert kl. 23:59),
    `getAllUniqueDaysWithEntries` (distinkt, nyeste først),
    `getAllAsCsvFile` (eksakt CSV) og lagring av `amount`. Grønn på 0.41.1
    før bump, grønn på 0.53.0 etter.
  - Deprecated DSL migrert for å forberede 1.0: `select { }` →
    `selectAll().where { }` og `slice(col).selectAll()` → `select(col)`.
  - 55/55 tester grønne. `InboundIT` kjører fortsatt `Report.insert` (tre
    inserts).
- Steg 2 (0.53.0 → 1.5.0) — **utsatt** til Kotlin-plugin 2.x. Da kreves
  pakke-rename til `org.jetbrains.exposed.v1.*` og at `transaction` flyttes
  til `exposed-jdbc`.

**kotlin-result: 1.1.6 → 2.3.1**
- Steg 1: 1.x → siste 1.1.x/2.0-forhåndsversjon om relevant
- Steg 2: → 2.3.1 — sjekk endringer i `Result`-typen/ekstensjonsfunksjoner
  som brukes i feilhåndteringskoden (kjernelogikk).
- Steg 1 (1.1.6 → 2.0.1) — **fullført**, ingen kodeendring. 2.0.1 er siste
  versjon bygget med Kotlin 1.9. Resten er **utsatt** til etter Kotlin 2.x
  (se «Utsatt til senere»).
  - 2.0 gjør `Result` til en inline value class. `Ok`/`Err` kan ikke lenger
    brukes som typer (`is Ok`, `as Err`), og flere deprecated funksjoner er
    fjernet (`binding`, `getOr(verdi)`, `getErrorOr(verdi)`, `Result.of`,
    `and`/`or` uten lambda). Koden bruker bare `Ok(...)`/`Err(...)` som
    konstruktører pluss `getOrElse`, `andThen` og `getErrorOrElse`, som alle
    er uendret.
  - `StandardBusinessDocumentGeneratorTest` og `AccessPointClientTest`
    dekker både Ok- og Err-stiene. 55/55 tester grønne.
  - 2.x er et multiplatform-bibliotek, og Gradle velger JVM-varianten.
    `Result`, `ResultKt` og `Failure` er med i fat-JAR-en.
- Steg 2 (2.0.1 → 2.3.1) — **utsatt** til Kotlin-plugin 2.x.

**camel-core/jms/ftp/jsonpath/test: 2.24.2 → 3.22.4**
- **Rødsone / kjernelogikk**: dette er ruting-motoren for hele meldingsflyten.
- Camel 3.0 modulariserte `camel-core` i mange JAR-er og flyttet komponenter
  (se migreringsguide). Sjekk at `camel-jaxb`-bruk, JMS- og FTP-endepunkter,
  og eventuelle Spring-koordinater ikke er berørt.
- Stegvis: 2.24 → siste 2.x → 3.0.x (mest brytende) → siste 3.22.x.
  (3.0.x ble byttet ut med 3.1.0, se steg 2.)
- Alle integrasjonstester (`AccessPointClientTest`, `AccessPointInboxSplitTest`,
  `RestArchiverTest` m.fl.) må kjøres etter hvert steg — disse tester trolig
  ruting/prosessering direkte.
- Steg 1 (2.24.2 → 2.25.4, siste 2.x) — **fullført**, ingen kodeendring.
  Transitive endringer: bare Spring 5.1.6 → 5.1.20 (patch, via `camel-jms`
  og `camel-spring`). JSch og json-path er uendret. Ingen nye
  deprecation-advarsler. 55/55 tester grønne.
- Steg 2 (2.25.4 → 3.1.0, ikke 3.0.x som først planlagt) — **fullført**.
  - Hvorfor 3.1.0: 3.0.x la til overloaden `process(Supplier<Processor>)`,
    som gjorde alle `.process { }`-lambdaene våre tvetydige i Kotlin.
    Overloaden ble fjernet igjen i 3.1.0. Å gå via 3.0.x hadde betydd å
    skrive om ca. 20 lambdaer og så skrive dem tilbake.
  - Ny `EndpointUriTest`, skrevet og grønn på 2.25.4 før bump. Den løser
    opp de ekte Ebasys-URI-ene med både `ftp://` og `sftp://` (prod bruker
    SFTP, testene FTP) og oppretter consumer for FTP-testruten. Den dekker
    også MQ-URI-en. En mutasjonssjekk med en ukjent parameter gjør testen
    rød. For å gjøre dette mulig er URI-verdiene i `Inbound.kt` endret fra
    `private` til `internal`, og FTP-test-URI-en er trukket ut i
    `ebasysConnectionTest`.
  - På 3.1.0 feilet testen med `Unknown parameters=[{consumer.bridgeErrorHandler=true}]`.
    I prod ville Camel-konteksten ikke startet. Rettet til
    `bridgeErrorHandler=true`. `passiveMode` på `sftp` godtas fortsatt.
  - Kodeendringer:
    - `SimpleRegistry.put` → `org.apache.camel.support.DefaultRegistry.bind`
      (`EhandelBootstrap`, `InboundIT`, `EndpointUriTest`).
    - Ubrukte importer av `org.apache.camel.language.XPath`/`NamespacePrefix`
      fjernet fra `AccessPointClient` (flyttet til `camel-xpath` i 3.x).
    - `routeDefinitions[0].adviceWith(...)` →
      `AdviceWithRouteBuilder.adviceWith(context, routeId) { }` (`InboundIT`).
    - `DefaultExchange` → `org.apache.camel.support.DefaultExchange`.
    - `JndiRegistry`/`createRegistry()` → `bindToRegistry(registry)`, og
      `@Produce(uri = ...)`/`@EndpointInject(uri = ...)` → `value`
      (`XmlDetectorTest`, `InboundSbdhRemoverTest`). Begge var deprecated
      i 3.1.
  - Transitivt: Spring 5.1.20 → 5.2.3 (minor). JSch 0.1.55 og json-path
    2.4.0 er uendret. JMS er fortsatt `javax.jms`, så IBM MQ påvirkes ikke.
  - Fat-JAR: Camel 3 finner type-convertere via
    `META-INF/services/org/apache/camel/TypeConverterLoader`, som finnes i
    fem JAR-er. `mergeServiceFiles()` (fra Flyway-steget) slår dem korrekt
    sammen. Komponentfilene (`sftp`, `ftp`, `jms`, `timer` osv.) er med.
  - Eksisterende, ikke nytt: `camel-core`/`camel-xml-jaxb` drar inn
    `com.sun.xml.bind:jaxb-impl`/`jaxb-core:2.3.0` ved siden av vår
    `jaxb-runtime:2.3.9`. Det var slik også på 2.24/2.25. Kandidat for
    `exclude` i JAXB-oppgaven.
  - 58/58 tester grønne, ingen deprecation-advarsler.
- Steg 3 (3.1.0 → 3.22.4) er delt i to:
  - Steg 3a: 3.1.0 → 3.14.10 (LTS).
  - Steg 3b: 3.14.10 → 3.22.4.

  Upgrade-guidene for 3.2–3.21 er gjennomgått. Relevante punkter for oss:
  - 3.7: `AdviceWithRouteBuilder.adviceWith` er deprecated.
  - 3.10: camel-jsonpath bruker Jackson som standard.
  - 3.13: split av en `Map` splitter nå i entries.
  - 3.17: camel-ftp bytter til JSch-forken `com.github.mwiede:jsch`.
  - 3.18.3/3.20: jsonpath `unpackArray` er av som standard.
  - 3.18: konvertering fra InputStream til `byte[]` lukker strømmen.
- Steg 3a (3.1.0 → 3.14.10):
  - Ny `InboxSplitHeadersTest`, grønn på 3.1.0 før bump. Den kjører
    split (`$.meldinger[*]`) og header-uttrekk (msgNo/messageUUID) for 1,
    2 og 0 meldinger. Den skal fange at ett enkelt element pakkes ut til
    en `Map` og splittes i entries (3.13/3.20).
  - `InboundIT` feilet 6 av 7 tester med `No bean could be found in the
    registry for: accessPointClient`:
    - Årsaken er at `DefaultRegistry.doStop()` lukker `SimpleRegistry`,
      som tømmer alle bindinger.
    - Testen stopper og starter den samme konteksten mellom hver test, så
      bare første test fikk bønnen.
    - Rettet ved å binde test-bønnene på nytt i `setUp()`
      (`Registry.bindTestBeans()`).
    - Prod påvirkes ikke, fordi konteksten der bare stoppes ved nedstenging.
  - `AdviceWithRouteBuilder.adviceWith` → `AdviceWith.adviceWith`
    (`InboundIT`).
  - `CamelTestSupport` fra `camel-test` (JUnit 4) er deprecated og brukes i
    `XmlDetectorTest` og `InboundSbdhRemoverTest`. Migrering til
    `camel-test-junit5` er utsatt, se «Utsatt til senere».
  - Transitivt:
    - Spring 5.2.3 → 5.3.27.
    - json-path 2.4.0 → 2.8.0.
    - JMS er fortsatt `javax.jms`.
  - **Rødsone – SFTP mot Ebasys:**
    - JSch 0.1.55 er allerede byttet til `com.github.mwiede:jsch:0.2.1` i
      3.14.10. Byttet er backportet fra 3.17.
    - mwiede 0.2.x skrur av `ssh-rsa` (RSA med SHA-1) som standard.
    - Ebasys-serveren har en RSA-vertsnøkkel. Hvis serveren bare støtter
      `ssh-rsa`-signatur og ikke `rsa-sha2-256`/`rsa-sha2-512`, feiler
      tilkoblingen.
    - Da stopper FTP-testruten prosessen (`exitProcess(1)`) → crashloop.
    - Camel 3.14.10 har `serverHostKeys`, `publicKeyAcceptedAlgorithms` og
      `keyExchangeProtocols` på sftp-endepunktet, slik at `ssh-rsa` kan
      skrus på igjen ved behov.
    - Må verifiseres ved deploy til dev. Prod-serveren kan avvike fra dev.
    - Verifisert i dev 2026-09-29: FTP-testruten koblet til og listet
      filer (`Testing FTP connection – …`), og appen ble `Application
      ready`. Dev-serveren godtar altså algoritmene i mwiede JSch 0.2.1.
      Prod er ikke verifisert.
  - Fat-JAR: `TypeConverterLoader` er korrekt slått sammen fra fem JAR-er.
  - 61/61 tester grønne. Ingen Camel-deprecations i main.
- Steg 3a ble også verifisert med en melding gjennom vefasrest i dev
  2026-09-29:
  - Split og header-uttrekk ga `msgNo` og `messageUUID`.
  - Nedlasting, SBDH-fjerning og DB-innsetting (Exposed) gikk gjennom.
  - En fil skrevet etter deploy dukket opp i SFTP-listingen, så skriving
    til Ebasys virker også.
- Steg 3b (3.14.10 → 3.22.4):
  - Ingen kodeendring.
  - Upgrade-guiden for 3.21 → 3.22 sier «No changes expected». 3.22.3
    endrer bare `readLock=changed` i camel-file, som vi ikke bruker.
  - 3.18: konvertering fra InputStream til `byte[]` lukker strømmen. Det
    påvirker oss ikke: `AccessPointClient` setter body som `ByteArray`,
    og prosessorene leser den med `getBody<InputStream>()`, som gir en ny
    strøm hver gang. `InboundIT` dekker flyten med ekte filer.
  - 3.20: jsonpath `unpackArray` er av som standard. `InboxSplitHeadersTest`
    er fortsatt grønn for 0, 1 og 2 meldinger.
  - Transitivt:
    - Spring 5.3.27 → 5.3.34.
    - JSch (mwiede 0.2.1), json-path 2.8.0 og `javax.jms` er uendret.
  - Fat-JAR: `TypeConverterLoader` er slått sammen fra fem JAR-er.
  - 61/61 tester grønne. Eneste deprecation er `CamelTestSupport`, som er
    utsatt.
  - Camel 4 (Jakarta, Java 17, Spring 6) er ikke med i denne planen. Den
    krever `jakarta.jms` (IBM MQ-klient) og henger sammen med JAXB/difi-
    oppgaven i «Utsatt til senere».

**Kotlin-plugin (jvm): 1.9.24 → 2.4.20**
- **Rødsone / verktøykjede**: Kotlin 2.0 introduserer K2-kompilatoren.
- Steg 1: 1.9.24 → 2.0.x (bekreft kompatibilitet med Gradle-versjon i bruk,
  se Kotlin 2.0-notater om Gradle 6.8.3–8.5-støtte).
- Steg 2: 2.0.x → 2.4.20.
- Kompiler hele prosjektet og kjør full testsuite etter hvert steg — K2 kan
  gi andre kompileringsfeil enn K1.
- Steg 1 (1.9.24 → 2.0.21):
  - K2 kompilerte hele prosjektet uten feil eller nye advarsler.
  - `tasks.withType<KotlinCompile> { kotlinOptions { jvmTarget = "21" } }`
    er fjernet. `kotlinOptions` er deprecated i 2.0 og blir en feil i 2.2,
    og `kotlin { jvmToolchain(21) }` setter allerede `jvmTarget`. Bytekoden
    er fortsatt versjon 65 (Java 21).
  - `kotlin-stdlib` er 2.0.21 i hele runtime-classpathen. De transitive
    `kotlin-stdlib-jdk7`/`jdk8:1.8.0` er tomme kompatibilitets-JAR-er
    siden Kotlin 1.8, så det blir ikke duplikate klasser.
  - 61/61 tester grønne.
- Steg 2 (2.0.21 → 2.4.20):
  - K2 kompilerte uten feil eller nye advarsler i koden.
  - KGP 2.4.x støtter Gradle 7.6.3 og nyere, så 7.6.4 fungerer. KGP
    advarer om at Kotlin 2.5.0 krever Gradle ≥ 8.14.4. Det tas i Fase 4.
  - `kotlin-reflect` ble værende på 1.9.25 (transitivt via
    `jackson-module-kotlin`), mens stdlib var 2.4.20. En eldre
    `kotlin-reflect` kan feile på metadata fra nyere kompilator. Lagt til
    `implementation(kotlin("reflect"))`, slik at den følger plugin-
    versjonen (2.4.20).
    - Main bruker Jackson bare til serialisering (`ArchiveRequest`,
      Ktor-svar) og `readTree`, ikke til deserialisering til Kotlin-
      klasser. `ArchiveRequestTest` dekker serialiseringen.
  - Bytekode versjon 65 (Java 21). 61/61 tester grønne.
  - Oppfølgingene i «Utsatt til senere» som var blokkert av Kotlin 2.x
    (Exposed, kotlin-result, jackson, mockk) kan nå tas. Hver tas som eget
    steg.

### Fase 4 — Byggeverktøy sist
**Gradle wrapper: 7.6.4 → 9.7.1**
- Gjøres til slutt, siden Kotlin-plugin/shadow-plugin-versjoner må være
  kompatible med Gradle-versjonen.
- Stegvis: 7.6.4 → siste 8.x → 9.7.1, med `./gradlew wrapper --gradle-version <x>`
  og full build+test etter hvert steg.
- Sjekk kompatibilitetsmatrise for Kotlin-plugin, shadow-plugin og
  flyway-plugin mot hver Gradle-major.
- Fase 4 kjøres på egen branch (`chore/OEBS-2320-dependency-upgrades-fase4`).
  Fase 3 er merget til dev (#54).
- Rekkefølge:
  1. Gradle 7.6.4 → 8.14.5.
  2. shadow-plugin til en versjon som støtter Gradle 9 (rydder
     deprecations).
  3. ben-manes 0.64.0, og junit-vintage 6.x med `camel-test-junit5`.
  4. Gradle 8.14.5 → 9.x.
- Steg 1 (7.6.4 → 8.14.5):
  - Wrapper oppgradert med `./gradlew wrapper --gradle-version 8.14.5`
    (kjørt to ganger, slik at også `gradlew`-skriptene og wrapper-JAR-en
    oppdateres). `gradleVersion` i `tasks.withType<Wrapper>` er oppdatert
    tilsvarende.
  - `gradle-wrapper.jar` er verifisert mot Gradles offisielle SHA-256
    (`7d3a4ac4…6172`).
  - `distributionSha256Sum` er lagt til i `gradle-wrapper.properties` og i
    `tasks.withType<Wrapper>`, slik at den ikke forsvinner neste gang
    `./gradlew wrapper` kjøres. Wrapperen nekter nå å bruke en Gradle-zip
    med feil sjekksum. Det er verifisert med en ren `GRADLE_USER_HOME`:
    riktig sum laster ned og starter, og feil sum stopper med «Expected
    checksum». Ved neste Gradle-oppgradering må både versjon og sum
    oppdateres (se https://gradle.org/release-checksums/).
  - Ingen endring nødvendig for Kotlin-plugin 2.4.20, flyway-plugin 13.8.0
    eller ben-manes 0.51.0. `dependencyUpdates` kjører.
  - Gradle 8 advarer om «automatic loading of test framework
    implementation dependencies» (fjernes i Gradle 9). Rettet ved å
    deklarere `junit-platform-launcher` eksplisitt, og ved å hente
    vintage-engine og launcher via `junit-bom` slik at versjonene alltid
    er like (1.11.4/5.11.4).
  - `jar` og `shadowJar` skriver fortsatt til samme fil (`archiveClassifier
    = ""`), og `build/libs` inneholder én fat-JAR med `Main-Class`. Det er
    viktig fordi Dockerfile kopierer `build/libs/*.jar`. Service-filene for
    Camel (`TypeConverterLoader`) og Flyway (`Plugin` med PostgreSQL) er
    slått sammen korrekt.
  - Gjenværende deprecations kommer fra shadow 7.1.2 (`Convention`,
    `setFileMode`, `ConfigureUtil`, `JavaPluginConvention`,
    `ApplicationPluginConvention`, `getMode`). Disse fjernes i Gradle 9 og
    løses i steg 2.
  - Toolchain-advarselen fra 7.6.4 («no java toolchain repositories») er
    borte. CI bruker `setup-java` med JDK 21, så toolchain-en trenger
    aldri å lastes ned.
  - 61/61 tester grønne.
- Steg 2 (shadow 7.1.2 → `com.gradleup.shadow` 9.2.2):
  - Pluginet har flyttet til GradleUp og fått ny plugin-id. Pakken for
    `ShadowJar` er uendret.
  - Versjonsvalget:
    - shadow ≥ 9.3.0 krever Gradle 9.0, og ≥ 9.5.0 krever Gradle 9.2.
      9.6.1 feilet på 8.14.5 med `NoSuchMethodError` i
      `addVariantsFromConfiguration`.
    - Gradle 9 kan heller ikke tas først, fordi shadow 7.1.2 bruker
      API-er som fjernes i Gradle 9.
    - Derfor 9.2.2 nå (krever Gradle ≥ 8.11), og bump til nyeste 9.x
      sammen med Gradle 9 i steg 4.
  - 🔴 `duplicatesStrategy`: shadow 9 bruker `EXCLUDE` som standard. Da
    blir duplikate service-filer forkastet før `mergeServiceFiles()` ser
    dem. Uten tiltak ville Camel sin `TypeConverterLoader` og Flyway sin
    PostgreSQL-plugin stille forsvunnet fra fat-JAR-en, og prod ville
    feilet ved oppstart.
    - Satt `duplicatesStrategy = EXCLUDE` eksplisitt (første fil vinner,
      som i 7.x).
    - Satt `filesMatching("META-INF/services/**") { duplicatesStrategy =
      INCLUDE }`, slik at service-filene slås sammen.
  - Fat-JAR sammenlignet med snapshot fra 7.1.2:
    - 45 154 → 45 165 entries, ingen duplikater.
    - Nye entries er bare katalogoppføringer under `META-INF/services/`.
    - `META-INF/versions/9/module-info.class` er borte (shadow 9 utelater
      den som standard, og den har ingen betydning på classpath).
    - Ingen service-linjer er tapt, verken for Camel, Flyway eller JAXB.
      Den nye sammenslåingen fjerner bare kommentarlinjer og like linjer.
  - `Multi-Release: true` er nå satt i manifestet. Shadow 7 mistet dette
    attributtet, så versjonerte klasser i `META-INF/versions/N/` ble
    ignorert. Nå brukes de på Java 21, slik bibliotekene er ment å kjøre
    på vanlig classpath. Det berører:
    - JSch: Ed25519/Ed448 via JDK og Unix domain sockets. Vår RSA-
      tilkobling berøres ikke.
    - BouncyCastle, transitivt via IBM MQ. `Jms.kt` har ikke TLS, så
      sannsynligvis ubrukt på vår sti.
    - 5 JAXB-klasser.

    Vi valgte å beholde det. Verifiseres i dev:
    - Invoice via vefasrest dekker JAXB-parsing og SFTP-skriving. Sjekk
      også at `Testing FTP connection` logges ved oppstart.
    - OrderResponse og Catalogue er de eneste dokumenttypene som går til
      MQ. Appen er ren inbound, og vi kan bare sende Invoice via
      vefasrest i dev, så MQ-stien kan ikke verifiseres ende-til-ende.
      Risikoen er vurdert som lav: MQ-koden er uendret, og
      Multi-Release berører bare BouncyCastle, som ikke brukes uten TLS.
      Vi godtar risikoen og følger med i prod-loggene etter deploy: se
      etter `Inbound EHF sent via MQ to internal systems` og etter
      JMS/MQ-exceptions (`JMSException`, `MQException`, `LinkageError`).
    - Hvis noe feiler, kan `Multi-Release: false` settes i manifestet
      uten å rulle tilbake shadow.
  - `create("printVersion") { println(...) }` er endret til
    `register(...) { doLast { ... } }`. `create` var deprecated, og
    versjonen ble skrevet ut ved konfigurasjon av hvert eneste
    Gradle-kall. Tasken brukes ikke i CI.
  - Ingen Gradle-deprecations igjen med `--warning-mode all`. 61/61 tester
    grønne.
- Steg 3a (ben-manes 0.51.0 → 0.64.0):
  - Plugin-id-en `com.github.ben-manes.versions` er deprecated i 0.64.0 og
    er byttet til `io.github.ben-manes.versions`.
  - `dependencyUpdates` kjører uten deprecations.
  - Rapporten viser også beta-, milestone- og RC-versjoner som «oppdateringer».
    Det er standardoppførsel (ingen `rejectVersionIf`) og er ikke endret.
  - 61/61 tester grønne.
- Steg 3b (JUnit 5.11.4 → 6.1.3, camel-test → camel-test-junit5):
  - JUnit 6 krever Java 17 og Kotlin 2.2. Vi har Java 21 og Kotlin 2.4.20.
  - JUnit 6 deprecater vintage-motoren (JUnit 4-støtten). Den logger en
    INFO-melding så lenge det finnes JUnit 4-tester, og er bare ment som
    en midlertidig bro under migrering til Jupiter.
    - Alle 18 testklassene var JUnit 4. Vi valgte å migrere bare de to
      `CamelTestSupport`-testene nå.
    - Resten av migreringen fra JUnit 4 til Jupiter er lagt som egen
      oppgave i Fase 5.
  - Endringer:
    - `junit_vintage_version` → `junit_bom_version = "6.1.3"`.
      `junit-bom` styrer Jupiter, vintage og launcher (alle 6.1.3).
    - Lagt til `testImplementation("org.junit.jupiter:junit-jupiter")`.
    - `camel-test` → `camel-test-junit5` (3.22.4). Den drar inn Jupiter
      5.9.1, som løftes til 6.1.3 av BOM-en. Testene er grønne.
    - `XmlDetectorTest` og `InboundSbdhRemoverTest`:
      `org.apache.camel.test.junit4.CamelTestSupport` →
      `org.apache.camel.test.junit5.CamelTestSupport` og `org.junit.Test`
      → `org.junit.jupiter.api.Test`. Samme API
      (`createRouteBuilder`/`bindToRegistry`), ingen andre endringer.
  - Verifisering:
    - 61/61 tester grønne. Jupiter kjører de 5 migrerte testene (4 + 1),
      vintage de øvrige 56.
    - Mutasjonssjekk: med en gyldig SBDH-fil som input i `not XML` feiler
      testen med `Expected: <false> but was: <true>`.
    - Et første forsøk med `<ok/>` som input feilet ikke. Det er riktig
      oppførsel: `XML=true` krever gyldig SBDH, ikke bare XML.
  - Eksisterende funn, ikke nytt: fat-JAR-en inneholder `junit:junit:4.10`
    (260 entries). Den kommer transitivt via
    `com.googlecode.json-simple:json-simple:1.1.1`, som `ktor-auth` 1.6.8
    drar inn, og json-simple deklarerer JUnit som compile-avhengighet.
    Kandidat for `exclude` eller forsvinner med Ktor-oppgradering.
- Steg 4 (Gradle 8.14.5 → 9.7.1, shadow 9.2.2 → 9.6.1):
  - Versjonsvalg: Kotlin-plugin 2.4.20 støtter fullt ut Gradle 7.6.3–9.7.0
    (KGP-kompatibilitetstabellen). 9.7.1 er en patch på 9.7 og ble valgt
    fremfor 9.8.0, som er utenfor støttet område og bare dager gammel.
  - Rekkefølge: shadow 9.6.1 lar seg ikke laste på Gradle 8.14.5
    (`NoSuchMethodError` i `addVariantsFromConfiguration`). Wrapperen ble
    derfor oppgradert først med shadow 9.2.2, og shadow ble bumpet etterpå.
  - Wrapper:
    - `./gradlew wrapper --gradle-version 9.7.1
      --gradle-distribution-sha256-sum …` kjørt to ganger.
    - `gradle-wrapper.jar` er verifisert mot Gradles offisielle SHA-256
      (`7a9ce74c…2c5d`).
    - `distributionSha256Sum` (`acd53f1e…d20a`) er oppdatert både i
      `gradle-wrapper.properties` og i `tasks.withType<Wrapper>`.
      Nedlastingen av 9.7.1 gikk gjennom sjekksumkontrollen.
    - Nye felter i `gradle-wrapper.properties`: `retries=0` og
      `retryBackOffMs=500` (standardverdier fra Gradle 9).
    - `gradlew`/`gradlew.bat` er regenerert. Den tomme
      `CLASSPATH`-variabelen er fjernet, fordi wrapperen startes med
      `-jar`.
  - Shadow 9.5+ legger til `KotlinModuleMetadataTransformer` som standard
    og advarer (56 linjer) om at den ikke fungerer sammen med `EXCLUDE`.
    Transformeren trengs bare ved relocation, og vi relocater ikke.
    - Satt `enableKotlinModuleRemapping = false` (med
      `@Suppress("DEPRECATION")`). Flagget fjernes i shadow 10, og da er
      remapping av som standard, så linjen må slettes ved neste major.
    - Verifisert at alle 56 `.kotlin_module`-filer er byte-identiske med
      og uten remapping, og at advarslene er borte.
  - Fat-JAR sammenlignet med steg 3b (Gradle 8.14.5, shadow 9.2.2):
    samme 42 851 fil-entries, identiske `META-INF/services/*` og identisk
    manifest (inkl. `Multi-Release: true`). Én JAR i `build/libs`.
  - Ingen Gradle-deprecations i CI-kommandoen
    (`--warning-mode all clean build shadowJar`). `printVersion` og
    `dependencyUpdates` kjører uten advarsler.
  - Kjent, ikke blokkerende: flyway-pluginet 13.8.0 kaller
    `Project.getProperties`, som er deprecated og feiler i Gradle 10. Det
    slår bare ut når `flyway*`-tasks kjøres manuelt, ikke i CI. Følges opp
    ved neste flyway-plugin-oppgradering.
  - 61/61 tester grønne.

### Fase 5 — Oppfølging av utsatte oppgraderinger (samlesteg)
Når Kotlin-pluginet (Fase 3) og Gradle wrapper (Fase 4) er oppgradert, går vi
tilbake til tabellen **«Utsatt til senere»** og fullfører de oppgraderingene
som da er blitt mulige, én commit per dependency som i de tidligere fasene:
- jackson (databind/module-kotlin/datatype-joda) → 2.22.2 (eller nyeste
  tilgjengelige på det tidspunktet)
- mockk → 1.14.11 (eller nyeste tilgjengelige)
- exposed → 1.x (pakkene flyttes til `org.jetbrains.exposed.v1.*`).
  ✅ Gjort i steg 3. Coroutines er låst til 1.8.1 i prod, se steg 3.
- Ktor 1.6.8 → nyere major (egen, stor oppgave). Krever migrering av
  server (routing, `StatusPages`, `ContentNegotiation`, `respondHtml`) og
  klient (`HttpClient(Apache)`, `JsonFeature`, `Auth`). Når Ktor ikke
  lenger bruker `ExperimentalCoroutineDispatcher`, fjernes
  coroutines-låsen i `build.gradle.kts`, slik at Exposed får versjonen den
  er bygget mot (1.11.0). Fjerner trolig også `junit:junit:4.10` fra
  fat-JAR-en (via `ktor-auth` → json-simple).
- ✅ kotlin-result 2.0.1 → 2.3.1 (steg 4)
- ✅ Migrer de gjenværende 16 JUnit 4-testklassene til JUnit Jupiter og
  fjern `junit-vintage-engine` og den direkte `junit:junit`-avhengigheten
  (steg 5).
- Flyway `initSql` → `afterConnect`-callback i `Database.kt` (`initRemote`,
  `SET ROLE "<db>-admin"`). Flyway logger `initSql is deprecated` to ganger
  ved hver oppstart (sett i dev 2026-09-29). 🔴 `SET ROLE` sørger for riktig
  eier på tabeller fra migreringer, så verifiser at nye migreringer
  fortsatt får admin-rollen som eier.
- HikariCP `keepaliveTime`: fra HikariCP 6.2.1 er `keepaliveTime` = 2 min
  som standard (tidligere 0). Poolen i `Database.kt` har `maxLifetime = 30001`, så HikariCP
  skrur av keepalive og logger `keepaliveTime is greater than or equal to
  maxLifetime, disabling it` ved oppstart (sett i dev 2026-09-29). Det gir
  samme oppførsel som før HikariCP-oppgraderingen (keepalive av). Sett
  `keepaliveTime = 0` eksplisitt for å fjerne advarselen uten å endre
  oppførsel.

- Fase 5 kjøres på egen branch (`chore/OEBS-2320-dependency-upgrades-fase5`).
  Fase 4 er merget til dev (#56).
- Steg 1 (jackson 2.19.4 → 2.22.3):
  - Release notes gjennomgått for 2.20, 2.21 og 2.22:
    - Ingen «Changes, behavior» i noen av dem.
    - 2.20: `jackson-annotations` har ikke lenger patch-nummer (`2.22`,
      ikke `2.22.3`). Databind fjernet de gamle
      `PropertyNamingStrategy`-implementasjonene (deprecated siden 2.12).
    - `jackson-module-kotlin`: 2.20 krever Kotlin 2.0.21, 2.21 krever
      Kotlin 2.1 og fjernet `MissingKotlinParameterException` og den gamle
      StrictNullChecks-motoren. Vi er på Kotlin 2.4.20.
  - Ingenting av det som er fjernet brukes, verken av oss eller av Ktor
    1.6.8. `ktor-jackson` og `ktor-client-jackson` er sjekket med `javap`,
    og de bruker bare `readValue`, `writeValue`, `writeValueAsString`,
    `TypeFactory.constructType`, `jacksonObjectMapper` og
    `registerKotlinModule`.
  - Resolusjon: alle Jackson-artefakter står på 2.22.3 (annotations 2.22),
    også de transitive fra Ktor, Camel og Flyway. `jackson-module-kotlin`
    ber om `kotlin-reflect` 2.1.21, som løftes til 2.4.20. Ingen Jackson 3
    (`tools.jackson`) på classpath.
  - Testdekning: serverens eneste JSON-svar, `HttpErrorResponse` fra
    `StatusPages`, hadde ingen test. Det objektet inneholder Ktors
    `HttpStatusCode` og serialiseres via Kotlin-modulen. Ny
    `ObjectMapperTest` (Jupiter) bruker prod-`objectMapper` og dekker:
    - `HttpErrorResponse` med alle felter og med null-felter utelatt
      (`NON_NULL`);
    - Joda `DateTime` (epoch-millis);
    - `readTree`, slik `RestArchiver` og `EntraIdTokenProvider` bruker den.

    Testen var grønn på 2.19.4 før oppgraderingen. Mutasjonssjekk:
    `NON_NULL` → `ALWAYS` gir 1 feilende test. `ArchiveRequestTest` og
    `RestArchiverTest` dekker serialiseringen mot arkivet.
  - Fat-JAR: `jackson-databind` 2.22.3, og service-filene for
    `databind.Module` (Joda, Kotlin), `ObjectCodec` og `JsonFactory` er
    slått sammen riktig.
  - Funn, ikke endret: `RestArchiver` konfigurerer
    `JacksonSerializer { objectMapper }`. Lambdaen er en konfigurasjonsblokk
    på Ktors egen `jacksonObjectMapper()`, så klienten bruker ikke den
    delte `objectMapper`. Det er uendret oppførsel, og `ArchiveRequestTest`
    tester nettopp med `jacksonObjectMapper()`.
  - 65/65 tester grønne (61 + 4 nye).

- Steg 2 (mockk 1.13.12 → 1.14.11):
  - Release notes gjennomgått for 1.13.13–1.14.11. Relevant for oss:
    - 1.13.13: `unmockkAll` kjøres etter hver JUnit 5-test, men bare via
      `MockKExtension`, som vi ikke bruker.
    - 1.14.7: JUnit 4 og 5 er nå `compileOnly` i mockk. Vi deklarerer JUnit
      selv, så ingenting forsvinner fra testklassestien.
    - Byte Buddy 1.14.17 → 1.18.2 og objenesis 3.3 → 3.4 (bare test).
  - Vi bruker bare `mockk { every { … } returns … }` og `relaxed = true`
    (`AccessPointClientTest` og `InboundIT`). Ingen API-endringer der.
  - Funn: mockk 1.14.11 løfter `kotlinx-coroutines` fra 1.8.1 til 1.10.2,
    men bare på testklassestien. Prod får 1.8.1 fra `exposed-core` 0.53.0.
    Da ville `InboundIT` og `RestArchiverTest` kjørt prod-koden (Ktor-
    klienten) med en annen coroutines-versjon enn prod.
    - Vi valgte å låse testklassestiene til prod-versjonen:
      `val coroutines_version = "1.8.1"` og
      `resolutionStrategy.eachDependency { useVersion(coroutines_version) }`
      for alle `kotlinx-coroutines-*` på `testCompileClasspath` og
      `testRuntimeClasspath`. Det dekker også `kotlinx-coroutines-debug`,
      som bare mockk drar inn.
    - Prod-klassestien er uendret (verifisert med diff av
      `runtimeClasspath`).
    - 🔴 `coroutines_version` må oppdateres når Exposed oppgraderes,
      ellers tester vi mot en annen versjon enn prod. (Oppdatering: i
      steg 3 ble låsen i stedet utvidet til prod, se der.)
    - Avvik som fantes fra før og ikke er endret: WireMock løfter `guava`
      (30.0 → 32.1.2) og `error_prone_annotations` på testklassestien, og
      `junit` er 4.13.2 i test mot 4.10 i prod (via json-simple, se Fase 4
      steg 3b). En full låsing med `consistentResolution` ville tvunget
      guava ned til 30.0 for WireMock, så det ble ikke valgt.
  - Mutasjonssjekk: når `getToken()`-mocken i `AccessPointClientTest`
    returnerer feil token, feiler 4/4 tester (WireMock matcher på
    `Authorization`). Mockene virker med den nye versjonen.
  - 65/65 tester grønne.

- Steg 3 (Exposed 0.53.0 → 1.5.0):
  - Kodeendringer (bare imports):
    - `org.jetbrains.exposed.sql.*` → `org.jetbrains.exposed.v1.core.*`
      (`Table`, `ResultRow`, `SortOrder`) og `…v1.jdbc.*` (`Database`,
      `insert`, `selectAll`, `select`, `deleteAll`,
      `transactions.transaction`).
    - `…sql.jodatime.date` → `…v1.jodatime.date`.
    - `between` og `select(kolonne)` må nå importeres eksplisitt
      (`v1.core.between`, `v1.jdbc.select`), fordi `SqlExpressionBuilder`
      er deprecated.
    - Kompilerer uten advarsler.
  - Breaking changes 0.54 → 1.5 gjennomgått. Det som kunne treffe oss:
    - Transaksjonshåndteringen er stack-basert i stedet for trådlokal
      (1.0-rc-3), og `transaction()` har fått `db` som første parameter.
      Vi kaller `transaction { }` uten argumenter, og hver kodesti har
      én `Database.connect`. `ReportTest` er grønn.
    - `insert` sender ikke lenger standardverdier implisitt (1.0-rc-1).
      `Report.insert` setter alle kolonner utenom `id` (autoIncrement).
    - Joda `DateColumnType` har fått nytt navn internt
      (`JodaLocalDateColumnType`). `date()`-funksjonen er uendret.
    - `exposed-dao` og `exposed-java-time` brukes ikke i koden, men er
      beholdt og oppgradert.
  - 🔴 Funn: coroutines-konflikt med Ktor 1.6.8.
    - Exposed 1.5.0 løfter `kotlinx-coroutines` i prod fra 1.8.1 til
      1.11.0.
    - `ktor-client-core` 1.6.8 (`ClosableBlockingDispatcher`, brukt av
      `ApacheEngine`) bruker `kotlinx.coroutines.scheduling.
      ExperimentalCoroutineDispatcher`, som er fjernet i coroutines 1.9.0.
    - Verifisert: med coroutines 1.11.0 feiler alle 5 HTTP-testene
      (`RestArchiverTest`, `AccessPointClientTest`) med
      `NoClassDefFoundError`. Prod ville ikke nådd aksesspunktet eller
      arkivet.
    - Coroutines-kravet per Exposed-versjon: 0.54 = 1.8.1, 0.55–0.57 =
      1.9.0, 0.61 = 1.10.1, 1.0–1.2 = 1.10.2, 1.3–1.5 = 1.11.0.
    - Valgt løsning: Exposed 1.5.0, med coroutines låst til 1.8.1 på alle
      fire klassestier (`compileClasspath`, `runtimeClasspath`,
      `testCompileClasspath`, `testRuntimeClasspath`) med
      `eachDependency { useVersion(coroutines_version) }`. Låsen fra
      steg 2 er utvidet fra bare test til også prod. Den fjernes når Ktor
      oppgraderes (egen oppgave over).
    - Bytekodesjekk (japicmp og et skript som leser konstant-poolen i
      alle 38 963 klassene i fat-JAR-en): med coroutines 1.8.1 finnes
      alle referansene fra Exposed 1.5.0, Ktor og øvrige biblioteker til
      `kotlinx/coroutines/*`. De eneste manglende er
      `kotlinx-coroutines-reactor`/`-reactive` fra valgfrie
      Spring-klasser, som ikke er på klassestien og ikke brukes (samme
      som før).
    - Restrisiko: Exposed kjører på en eldre coroutines-versjon enn den
      er bygget mot. Vi bruker ikke `suspendTransaction` eller
      coroutine-API-ene i Exposed, bare blokkerende `transaction { }`
      inne i `withContext(dispatcher)`.
  - Prod-klassestien ellers: `joda-time` 2.12.7 → 2.14.3 (tidssonedata)
    og ny `kotlinx-datetime-jvm` 0.7.1-0.6.x-compat (via exposed-core).
  - Mutasjonssjekk: uten `.withDistinct()` i
    `getAllUniqueDaysWithEntries` feiler 1 test i `ReportTest`.
  - Merk: `./gradlew test` skriver den tynne `jar`-en til samme filnavn
    som fat-JAR-en. CI kjører `build shadowJar`, så `shadowJar` blir
    sist, men lokale analyser av `build/libs` må kjøres etter
    `shadowJar`.
  - 65/65 tester grønne.
  - Verifiseres i dev: oppstart (Flyway og Hikari), `/report` (HTML og
    CSV-nedlasting), og en Invoice via vefasrest (DB-insert og
    juridisk logg via Apache-klienten).

- **Steg 4: kotlin-result 2.0.1 → 2.3.1**
  - Breaking endringer i 2.1–2.3 som ikke treffer oss:
    - 2.1.0: direkte bruk av `Result.value`/`.error` krever opt-in
      (`UnsafeResultValueAccess`/`UnsafeResultErrorAccess`). Vi bruker
      bare `Ok`, `Err`, `andThen`, `getOrElse` og `getErrorOrElse`.
    - 2.2.0: `mapResult*`/`fold`/`onSuccess`/`onFailure` ble omdøpt til
      `try*`/`onOk`/`onErr`. `onSuccess`/`onFailure` i
      `StandardBusinessDocumentGenerator` er `kotlin.Result.fold`, ikke
      kotlin-result.
    - 2.3.0: returverdi-sjekken gir bare advarsler med
      `-Xreturn-value-checker`, som vi ikke har slått på.
  - Kjerneartefakten avhenger bare av `kotlin-stdlib` (2.3.10, løftes til
    2.4.20). Den trekker ikke inn coroutines, det gjør bare
    `kotlin-result-coroutines`, som vi ikke bruker.
  - Runtime-klassestien er uendret bortsett fra kotlin-result selv.
  - Kompilerer uten nye advarsler.
  - Mutasjonssjekk: når det andre `andThen`-steget i
    `StandardBusinessDocumentGenerator` alltid returnerer `Err`, feiler
    2 av 6 tester i `StandardBusinessDocumentGeneratorTest`.
  - 65/65 tester grønne.

- **Steg 5: JUnit 4 → JUnit Jupiter (16 testklasser)**
  - `@Test`/`@Before`/`@After`/`@BeforeClass`/`@AfterClass` →
    `org.junit.jupiter.api.Test`/`@BeforeEach`/`@AfterEach`/`@BeforeAll`/
    `@AfterAll`. `@BeforeAll`/`@AfterAll` i `companion object` med
    `@JvmStatic` fungerer som før.
  - `org.junit.Assert.assertThrows` →
    `org.junit.jupiter.api.Assertions.assertThrows` (samme signatur).
  - `ConfigurationTest`: `@get:Rule TemporaryFolder` → `@TempDir lateinit
    var tempDir: File`, og `newFile(...)` → `tempDir.resolve(...)`. Hver
    test får fortsatt en ny, tom katalog.
  - WireMock i `RestArchiverTest`, `AccessPointClientTest` og `InboundIT`:
    `@ClassRule WireMockRule` → privat `WireMockServer` i
    `companion object`, startet i `@BeforeAll` og stoppet i `@AfterAll`
    (samme mønster som `VaultClientTest`). Valgt framfor
    `WireMockExtension` fordi den (3.0.1) kaller `resetToDefaultMappings()`
    før hver test, og stubs som settes opp i `@BeforeAll` ville da blitt
    slettet. `WireMockRule` kalte `WireMock.configureFor("localhost",
    port)` ved start, så vi gjør det samme i `RestArchiverTest` og
    `InboundIT`, som bruker statisk `stubFor`/`verify`.
    `AccessPointClientTest` bruker bare instans-API-et.
  - Fjernet `junit:junit` (direkte) og `junit-vintage-engine` fra
    `build.gradle.kts`. Runtime-klassestien er uendret. På testklassestien
    forsvinner bare `junit-vintage-engine`.
  - `junit:junit` 4.13.2 ligger fortsatt transitivt på testklassestien
    via `kluent` (bruker `org.junit.ComparisonFailure` o.l. internt, så den
    kan ikke ekskluderes), `ktor-server-test-host` og json-simple. Merk:
    en ny test med `org.junit.Test` kompilerer derfor, men blir ikke
    kjørt (ingen vintage-motor). Bruk alltid `org.junit.jupiter.api.Test`.
  - Verifisert at nøyaktig de samme 65 testcasene kjøres (sammenlignet
    per klasse og per navn før og etter; Jupiter legger bare til `()` i
    navnet).
  - Mutasjonssjekk: uten `WireMock.configureFor(...)` i
    `RestArchiverTest` feiler klassen i `@BeforeAll` (statisk `stubFor`
    går mot standardporten).
  - 65/65 tester grønne.

Jackson, mockk, Exposed og kotlin-result ble frigjort av Kotlin 2.4.20
(Fase 3). Vi valgte likevel å ta dem her i Fase 5 og ikke på slutten av
Fase 3, slik at Fase 3 kunne merges til dev først.

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
