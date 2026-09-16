package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
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

/** Permanent guild deletion uses the shared stripped-down danger template. */
class GuildDisbandConfirmationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val lang: LangService by inject()

    override fun open() {
        if (!guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RANKS)) {
            player.sendMessage(lang.msg("menu.guild_confirmation.disband.feedback.no_permission"))
            menuNavigator.goBack()
            return
        }

        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(
                MenuSurface.DANGER,
                lang.guiTitle("menu.guild_confirmation.disband.title", "guild" to guild.name),
            ),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val info = ItemStack.of(Material.TNT)
            .name(lang.gui("menu.guild_confirmation.disband.item.info.name"))
            .lore(lang.gui("menu.guild_confirmation.disband.item.info.lore.guild", "guild" to guild.name))
            .lore(lang.gui("menu.guild_confirmation.disband.item.info.lore.level", "level" to guild.level))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.guild_confirmation.disband.item.info.lore.irreversible"))
            .lore(lang.gui("menu.guild_confirmation.disband.item.info.lore.vault_loss"))
        pane.addItem(GuiItem(info), 4, 1)

        val cancel = ItemStack.of(Material.LIME_CONCRETE)
            .name(lang.gui("menu.guild_confirmation.common.cancel.name"))
            .lore(lang.gui("menu.guild_confirmation.common.cancel.lore"))
        pane.addItem(GuiItem(cancel) { menuNavigator.goBack() }, 2, 4)

        val confirm = ItemStack.of(Material.RED_CONCRETE)
            .name(lang.gui("menu.guild_confirmation.disband.item.confirm.name"))
            .lore(lang.gui("menu.guild_confirmation.disband.item.confirm.lore"))
        pane.addItem(GuiItem(confirm) {
            if (guildService.disbandGuild(guild.id, player.uniqueId)) {
                player.sendMessage(lang.msg("menu.guild_confirmation.disband.feedback.success", "guild" to guild.name))
                player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f)
                menuNavigator.clearMenuStack()
                player.closeInventory()
            } else {
                player.sendMessage(lang.msg("menu.guild_confirmation.disband.feedback.failure"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }, 6, 4)

        gui.show(player)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
