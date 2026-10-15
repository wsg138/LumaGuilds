package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.GuildDiscordRoleRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import java.util.UUID
import java.util.concurrent.CompletableFuture

data class GuildDiscordRoleShadowSummary(
    val claimsPublished: Int = 0,
    val deleteClaimsPublished: Int = 0,
    val failures: Int = 0,
) {
    operator fun plus(other: GuildDiscordRoleShadowSummary) = GuildDiscordRoleShadowSummary(
        claimsPublished + other.claimsPublished,
        deleteClaimsPublished + other.deleteClaimsPublished,
        failures + other.failures,
    )
}

/**
 * Migration-period publisher for complete provider-neutral guild role claims.
 *
 * DiscordSRV remains the live mutation path. This class only publishes the same desired state to
 * EnthusiaStaff for read-only parity checks.
 */
class GuildDiscordRoleShadowPublisher(
    private val configService: ConfigService,
    private val guildService: GuildService,
    private val memberService: MemberService,
    private val repository: GuildDiscordRoleRepository,
    private val progressionRepository: ProgressionRepository,
    private val backendProvider: () -> GuildDiscordRoleBackend?,
) {
    fun enabled(): Boolean {
        val config = configService.loadConfig().discordGuildRoles
        return config.enabled && config.enthusiaShadowEnabled
    }

    fun reconcileAll(): CompletableFuture<GuildDiscordRoleShadowSummary> = safePublish {
        if (!enabled()) return@safePublish completed(GuildDiscordRoleShadowSummary())
        val work = guildService.getAllGuilds().map { guild ->
            try {
                reconcileGuild(guild.id)
            } catch (_: RuntimeException) {
                completed(GuildDiscordRoleShadowSummary(failures = 1))
            }
        }
        combine(work)
    }

    fun reconcileGuild(guildId: UUID): CompletableFuture<GuildDiscordRoleShadowSummary> = safePublish {
        if (!enabled()) return@safePublish completed(GuildDiscordRoleShadowSummary())
        val guild = guildService.getGuild(guildId)
            ?: return@safePublish completed(GuildDiscordRoleShadowSummary())
        // Match the live DiscordSRV writer: only guilds above the current
        // progression threshold, or with a persisted prior unlock, own a role.
        // A failed progression read must fail this SHADOW sample closed.
        val config = configService.loadConfig().discordGuildRoles
        val legacyLink = repository.get(guild.id)
        val level = progressionRepository.getGuildProgression(guild.id)?.currentLevel ?: guild.level
        if (level < config.minimumLevel && legacyLink == null) {
            return@safePublish completed(GuildDiscordRoleShadowSummary())
        }
        val backend = backendProvider()
            ?: return@safePublish completed(GuildDiscordRoleShadowSummary(failures = 1))
        if (!backend.isAvailable()) {
            return@safePublish completed(GuildDiscordRoleShadowSummary(failures = 1))
        }

        val desired = GuildDiscordRoleDesiredState(
            guildId = guild.id,
            roleName = renderRoleName(config.roleNameFormat, guild.name),
            desiredPlayerIds = memberService.getGuildMembers(guild.id)
                .mapTo(linkedSetOf()) { member -> member.playerId },
        )
        val legacyOwnership = legacyLink?.let { link ->
            GuildDiscordRoleOwnership(
                provider = GuildDiscordRoleProvider.DISCORDSRV,
                providerReference = link.discordRoleId,
            )
        }
        backend.reconcile(desired, legacyOwnership).handle { _, error ->
            if (error == null) {
                GuildDiscordRoleShadowSummary(claimsPublished = 1)
            } else {
                GuildDiscordRoleShadowSummary(failures = 1)
            }
        }
    }

    fun deleteGuild(guildId: UUID): CompletableFuture<GuildDiscordRoleShadowSummary> = safePublish {
        if (!enabled()) return@safePublish completed(GuildDiscordRoleShadowSummary())
        val backend = backendProvider()
            ?: return@safePublish completed(GuildDiscordRoleShadowSummary(failures = 1))
        if (!backend.isAvailable()) {
            return@safePublish completed(GuildDiscordRoleShadowSummary(failures = 1))
        }

        val ownership = GuildDiscordRoleOwnership(
            provider = GuildDiscordRoleProvider.ENTHUSIA,
            providerReference = "guild:$guildId",
        )
        backend.delete(guildId, ownership).handle { result, error ->
            if (error == null && result != GuildDiscordRoleDeleteResult.REJECTED
                && result != GuildDiscordRoleDeleteResult.UNAVAILABLE
            ) {
                GuildDiscordRoleShadowSummary(deleteClaimsPublished = 1)
            } else {
                GuildDiscordRoleShadowSummary(failures = 1)
            }
        }
    }

    /**
     * A read-only Discord role migration probe must never fail an ordinary guild event
     * or leave a periodic scheduler with an uncaught snapshot/provider exception.
     */
    private fun safePublish(
        operation: () -> CompletableFuture<GuildDiscordRoleShadowSummary>,
    ): CompletableFuture<GuildDiscordRoleShadowSummary> =
        try {
            operation()
        } catch (_: RuntimeException) {
            completed(GuildDiscordRoleShadowSummary(failures = 1))
        }

    internal fun renderRoleName(format: String, guildName: String): String =
        format.replace("<guild>", guildName)
            .replace(Regex("[\r\n\t]+"), " ")
            .trim()
            .take(GuildDiscordRoleDesiredState.MAX_ROLE_NAME_LENGTH)
            .ifEmpty { "Guild" }

    private fun combine(
        futures: Collection<CompletableFuture<GuildDiscordRoleShadowSummary>>,
    ): CompletableFuture<GuildDiscordRoleShadowSummary> {
        if (futures.isEmpty()) return completed(GuildDiscordRoleShadowSummary())
        return CompletableFuture.allOf(*futures.toTypedArray()).thenApply {
            futures.fold(GuildDiscordRoleShadowSummary()) { total, future -> total + future.join() }
        }
    }

    private fun <T> completed(value: T): CompletableFuture<T> = CompletableFuture.completedFuture(value)
}
