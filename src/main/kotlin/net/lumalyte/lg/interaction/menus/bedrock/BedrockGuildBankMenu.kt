package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.GuildBankMenu
import org.bukkit.entity.Player
import java.util.logging.Logger

/**
 * Bedrock uses the same redesigned bank inventory as Java.
 * Geyser provides the inventory interaction and Nexo Scaffolding translates the custom visuals.
 */
class BedrockGuildBankMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    @Suppress("UNUSED_PARAMETER") private val logger: Logger,
) : Menu {
    override fun open() {
        GuildBankMenu(menuNavigator, player, guild).open()
    }
}
