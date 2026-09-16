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

/** A contextual detail page for one guild member. */
class GuildMemberProfileMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val member: Member,
) : Menu, KoinComponent {
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        val targetName = Bukkit.getOfflinePlayer(member.playerId).name
            ?: lang.raw("menu.guild_confirmation.common.unknown_player")
        val gui = ChestGui(6, MenuTitleBuilder.redesign(MenuSurface.DETAIL, targetName))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val rank = rankService.getRank(member.rankId)
        val head = ItemStack.of(Material.PLAYER_HEAD)
        head.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile().uuid(member.playerId).build())
        head.name(lang.gui("menu.member_management.item.member.name", "player" to targetName))
            .lore(lang.gui("menu.member_management.item.member.lore.player", "player" to targetName))
            .lore(lang.gui("menu.member_management.item.member.lore.joined", "joined" to member.joinedAt))
        rank?.let { head.lore(lang.gui("menu.rank_management.item.rank.name", "rank" to it.name)) }
        pane.addItem(GuiItem(head), 4, 1)

        val canManage = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)

        val rankItem = ItemStack.of(if (canManage) Material.GOLDEN_HELMET else Material.GRAY_DYE)
            .name(lang.gui("menu.member_management.item.rank_change.name"))
            .lore(lang.gui("menu.member_management.item.rank_change.lore.description"))
            .lore(lang.gui("menu.member_management.item.rank_change.lore.requirement"))
        pane.addItem(GuiItem(rankItem) {
            if (canManage) {
                menuNavigator.openMenu(menuFactory.createGuildMemberRankMenu(menuNavigator, player, guild, member))
            } else {
                player.sendMessage(lang.msg("menu.member_management.feedback.no_rank_permission"))
            }
        }, 2, 3)

        val kickItem = ItemStack.of(if (canManage) Material.IRON_BOOTS else Material.GRAY_DYE)
            .name(lang.gui("menu.member_management.item.kick.name"))
            .lore(lang.gui("menu.member_management.item.kick.lore.description"))
            .lore(lang.gui("menu.member_management.item.kick.lore.requirement"))
        pane.addItem(GuiItem(kickItem) {
            if (canManage) {
                menuNavigator.openMenu(menuFactory.createGuildKickConfirmationMenu(menuNavigator, player, guild, member))
            } else {
                player.sendMessage(lang.msg("menu.member_management.feedback.no_kick_permission"))
            }
        }, 6, 3)

        val back = ItemStack.of(Material.ARROW).name(lang.gui("menu.member_management.item.back.name"))
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 0, 5)

        val home = ItemStack.of(Material.COMPASS).name(lang.gui("menu.member_management.item.back.name"))
        pane.addItem(GuiItem(home) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.permission_category.action.cancel.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
        gui.show(player)
    }
}
