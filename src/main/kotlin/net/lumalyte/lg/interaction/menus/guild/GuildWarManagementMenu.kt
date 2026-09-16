package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.War
import net.lumalyte.lg.domain.entities.WarDeclaration
import net.lumalyte.lg.domain.entities.WarStatus
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuSurface
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Warfare organized as hub -> list -> detail, with no chat-only destinations. */
class GuildWarManagementMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
) : Menu, KoinComponent {

    private val warService: WarService by inject()
    private val guildService: GuildService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    private var activePage = 0
    private var incomingPage = 0
    private var outgoingPage = 0
    private var historyPage = 0
    private val pageSize = 36

    override fun open() {
        val allWars = warService.getWarsForGuild(guild.id)
        val active = allWars.filter { it.isActive }
        val incoming = warService.getPendingDeclarationsForGuild(guild.id)
        val outgoing = warService.getDeclarationsByGuild(guild.id).filter { it.isValid }

        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(
                MenuSurface.GRID,
                lang.guiTitle("menu.guild_war_management.title", "guild" to guild.name),
            ),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val activeItem = ItemStack.of(if (active.isEmpty()) Material.GRAY_DYE else Material.DIAMOND_SWORD)
            .name(lang.gui("menu.guild_war_management.quick_stats.active", "count" to active.size))
            .lore(
                if (active.isEmpty()) lang.gui("menu.guild_war_management.current.none.description")
                else lang.gui("menu.guild_war_management.current.more.description"),
            )
        pane.addItem(GuiItem(activeItem) {
            activePage = 0
            openActiveWars()
        }, 1, 1)

        val incomingItem = ItemStack.of(if (incoming.isEmpty()) Material.GRAY_DYE else Material.PAPER)
            .name(lang.gui("menu.guild_war_management.declarations.incoming.name"))
            .lore(lang.gui("menu.guild_war_management.declarations.count", "count" to incoming.size))
        pane.addItem(GuiItem(incomingItem) {
            incomingPage = 0
            openIncomingDeclarations()
        }, 3, 1)

        val outgoingItem = ItemStack.of(if (outgoing.isEmpty()) Material.GRAY_DYE else Material.WRITABLE_BOOK)
            .name(lang.gui("menu.guild_war_management.declarations.outgoing.name"))
            .lore(lang.gui("menu.guild_war_management.declarations.count", "count" to outgoing.size))
        pane.addItem(GuiItem(outgoingItem) {
            outgoingPage = 0
            openOutgoingDeclarations()
        }, 5, 1)

        val declare = ItemStack.of(Material.IRON_SWORD)
            .name(lang.gui("menu.guild_war_management.actions.declare.name"))
            .lore(lang.gui("menu.guild_war_management.actions.declare.description"))
        pane.addItem(GuiItem(declare) {
            menuNavigator.openMenu(menuFactory.createGuildWarDeclarationMenu(menuNavigator, player, guild))
        }, 7, 1)

        val history = ItemStack.of(Material.BOOKSHELF)
            .name(lang.gui("menu.guild_war_management.actions.history.name"))
            .lore(lang.gui("menu.guild_war_management.actions.history.description"))
        pane.addItem(GuiItem(history) {
            historyPage = 0
            openHistory()
        }, 2, 3)

        val stats = ItemStack.of(Material.TOTEM_OF_UNDYING)
            .name(lang.gui("menu.guild_war_management.actions.statistics.name"))
            .lore(lang.gui("menu.guild_war_management.quick_stats.total", "count" to allWars.size))
            .lore(lang.gui("menu.guild_war_management.quick_stats.ratio", "ratio" to String.format("%.2f", warService.getWinLossRatio(guild.id))))
        pane.addItem(GuiItem(stats) { openStatistics() }, 4, 3)

        val peace = ItemStack.of(Material.WHITE_WOOL)
            .name(lang.gui("menu.guild_war_management.actions.peace.name"))
            .lore(lang.gui("menu.guild_war_management.actions.peace.description"))
        pane.addItem(GuiItem(peace) {
            menuNavigator.openMenu(menuFactory.createPeaceAgreementMenu(menuNavigator, player, guild))
        }, 6, 3)

        addHubFooter(pane)
        gui.show(player)
    }

    private fun openActiveWars() {
        val wars = warService.getWarsForGuild(guild.id).filter { it.isActive }
        val gui = listGui("menu.guild_war_management.war_list.title")
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)
        val totalPages = maxOf(1, (wars.size + pageSize - 1) / pageSize)
        activePage = activePage.coerceIn(0, totalPages - 1)

        if (wars.isEmpty()) {
            val empty = ItemStack.of(Material.GREEN_BANNER)
                .name(lang.gui("menu.guild_war_management.war_list.empty.name"))
                .lore(lang.gui("menu.guild_war_management.war_list.empty.description"))
            pane.addItem(GuiItem(empty), 4, 2)
        } else {
            wars.drop(activePage * pageSize).take(pageSize).forEachIndexed { index, war ->
                val enemyName = enemyName(war)
                val stats = warService.getWarStats(war.id)
                val declaring = war.declaringGuildId == guild.id
                val kills = if (declaring) stats.declaringGuildKills else stats.defendingGuildKills
                val deaths = if (declaring) stats.declaringGuildDeaths else stats.defendingGuildDeaths
                val item = ItemStack.of(Material.DIAMOND_SWORD)
                    .name(lang.gui("menu.guild_war_management.war_list.item.name", "enemy" to enemyName))
                    .lore(lang.gui("menu.guild_war_management.war_list.item.lore.duration", "days" to war.duration.toDays()))
                    .lore(lang.gui("menu.guild_war_management.war_list.item.lore.kills", "kills" to kills))
                    .lore(lang.gui("menu.guild_war_management.war_list.item.lore.deaths", "deaths" to deaths))
                    .lore(lang.gui("menu.guild_war_management.war_list.item.lore.click"))
                pane.addItem(GuiItem(item) { openWarDetail(war) }, index % 9, 1 + index / 9)
            }
        }
        addListFooter(pane, activePage, totalPages, { activePage--; openActiveWars() }, { activePage++; openActiveWars() })
        gui.show(player)
    }

    private fun openIncomingDeclarations() {
        val declarations = warService.getPendingDeclarationsForGuild(guild.id)
        val gui = listGui("menu.guild_war_management.incoming_declarations.title")
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)
        val totalPages = maxOf(1, (declarations.size + pageSize - 1) / pageSize)
        incomingPage = incomingPage.coerceIn(0, totalPages - 1)

        declarations.drop(incomingPage * pageSize).take(pageSize).forEachIndexed { index, declaration ->
            val enemy = guildService.getGuild(declaration.declaringGuildId)?.name
                ?: lang.raw("menu.guild_war_management.fallback.unknown")
            val item = ItemStack.of(if (declaration.isValid) Material.PAPER else Material.GRAY_DYE)
                .name(
                    if (declaration.isValid) {
                        lang.gui("menu.guild_war_management.incoming_declarations.item.name", "enemy" to enemy)
                    } else {
                        lang.gui("menu.guild_war_management.incoming_declarations.item.expired.name", "enemy" to enemy)
                    },
                )
            if (declaration.isValid) {
                item.lore(lang.gui("menu.guild_war_management.incoming_declarations.item.lore.duration", "days" to declaration.proposedDuration.toDays()))
                    .lore(lang.gui("menu.guild_war_management.incoming_declarations.item.lore.expires", "hours" to declaration.remainingTime.toHours()))
                    .lore(lang.gui("menu.guild_war_management.incoming_declarations.item.lore.accept"))
                    .lore(lang.gui("menu.guild_war_management.incoming_declarations.item.lore.reject"))
            }
            pane.addItem(GuiItem(item) { event ->
                if (!declaration.isValid) return@GuiItem
                when (event.click) {
                    ClickType.LEFT -> {
                        if (warService.acceptWarDeclaration(declaration.id, player.uniqueId) != null) {
                            player.sendMessage(lang.msg("menu.guild_war_management.incoming_declarations.accepted"))
                        } else {
                            player.sendMessage(lang.msg("menu.guild_war_management.feedback.action_failed"))
                        }
                    }
                    ClickType.RIGHT -> {
                        if (warService.rejectWarDeclaration(declaration.id, player.uniqueId)) {
                            player.sendMessage(lang.msg("menu.guild_war_management.incoming_declarations.rejected"))
                        } else {
                            player.sendMessage(lang.msg("menu.guild_war_management.feedback.action_failed"))
                        }
                    }
                    else -> return@GuiItem
                }
                openIncomingDeclarations()
            }, index % 9, 1 + index / 9)
        }
        if (declarations.isEmpty()) {
            val empty = ItemStack.of(Material.GREEN_DYE)
                .name(lang.gui("menu.guild_war_management.incoming_declarations.empty.name"))
                .lore(lang.gui("menu.guild_war_management.incoming_declarations.empty.description"))
            pane.addItem(GuiItem(empty), 4, 2)
        }
        addListFooter(pane, incomingPage, totalPages, { incomingPage--; openIncomingDeclarations() }, { incomingPage++; openIncomingDeclarations() })
        gui.show(player)
    }

    private fun openOutgoingDeclarations() {
        val declarations = warService.getDeclarationsByGuild(guild.id).filter { it.isValid }
        val gui = listGui("menu.guild_war_management.outgoing_declarations.title")
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)
        val totalPages = maxOf(1, (declarations.size + pageSize - 1) / pageSize)
        outgoingPage = outgoingPage.coerceIn(0, totalPages - 1)

        declarations.drop(outgoingPage * pageSize).take(pageSize).forEachIndexed { index, declaration ->
            val enemy = guildService.getGuild(declaration.defendingGuildId)?.name
                ?: lang.raw("menu.guild_war_management.fallback.unknown")
            val item = ItemStack.of(Material.WRITABLE_BOOK)
                .name(lang.gui("menu.guild_war_management.outgoing_declarations.item.name", "enemy" to enemy))
                .lore(lang.gui("menu.guild_war_management.outgoing_declarations.item.lore.duration", "days" to declaration.proposedDuration.toDays()))
                .lore(lang.gui("menu.guild_war_management.outgoing_declarations.item.lore.expires", "hours" to declaration.remainingTime.toHours()))
                .lore(lang.gui("menu.guild_war_management.outgoing_declarations.item.lore.cancel"))
            pane.addItem(GuiItem(item) {
                if (warService.cancelWarDeclaration(declaration.id, player.uniqueId)) {
                    player.sendMessage(lang.msg("menu.guild_war_management.outgoing_declarations.cancelled"))
                } else {
                    player.sendMessage(lang.msg("menu.guild_war_management.feedback.action_failed"))
                }
                openOutgoingDeclarations()
            }, index % 9, 1 + index / 9)
        }
        if (declarations.isEmpty()) {
            val empty = ItemStack.of(Material.YELLOW_DYE)
                .name(lang.gui("menu.guild_war_management.outgoing_declarations.empty.name"))
                .lore(lang.gui("menu.guild_war_management.outgoing_declarations.empty.description"))
            pane.addItem(GuiItem(empty), 4, 2)
        }
        addListFooter(pane, outgoingPage, totalPages, { outgoingPage--; openOutgoingDeclarations() }, { outgoingPage++; openOutgoingDeclarations() })
        gui.show(player)
    }

    private fun openHistory() {
        val wars = warService.getWarHistory(guild.id, 100)
        val gui = listGui("menu.guild_war_management.war_history.title")
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)
        val totalPages = maxOf(1, (wars.size + pageSize - 1) / pageSize)
        historyPage = historyPage.coerceIn(0, totalPages - 1)

        wars.drop(historyPage * pageSize).take(pageSize).forEachIndexed { index, war ->
            val key = when {
                war.winner == guild.id -> "menu.guild_war_management.war_history.item.won"
                war.loser == guild.id -> "menu.guild_war_management.war_history.item.lost"
                war.isEnded -> "menu.guild_war_management.war_history.item.draw"
                else -> "menu.guild_war_management.war_history.item.ended"
            }
            val material = when {
                war.winner == guild.id -> Material.LIME_DYE
                war.loser == guild.id -> Material.RED_DYE
                war.isEnded -> Material.YELLOW_DYE
                else -> Material.GRAY_DYE
            }
            val item = ItemStack.of(material)
                .name(lang.gui(key, "enemy" to enemyName(war)))
                .lore(lang.gui("menu.guild_war_management.war_history.item.duration", "days" to war.duration.toDays()))
                .lore(lang.gui("menu.guild_war_management.war_history.item.date", "date" to (war.endedAt?.let(dateFormatter::format) ?: "---")))
            pane.addItem(GuiItem(item) { openWarDetail(war) }, index % 9, 1 + index / 9)
        }
        if (wars.isEmpty()) {
            val empty = ItemStack.of(Material.GRAY_DYE)
                .name(lang.gui("menu.guild_war_management.war_history.empty.name"))
                .lore(lang.gui("menu.guild_war_management.war_history.empty.description"))
            pane.addItem(GuiItem(empty), 4, 2)
        }
        addListFooter(pane, historyPage, totalPages, { historyPage--; openHistory() }, { historyPage++; openHistory() })
        gui.show(player)
    }

    private fun openWarDetail(war: War) {
        val enemyId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
        val enemy = enemyName(war)
        val stats = warService.getWarStats(war.id)
        val declaring = war.declaringGuildId == guild.id

        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(
                MenuSurface.DETAIL,
                lang.guiTitle("menu.guild_war_management.war_details.title", "enemy" to enemy),
            ),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val info = ItemStack.of(Material.DIAMOND_SWORD)
            .name(lang.gui("menu.guild_war_management.war_details.info.enemy", "enemy" to enemy))
            .lore(lang.gui("menu.guild_war_management.war_details.info.duration", "days" to war.duration.toDays()))
            .lore(
                lang.gui(
                    when (war.status) {
                        WarStatus.ACTIVE -> "menu.guild_war_management.war_details.info.status_active"
                        WarStatus.ENDED -> "menu.guild_war_management.war_details.info.status_ended"
                        WarStatus.DECLARED -> "menu.guild_war_management.war_details.info.status_declared"
                        WarStatus.CANCELLED -> "menu.guild_war_management.war_details.info.status_cancelled"
                    },
                ),
            )
        if (war.isActive) {
            info.lore(lang.gui("menu.guild_war_management.war_details.info.remaining", "days" to (war.remainingDuration?.toDays() ?: 0)))
        }
        pane.addItem(GuiItem(info), 4, 0)

        war.objectives.take(9).forEachIndexed { index, objective ->
            val item = ItemStack.of(if (objective.isCompleted) Material.LIME_DYE else Material.RED_DYE)
                .name(
                    lang.gui(
                        if (objective.isCompleted) "menu.guild_war_management.war_details.objectives.completed"
                        else "menu.guild_war_management.war_details.objectives.entry",
                        "type" to objective.type.name,
                        "current" to objective.currentValue,
                        "target" to objective.targetValue,
                    ),
                )
            pane.addItem(GuiItem(item), index, 2)
        }

        val yourKills = if (declaring) stats.declaringGuildKills else stats.defendingGuildKills
        val yourDeaths = if (declaring) stats.declaringGuildDeaths else stats.defendingGuildDeaths
        val yourKdr = if (declaring) stats.declaringKillRatio else stats.defendingKillRatio
        val enemyKills = if (declaring) stats.defendingGuildKills else stats.declaringGuildKills
        val enemyDeaths = if (declaring) stats.defendingGuildDeaths else stats.declaringGuildDeaths
        val enemyKdr = if (declaring) stats.defendingKillRatio else stats.declaringKillRatio

        val yours = ItemStack.of(Material.DIAMOND_CHESTPLATE)
            .name(lang.gui("menu.guild_war_management.war_details.stats.your_kills", "kills" to yourKills))
            .lore(lang.gui("menu.guild_war_management.war_details.stats.your_deaths", "deaths" to yourDeaths))
            .lore(lang.gui("menu.guild_war_management.war_details.stats.your_kdr", "ratio" to String.format("%.2f", yourKdr)))
        pane.addItem(GuiItem(yours), 2, 3)

        val theirs = ItemStack.of(Material.IRON_CHESTPLATE)
            .name(lang.gui("menu.guild_war_management.war_details.stats.enemy_kills", "enemy" to enemy, "kills" to enemyKills))
            .lore(lang.gui("menu.guild_war_management.war_details.stats.enemy_deaths", "enemy" to enemy, "deaths" to enemyDeaths))
            .lore(lang.gui("menu.guild_war_management.war_details.stats.enemy_kdr", "enemy" to enemy, "ratio" to String.format("%.2f", enemyKdr)))
        pane.addItem(GuiItem(theirs), 6, 3)

        if (war.isActive) {
            val peace = ItemStack.of(Material.WHITE_WOOL)
                .name(lang.gui("menu.guild_war_management.war_details.actions.propose_peace.name"))
                .lore(lang.gui("menu.guild_war_management.war_details.actions.propose_peace.lore"))
            pane.addItem(GuiItem(peace) {
                menuNavigator.openMenu(menuFactory.createPeaceAgreementMenu(menuNavigator, player, guild))
            }, 3, 4)

            val surrender = ItemStack.of(Material.RED_CONCRETE)
                .name(lang.gui("menu.guild_war_management.war_details.actions.surrender.name"))
                .lore(lang.gui("menu.guild_war_management.war_details.actions.surrender.lore"))
            pane.addItem(GuiItem(surrender) {
                if (warService.endWar(war.id, enemyId, null, player.uniqueId)) {
                    player.sendMessage(lang.msg("menu.guild_war_management.feedback.surrendered"))
                    open()
                } else {
                    player.sendMessage(lang.msg("menu.guild_war_management.feedback.action_failed"))
                }
            }, 5, 4)
        }

        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.guild_war_management.war_details.actions.back.name"))
            .lore(lang.gui("menu.guild_war_management.war_details.actions.back.lore"))
        pane.addItem(GuiItem(back) { open() }, 0, 5)
        gui.show(player)
    }

    private fun openStatistics() {
        val wars = warService.getWarsForGuild(guild.id)
        val wins = wars.count { it.winner == guild.id }
        val losses = wars.count { it.loser == guild.id }
        val draws = wars.count { it.isEnded && it.winner == null && it.loser == null }
        val active = wars.count { it.isActive }
        var kills = 0
        var deaths = 0
        var captured = 0
        var lost = 0
        wars.forEach { war ->
            val stats = runCatching { warService.getWarStats(war.id) }.getOrNull() ?: return@forEach
            val declaring = war.declaringGuildId == guild.id
            kills += if (declaring) stats.declaringGuildKills else stats.defendingGuildKills
            deaths += if (declaring) stats.declaringGuildDeaths else stats.defendingGuildDeaths
            captured += stats.claimsCaptured
            lost += stats.claimsLost
        }
        val kdr = if (deaths > 0) kills.toDouble() / deaths else kills.toDouble()
        val winRate = if (wars.isNotEmpty()) wins.toDouble() / wars.size * 100.0 else 0.0

        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(MenuSurface.DETAIL, lang.guiTitle("menu.guild_war_management.war_stats.title")),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val overview = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.guild_war_management.war_stats.total", "count" to wars.size))
            .lore(lang.gui("menu.guild_war_management.war_stats.wins", "count" to wins))
            .lore(lang.gui("menu.guild_war_management.war_stats.losses", "count" to losses))
            .lore(lang.gui("menu.guild_war_management.war_stats.draws", "count" to draws))
            .lore(lang.gui("menu.guild_war_management.war_stats.active", "count" to active))
        pane.addItem(GuiItem(overview), 2, 2)

        val combat = ItemStack.of(Material.DIAMOND_SWORD)
            .name(lang.gui("menu.guild_war_management.war_stats.kills", "count" to kills))
            .lore(lang.gui("menu.guild_war_management.war_stats.deaths", "count" to deaths))
            .lore(lang.gui("menu.guild_war_management.war_stats.kdr", "ratio" to String.format("%.2f", kdr)))
        pane.addItem(GuiItem(combat), 4, 2)

        val claims = ItemStack.of(Material.STRUCTURE_BLOCK)
            .name(lang.gui("menu.guild_war_management.war_stats.objectives", "count" to captured))
            .lore(lang.gui("menu.guild_war_management.war_stats.claims_captured", "count" to captured))
            .lore(lang.gui("menu.guild_war_management.war_stats.claims_lost", "count" to lost))
            .lore(lang.gui("menu.guild_war_management.war_stats.win_rate", "rate" to String.format("%.1f", winRate)))
        pane.addItem(GuiItem(claims), 6, 2)

        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.guild_war_management.war_stats.back.name"))
            .lore(lang.gui("menu.guild_war_management.war_stats.back.lore"))
        pane.addItem(GuiItem(back) { open() }, 0, 5)
        gui.show(player)
    }

    private fun listGui(titleKey: String): ChestGui = ChestGui(
        6,
        MenuTitleBuilder.redesign(MenuSurface.LIST, lang.guiTitle(titleKey)),
    ).also { gui -> gui.setOnGlobalClick { it.isCancelled = true } }

    private fun addHubFooter(pane: StaticPane) {
        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.guild_war_management.back.name"))
            .lore(lang.gui("menu.guild_war_management.back.description"))
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 0, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    private fun addListFooter(
        pane: StaticPane,
        page: Int,
        totalPages: Int,
        previous: () -> Unit,
        next: () -> Unit,
    ) {
        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.guild_war_management.war_list.navigation.back.name"))
        pane.addItem(GuiItem(back) { open() }, 0, 5)

        if (page > 0) {
            val previousItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.guild_war_management.war_list.navigation.previous.name"))
            pane.addItem(GuiItem(previousItem) { previous() }, 3, 5)
        }

        val pageItem = ItemStack.of(Material.PAPER)
            .name(lang.gui("menu.guild_war_management.war_list.navigation.page", "page" to page + 1, "pages" to totalPages))
        pane.addItem(GuiItem(pageItem), 4, 5)

        if (page + 1 < totalPages) {
            val nextItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.guild_war_management.war_list.navigation.next.name"))
            pane.addItem(GuiItem(nextItem) { next() }, 5, 5)
        }

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    private fun enemyName(war: War): String {
        val enemyId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
        return guildService.getGuild(enemyId)?.name ?: lang.raw("menu.guild_war_management.fallback.unknown")
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
