package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.GuildDiscordRoleBackend
import org.bukkit.Bukkit

/**
 * Avoids eagerly linking EnthusiaStaff API classes while LumaGuilds is still in its STARTUP phase.
 */
object EnthusiaGuildRoleBackendFactory {
    @Volatile
    private var cached: GuildDiscordRoleBackend? = null

    fun current(): GuildDiscordRoleBackend? {
        cached?.let { return it }
        if (!Bukkit.getPluginManager().isPluginEnabled("EnthusiaStaff")) return null
        return synchronized(this) {
            cached ?: instantiate()?.also { cached = it }
        }
    }

    private fun instantiate(): GuildDiscordRoleBackend? =
        try {
            val type = Class.forName(
                "net.lumalyte.lg.infrastructure.services.EnthusiaGuildRoleBackend",
                true,
                EnthusiaGuildRoleBackendFactory::class.java.classLoader,
            )
            type.getDeclaredConstructor().newInstance() as GuildDiscordRoleBackend
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: LinkageError) {
            null
        }
}
