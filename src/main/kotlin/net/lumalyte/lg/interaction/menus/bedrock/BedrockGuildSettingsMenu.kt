package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.GuildSettingsMenu
import org.bukkit.entity.Player
import java.util.logging.Logger

/** Bedrock follows the same redesigned settings hierarchy as Java. */
class BedrockGuildSettingsMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    @Suppress("UNUSED_PARAMETER") private val logger: Logger,
) : Menu {
    override fun open() {
        GuildSettingsMenu(menuNavigator, player, guild).open()
    }
}
