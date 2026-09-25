package no.nav.ehandel.kanal

import org.amshove.kluent.shouldContain
import org.flywaydb.core.Flyway
import org.flywaydb.core.internal.database.DatabaseTypeRegister
import org.junit.Test

class FlywayDatabaseSupportTest {

    private fun supportedTypesFor(url: String): List<String> =
        DatabaseTypeRegister.getDatabaseTypesForUrl(url, Flyway.configure()).map { it.name }

    @Test
    fun `flyway supports PostgreSQL used by initRemote`() {
        supportedTypesFor("jdbc:postgresql://localhost:5432/ehandelkanal") shouldContain "PostgreSQL"
    }

    @Test
    fun `flyway supports H2 used by initLocal`() {
        supportedTypesFor("jdbc:h2:mem:ehandelkanal;MODE=PostgreSQL") shouldContain "H2"
    }
}
