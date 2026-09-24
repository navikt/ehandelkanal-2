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

### Fase 2 — Én major-versjon å krysse (egen commit hver)
| Dependency | Fra | Til | Breaking changes å sjekke |
|---|---|---|---|
| IBM MQ allclient | 9.1.3.0 | 10.0.0.5 | Sjekk MQ-klient-API/JMS-kompatibilitet mot Camel-JMS-oppsettet |
| shadow-plugin | 7.1.2 | 8.1.1 | Task-navn/konfig for shadowJar kan ha endret seg |
| wiremock-jre8 | 2.35.1 | 3.0.1 | Artefakt `wiremock-jre8` er avviklet i 3.x → vurder bytte til `wiremock` (og evt. `wiremock-standalone`); pakkenavn/API endret (`com.github.tomakehurst.wiremock` → `org.wiremock`) |
| junit-vintage-engine | 5.10.2 | 6.1.3 | JUnit 6 krever nyere JUnit Platform; sjekk at JUnit 4-testene (`ArchiveRequestTest` mfl. som evt. bruker JUnit4) fortsatt kjøres av vintage-engine |

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
- Steg 1: 7.4 → siste 8.x
- Steg 2: 8.x → 9.0
- Sjekk encoder-konfigurasjon i `logback.xml`/kode for feltnavn-endringer.

**JAXB runtime: 2.4.0 → 4.0.9**
- **Rødsone / kjernelogikk**: SBDH/XML-parsing er kjernen i meldingsflyten.
- Steg 1: 2.x → siste 3.0.x (jaxb-runtime 3.0 = siste `javax.xml.bind`-generasjon)
- Steg 2: 3.0.x → 4.0.x — dette er **jakarta-navnerom-migrering**
  (`javax.xml.bind.*` → `jakarta.xml.bind.*`). Krever endring i importer i
  koden og i genererte SBDH/UBL-klasser, samt bytte av `jaxb-api`-avhengigheten
  til jakarta-varianten. Dette er den mest risikable enkeltoppgraderingen i PR-en.
- Verifiser med `InboundSbdhMetaDataExtractorTest`, `InboundSbdhRemoverTest`,
  `StandardBusinessDocumentGeneratorTest`, `XmlDetectorTest` — utvid disse om
  de ikke dekker (de)serialisering etter namespace-bytte.

**HikariCP: 5.1.0 → 7.1.0**
- Steg 1: 5.x → siste 6.x
- Steg 2: 6.x → 7.1.0
- Sjekk minimum-Java-krav per major (nyere HikariCP kan kreve nyere JDK-baseline —
  vi er på JDK 21 så bør være greit, men bekreft).

**vault-java-driver: 3.1.0 → 5.1.0**
- Steg 1: 3.x → 4.x
- Steg 2: 4.x → 5.1.0
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
