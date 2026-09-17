package net.lumalyte.lg.domain.entities

import net.lumalyte.lg.domain.values.Position3D
import net.lumalyte.lg.utils.GuiTheme
import java.time.Instant
import java.util.UUID

/**
 * Represents a guild in the system.
 *
 * @property id The unique identifier for the guild.
 * @property name The name of the guild.
 * @property banner The banner ItemStack data serialized as string for the guild.
 * @property emoji The Nexo emoji placeholder for the guild tag (e.g., ":catsmileysmile:").
 * @property tag The custom display tag for the guild (supports MiniMessage formatting).
 * @property description The description of the guild.
 * @property homes The home locations of the guild.
 * @property level The current level of the guild.
 * @property bankBalance The current bank balance of the guild (for virtual economy mode).
 * @property mode The current mode of the guild (Peaceful or Hostile).
 * @property modeChangedAt The timestamp when the mode was last changed.
 * @property createdAt The timestamp when the guild was created.
 * @property vaultChestLocation The location of the physical guild vault chest.
 * @property vaultStatus The status of the guild vault.
 * @property isOpen Whether the guild is open for anyone to join (true) or invite-only (false).
 * @property joinFeeEnabled Whether the guild requires a join fee for players joining via LFG.
 * @property joinFeeAmount The amount of currency required to join this guild via LFG (0 if no fee).
 * @property trackingEnabled Whether Lunar Client location tracking (Apollo teams/waypoints) is enabled for this guild.
 * @property bankFrozen Whether the guild bank is emergency-frozen (all deposits and withdrawals blocked).
 * @property bannermanEnabled Whether members of this guild render the guild banner on their backs.
 */
data class Guild(
    val id: UUID,
    val name: String,
    val banner: String? = null,
    val emoji: String? = null,
    val tag: String? = null,
    val description: String? = null,
    val homes: GuildHomes = GuildHomes.EMPTY,
    val level: Int = 1,
    val bankBalance: Int = 0,
    val mode: GuildMode = GuildMode.HOSTILE,
    val modeChangedAt: Instant? = null,
    val createdAt: Instant,
    val vaultChestLocation: GuildVaultLocation? = null,
    val vaultStatus: VaultStatus = VaultStatus.NEVER_PLACED,
    val vaultLocked: Boolean = false,
    val isOpen: Boolean = false,
    val joinFeeEnabled: Boolean = false,
    val joinFeeAmount: Int = 0,
    val trackingEnabled: Boolean = true,
    val bankFrozen: Boolean = false,
    val bannermanEnabled: Boolean = false,
    val allyHome: GuildHome? = null,
    val allyHomeAllowedGuilds: Set<UUID> = emptySet(),
    val guiTheme: GuiTheme = GuiTheme.NEUTRAL
) {
    init {
        require(name.length in 1..32) { "Guild name must be between 1 and 32 characters." }
        require(level > 0) { "Guild level must be positive." }
        require(bankBalance >= 0) { "Guild bank balance cannot be negative." }
        require(joinFeeAmount >= 0) { "Join fee amount cannot be negative." }

        emoji?.let { emojiValue ->
            require(emojiValue.startsWith(":") && emojiValue.endsWith(":") && emojiValue.length > 2) {
                "Guild emoji must be a valid Nexo placeholder format (e.g., ':catsmileysmile:')"
            }
        }

        description?.let { descValue ->
            require(descValue.length <= 100) {
                "Guild description must be 100 characters or less."
            }
        }
    }

    /**
     * Gets the default/main home for backward compatibility.
     * @deprecated Use homes.defaultHome instead for new code.
     */
    val home: GuildHome?
        get() = homes.defaultHome
}

/** Represents the home location of a guild. */
data class GuildHome(
    val worldId: UUID,
    val position: Position3D,
    val allowedRankIds: Set<UUID> = emptySet()
)

/** Represents multiple home locations for a guild with names/identifiers. */
data class GuildHomes(
    val homes: Map<String, GuildHome> = emptyMap()
) {
    val defaultHome: GuildHome?
        get() = homes["main"] ?: homes.values.firstOrNull()

    val homeNames: Set<String>
        get() = homes.keys

    fun getHome(name: String): GuildHome? = homes[name]

    fun withHome(name: String, home: GuildHome): GuildHomes {
        val newHomes = homes.toMutableMap()
        newHomes[name] = home
        return GuildHomes(newHomes)
    }

    fun withoutHome(name: String): GuildHomes {
        val newHomes = homes.toMutableMap()
        newHomes.remove(name)
        return GuildHomes(newHomes)
    }

    fun hasHomes(): Boolean = homes.isNotEmpty()

    /** Mirrors collection semantics for UI/state checks. */
    fun isEmpty(): Boolean = homes.isEmpty()

    val size: Int
        get() = homes.size

    companion object {
        val EMPTY = GuildHomes(emptyMap())
    }
}

enum class GuildMode {
    PEACEFUL,
    HOSTILE
}

data class GuildVaultLocation(
    val worldId: UUID,
    val x: Int,
    val y: Int,
    val z: Int
)

enum class VaultStatus {
    AVAILABLE,
    UNAVAILABLE,
    NEVER_PLACED
}
