package no.nav.ehandel.kanal.db

import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

internal class SetRoleDataSource(
    private val delegate: DataSource,
    private val role: String
) : DataSource by delegate {

    override fun getConnection(): Connection = delegate.connection.withRole()

    override fun getConnection(username: String?, password: String?): Connection =
        delegate.getConnection(username, password).withRole()

    private fun Connection.withRole(): Connection = apply {
        try {
            createStatement().use { it.execute("SET ROLE \"$role\"") }
        } catch (e: SQLException) {
            close()
            throw e
        }
    }
}
