package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.GuildDashboard
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock entry point for the focused Guild Home preview.
 *
 * Bedrock receives the same inventory and feature grouping as Java. Geyser handles inventory
 * interaction; Nexo Scaffolding is expected to translate the custom background and item visuals.
 */
class BedrockGuildControlPanelMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    @Suppress("UNUSED_PARAMETER") private val logger: Logger,
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val rankService: RankService by inject()
    private val memberService: MemberService by inject()
    private val menuFactory: MenuFactory by inject()

    override fun open() {
        GuildDashboard(
            menuNavigator = menuNavigator,
            player = player,
            guild = guild,
            guildService = guildService,
            rankService = rankService,
            memberService = memberService,
            menuFactory = menuFactory,
        ).open()
    }
}
