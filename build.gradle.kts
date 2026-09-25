import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.integrasjon"
version = "1.0.52-SNAPSHOT"

val camel_version = "2.24.2"
val ibm_mq_version = "10.0.0.5"
val jackson_version = "2.19.4"
val konfig_version = "1.6.10.0"
val difi_commons_sbdh_version = "0.9.5"
val difi_peppol_sbdh_version = "1.1.4"
val kotlin_logging_version = "3.0.5"
val jaxb_api_version = "2.3.3"
val jaxb_runtime_version = "2.3.9"
val ktor_version = "1.6.8"
val logstash_version = "8.1"
val logback_version = "1.6.3"
val prometheus_version = "0.16.0"
val javax_activation_version = "1.2.0"
val difi_commons_ubl_version = "0.9.5"
val hikari_version = "7.1.0"
val vault_driver_version = "5.1.0"
val flyway_version = "11.20.3"
val h2_version = "1.4.200"
val postgres_version = "42.7.13"
val exposed_version = "0.41.1"
val result_version = "1.1.6"
val wiremock_version = "3.0.1"
val mockk_version = "1.13.12"
val kluent_version = "1.73"
val junit_version = "4.13.2"
val junit_vintage_version = "5.11.4"

plugins {
    application
    kotlin("jvm") version "1.9.24"
    //id("org.jmailen.kotlinter") version "5.2.0"
    id("com.github.ben-manes.versions") version "0.51.0"
    id("org.flywaydb.flyway") version "11.20.3"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

// kotlinter {
//     ignoreLintFailures = true       
//     ignoreFormatFailures = true     
//     reporters = arrayOf("plain")    
// }

flyway {
    locations = arrayOf(
        "classpath:db/migration/common",
        "classpath:db/migration/{vendor}"
    )
}

application {
    mainClass.set("no.nav.ehandel.kanal.EhandelBootstrapKt")
}


repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
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
    implementation("org.apache.camel:camel-jsonpath:$camel_version")
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
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:$jaxb_api_version")
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

    runtimeOnly("org.flywaydb:flyway-database-postgresql:$flyway_version")

    testImplementation("org.apache.camel:camel-test:$camel_version")
    testImplementation("org.wiremock:wiremock:$wiremock_version")
    testImplementation("io.mockk:mockk:$mockk_version")
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version") {
        exclude(group = "org.eclipse.jetty") // conflicts with WireMock
    }
    testImplementation("org.amshove.kluent:kluent:$kluent_version") {
        exclude(group = "com.nhaarman.mockitokotlin2")
    }
    testImplementation("junit:junit:$junit_version")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:$junit_vintage_version")
}

tasks {
    create("printVersion") {
        println(project.version)
    }
    withType<ShadowJar> {
        archiveClassifier.set("")
        mergeServiceFiles()
    }
    withType<Test> {
        useJUnitPlatform()
        systemProperty("user.timezone", "Europe/Oslo")
        testLogging {
            events("passed", "skipped", "failed", "standardOut", "standardError")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStackTraces = true
            showCauses = true
            showExceptions = true
        }
        include("**/*Test.class")
        include("**/*IT.class")

        reports {
            junitXml.required.set(true)
            html.required.set(true)
        }
    }
// withType<Test> {
//     useJUnitPlatform()
//
//     testLogging {
//         events = setOf(
//             TestLogEvent.PASSED,
//             TestLogEvent.SKIPPED,
//             TestLogEvent.FAILED,
//             TestLogEvent.STANDARD_OUT,
//             TestLogEvent.STANDARD_ERROR
//         )
//         exceptionFormat = TestExceptionFormat.FULL
//         showExceptions = true
//         showCauses = true
//         showStackTraces = true
//     }
//
//     reports {
//         junitXml.required.set(true)
//         html.required.set(true)
//     }
//
//
//}
    withType<Wrapper> {
        gradleVersion = "7.6.4"
        distributionType = Wrapper.DistributionType.BIN
    }
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "21"
        }
    }
    named("distZip")  { dependsOn("shadowJar") }
    named("distTar")  { dependsOn("shadowJar") }
    named("startScripts") { dependsOn("shadowJar") }
    named("startShadowScripts") { dependsOn("jar") }
    named("shadowDistZip")      { dependsOn("jar") }
    named("shadowDistTar")      { dependsOn("jar") }
}
tasks.matching { it.name.startsWith("flyway") }.configureEach {
    dependsOn("processResources")
}


