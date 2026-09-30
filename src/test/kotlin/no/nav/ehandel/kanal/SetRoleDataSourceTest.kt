package no.nav.ehandel.kanal

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import javax.sql.DataSource
import no.nav.ehandel.kanal.db.SetRoleDataSource
import org.amshove.kluent.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SetRoleDataSourceTest {
    private val statement = mockk<Statement>(relaxed = true)
    private val jdbcConnection = mockk<Connection>(relaxed = true) {
        every { createStatement() } returns statement
    }
    private val delegate = mockk<DataSource> {
        every { getConnection() } returns jdbcConnection
        every { getConnection(any(), any()) } returns jdbcConnection
    }
    private val dataSource = SetRoleDataSource(delegate, "ehandelkanal-2-dev-admin")

    @Test
    fun `sets role on every new connection before returning it`() {
        dataSource.connection shouldBe jdbcConnection
        dataSource.connection shouldBe jdbcConnection

        verify(exactly = 2) { statement.execute("SET ROLE \"ehandelkanal-2-dev-admin\"") }
        verify(exactly = 2) { statement.close() }
        verify(exactly = 0) { jdbcConnection.close() }
    }

    @Test
    fun `sets role when credentials are given explicitly`() {
        dataSource.getConnection("user", "password") shouldBe jdbcConnection

        verifyOrder {
            delegate.getConnection("user", "password")
            statement.execute("SET ROLE \"ehandelkanal-2-dev-admin\"")
        }
    }

    @Test
    fun `closes the connection when setting the role fails`() {
        every { statement.execute(any<String>()) } throws SQLException("permission denied to set role")

        assertThrows<SQLException> { dataSource.connection }

        verify { jdbcConnection.close() }
    }
}
