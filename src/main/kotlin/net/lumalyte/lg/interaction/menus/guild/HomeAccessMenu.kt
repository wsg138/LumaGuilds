package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
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

/** Rank-access editor for one guild home on the shared dense-list surface. */
class HomeAccessMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val homeName: String,
) : Menu, KoinComponent {

    private val rankService: RankService by inject()
    private val guildService: GuildService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)) {
            player.sendMessage(lang.msg("menu.home_access.permission_denied"))
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
            return
        }

        val home = guildService.getHome(guild.id, homeName)
        if (home == null) {
            player.sendMessage(lang.msg("menu.home_access.missing", "home" to homeName))
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
            return
        }

        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(
                MenuSurface.LIST,
                lang.guiTitle("menu.home_access.title", "home" to homeName),
            ),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val homeInfo = ItemStack.of(Material.RECOVERY_COMPASS)
            .name(lang.gui("menu.home_access.title", "home" to homeName))
        pane.addItem(GuiItem(homeInfo), 4, 0)

        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        val ownerRank = rankService.getHighestRank(guild.id)
        val allowed = home.allowedRankIds.toMutableSet()

        ranks.take(36).forEachIndexed { index, rank ->
            val isOwner = rank.id == ownerRank?.id
            val enabled = isOwner || rank.id in allowed
            val item = ItemStack.of(
                when {
                    isOwner -> Material.NETHER_STAR
                    enabled -> Material.LIME_DYE
                    else -> Material.GRAY_DYE
                },
            ).name(
                if (enabled) lang.gui("menu.home_access.rank.allowed", "rank" to rank.name)
                else lang.gui("menu.home_access.rank.denied", "rank" to rank.name),
            )
                .lore(lang.gui("menu.home_access.rank.priority", "priority" to rank.priority))
                .lore(
                    when {
                        isOwner -> lang.gui("menu.home_access.rank.owner")
                        enabled -> lang.gui("menu.home_access.rank.revoke")
                        else -> lang.gui("menu.home_access.rank.grant")
                    },
                )
            pane.addItem(GuiItem(item) {
                if (isOwner) return@GuiItem
                if (enabled) allowed.remove(rank.id) else allowed.add(rank.id)
                guildService.setHomeAllowedRanks(guild.id, homeName, allowed.toSet(), player.uniqueId)
                open()
            }, index % 9, 1 + index / 9)
        }

        val back = ItemStack.of(Material.ARROW).name(lang.gui("menu.home_access.back"))
        pane.addItem(GuiItem(back) {
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
        }, 0, 5)

        val dashboard = ItemStack.of(Material.COMPASS).name(lang.gui("menu.control_panel.item.home.name"))
        pane.addItem(GuiItem(dashboard) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)

        gui.show(player)
    }
}
