package no.nav.ehandel.kanal

import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.Misconfiguration
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import java.util.Properties

val config = systemProperties() overriding
    EnvironmentVariables() overriding
    ConfigurationProperties.fromResourceAsStream("application.properties")

private fun ConfigurationProperties.Companion.fromResourceAsStream(resourceName: String): ConfigurationProperties {
    val input = this::class.java.classLoader.getResourceAsStream(resourceName)
    return (input ?: throw Misconfiguration("resource $resourceName not found")).use {
        ConfigurationProperties(Properties().apply { load(input) })
    }
}

object AccessPointProps {
    data class Properties(val url: String, val apiKey: String, val header: String)
    val inbox = Properties(
        url = config[Key("vefasrest.inbox.url", stringType)].removeSuffix("/"),
        apiKey = config[Key("vefasrest.inbox.apikey", stringType)],
        header = config[Key("vefasrest.inbox.header", stringType)]
    )
    val outbox = Properties(
        url = config[Key("vefasrest.outbox.url", stringType)].removeSuffix("/"),
        apiKey = config[Key("vefasrest.outbox.apikey", stringType)],
        header = config[Key("vefasrest.outbox.header", stringType)]
    )
    val messages = Properties(
        url = config[Key("vefasrest.messages.url", stringType)].removeSuffix("/"),
        apiKey = config[Key("vefasrest.messages.apikey", stringType)],
        header = config[Key("vefasrest.messages.header", stringType)]
    )
    val transmit = Properties(
        url = config[Key("vefasrest.transmit.url", stringType)].removeSuffix("/"),
        apiKey = config[Key("vefasrest.transmit.apikey", stringType)],
        header = config[Key("vefasrest.transmit.header", stringType)]
    )
}

object EbasysProps {
    val url = config[Key("ebasys.url", stringType)]
    val username = config[Key("ebasys.username", stringType)]
    val password = config[Key("ebasys.password", stringType)]
    val unknownFileDirectory = config[Key("ebasys.directories.unknownfiles", stringType)]
}

object FileAreaProps {
    val eFaktura = config[Key("filearea.efaktura", stringType)]
}

object LegalArchiveProps {
    val endpointUrl = config[Key("legalarchive.endpoint.url", stringType)]
}

object LoggerProps {
    val urnNav = config[Key("urn.nav", stringType)]
    val urnEhandel = config[Key("urn.ehandelkanal", stringType)]
}

object MqProps {
    val name = config[Key("mq.name", stringType)]
    val hostName = config[Key("mq.hostname", stringType)]
    val port = config[Key("mq.port", intType)]
    val username = config[Key("mq.username", stringType)]
    val password = config[Key("mq.password", stringType)]
    val channelName = config[Key("mq.channelname", stringType)]
}

object ServiceUserProps {
    val username = config[Key("serviceuser.username", stringType)]
    val password = config[Key("serviceuser.password", stringType)]
}

object QueueProps {
    val inName = config[Key("mq.queue.in.name", stringType)]
}

val catalogueSizeLimit = config[Key("catalogue.mqsizelimit", intType)]
val appName = config[Key("app.name", stringType)]
