package net.lumalyte.lg.infrastructure.services

import net.enthusia.discord.platform.api.DiscordPlatformAvailability
import net.enthusia.discord.platform.api.ManagedRoleClaim
import net.enthusia.discord.platform.api.ManagedRoleClient
import net.enthusia.discord.platform.api.ManagedRoleDeleteResult
import net.enthusia.discord.platform.api.ManagedRoleKey
import net.enthusia.discord.platform.api.ManagedRoleNamespace
import net.enthusia.discord.platform.api.ManagedRolePlatform
import net.enthusia.discord.platform.api.ManagedRoleReconcileResult
import net.enthusia.discord.platform.api.ManagedRoleReconcileStatus
import net.lumalyte.lg.application.services.GuildDiscordRoleDesiredState
import net.lumalyte.lg.application.services.GuildDiscordRoleOwnership
import net.lumalyte.lg.application.services.GuildDiscordRoleProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class EnthusiaGuildRoleBackendTest {
    @Test
    fun `reconcile publishes complete namespace-scoped Minecraft membership`() {
        val client = FakeClient()
        val backend = EnthusiaGuildRoleBackend { FakePlatform(client) }
        val guildId = UUID.randomUUID()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()

        val result = backend.reconcile(
            GuildDiscordRoleDesiredState(guildId, "Guild • Test", setOf(first, second)),
            null,
        ).join()

        val claim = requireNotNull(client.lastClaim)
        assertEquals("luma-guilds", claim.key().namespace().value())
        assertEquals("guild:$guildId", claim.key().localKey())
        assertEquals("Guild • Test", claim.displayName())
        assertEquals(setOf(first, second), claim.desiredMinecraftAccounts())
        assertEquals(GuildDiscordRoleProvider.ENTHUSIA, result.ownership.provider)
        assertFalse(result.created)
    }

    @Test
    fun `unavailable platform fails closed`() {
        val backend = EnthusiaGuildRoleBackend { null }
        assertFalse(backend.isAvailable())

        val future = backend.reconcile(
            GuildDiscordRoleDesiredState(UUID.randomUUID(), "Guild • Test", emptySet()),
            null,
        )
        assertTrue(future.isCompletedExceptionally)
    }

    @Test
    fun `delete uses the same stable guild key`() {
        val client = FakeClient()
        val backend = EnthusiaGuildRoleBackend { FakePlatform(client) }
        val guildId = UUID.randomUUID()

        val result = backend.delete(
            guildId,
            GuildDiscordRoleOwnership(GuildDiscordRoleProvider.ENTHUSIA, "guild:$guildId"),
        ).join()

        assertEquals(net.lumalyte.lg.application.services.GuildDiscordRoleDeleteResult.RETRY_SCHEDULED, result)
        assertEquals("guild:$guildId", client.lastDelete?.localKey())
    }

    private class FakePlatform(
        private val client: ManagedRoleClient,
    ) : ManagedRolePlatform {
        override fun apiVersion(): Int = ManagedRolePlatform.API_VERSION
        override fun availability(): DiscordPlatformAvailability = DiscordPlatformAvailability.AVAILABLE

        override fun clientFor(namespace: ManagedRoleNamespace): Optional<ManagedRoleClient> =
            if (namespace.value() == "luma-guilds") Optional.of(client) else Optional.empty()
    }

    private class FakeClient : ManagedRoleClient {
        var lastClaim: ManagedRoleClaim? = null
        var lastDelete: ManagedRoleKey? = null

        override fun namespace(): ManagedRoleNamespace = ManagedRoleNamespace("luma-guilds")
        override fun availability(): DiscordPlatformAvailability = DiscordPlatformAvailability.AVAILABLE

        override fun reconcile(claim: ManagedRoleClaim): CompletionStage<ManagedRoleReconcileResult> {
            lastClaim = claim
            return CompletableFuture.completedFuture(
                ManagedRoleReconcileResult(
                    ManagedRoleReconcileStatus.RETRY_SCHEDULED,
                    claim.desiredMinecraftAccounts().size,
                    0,
                    0,
                    0,
                ),
            )
        }

        override fun delete(key: ManagedRoleKey): CompletionStage<ManagedRoleDeleteResult> {
            lastDelete = key
            return CompletableFuture.completedFuture(ManagedRoleDeleteResult.RETRY_SCHEDULED)
        }
    }
}
