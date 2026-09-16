package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.Relation
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuSurface
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.deserializeToItemStack
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.Instant

/** Dense enemy browser whose actions stay inside the GUI instead of dumping commands into chat. */
class EnemiesListMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val relationService: RelationService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    private var currentPage = 0
    private val itemsPerPage = 45

    override fun open() {
        guild = guildService.getGuild(guild.id) ?: guild
        val enemies = activeEnemies()
        val totalPages = maxOf(1, (enemies.size + itemsPerPage - 1) / itemsPerPage)
        currentPage = currentPage.coerceIn(0, totalPages - 1)

        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(MenuSurface.LIST, lang.guiTitle("menu.enemies_list.title")),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val pageEnemies = enemies.drop(currentPage * itemsPerPage).take(itemsPerPage)
        if (pageEnemies.isEmpty()) {
            val empty = ItemStack.of(Material.WHITE_BANNER)
                .name(lang.gui("menu.enemies_list.empty.name"))
                .lore(lang.gui("menu.enemies_list.empty.description"))
                .lore(lang.gui("menu.enemies_list.empty.hint"))
            pane.addItem(GuiItem(empty), 4, 2)
        } else {
            pageEnemies.forEachIndexed { index, relation ->
                pane.addItem(GuiItem(createEnemyItem(relation)) { openEnemyActionsMenu(relation) }, index % 9, index / 9)
            }
        }

        addFooter(pane, enemies.size, totalPages)
        gui.show(player)
    }

    private fun activeEnemies(): List<Relation> =
        relationService.getGuildRelationsByType(guild.id, RelationType.ENEMY)
            .filter { it.isActive() }
            .sortedByDescending { it.createdAt }

    private fun createEnemyItem(relation: Relation): ItemStack {
        val otherGuild = guildService.getGuild(relation.getOtherGuild(guild.id))
        val guildName = otherGuild?.name ?: lang.raw("menu.enemies_list.fallback.unknown_guild")
        val memberCount = otherGuild?.let { memberService.getMemberCount(it.id) } ?: 0
        val durationText = formatDuration(Duration.between(relation.createdAt, Instant.now()))
        val item = otherGuild?.banner?.deserializeToItemStack() ?: ItemStack.of(Material.RED_BANNER)
        val mode = if (otherGuild?.mode?.name == "PEACEFUL") {
            lang.gui("menu.enemies_list.guild.mode.peaceful")
        } else {
            lang.gui("menu.enemies_list.guild.mode.hostile")
        }
        return item
            .name(lang.gui("menu.enemies_list.guild.name", "guild" to guildName))
            .lore(lang.gui("menu.enemies_list.guild.members", "count" to memberCount))
            .lore(lang.gui("menu.enemies_list.guild.duration", "duration" to durationText))
            .lore(lang.gui("menu.enemies_list.guild.level", "level" to (otherGuild?.level ?: 1)))
            .lore(lang.gui("menu.enemies_list.guild.mode.line", "mode" to mode))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.enemies_list.guild.actions"))
    }

    private fun openEnemyActionsMenu(relation: Relation) {
        val otherGuild = guildService.getGuild(relation.getOtherGuild(guild.id))
        val guildName = otherGuild?.name ?: lang.raw("menu.enemies_list.fallback.unknown_guild")
        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(MenuSurface.DETAIL, lang.guiTitle("menu.enemies_list.title")),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        pane.addItem(GuiItem(createEnemyItem(relation)), 4, 1)

        val info = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.enemies_list.actions.info.name"))
            .lore(lang.gui("menu.enemies_list.actions.info.description"))
            .lore(lang.gui("menu.enemies_list.actions.info.guild", "guild" to guildName))
        pane.addItem(GuiItem(info) {
            if (otherGuild != null) menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, otherGuild))
        }, 1, 3)

        val canManage = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RELATIONS)
        val truce = ItemStack.of(if (canManage) Material.WHITE_BANNER else Material.GRAY_DYE)
            .name(
                if (canManage) lang.gui("menu.enemies_list.actions.truce.name")
                else lang.gui("menu.enemies_list.actions.truce.disabled"),
            )
            .lore(
                if (canManage) lang.gui("menu.enemies_list.actions.truce.description")
                else lang.gui("menu.enemies_list.permission.required_relation"),
            )
        pane.addItem(GuiItem(truce) {
            if (canManage) menuNavigator.openMenu(menuFactory.createTruceRequestMenu(menuNavigator, player, guild))
            else player.sendMessage(lang.msg("menu.enemies_list.feedback.no_permission"))
        }, 3, 3)

        val peace = ItemStack.of(if (canManage) Material.PAPER else Material.GRAY_DYE)
            .name(
                if (canManage) lang.gui("menu.enemies_list.actions.peace.name")
                else lang.gui("menu.enemies_list.actions.peace.disabled"),
            )
            .lore(
                if (canManage) lang.gui("menu.enemies_list.actions.peace.description")
                else lang.gui("menu.enemies_list.permission.required_relation"),
            )
        pane.addItem(GuiItem(peace) {
            if (canManage) menuNavigator.openMenu(menuFactory.createPeaceAgreementMenu(menuNavigator, player, guild))
            else player.sendMessage(lang.msg("menu.enemies_list.feedback.no_permission"))
        }, 5, 3)

        val war = ItemStack.of(Material.IRON_SWORD)
            .name(lang.gui("menu.enemies_list.actions.details.name"))
            .lore(lang.gui("menu.enemies_list.actions.details.description"))
            .lore(lang.gui("menu.enemies_list.actions.details.started", "duration" to formatDuration(Duration.between(relation.createdAt, Instant.now()))))
        pane.addItem(GuiItem(war) {
            menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
        }, 7, 3)

        addDetailFooter(pane)
        gui.show(player)
    }

    private fun addFooter(pane: StaticPane, totalEnemies: Int, totalPages: Int) {
        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.enemies_list.navigation.back.name"))
            .lore(lang.gui("menu.enemies_list.navigation.back.description"))
        pane.addItem(GuiItem(back) {
            menuNavigator.openMenu(menuFactory.createGuildRelationsMenu(menuNavigator, player, guild))
        }, 0, 5)

        if (currentPage > 0) {
            val previous = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.enemies_list.navigation.previous.name"))
                .lore(lang.gui("menu.enemies_list.navigation.previous.description"))
            pane.addItem(GuiItem(previous) { currentPage--; open() }, 1, 5)
        }

        val page = ItemStack.of(Material.PAPER)
            .name(lang.gui("menu.enemies_list.navigation.page", "page" to currentPage + 1, "pages" to totalPages))
            .lore(lang.gui("menu.enemies_list.navigation.total", "count" to totalEnemies))
        pane.addItem(GuiItem(page), 3, 5)

        val home = ItemStack.of(Material.COMPASS).name(lang.gui("menu.control_panel.item.home.name"))
        pane.addItem(GuiItem(home) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        if (currentPage < totalPages - 1) {
            val next = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.enemies_list.navigation.next.name"))
                .lore(lang.gui("menu.enemies_list.navigation.next.description"))
            pane.addItem(GuiItem(next) { currentPage++; open() }, 7, 5)
        }

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    private fun addDetailFooter(pane: StaticPane) {
        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.enemies_list.actions.back.name"))
            .lore(lang.gui("menu.enemies_list.actions.back.description"))
        pane.addItem(GuiItem(back) { open() }, 0, 5)

        val home = ItemStack.of(Material.COMPASS).name(lang.gui("menu.control_panel.item.home.name"))
        pane.addItem(GuiItem(home) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    private fun formatDuration(duration: Duration): Component {
        val days = duration.toDays()
        return when {
            days < 1 -> lang.gui("menu.enemies_list.duration.less_than_day")
            days < 7 && days == 1L -> lang.gui("menu.enemies_list.duration.day", "count" to days)
            days < 7 -> lang.gui("menu.enemies_list.duration.days", "count" to days)
            days < 30 && days / 7 == 1L -> lang.gui("menu.enemies_list.duration.week", "count" to days / 7)
            days < 30 -> lang.gui("menu.enemies_list.duration.weeks", "count" to days / 7)
            days < 365 && days / 30 == 1L -> lang.gui("menu.enemies_list.duration.month", "count" to days / 30)
            days < 365 -> lang.gui("menu.enemies_list.duration.months", "count" to days / 30)
            days / 365 == 1L -> lang.gui("menu.enemies_list.duration.year", "count" to days / 365)
            else -> lang.gui("menu.enemies_list.duration.years", "count" to days / 365)
        }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
