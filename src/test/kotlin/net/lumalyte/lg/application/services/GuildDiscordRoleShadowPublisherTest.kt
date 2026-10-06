package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.GuildDiscordRoleRepository
import net.lumalyte.lg.config.DiscordGuildRolesConfig
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildDiscordRoleLink
import net.lumalyte.lg.domain.entities.Member
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

class GuildDiscordRoleShadowPublisherTest {
    @Test
    fun `reconcile publishes the complete current guild membership snapshot`() {
        val guildId = UUID.randomUUID()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val guild = Guild(guildId, "Badgers", createdAt = Instant.EPOCH)
        val members = setOf(
            Member(first, guildId, UUID.randomUUID(), Instant.EPOCH),
            Member(second, guildId, UUID.randomUUID(), Instant.EPOCH),
        )

        val config = mockk<ConfigService>()
        val guilds = mockk<GuildService>()
        val memberService = mockk<MemberService>()
        val backend = CapturingBackend()
        val repository = mockk<GuildDiscordRoleRepository>()
        every { config.loadConfig() } returns MainConfig(
            discordGuildRoles = DiscordGuildRolesConfig(
                enabled = true,
                roleNameFormat = "Guild • <guild>",
                enthusiaShadowEnabled = true,
            ),
        )
        every { guilds.getGuild(guildId) } returns guild
        every { memberService.getGuildMembers(guildId) } returns members
        every { repository.get(guildId) } returns GuildDiscordRoleLink(
            guildId,
            "1552390213500928122",
            Instant.EPOCH,
        )

        val publisher = GuildDiscordRoleShadowPublisher(config, guilds, memberService, repository) { backend }
        val summary = publisher.reconcileGuild(guildId).join()

        assertEquals(1, summary.claimsPublished)
        assertEquals(0, summary.failures)
        assertEquals("Guild • Badgers", backend.lastDesired?.roleName)
        assertEquals(setOf(first, second), backend.lastDesired?.desiredPlayerIds)
        assertEquals(GuildDiscordRoleProvider.DISCORDSRV, backend.lastCurrentOwnership?.provider)
        assertEquals("1552390213500928122", backend.lastCurrentOwnership?.providerReference)
    }

    @Test
    fun `reconcile all isolates a failing guild and continues publishing others`() {
        val failingGuildId = UUID.randomUUID()
        val healthyGuildId = UUID.randomUUID()
        val failingGuild = Guild(failingGuildId, "Broken", createdAt = Instant.EPOCH)
        val healthyGuild = Guild(healthyGuildId, "Healthy", createdAt = Instant.EPOCH)
        val healthyPlayer = UUID.randomUUID()

        val config = mockk<ConfigService>()
        val guilds = mockk<GuildService>()
        val memberService = mockk<MemberService>()
        val backend = CapturingBackend()
        val repository = mockk<GuildDiscordRoleRepository>()

        every { config.loadConfig() } returns MainConfig(
            discordGuildRoles = DiscordGuildRolesConfig(
                enabled = true,
                roleNameFormat = "Guild • <guild>",
                enthusiaShadowEnabled = true,
            ),
        )
        every { guilds.getAllGuilds() } returns setOf(failingGuild, healthyGuild)
        every { guilds.getGuild(failingGuildId) } returns failingGuild
        every { guilds.getGuild(healthyGuildId) } returns healthyGuild
        every { memberService.getGuildMembers(failingGuildId) } throws IllegalStateException("broken snapshot")
        every { memberService.getGuildMembers(healthyGuildId) } returns setOf(
            Member(healthyPlayer, healthyGuildId, UUID.randomUUID(), Instant.EPOCH),
        )
        every { repository.get(healthyGuildId) } returns null

        val publisher = GuildDiscordRoleShadowPublisher(config, guilds, memberService, repository) { backend }
        val summary = publisher.reconcileAll().join()

        assertEquals(1, summary.claimsPublished)
        assertEquals(1, summary.failures)
        assertEquals(healthyGuildId, backend.lastDesired?.guildId)
        assertEquals(setOf(healthyPlayer), backend.lastDesired?.desiredPlayerIds)
    }

    @Test
    fun `shadow publication is independently disabled by default`() {
        val config = mockk<ConfigService>()
        val guilds = mockk<GuildService>()
        val memberService = mockk<MemberService>()
        every { config.loadConfig() } returns MainConfig(
            discordGuildRoles = DiscordGuildRolesConfig(enabled = true),
        )

        val repository = mockk<GuildDiscordRoleRepository>()
        val publisher = GuildDiscordRoleShadowPublisher(config, guilds, memberService, repository) {
            error("backend must not be resolved while shadow publication is disabled")
        }

        val summary = publisher.reconcileGuild(UUID.randomUUID()).join()
        assertEquals(GuildDiscordRoleShadowSummary(), summary)
    }

    @Test
    fun `disband publishes a namespace-owned deletion claim`() {
        val config = mockk<ConfigService>()
        val guilds = mockk<GuildService>()
        val memberService = mockk<MemberService>()
        val backend = CapturingBackend()
        val repository = mockk<GuildDiscordRoleRepository>()
        every { config.loadConfig() } returns MainConfig(
            discordGuildRoles = DiscordGuildRolesConfig(
                enabled = true,
                enthusiaShadowEnabled = true,
            ),
        )

        val publisher = GuildDiscordRoleShadowPublisher(config, guilds, memberService, repository) { backend }
        val guildId = UUID.randomUUID()
        val summary = publisher.deleteGuild(guildId).join()

        assertEquals(1, summary.deleteClaimsPublished)
        assertEquals(guildId, backend.lastDeleteGuild)
        assertEquals(GuildDiscordRoleProvider.ENTHUSIA, backend.lastDeleteOwnership?.provider)
        assertEquals("guild:$guildId", backend.lastDeleteOwnership?.providerReference)
    }

    private class CapturingBackend : GuildDiscordRoleBackend {
        override val provider = GuildDiscordRoleProvider.ENTHUSIA
        var lastDesired: GuildDiscordRoleDesiredState? = null
        var lastCurrentOwnership: GuildDiscordRoleOwnership? = null
        var lastDeleteGuild: UUID? = null
        var lastDeleteOwnership: GuildDiscordRoleOwnership? = null

        override fun isAvailable(): Boolean = true

        override fun reconcile(
            desiredState: GuildDiscordRoleDesiredState,
            currentOwnership: GuildDiscordRoleOwnership?,
        ): CompletableFuture<GuildDiscordRoleReconcileResult> {
            lastDesired = desiredState
            lastCurrentOwnership = currentOwnership
            return CompletableFuture.completedFuture(
                GuildDiscordRoleReconcileResult(
                    GuildDiscordRoleOwnership(provider, "guild:${desiredState.guildId}"),
                    false,
                    0,
                    0,
                    0,
                ),
            )
        }

        override fun delete(
            guildId: UUID,
            currentOwnership: GuildDiscordRoleOwnership,
        ): CompletableFuture<GuildDiscordRoleDeleteResult> {
            lastDeleteGuild = guildId
            lastDeleteOwnership = currentOwnership
            return CompletableFuture.completedFuture(GuildDiscordRoleDeleteResult.RETRY_SCHEDULED)
        }
    }
}
