package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuSurface
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Leaving a guild uses the same predictable danger template as every destructive action. */
class GuildLeaveConfirmationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
) : Menu, KoinComponent {

    private val memberService: MemberService by inject()
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(
                MenuSurface.DANGER,
                lang.guiTitle("menu.guild_confirmation.leave.title", "guild" to guild.name),
            ),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val info = ItemStack.of(Material.OAK_DOOR)
            .name(lang.gui("menu.guild_confirmation.leave.item.info.name"))
            .lore(lang.gui("menu.guild_confirmation.leave.item.info.lore.guild", "guild" to guild.name))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.guild_confirmation.leave.item.info.lore.warning"))
            .lore(lang.gui("menu.guild_confirmation.leave.item.info.lore.bank"))
            .lore(lang.gui("menu.guild_confirmation.leave.item.info.lore.homes"))
            .lore(lang.gui("menu.guild_confirmation.leave.item.info.lore.chat"))
            .lore(lang.gui("menu.guild_confirmation.leave.item.info.lore.permissions"))
        pane.addItem(GuiItem(info), 4, 1)

        val cancel = ItemStack.of(Material.LIME_CONCRETE)
            .name(lang.gui("menu.guild_confirmation.common.cancel.name"))
            .lore(lang.gui("menu.guild_confirmation.common.cancel.lore"))
        pane.addItem(GuiItem(cancel) { menuNavigator.goBack() }, 2, 4)

        val confirm = ItemStack.of(Material.RED_CONCRETE)
            .name(lang.gui("menu.guild_confirmation.leave.item.confirm.name"))
            .lore(lang.gui("menu.guild_confirmation.leave.item.confirm.lore"))
        pane.addItem(GuiItem(confirm) {
            if (memberService.removeMember(player.uniqueId, guild.id, player.uniqueId)) {
                player.sendMessage(lang.msg("menu.guild_confirmation.leave.feedback.success", "guild" to guild.name))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f)
                menuNavigator.clearMenuStack()
                player.closeInventory()
            } else {
                player.sendMessage(lang.msg("menu.guild_confirmation.leave.feedback.failure"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }, 6, 4)

        gui.show(player)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
