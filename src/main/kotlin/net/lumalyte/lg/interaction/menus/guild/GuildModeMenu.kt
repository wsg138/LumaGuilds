package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.infrastructure.i18n.GuiTextStyler
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuSurface
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.Instant

/** Guild mode editor on the shared redesign detail surface. */
class GuildModeMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val configService: ConfigService by inject()
    private val warService: WarService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        guild = guildService.getGuild(guild.id) ?: guild
        val config = configService.loadConfig()

        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(MenuSurface.DETAIL, lang.guiTitle("menu.guild_mode.title")),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        addCurrentMode(pane, config.guild.modeSwitchingEnabled)
        if (config.guild.modeSwitchingEnabled) {
            addPeacefulOption(pane)
            addHostileOption(pane)
        } else {
            addDisabledNotice(pane)
        }
        addFooter(pane)

        gui.show(player)
    }

    private fun addCurrentMode(pane: StaticPane, switchingEnabled: Boolean) {
        val item = ItemStack.of(
            if (guild.mode == GuildMode.PEACEFUL) Material.GREEN_BANNER else Material.RED_BANNER,
        )
            .name(lang.gui("menu.guild_mode.current.name"))
            .lore(lang.gui("menu.guild_mode.current.mode", "mode" to modeDisplayName(guild.mode)))
            .lore(lang.gui("menu.guild_mode.current.changed", "time" to (guild.modeChangedAt?.let(::formatTimeAgo) ?: lang.gui("menu.guild_mode.time.never"))))
            .lore(lang.gui("menu.common.blank"))
            .lore(
                if (switchingEnabled) {
                    if (guild.mode == GuildMode.PEACEFUL) lang.gui("menu.guild_mode.peaceful.safe_trading")
                    else lang.gui("menu.guild_mode.hostile.competitive")
                } else {
                    lang.gui("menu.guild_mode.disabled.message")
                },
            )

        pane.addItem(GuiItem(item), 4, 1)
    }

    private fun addPeacefulOption(pane: StaticPane) {
        val mainConfig = configService.loadConfig()
        val config = mainConfig.guild
        val activeWar = warService.getWarsForGuild(guild.id).any { it.isActive }
        val cooldownReady = canSwitchToPeaceful(guild, config.modeSwitchCooldownDays)
        val canSwitch = guild.mode != GuildMode.PEACEFUL && cooldownReady && !activeWar

        val item = ItemStack.of(
            when {
                guild.mode == GuildMode.PEACEFUL -> Material.LIME_STAINED_GLASS_PANE
                canSwitch -> Material.GREEN_WOOL
                else -> Material.GRAY_WOOL
            },
        )
            .name(lang.gui("menu.guild_mode.peaceful.name"))
            .lore(lang.gui("menu.guild_mode.peaceful.benefits"))

        if (mainConfig.claimsEnabled) item.lore(lang.gui("menu.guild_mode.peaceful.no_pvp"))
        item.lore(lang.gui("menu.guild_mode.peaceful.safe_trading"))
            .lore(lang.gui("menu.guild_mode.peaceful.no_wars"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.guild_mode.peaceful.cooldown", "days" to config.modeSwitchCooldownDays))

        when {
            guild.mode == GuildMode.PEACEFUL -> item.lore(lang.gui("menu.guild_mode.current.mode", "mode" to modeDisplayName(guild.mode)))
            activeWar -> item.lore(lang.gui("menu.guild_mode.switch.unavailable"))
                .lore(lang.gui("menu.guild_mode.peaceful.active_war"))
            !cooldownReady -> item.lore(lang.gui("menu.guild_mode.switch.unavailable"))
                .lore(lang.gui("menu.guild_mode.switch.reason", "reason" to getCooldownMessage(guild, config.modeSwitchCooldownDays)))
            else -> item.lore(lang.gui("menu.guild_mode.peaceful.click"))
        }

        pane.addItem(GuiItem(item) {
            when {
                guild.mode == GuildMode.PEACEFUL -> return@GuiItem
                activeWar -> player.sendMessage(lang.msg("menu.guild_mode.feedback.active_war"))
                !cooldownReady -> player.sendMessage(
                    lang.msg(
                        "menu.guild_mode.feedback.blocked",
                        "reason" to getCooldownMessage(guild, config.modeSwitchCooldownDays, styled = false),
                    ),
                )
                guildService.setMode(guild.id, GuildMode.PEACEFUL, player.uniqueId) -> {
                    player.sendMessage(lang.msg("menu.guild_mode.feedback.peaceful_success"))
                    guild = guildService.getGuild(guild.id) ?: guild
                    open()
                }
                else -> player.sendMessage(lang.msg("menu.guild_mode.feedback.failed"))
            }
        }, 2, 3)
    }

    private fun addHostileOption(pane: StaticPane) {
        val config = configService.loadConfig().guild
        val cooldownReady = canSwitchToHostile(guild, config.hostileModeMinimumDays)
        val canSwitch = guild.mode != GuildMode.HOSTILE && cooldownReady

        val item = ItemStack.of(
            when {
                guild.mode == GuildMode.HOSTILE -> Material.RED_STAINED_GLASS_PANE
                canSwitch -> Material.RED_WOOL
                else -> Material.GRAY_WOOL
            },
        )
            .name(lang.gui("menu.guild_mode.hostile.name"))
            .lore(lang.gui("menu.guild_mode.hostile.benefits"))

        if (configService.loadConfig().claimsEnabled) item.lore(lang.gui("menu.guild_mode.hostile.pvp"))
        item.lore(lang.gui("menu.guild_mode.hostile.wars"))
            .lore(lang.gui("menu.guild_mode.hostile.competitive"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.guild_mode.hostile.lock", "days" to config.hostileModeMinimumDays))

        when {
            guild.mode == GuildMode.HOSTILE -> item.lore(lang.gui("menu.guild_mode.current.mode", "mode" to modeDisplayName(guild.mode)))
            !cooldownReady -> item.lore(lang.gui("menu.guild_mode.switch.unavailable"))
                .lore(lang.gui("menu.guild_mode.switch.reason", "reason" to getHostileLockMessage(guild, config.hostileModeMinimumDays)))
            else -> item.lore(lang.gui("menu.guild_mode.hostile.click"))
        }

        pane.addItem(GuiItem(item) {
            when {
                guild.mode == GuildMode.HOSTILE -> return@GuiItem
                !cooldownReady -> player.sendMessage(
                    lang.msg(
                        "menu.guild_mode.feedback.blocked",
                        "reason" to getHostileLockMessage(guild, config.hostileModeMinimumDays, styled = false),
                    ),
                )
                guildService.setMode(guild.id, GuildMode.HOSTILE, player.uniqueId) -> {
                    player.sendMessage(lang.msg("menu.guild_mode.feedback.hostile_success"))
                    guild = guildService.getGuild(guild.id) ?: guild
                    open()
                }
                else -> player.sendMessage(lang.msg("menu.guild_mode.feedback.failed"))
            }
        }, 6, 3)
    }

    private fun addDisabledNotice(pane: StaticPane) {
        val item = ItemStack.of(Material.GRAY_DYE)
            .name(lang.gui("menu.guild_mode.disabled.message"))
            .lore(lang.gui("menu.guild_mode.disabled.description"))
        pane.addItem(GuiItem(item), 4, 3)
    }

    private fun addFooter(pane: StaticPane) {
        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.guild_mode.back.name"))
            .lore(lang.gui("menu.guild_mode.back.description"))
        pane.addItem(GuiItem(back) {
            menuNavigator.openMenu(menuFactory.createGuildSettingsMenu(menuNavigator, player, guild))
        }, 0, 5)

        val home = ItemStack.of(Material.COMPASS).name(lang.gui("menu.control_panel.item.home.name"))
        pane.addItem(GuiItem(home) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    private fun canSwitchToPeaceful(guild: Guild, cooldownDays: Int): Boolean {
        val changedAt = guild.modeChangedAt ?: return true
        return Instant.now().isAfter(changedAt.plus(Duration.ofDays(cooldownDays.toLong())))
    }

    private fun canSwitchToHostile(guild: Guild, minimumDays: Int): Boolean {
        if (guild.mode != GuildMode.PEACEFUL) return true
        val changedAt = guild.modeChangedAt ?: return true
        return Instant.now().isAfter(changedAt.plus(Duration.ofDays(minimumDays.toLong())))
    }

    private fun getCooldownMessage(guild: Guild, cooldownDays: Int, styled: Boolean = true): Component {
        val changedAt = guild.modeChangedAt ?: return renderModeText("menu.guild_mode.cooldown.no_changes", styled = styled)
        val remaining = Duration.between(Instant.now(), changedAt.plus(Duration.ofDays(cooldownDays.toLong())))
        if (remaining.isNegative) return renderModeText("menu.guild_mode.cooldown.expired", styled = styled)
        return renderModeText(
            "menu.guild_mode.cooldown.peaceful",
            "days" to remaining.toDays(),
            "hours" to remaining.toHours() % 24,
            styled = styled,
        )
    }

    private fun getHostileLockMessage(guild: Guild, minimumDays: Int, styled: Boolean = true): Component {
        val changedAt = guild.modeChangedAt ?: return renderModeText("menu.guild_mode.cooldown.no_changes", styled = styled)
        val remaining = Duration.between(Instant.now(), changedAt.plus(Duration.ofDays(minimumDays.toLong())))
        if (remaining.isNegative) return renderModeText("menu.guild_mode.cooldown.lock_expired", styled = styled)
        return renderModeText(
            "menu.guild_mode.cooldown.hostile",
            "days" to remaining.toDays(),
            "hours" to remaining.toHours() % 24,
            styled = styled,
        )
    }

    private fun formatTimeAgo(instant: Instant): Component {
        val duration = Duration.between(instant, Instant.now())
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        return when {
            days > 0 -> lang.gui("menu.guild_mode.time.days_ago", "days" to days, "hours" to hours)
            hours > 0 -> lang.gui("menu.guild_mode.time.hours_ago", "hours" to hours)
            else -> lang.gui("menu.guild_mode.time.recently")
        }
    }

    private fun modeDisplayName(mode: GuildMode): Component = when (mode) {
        GuildMode.PEACEFUL -> lang.gui("menu.guild_mode.mode.peaceful")
        GuildMode.HOSTILE -> lang.gui("menu.guild_mode.mode.hostile")
    }

    private fun renderModeText(
        key: String,
        vararg placeholders: Pair<String, Any?>,
        styled: Boolean,
    ): Component {
        val component = lang.msg(key, *placeholders)
        return if (styled) GuiTextStyler.style(component) else component
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
