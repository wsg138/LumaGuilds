package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
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

/** Dense rank hierarchy. Detailed permissions live on the selected rank, not in list-item lore. */
class GuildRankManagementMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
) : Menu, KoinComponent {
    private val rankService: RankService by inject()
    private val memberService: MemberService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    private var currentPage = 0
    private val ranksPerPage = 36

    override fun open() {
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RANKS)) {
            player.sendMessage(lang.msg("menu.rank_management.feedback.no_permission"))
            player.sendMessage(lang.msg("menu.rank_management.feedback.required_permission"))
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
            return
        }

        val gui = ChestGui(6, MenuTitleBuilder.redesign(
            MenuSurface.LIST,
            lang.guiTitle("menu.rank_management.title", "guild" to guild.name),
        ))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        val totalPages = maxOf(1, (ranks.size + ranksPerPage - 1) / ranksPerPage)
        currentPage = currentPage.coerceIn(0, totalPages - 1)

        val members = ItemStack.of(Material.PLAYER_HEAD)
            .name(lang.gui("menu.member_management.item.rank_change.name"))
            .lore(lang.gui("menu.member_management.item.rank_change.lore.description"))
        pane.addItem(GuiItem(members) {
            menuNavigator.openMenu(menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild))
        }, 0, 0)

        val create = ItemStack.of(Material.EMERALD)
            .name(lang.gui("menu.rank_management.item.create.name"))
            .lore(lang.gui("menu.rank_management.item.create.lore.description"))
            .lore(lang.gui("menu.rank_management.item.create.lore.limit"))
        pane.addItem(GuiItem(create) {
            menuNavigator.openMenu(menuFactory.createRankCreationMenu(menuNavigator, player, guild))
        }, 8, 0)

        ranks.drop(currentPage * ranksPerPage).take(ranksPerPage).forEachIndexed { index, rank ->
            addRankButton(pane, rank, index % 9, 1 + index / 9)
        }

        addFooter(pane, totalPages, ranks.size)
        gui.show(player)
    }

    private fun addRankButton(pane: StaticPane, rank: Rank, x: Int, y: Int) {
        val material = runCatching { rank.icon?.let(Material::valueOf) }.getOrNull() ?: when {
            rank.priority == 0 -> Material.NETHERITE_HELMET
            rank.permissions.size >= 20 -> Material.DIAMOND_HELMET
            rank.permissions.size >= 10 -> Material.GOLDEN_HELMET
            else -> Material.IRON_HELMET
        }
        val item = ItemStack.of(material)
            .name(lang.gui("menu.rank_management.item.rank.name", "rank" to rank.name))
            .lore(lang.gui("menu.rank_management.item.rank.lore.priority", "priority" to rank.priority))
            .lore(lang.gui("menu.rank_management.item.rank.lore.members", "count" to memberService.getMembersByRank(guild.id, rank.id).size))
            .lore(lang.gui("menu.rank_management.item.rank.lore.permissions"))
            .lore(lang.gui("menu.permission_category.status.count", "enabled" to rank.permissions.size, "total" to RankPermission.entries.size))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.rank_management.item.rank.lore.action"))
        pane.addItem(GuiItem(item) {
            menuNavigator.openMenu(menuFactory.createRankEditMenu(menuNavigator, player, guild, rank))
        }, x, y)
    }

    private fun addFooter(pane: StaticPane, totalPages: Int, totalRanks: Int) {
        val back = ItemStack.of(Material.ARROW).name(lang.gui("menu.rank_management.item.back.name"))
        pane.addItem(GuiItem(back) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 0, 5)

        if (currentPage > 0) {
            val previous = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.rank_management.item.previous.name"))
                .lore(lang.gui("menu.rank_management.item.pagination.lore", "current" to currentPage + 1, "total" to totalPages))
            pane.addItem(GuiItem(previous) { currentPage--; open() }, 3, 5)
        }

        val page = ItemStack.of(Material.PAPER)
            .name(lang.gui("menu.rank_management.item.page.name", "current" to currentPage + 1, "total" to totalPages))
            .lore(lang.gui("menu.rank_management.item.page.lore", "count" to totalRanks))
        pane.addItem(GuiItem(page), 4, 5)

        if (currentPage < totalPages - 1) {
            val next = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.rank_management.item.next.name"))
                .lore(lang.gui("menu.rank_management.item.pagination.lore", "current" to currentPage + 1, "total" to totalPages))
            pane.addItem(GuiItem(next) { currentPage++; open() }, 5, 5)
        }

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.permission_category.action.cancel.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
