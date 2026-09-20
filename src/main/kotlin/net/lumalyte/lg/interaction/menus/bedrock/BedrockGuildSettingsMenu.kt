package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.GuildSettingsMenu
import net.lumalyte.lg.utils.MenuItemBuilder
import org.bukkit.entity.Player
import org.koin.core.context.GlobalContext
import java.util.logging.Logger

/** Bedrock follows the same redesigned settings hierarchy as Java. */
class BedrockGuildSettingsMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    @Suppress("UNUSED_PARAMETER") private val logger: Logger,
) : Menu {
    override fun open() {
        val koin = GlobalContext.get()
        GuildSettingsMenu(
            menuNavigator = menuNavigator,
            player = player,
            guild = guild,
            guildService = koin.get<GuildService>(),
            menuItemBuilder = koin.get<MenuItemBuilder>(),
            menuFactory = koin.get<MenuFactory>(),
            configService = koin.get<ConfigService>(),
            progressionService = koin.get<ProgressionService>(),
            progressionRepository = koin.get<ProgressionRepository>(),
        ).open()
    }
}
