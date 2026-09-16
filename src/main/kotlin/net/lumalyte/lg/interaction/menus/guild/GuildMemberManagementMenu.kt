package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
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
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Dense guild roster. Member-specific actions live on the selected member's detail page instead of
 * pretending that footer buttons can act on an unspecified player.
 */
class GuildMemberManagementMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
) : Menu, KoinComponent {
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    private var currentPage = 0
    private val itemsPerPage = 45

    override fun open() {
        val gui = ChestGui(6, MenuTitleBuilder.redesign(
            MenuSurface.LIST,
            lang.guiTitle("menu.member_management.title", "guild" to guild.name),
        ))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val members = memberService.getGuildMembers(guild.id).sortedWith(
            compareBy<Member>({ rankService.getRank(it.rankId)?.priority ?: Int.MAX_VALUE }, {
                Bukkit.getOfflinePlayer(it.playerId).name ?: ""
            })
        )
        val totalPages = maxOf(1, (members.size + itemsPerPage - 1) / itemsPerPage)
        currentPage = currentPage.coerceIn(0, totalPages - 1)

        members.drop(currentPage * itemsPerPage).take(itemsPerPage).forEachIndexed { index, member ->
            val x = index % 9
            val y = index / 9
            pane.addItem(GuiItem(createMemberHead(member)) {
                menuNavigator.openMenu(GuildMemberProfileMenu(menuNavigator, player, guild, member))
            }, x, y)
        }

        addFooter(pane, totalPages)
        gui.show(player)
    }

    private fun createMemberHead(member: Member): ItemStack {
        val playerName = Bukkit.getOfflinePlayer(member.playerId).name
            ?: lang.raw("menu.guild_confirmation.common.unknown_player")
        val rank = rankService.getRank(member.rankId)
        val head = ItemStack.of(Material.PLAYER_HEAD)
        head.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile().uuid(member.playerId).build())
        head.name(lang.gui("menu.member_management.item.member.name", "player" to playerName))
            .lore(rank?.let { lang.gui("menu.rank_management.item.rank.name", "rank" to it.name) }
                ?: lang.gui("menu.member_management.item.member.lore.player", "player" to playerName))
            .lore(lang.gui("menu.member_management.item.member.lore.joined", "joined" to member.joinedAt))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.member_management.item.member.lore.action"))
        return head
    }

    private fun addFooter(pane: StaticPane, totalPages: Int) {
        if (currentPage > 0) {
            val previous = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.member_management.item.previous.name"))
                .lore(lang.gui("menu.member_management.item.previous.lore"))
            pane.addItem(GuiItem(previous) { currentPage--; open() }, 0, 5)
        }

        val canManage = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)
        val invite = ItemStack.of(if (canManage) Material.LIME_DYE else Material.GRAY_DYE)
            .name(lang.gui("menu.member_management.item.invite.name"))
            .lore(lang.gui("menu.member_management.item.invite.lore.description"))
            .lore(lang.gui("menu.member_management.item.invite.lore.requirement"))
        pane.addItem(GuiItem(invite) {
            if (canManage) menuNavigator.openMenu(menuFactory.createGuildInviteMenu(menuNavigator, player, guild))
            else player.sendMessage(lang.msg("menu.member_management.feedback.no_invite_permission"))
        }, 2, 5)

        val page = ItemStack.of(Material.PAPER)
            .name(lang.gui("menu.member_management.item.page.name", "current" to currentPage + 1, "total" to totalPages))
            .lore(lang.gui("menu.member_management.item.page.lore"))
        pane.addItem(GuiItem(page), 4, 5)

        val ranks = ItemStack.of(Material.GOLDEN_HELMET)
            .name(lang.gui("menu.member_management.item.rank_change.name"))
            .lore(lang.gui("menu.member_management.item.rank_change.lore.description"))
        pane.addItem(GuiItem(ranks) {
            menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
        }, 6, 5)

        if (currentPage < totalPages - 1) {
            val next = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.member_management.item.next.name"))
                .lore(lang.gui("menu.member_management.item.next.lore"))
            pane.addItem(GuiItem(next) { currentPage++; open() }, 8, 5)
        } else {
            val back = ItemStack.of(Material.BARRIER)
                .name(lang.gui("menu.member_management.item.back.name"))
                .lore(lang.gui("menu.member_management.item.back.lore"))
            pane.addItem(GuiItem(back) {
                menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
            }, 8, 5)
        }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
