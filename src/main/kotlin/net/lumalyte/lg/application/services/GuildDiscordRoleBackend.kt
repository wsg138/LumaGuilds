package net.lumalyte.lg.application.services

import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** Explicit owner of one guild-role transport during the DiscordSRV migration. */
enum class GuildDiscordRoleProvider(val configValue: String) {
    DISCORDSRV("discordsrv"),
    ENTHUSIA("enthusia");

    companion object {
        fun parse(raw: String): GuildDiscordRoleProvider? {
            val normalized = raw.trim().lowercase(Locale.ROOT)
            return entries.firstOrNull { it.configValue == normalized }
        }
    }
}

/** Durable provider ownership. The optional reference is opaque outside that provider. */
data class GuildDiscordRoleOwnership(
    val provider: GuildDiscordRoleProvider,
    val providerReference: String?,
) {
    init {
        require(providerReference == null || providerReference.isNotBlank()) {
            "Discord guild-role provider reference cannot be blank"
        }
        require(providerReference == null || providerReference.length <= MAX_PROVIDER_REFERENCE_LENGTH) {
            "Discord guild-role provider reference is too long"
        }
        require(providerReference == null || providerReference.none(Char::isISOControl)) {
            "Discord guild-role provider reference cannot contain control characters"
        }
    }

    companion object {
        const val MAX_PROVIDER_REFERENCE_LENGTH = 128
    }
}

/** Complete desired state for one guild-owned Discord role. */
class GuildDiscordRoleDesiredState(
    val guildId: UUID,
    val roleName: String,
    desiredPlayerIds: Set<UUID>,
) {
    val desiredPlayerIds: Set<UUID> = desiredPlayerIds.toSet()

    init {
        require(roleName.isNotBlank()) { "Discord guild-role name cannot be blank" }
        require(roleName.length <= MAX_ROLE_NAME_LENGTH) { "Discord guild-role name is too long" }
        require(roleName.none(Char::isISOControl)) {
            "Discord guild-role name cannot contain control characters"
        }
        require(this.desiredPlayerIds.size <= MAX_DESIRED_PLAYERS) {
            "Discord guild-role desired player set is too large"
        }
    }

    companion object {
        const val MAX_ROLE_NAME_LENGTH = 100
        const val MAX_DESIRED_PLAYERS = 10_000
    }
}

enum class GuildDiscordRoleDeleteResult {
    DELETED,
    ABSENT,
    RETRY_SCHEDULED,
    REJECTED,
    UNAVAILABLE,
}

data class GuildDiscordRoleReconcileResult(
    val ownership: GuildDiscordRoleOwnership,
    val created: Boolean,
    val memberRolesApplied: Int,
    val memberRolesRemoved: Int,
    val skippedMembers: Int,
) {
    init {
        require(memberRolesApplied >= 0) { "Applied member-role count cannot be negative" }
        require(memberRolesRemoved >= 0) { "Removed member-role count cannot be negative" }
        require(skippedMembers >= 0) { "Skipped member count cannot be negative" }
    }
}

/**
 * High-level provider boundary for guild Discord roles.
 *
 * Implementations receive a complete membership snapshot. They must never infer guild policy,
 * and provider-specific Discord identities must not escape through this interface.
 */
interface GuildDiscordRoleBackend {
    val provider: GuildDiscordRoleProvider

    fun isAvailable(): Boolean

    fun reconcile(
        desiredState: GuildDiscordRoleDesiredState,
        currentOwnership: GuildDiscordRoleOwnership?,
    ): CompletableFuture<GuildDiscordRoleReconcileResult>

    fun delete(
        guildId: UUID,
        currentOwnership: GuildDiscordRoleOwnership,
    ): CompletableFuture<GuildDiscordRoleDeleteResult>
}
