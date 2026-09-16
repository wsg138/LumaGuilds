package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.GuiTheme
import net.lumalyte.lg.utils.MenuItemBuilder
import net.lumalyte.lg.utils.MenuSurface
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Focused system-settings screen.
 *
 * Progression, appearance, homes and members deliberately do not live here anymore. Those systems
 * have their own player-intent sections from Guild Home. This screen only contains guild-wide
 * switches plus explicit navigation to customization and destructive account-level actions.
 */
@Suppress("UNUSED_PARAMETER")
class GuildSettingsMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private val guildService: GuildService,
    menuItemBuilder: MenuItemBuilder,
    private val menuFactory: MenuFactory,
    private val configService: ConfigService,
    progressionService: ProgressionService,
    progressionRepository: ProgressionRepository,
) : Menu, KoinComponent {

    private val lang: LangService by inject()

    override fun open() {
        guild = guildService.getGuild(guild.id) ?: guild

        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(
                MenuSurface.GRID,
                lang.guiTitle("menu.guild_settings.title", "guild" to guild.name),
            ),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        addIdentity(pane)
        addAccess(pane)
        addTracking(pane)
        addMode(pane)
        addTheme(pane)
        addSectionLinks(pane)
        addDangerActions(pane)
        addFooter(pane)

        gui.show(player)
    }

    private fun addIdentity(pane: StaticPane) {
        val item = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.guild_settings.item.name.name"))
            .lore(lang.gui("menu.guild_settings.item.name.lore.current", "guild" to guild.name))
            .lore(lang.gui("menu.common.blank"))
            .lore(Component.text("Owner, creation date and public guild details.", NamedTextColor.GRAY))
            .lore(Component.text("Click to view guild information", NamedTextColor.AQUA))

        pane.addItem(GuiItem(item) {
            menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, guild))
        }, 1, 1)
    }

    private fun addAccess(pane: StaticPane) {
        val canManage = canManageSettings()
        val item = ItemStack.of(
            when {
                !canManage -> Material.GRAY_DYE
                guild.isOpen -> Material.LIME_DYE
                else -> Material.RED_DYE
            },
        )
            .name(lang.gui("menu.guild_settings.item.access.name"))
            .lore(
                if (guild.isOpen) {
                    lang.gui("menu.guild_settings.item.access.lore.current.open")
                } else {
                    lang.gui("menu.guild_settings.item.access.lore.current.closed")
                },
            )
            .lore(lang.gui("menu.common.blank"))
            .lore(
                if (canManage) {
                    lang.gui("menu.guild_settings.item.access.lore.action")
                } else {
                    lang.gui("menu.guild_settings.item.description.lore.locked")
                },
            )

        pane.addItem(GuiItem(item) {
            if (!canManage) {
                player.sendMessage(lang.msg("menu.guild_settings.feedback.no_access_permission"))
                player.sendMessage(lang.msg("menu.guild_settings.feedback.settings_requirement"))
                return@GuiItem
            }

            val newValue = !guild.isOpen
            if (guildService.setOpen(guild.id, newValue, player.uniqueId)) {
                guild = guild.copy(isOpen = newValue)
                player.sendMessage(
                    if (newValue) lang.msg("menu.guild_settings.feedback.access_open")
                    else lang.msg("menu.guild_settings.feedback.access_closed"),
                )
                open()
            } else {
                player.sendMessage(lang.msg("menu.guild_settings.feedback.access_failure"))
            }
        }, 3, 1)
    }

    private fun addTracking(pane: StaticPane) {
        val canManage = canManageSettings()
        val item = ItemStack.of(
            when {
                !canManage -> Material.GRAY_DYE
                guild.trackingEnabled -> Material.RECOVERY_COMPASS
                else -> Material.COMPASS
            },
        )
            .name(lang.gui("menu.guild_settings.item.tracking.name"))
            .lore(
                if (guild.trackingEnabled) {
                    lang.gui("menu.guild_settings.item.tracking.lore.current.enabled")
                } else {
                    lang.gui("menu.guild_settings.item.tracking.lore.current.disabled")
                },
            )
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.guild_settings.item.tracking.lore.hud"))
            .lore(
                if (canManage) {
                    lang.gui("menu.guild_settings.item.tracking.lore.action")
                } else {
                    lang.gui("menu.guild_settings.item.description.lore.locked")
                },
            )

        pane.addItem(GuiItem(item) {
            if (!canManage) {
                player.sendMessage(lang.msg("menu.guild_settings.feedback.no_settings_permission"))
                player.sendMessage(lang.msg("menu.guild_settings.feedback.settings_requirement"))
                return@GuiItem
            }

            val newValue = !guild.trackingEnabled
            if (guildService.setTrackingEnabled(guild.id, newValue, player.uniqueId)) {
                guild = guild.copy(trackingEnabled = newValue)
                player.sendMessage(
                    if (newValue) lang.msg("menu.guild_settings.feedback.tracking_enabled")
                    else lang.msg("menu.guild_settings.feedback.tracking_disabled"),
                )
                open()
            } else {
                player.sendMessage(lang.msg("menu.guild_settings.feedback.tracking_failure"))
            }
        }, 5, 1)
    }

    private fun addMode(pane: StaticPane) {
        val enabled = configService.loadConfig().guild.peacefulModeEnabled
        val item = ItemStack.of(
            when {
                !enabled -> Material.GRAY_WOOL
                guild.mode == GuildMode.PEACEFUL -> Material.GREEN_WOOL
                else -> Material.RED_WOOL
            },
        )
            .name(lang.gui("menu.guild_settings.item.mode.name"))
            .lore(
                if (guild.mode == GuildMode.PEACEFUL) {
                    lang.gui("menu.guild_settings.item.mode.lore.current.peaceful")
                } else {
                    lang.gui("menu.guild_settings.item.mode.lore.current.hostile")
                },
            )
            .lore(lang.gui("menu.common.blank"))
            .lore(
                if (enabled) lang.gui("menu.guild_settings.item.mode.lore.action")
                else lang.gui("menu.guild_settings.item.mode.lore.disabled"),
            )

        pane.addItem(GuiItem(item) {
            if (enabled) {
                menuNavigator.openMenu(menuFactory.createGuildModeMenu(menuNavigator, player, guild))
            }
        }, 7, 1)
    }

    private fun addTheme(pane: StaticPane) {
        val canManage = canManageSettings()
        val item = ItemStack.of(if (canManage) Material.PAINTING else Material.GRAY_DYE)
            .name(lang.gui("menu.guild_settings.item.theme.name"))
            .lore(lang.gui("menu.guild_settings.item.theme.lore.current", "theme" to guild.guiTheme.displayName))
            .lore(lang.gui("menu.common.blank"))
            .lore(
                if (canManage) lang.gui("menu.guild_settings.item.theme.lore.action")
                else lang.gui("menu.guild_settings.item.description.lore.locked"),
            )

        pane.addItem(GuiItem(item) {
            if (!canManage) {
                player.sendMessage(lang.msg("menu.guild_settings.feedback.no_settings_permission"))
                return@GuiItem
            }

            val themes = GuiTheme.entries
            val currentIndex = themes.indexOf(guild.guiTheme).coerceAtLeast(0)
            val next = themes[(currentIndex + 1) % themes.size]
            guildService.setGuiTheme(guild.id, next, player.uniqueId)
            guild = guild.copy(guiTheme = next)
            player.sendMessage(lang.msg("menu.guild_settings.feedback.theme_changed", "theme" to next.displayName))
            open()
        }, 2, 3)
    }

    private fun addSectionLinks(pane: StaticPane) {
        val customize = ItemStack.of(Material.WHITE_BANNER)
            .name(Component.text("Customize Guild", NamedTextColor.WHITE))
            .lore(Component.text("Description, tag, emoji and banner.", NamedTextColor.GRAY))
            .lore(Component.text("Click to open", NamedTextColor.AQUA))
        pane.addItem(GuiItem(customize) {
            menuNavigator.openMenu(
                GuildRedesignSectionMenu(menuNavigator, player, guild, GuildRedesignSectionMenu.Section.CUSTOMIZE),
            )
        }, 4, 3)

        val joining = ItemStack.of(Material.OAK_SIGN)
            .name(Component.text("Members & Joining", NamedTextColor.WHITE))
            .lore(Component.text("Invites, ranks, members and staffing actions.", NamedTextColor.GRAY))
            .lore(Component.text("Click to open", NamedTextColor.AQUA))
        pane.addItem(GuiItem(joining) {
            menuNavigator.openMenu(
                GuildRedesignSectionMenu(menuNavigator, player, guild, GuildRedesignSectionMenu.Section.MEMBERS),
            )
        }, 6, 3)
    }

    private fun addDangerActions(pane: StaticPane) {
        val leave = ItemStack.of(Material.OAK_DOOR)
            .name(Component.text("Leave Guild", NamedTextColor.RED))
            .lore(Component.text("Requires confirmation before anything changes.", NamedTextColor.GRAY))
        pane.addItem(GuiItem(leave) {
            menuNavigator.openMenu(menuFactory.createGuildLeaveConfirmationMenu(menuNavigator, player, guild))
        }, 3, 4)

        val disband = ItemStack.of(Material.TNT)
            .name(Component.text("Disband Guild", NamedTextColor.DARK_RED))
            .lore(Component.text("Permanently deletes the guild.", NamedTextColor.RED))
            .lore(Component.text("Requires explicit confirmation.", NamedTextColor.GRAY))
        pane.addItem(GuiItem(disband) {
            menuNavigator.openMenu(menuFactory.createGuildDisbandConfirmationMenu(menuNavigator, player, guild))
        }, 5, 4)
    }

    private fun addFooter(pane: StaticPane) {
        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.common.item.back.name"))
            .lore(Component.text("Return to Guild Home", NamedTextColor.GRAY))
        pane.addItem(GuiItem(back) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 0, 5)

        val home = ItemStack.of(Material.COMPASS)
            .name(Component.text("Guild Home", NamedTextColor.AQUA))
            .lore(Component.text("Open the main guild dashboard", NamedTextColor.GRAY))
        pane.addItem(GuiItem(home) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    private fun canManageSettings(): Boolean =
        guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_GUILD_SETTINGS)

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
