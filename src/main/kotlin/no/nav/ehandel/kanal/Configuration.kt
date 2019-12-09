package no.nav.ehandel.kanal

import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import io.ktor.client.request.get
import java.io.File

private const val VAULT_APPLICATION_PROPERTIES_PATH = "/var/run/secrets/nais.io/vault/application.properties"

val config = if (System.getenv("APP_PROFILE") == "remote") {
    systemProperties() overriding
        EnvironmentVariables() overriding
        ConfigurationProperties.fromFile(File(VAULT_APPLICATION_PROPERTIES_PATH)) overriding
        ConfigurationProperties.fromResource("application.properties")
} else {
    systemProperties() overriding
        EnvironmentVariables() overriding
        ConfigurationProperties.fromResource("application.properties")
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

object DatabaseProps {
    val url = config[Key("db.url", stringType)]
    val username = config[Key("db.username", stringType)]
    val password = config[Key("db.password", stringType)]
    val vaultMountPath = config[Key("db.vault.mount.path", stringType)]
    val name = config[Key("db.name", stringType)]
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

object SecurityTokenServiceProps {
    val wellKnownUrl: String = config.getOrElse(
        Key("sts.well-known.url", stringType),
        "http://security-token-service.default.svc.nais.local/rest/v1/sts/.well-known/openid-configuration"
    )
    val acceptedAudiences: List<String> = config[Key("sts.jwt.accepted.audiences", stringType)].split(",")
}

val catalogueSizeLimit = config[Key("catalogue.mqsizelimit", intType)]
val appName = config[Key("app.name", stringType)]
val appProfileLocal = config[Key("app.profile", stringType)] == "local"
