package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.api.events.GuildCreatedEvent
import net.lumalyte.lg.api.events.GuildDisbandedEvent
import net.lumalyte.lg.api.events.GuildMemberJoinEvent
import net.lumalyte.lg.api.events.GuildMemberRemovedEvent
import net.lumalyte.lg.api.events.GuildRenamedEvent
import net.lumalyte.lg.application.services.GuildDiscordRoleShadowPublisher
import net.lumalyte.lg.application.services.GuildDiscordRoleShadowSummary
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Migration-only dual-publish runtime.
 *
 * Existing DiscordSRV reconciliation remains authoritative for mutations; this runtime only
 * publishes complete desired membership snapshots to EnthusiaStaff.
 */
class GuildDiscordRoleShadowRuntime(
    private val plugin: LumaGuilds,
    private val publisher: GuildDiscordRoleShadowPublisher,
) : Listener, AutoCloseable {
    private val started = AtomicBoolean()
    private var periodicTask: BukkitTask? = null

    fun start() {
        if (!publisher.enabled() || !started.compareAndSet(false, true)) return
        plugin.server.pluginManager.registerEvents(this, plugin)
        periodicTask = plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable { observe("periodic", publisher.reconcileAll()) },
            INITIAL_DELAY_TICKS,
            PERIOD_TICKS,
        )
        plugin.logger.info(
            "Enthusia guild-role shadow publication enabled; DiscordSRV remains authoritative for mutations.",
        )
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onMemberJoin(event: GuildMemberJoinEvent) = publish(event.guildId, "member join")

    @EventHandler(priority = EventPriority.MONITOR)
    fun onMemberRemoved(event: GuildMemberRemovedEvent) = publish(event.guildId, "member removal")

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildCreated(event: GuildCreatedEvent) = publish(event.guild.id, "guild create")

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildRenamed(event: GuildRenamedEvent) = publish(event.guildId, "guild rename")

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGuildDisbanded(event: GuildDisbandedEvent) {
        observe("guild disband guild=${event.guild.id}", publisher.deleteGuild(event.guild.id))
    }

    private fun publish(guildId: UUID, operation: String) {
        observe("$operation guild=$guildId", publisher.reconcileGuild(guildId))
    }

    private fun observe(
        operation: String,
        future: CompletableFuture<GuildDiscordRoleShadowSummary>,
    ) {
        future.whenComplete { result, error ->
            when {
                error != null ->
                    plugin.logger.warning("Enthusia guild-role shadow publication failed for $operation: ${error.message}")
                result.failures > 0 ->
                    plugin.logger.warning(
                        "Enthusia guild-role shadow publication reported ${result.failures} failure(s) for $operation",
                    )
            }
        }
    }

    override fun close() {
        periodicTask?.cancel()
        periodicTask = null
        started.set(false)
    }

    private companion object {
        const val INITIAL_DELAY_TICKS = 20L * 15L
        const val PERIOD_TICKS = 20L * 60L * 5L
    }
}
