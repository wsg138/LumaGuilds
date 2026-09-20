package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
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

class GuildKickConfirmationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val memberToKick: Member,
) : Menu, KoinComponent {

    private val memberService: MemberService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(
                MenuSurface.DANGER,
                lang.guiTitle("menu.guild_confirmation.kick.title", "guild" to guild.name),
            ),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val playerName = Bukkit.getOfflinePlayer(memberToKick.playerId).name
            ?: lang.raw("menu.guild_confirmation.common.unknown_player")
        val head = ItemStack.of(Material.PLAYER_HEAD)
        head.setData(
            DataComponentTypes.PROFILE,
            ResolvableProfile.resolvableProfile().uuid(memberToKick.playerId).build(),
        )
        head.name(lang.gui("menu.guild_confirmation.kick.item.player.name", "player" to playerName))
            .lore(lang.gui("menu.guild_confirmation.kick.item.player.lore.player", "player" to playerName))
            .lore(lang.gui("menu.guild_confirmation.kick.item.player.lore.joined", "joined" to memberToKick.joinedAt))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.player.lore.result"))
        pane.addItem(GuiItem(head), 4, 1)

        val warning = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.guild_confirmation.kick.item.warning.name"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.warning.lore.irreversible"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.warning.lore.loss_header"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.warning.lore.bank"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.warning.lore.claims"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.warning.lore.permissions"))
        pane.addItem(GuiItem(warning), 4, 2)

        val cancel = ItemStack.of(Material.LIME_CONCRETE)
            .name(lang.gui("menu.guild_confirmation.kick.item.cancel.name"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.cancel.lore.line_1"))
        pane.addItem(GuiItem(cancel) { menuNavigator.goBack() }, 2, 4)

        val confirm = ItemStack.of(Material.RED_CONCRETE)
            .name(lang.gui("menu.guild_confirmation.kick.item.confirm.name"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.confirm.lore.line_1"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.confirm.lore.line_2"))
        pane.addItem(GuiItem(confirm) { performKick(playerName) }, 6, 4)

        gui.show(player)
    }

    private fun performKick(targetName: String) {
        val targetPlayer = Bukkit.getPlayer(memberToKick.playerId)
        if (memberService.removeMember(memberToKick.playerId, guild.id, player.uniqueId)) {
            player.sendMessage(
                lang.msg("menu.guild_confirmation.kick.feedback.success", "player" to targetName, "guild" to guild.name),
            )
            targetPlayer?.sendMessage(
                lang.msg("menu.guild_confirmation.kick.feedback.target", "guild" to guild.name, "player" to player.name),
            )
            menuNavigator.openMenu(menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild))
        } else {
            player.sendMessage(lang.msg("menu.guild_confirmation.kick.feedback.failure", "player" to targetName))
            menuNavigator.goBack()
        }
    }

    override fun passData(data: Any?) = Unit
}
