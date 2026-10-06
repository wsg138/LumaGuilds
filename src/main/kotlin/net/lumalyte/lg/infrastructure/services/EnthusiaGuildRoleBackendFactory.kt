package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.GuildDiscordRoleBackend
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin

/**
 * Avoids eagerly linking EnthusiaStaff API classes while LumaGuilds is still in its STARTUP phase.
 */
object EnthusiaGuildRoleBackendFactory {
    @Volatile
    private var cached: GuildDiscordRoleBackend? = null

    @Volatile
    private var cachedPlugin: Plugin? = null

    fun current(): GuildDiscordRoleBackend? {
        val staffPlugin = Bukkit.getPluginManager().getPlugin("EnthusiaStaff")
        if (staffPlugin == null || !staffPlugin.isEnabled) {
            synchronized(this) {
                cached = null
                cachedPlugin = null
            }
            return null
        }

        val currentCached = cached
        if (cachedPlugin === staffPlugin && currentCached != null) return currentCached

        return synchronized(this) {
            if (cachedPlugin !== staffPlugin) {
                cached = null
                cachedPlugin = staffPlugin
            }
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
