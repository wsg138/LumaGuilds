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
 * Bedrock entry point for the redesigned guild UI.
 *
 * Bedrock intentionally receives the same inventory UI and the same feature tree as Java instead
 * of a reduced SimpleForm mirror. Geyser handles the inventory interaction itself; Nexo
 * Scaffolding can translate the Nexo custom GUI background/glyphs and item visuals for Bedrock.
 * If the custom resource-pack translation is unavailable, every feature remains usable through
 * the same inventory with the normal vanilla fallback items.
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
