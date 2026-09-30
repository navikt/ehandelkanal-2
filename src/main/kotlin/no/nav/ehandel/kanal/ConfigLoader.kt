package no.nav.ehandel.kanal

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.overriding
import java.io.File

internal fun loadConfig(appProfile: String?, vaultProperties: File): Configuration =
    if (appProfile == "remote") {
        systemProperties() overriding
            EnvironmentVariables() overriding
            ConfigurationProperties.fromFile(vaultProperties) overriding
            ConfigurationProperties.fromResource("application.properties")
    } else {
        systemProperties() overriding
            EnvironmentVariables() overriding
            ConfigurationProperties.fromResource("application.properties")
    }
