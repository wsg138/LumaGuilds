package net.lumalyte.lg.infrastructure.services

import net.enthusia.discord.platform.api.DiscordPlatformAvailability
import net.enthusia.discord.platform.api.ManagedRoleClaim
import net.enthusia.discord.platform.api.ManagedRoleClient
import net.enthusia.discord.platform.api.ManagedRoleDeleteResult
import net.enthusia.discord.platform.api.ManagedRoleKey
import net.enthusia.discord.platform.api.ManagedRoleNamespace
import net.enthusia.discord.platform.api.ManagedRolePlatform
import net.enthusia.discord.platform.api.ManagedRoleReconcileStatus
import net.lumalyte.lg.application.services.GuildDiscordRoleBackend
import net.lumalyte.lg.application.services.GuildDiscordRoleDeleteResult
import net.lumalyte.lg.application.services.GuildDiscordRoleDesiredState
import net.lumalyte.lg.application.services.GuildDiscordRoleOwnership
import net.lumalyte.lg.application.services.GuildDiscordRoleProvider
import net.lumalyte.lg.application.services.GuildDiscordRoleReconcileResult
import org.bukkit.Bukkit
import java.util.Optional
import java.util.concurrent.CompletableFuture

/**
 * Provider-neutral Enthusia managed-role adapter used only for shadow publication.
 *
 * This class is loaded lazily after EnthusiaStaff is enabled because LumaGuilds is a STARTUP
 * plugin while EnthusiaStaff currently enables POSTWORLD.
 */
class EnthusiaGuildRoleBackend(
    private val platformProvider: () -> ManagedRolePlatform? = {
        Bukkit.getServicesManager().load(ManagedRolePlatform::class.java)
    },
) : GuildDiscordRoleBackend {
    override val provider: GuildDiscordRoleProvider = GuildDiscordRoleProvider.ENTHUSIA

    override fun isAvailable(): Boolean =
        client()?.availability()?.let { it != DiscordPlatformAvailability.UNAVAILABLE } ?: false

    override fun reconcile(
        desiredState: GuildDiscordRoleDesiredState,
        currentOwnership: GuildDiscordRoleOwnership?,
    ): CompletableFuture<GuildDiscordRoleReconcileResult> {
        val client = client() ?: return failed("Enthusia managed-role platform is unavailable")
        val key = key(desiredState.guildId.toString())
        return client.reconcile(
            ManagedRoleClaim(
                key,
                desiredState.roleName,
                existingDiscordRoleId(currentOwnership),
                desiredState.desiredPlayerIds,
            ),
        ).toCompletableFuture().thenCompose { result ->
            when (result.status()) {
                ManagedRoleReconcileStatus.APPLIED,
                ManagedRoleReconcileStatus.UNCHANGED,
                ManagedRoleReconcileStatus.RETRY_SCHEDULED -> CompletableFuture.completedFuture(
                    GuildDiscordRoleReconcileResult(
                        ownership = ownership(key.localKey()),
                        created = false,
                        memberRolesApplied = 0,
                        memberRolesRemoved = 0,
                        skippedMembers = 0,
                    ),
                )
                ManagedRoleReconcileStatus.REJECTED ->
                    failed("Enthusia managed-role claim was rejected")
                ManagedRoleReconcileStatus.UNAVAILABLE ->
                    failed("Enthusia managed-role platform became unavailable")
            }
        }
    }

    override fun delete(
        guildId: java.util.UUID,
        currentOwnership: GuildDiscordRoleOwnership,
    ): CompletableFuture<GuildDiscordRoleDeleteResult> {
        if (currentOwnership.provider != provider) {
            return CompletableFuture.completedFuture(GuildDiscordRoleDeleteResult.REJECTED)
        }
        val client = client() ?: return CompletableFuture.completedFuture(GuildDiscordRoleDeleteResult.UNAVAILABLE)
        return client.delete(key(guildId.toString())).toCompletableFuture().thenApply { result ->
            when (result) {
                ManagedRoleDeleteResult.DELETED -> GuildDiscordRoleDeleteResult.DELETED
                ManagedRoleDeleteResult.ABSENT -> GuildDiscordRoleDeleteResult.ABSENT
                ManagedRoleDeleteResult.RETRY_SCHEDULED -> GuildDiscordRoleDeleteResult.RETRY_SCHEDULED
                ManagedRoleDeleteResult.REJECTED -> GuildDiscordRoleDeleteResult.REJECTED
                ManagedRoleDeleteResult.UNAVAILABLE -> GuildDiscordRoleDeleteResult.UNAVAILABLE
            }
        }
    }

    private fun existingDiscordRoleId(
        currentOwnership: GuildDiscordRoleOwnership?,
    ): Optional<String> =
        if (currentOwnership?.provider == GuildDiscordRoleProvider.DISCORDSRV) {
            Optional.ofNullable(currentOwnership.providerReference)
        } else {
            Optional.empty()
        }

    private fun client(): ManagedRoleClient? {
        val platform = platformProvider() ?: return null
        if (platform.apiVersion() != ManagedRolePlatform.API_VERSION) return null
        return platform.clientFor(NAMESPACE).orElse(null)
    }

    private fun key(guildId: String): ManagedRoleKey =
        ManagedRoleKey(NAMESPACE, "guild:$guildId")

    private fun ownership(localKey: String) =
        GuildDiscordRoleOwnership(provider, localKey)

    private fun <T> failed(message: String): CompletableFuture<T> =
        CompletableFuture<T>().also { it.completeExceptionally(IllegalStateException(message)) }

    private companion object {
        val NAMESPACE = ManagedRoleNamespace("luma-guilds")
    }
}
