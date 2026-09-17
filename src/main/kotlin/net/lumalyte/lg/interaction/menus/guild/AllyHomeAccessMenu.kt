package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.RelationType
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

/** Dense ally-access list for the guild ally home. */
class AllyHomeAccessMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
) : Menu, KoinComponent {

    private val rankService: RankService by inject()
    private val guildService: GuildService by inject()
    private val relationService: RelationService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)) {
            player.sendMessage(lang.msg("menu.ally_home_access.permission_denied"))
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
            return
        }

        val current = guildService.getGuild(guild.id) ?: return
        val allies = relationService.getGuildRelationsByType(guild.id, RelationType.ALLY)
            .asSequence()
            .filter { it.isActive() }
            .mapNotNull { relation ->
                val otherId = relation.getOtherGuild(guild.id)
                guildService.getGuild(otherId)?.let { otherId to it.name }
            }
            .sortedBy { it.second.lowercase() }
            .toList()
        val allowed = current.allyHomeAllowedGuilds.toMutableSet()

        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(MenuSurface.LIST, lang.guiTitle("menu.ally_home_access.title")),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val info = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.ally_home_access.info.name"))
            .lore(lang.gui("menu.ally_home_access.info.description"))
            .lore(lang.gui("menu.ally_home_access.info.instructions"))
        pane.addItem(GuiItem(info), 4, 0)

        if (allies.isEmpty()) {
            val empty = ItemStack.of(Material.GRAY_DYE)
                .name(lang.gui("menu.ally_home_access.info.name"))
                .lore(lang.gui("menu.ally_home_access.info.description"))
            pane.addItem(GuiItem(empty), 4, 2)
        } else {
            allies.take(36).forEachIndexed { index, (allyId, allyName) ->
                val enabled = allyId in allowed
                val item = ItemStack.of(if (enabled) Material.LIME_DYE else Material.GRAY_DYE)
                    .name(
                        if (enabled) lang.gui("menu.ally_home_access.ally.allowed", "guild" to allyName)
                        else lang.gui("menu.ally_home_access.ally.denied", "guild" to allyName),
                    )
                    .lore(lang.gui("menu.ally_home_access.ally.toggle"))
                pane.addItem(GuiItem(item) {
                    if (enabled) allowed.remove(allyId) else allowed.add(allyId)
                    guildService.setAllyHomeAllowedGuilds(guild.id, allowed.toSet(), player.uniqueId)
                    open()
                }, index % 9, 1 + index / 9)
            }
        }

        val back = ItemStack.of(Material.ARROW).name(lang.gui("menu.ally_home_access.back"))
        pane.addItem(GuiItem(back) {
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
        }, 0, 5)

        val home = ItemStack.of(Material.COMPASS).name(lang.gui("menu.control_panel.item.home.name"))
        pane.addItem(GuiItem(home) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)

        gui.show(player)
    }
}
