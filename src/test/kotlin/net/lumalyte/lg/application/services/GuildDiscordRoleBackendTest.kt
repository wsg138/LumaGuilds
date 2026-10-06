package net.lumalyte.lg.application.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class GuildDiscordRoleBackendTest {
    @Test
    fun `provider parsing is explicit and case insensitive`() {
        assertEquals(GuildDiscordRoleProvider.DISCORDSRV, GuildDiscordRoleProvider.parse(" DiscordSRV "))
        assertEquals(GuildDiscordRoleProvider.ENTHUSIA, GuildDiscordRoleProvider.parse("ENTHUSIA"))
        assertNull(GuildDiscordRoleProvider.parse("automatic"))
    }

    @Test
    fun `desired state defensively copies membership snapshot`() {
        val first = UUID.randomUUID()
        val source = linkedSetOf(first)
        val desired = GuildDiscordRoleDesiredState(UUID.randomUUID(), "Guild • Badgers", source)

        source.clear()

        assertTrue(source.isEmpty())
        assertEquals(setOf(first), desired.desiredPlayerIds)
    }

    @Test
    fun `desired state rejects unsafe role names`() {
        assertThrows(IllegalArgumentException::class.java) {
            GuildDiscordRoleDesiredState(UUID.randomUUID(), "Guild\nInjected", emptySet())
        }
    }

    @Test
    fun `ownership accepts opaque references but rejects blank values`() {
        val ownership = GuildDiscordRoleOwnership(
            GuildDiscordRoleProvider.DISCORDSRV,
            "123456789012345678",
        )

        assertEquals("123456789012345678", ownership.providerReference)
        assertThrows(IllegalArgumentException::class.java) {
            GuildDiscordRoleOwnership(GuildDiscordRoleProvider.ENTHUSIA, "   ")
        }
    }

    @Test
    fun `reconcile result rejects negative aggregate counts`() {
        val ownership = GuildDiscordRoleOwnership(GuildDiscordRoleProvider.ENTHUSIA, null)

        assertThrows(IllegalArgumentException::class.java) {
            GuildDiscordRoleReconcileResult(ownership, false, -1, 0, 0)
        }
    }
}
