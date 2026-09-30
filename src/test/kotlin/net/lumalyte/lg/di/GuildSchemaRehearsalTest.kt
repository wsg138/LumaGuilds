package net.lumalyte.lg.di

import io.mockk.every
import io.mockk.mockk
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.infrastructure.persistence.migrations.SQLiteMigrations
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockbukkit.mockbukkit.MockBukkit
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager

class GuildSchemaRehearsalTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `existing schema rehearsal preserves every stored row`() {
        val source = System.getenv("ES_GUILD_REHEARSAL_DATABASE")
        assumeTrue(!source.isNullOrBlank(), "An offline database snapshot is required")
        val copy = directory.resolve("rehearsal.db")
        Files.copy(Path.of(source!!), copy)
        MockBukkit.mock()
        try {
            val plugin = mockk<LumaGuilds>(relaxed = true)
            every { plugin.getComponentLogger() } returns ComponentLogger.logger("GuildSchemaRehearsal")
            DriverManager.getConnection("jdbc:sqlite:$copy").use { connection ->
                val before = snapshot(connection)
                SQLiteMigrations(plugin, connection).migrate()
                assertEquals(before, snapshot(connection), "Stored rows changed during the compatibility rehearsal")
            }
        } finally {
            MockBukkit.unmock()
        }
    }

    private fun snapshot(connection: Connection): Map<String, List<String>> {
        val tables = mutableListOf<String>()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { result ->
                while (result.next()) tables.add(result.getString(1))
            }
        }
        return tables.associateWith { table ->
            val rows = mutableListOf<String>()
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT * FROM \"${table.replace("\"", "\"\"")}\"").use { result ->
                    while (result.next()) {
                        val digest = MessageDigest.getInstance("SHA-256")
                        for (column in 1..result.metaData.columnCount) {
                            val value = result.getBytes(column)
                            digest.update((value?.size ?: -1).toString().toByteArray())
                            digest.update(0.toByte())
                            if (value != null) digest.update(value)
                        }
                        rows.add(digest.digest().joinToString("") { "%02x".format(it) })
                    }
                }
            }
            rows.sorted()
        }
    }
}
