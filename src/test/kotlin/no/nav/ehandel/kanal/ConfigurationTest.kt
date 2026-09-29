package no.nav.ehandel.kanal

import com.natpryce.konfig.Key
import com.natpryce.konfig.stringType
import java.io.File
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

private val ebasysUrl = Key("ebasys.url", stringType)
private const val RESOURCE_EBASYS_URL = "ftp://localhost:20000/ftpeFaktura"
private const val VAULT_EBASYS_URL = "ftp://vault.example.invalid/ftpeFaktura"
private const val SYSTEM_PROPERTY_EBASYS_URL = "ftp://sysprop.example.invalid/ftpeFaktura"

class ConfigurationTest {

    @TempDir
    lateinit var tempDir: File

    @AfterEach
    fun tearDown() {
        System.clearProperty(ebasysUrl.name)
    }

    @Test
    fun `remote profile should read vault properties before resource file`() {
        loadConfig("remote", vaultProperties())[ebasysUrl] shouldBeEqualTo VAULT_EBASYS_URL
    }

    @Test
    fun `missing profile should not read vault properties`() {
        loadConfig(null, vaultProperties())[ebasysUrl] shouldBeEqualTo RESOURCE_EBASYS_URL
    }

    @Test
    fun `other profile should not read vault properties`() {
        loadConfig("local", vaultProperties())[ebasysUrl] shouldBeEqualTo RESOURCE_EBASYS_URL
    }

    @Test
    fun `system property should override vault properties`() {
        System.setProperty(ebasysUrl.name, SYSTEM_PROPERTY_EBASYS_URL)
        loadConfig("remote", vaultProperties())[ebasysUrl] shouldBeEqualTo SYSTEM_PROPERTY_EBASYS_URL
    }

    private fun vaultProperties(): File =
        tempDir.resolve("application.properties").apply {
            writeText(
                """
                ebasys.url=$VAULT_EBASYS_URL
                ebasys.username=vaultuser
                """.trimIndent()
            )
        }
}
