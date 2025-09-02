import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

group = "no.nav.integrasjon"
version = "1.0.52-SNAPSHOT"

val camel_version = "2.24.2"
val ibm_mq_version = "9.1.3.0"
val jackson_version = "2.10.0"
val konfig_version = "1.6.10.0"
val difi_commons_sbdh_version = "0.9.5"
val difi_peppol_sbdh_version = "1.1.3"
val kotlin_logging_version = "1.7.6"
val jaxb_api_version = "2.4.0-b180830.0359"
val jaxb_runtime_version = "2.4.0-b180830.0438"
val ktor_version = "1.6.8"
val logstash_version = "6.2"
val logback_version = "1.2.3"
val prometheus_version = "0.8.0"
val javax_activation_version = "1.2.0"
val difi_commons_ubl_version = "0.9.5"
val hikari_version = "3.4.1"
val vault_driver_version = "3.1.0"
val flyway_version = "6.0.4"
val h2_version = "1.4.200"
val postgres_version = "42.2.8"
val exposed_version = "0.41.1"
val result_version = "1.1.6"
val wiremock_version = "2.25.1"
val mockk_version = "1.9"
val kluent_version = "1.73"




plugins {
    application
    kotlin("jvm") version "1.9.24"
    //id("org.jmailen.kotlinter") version "5.2.0"
    id("com.github.ben-manes.versions") version "0.51.0"
    id("org.flywaydb.flyway") version "6.0.8"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

// kotlinter {
//     ignoreLintFailures = true       
//     ignoreFormatFailures = true     
//     reporters = arrayOf("plain")    
// }

application {
    mainClassName = "no.nav.ehandel.kanal.EhandelBootstrapKt"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-html-builder:$ktor_version")
    implementation("io.ktor:ktor-jackson:$ktor_version")
    implementation("io.ktor:ktor-auth:$ktor_version")
    implementation("io.ktor:ktor-auth-jwt:$ktor_version")
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-apache:$ktor_version")
    implementation("io.ktor:ktor-client-auth-basic-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-auth-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-json-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-jackson:$ktor_version")
    implementation("com.natpryce:konfig:$konfig_version")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jackson_version")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_version")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-joda:$jackson_version")
    implementation("org.apache.camel:camel-core:$camel_version")
    implementation("org.apache.camel:camel-jms:$camel_version")
    implementation("org.apache.camel:camel-ftp:$camel_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstash_version")
    implementation("io.github.microutils:kotlin-logging:$kotlin_logging_version")
    implementation("no.difi.commons:commons-sbdh:$difi_commons_sbdh_version")
    implementation("no.difi.vefa:peppol-sbdh:$difi_peppol_sbdh_version")
    implementation("com.ibm.mq:com.ibm.mq.allclient:$ibm_mq_version")
    implementation("io.prometheus:simpleclient_common:$prometheus_version")
    implementation("io.prometheus:simpleclient_hotspot:$prometheus_version")
    implementation("com.sun.activation:javax.activation:$javax_activation_version")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxb_runtime_version")
    implementation("javax.xml.bind:jaxb-api:$jaxb_api_version")
    implementation("no.difi.commons:commons-ubl21:$difi_commons_ubl_version")
    implementation("com.zaxxer:HikariCP:$hikari_version")
    implementation("com.bettercloud:vault-java-driver:$vault_driver_version")
    implementation("org.flywaydb:flyway-core:$flyway_version")
    implementation("org.postgresql:postgresql:$postgres_version")
    implementation("com.h2database:h2:$h2_version")
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("com.michael-bull.kotlin-result:kotlin-result:$result_version")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jodatime:$exposed_version")
    testImplementation("org.apache.camel:camel-test:$camel_version")
    testImplementation("com.github.tomakehurst:wiremock:$wiremock_version")
    testImplementation("io.mockk:mockk:$mockk_version")
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version") {
        exclude(group = "org.eclipse.jetty") // conflicts with WireMock
    }
    testImplementation("org.amshove.kluent:kluent:$kluent_version") {
        exclude(group = "com.nhaarman.mockitokotlin2")
    }
    //testCompileOnly("junit:junit:4.12")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.5.1")
}

tasks {
    create("printVersion") {
        println(project.version)
    }
    withType<ShadowJar> {
        archiveClassifier.set("")
    }
withType<Test> {
    useJUnitPlatform()

    testLogging {
        events = setOf(
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
            TestLogEvent.FAILED,
            TestLogEvent.STANDARD_OUT,
            TestLogEvent.STANDARD_ERROR
        )
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }

    reports {
        junitXml.required.set(true)
        html.required.set(true)
    }

    // nice summary at end of test run
    afterSuite(KotlinClosure2({ desc: TestDescriptor, result: TestResult ->
        if (desc.parent == null) {
            println(
                "\nResults: ${result.resultType} " +
                "(${result.successfulTestCount} passed, " +
                "${result.failedTestCount} failed, " +
                "${result.skippedTestCount} skipped)"
            )
            println("Report HTML: ${reports.html.outputLocation.get().asFile.absolutePath}/index.html")
        }
    }))
}
    withType<Wrapper> {
        gradleVersion = "7.6.4"
        distributionType = Wrapper.DistributionType.BIN
    }
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
        }
    }
    named("distZip")  { dependsOn("shadowJar") }
    named("distTar")  { dependsOn("shadowJar") }
    named("startScripts") { dependsOn("shadowJar") }
    named("startShadowScripts") { dependsOn("jar") }
    named("shadowDistZip")      { dependsOn("jar") }
    named("shadowDistTar")      { dependsOn("jar") }
}
