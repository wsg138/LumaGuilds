package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.persistence.RelationRepository
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
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Dense ally browser with detail and danger screens from the shared redesign system. */
class AlliesListMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val relationService: RelationService by inject()
    private val relationRepository: RelationRepository by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    private var currentPage = 0
    private val itemsPerPage = 45

    override fun open() {
        guild = guildService.getGuild(guild.id) ?: guild
        val allies = activeAllies()
        val totalPages = maxOf(1, (allies.size + itemsPerPage - 1) / itemsPerPage)
        currentPage = currentPage.coerceIn(0, totalPages - 1)

        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(MenuSurface.LIST, lang.guiTitle("menu.allies_list.title")),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val pageAllies = allies.drop(currentPage * itemsPerPage).take(itemsPerPage)
        if (pageAllies.isEmpty()) {
            val empty = ItemStack.of(Material.GRAY_BANNER)
                .name(lang.gui("menu.allies_list.empty.name"))
                .lore(lang.gui("menu.allies_list.empty.description"))
                .lore(lang.gui("menu.allies_list.empty.hint"))
            pane.addItem(GuiItem(empty), 4, 2)
        } else {
            pageAllies.forEachIndexed { index, relation ->
                pane.addItem(GuiItem(createAllyItem(relation)) { openAllyActionsMenu(relation) }, index % 9, index / 9)
            }
        }

        addFooter(pane, allies.size, totalPages)
        gui.show(player)
    }

    private fun activeAllies(): List<Relation> =
        relationService.getGuildRelationsByType(guild.id, RelationType.ALLY)
            .filter { it.isActive() }
            .sortedByDescending { it.createdAt }

    private fun createAllyItem(relation: Relation): ItemStack {
        val otherGuild = guildService.getGuild(relation.getOtherGuild(guild.id))
        val guildName = otherGuild?.name ?: lang.raw("menu.allies_list.fallback.unknown_guild")
        val memberCount = otherGuild?.let { memberService.getMemberCount(it.id) } ?: 0
        val durationText = formatDuration(Duration.between(relation.createdAt, Instant.now()))
        val item = otherGuild?.banner?.deserializeToItemStack() ?: ItemStack.of(Material.GREEN_BANNER)
        val mode = if (otherGuild?.mode?.name == "PEACEFUL") {
            lang.gui("menu.allies_list.guild.mode.peaceful")
        } else {
            lang.gui("menu.allies_list.guild.mode.hostile")
        }
        return item
            .name(lang.gui("menu.allies_list.guild.name", "guild" to guildName))
            .lore(lang.gui("menu.allies_list.guild.members", "count" to memberCount))
            .lore(lang.gui("menu.allies_list.guild.duration", "duration" to durationText))
            .lore(lang.gui("menu.allies_list.guild.level", "level" to (otherGuild?.level ?: 1)))
            .lore(lang.gui("menu.allies_list.guild.mode.line", "mode" to mode))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.allies_list.guild.actions"))
    }

    private fun openAllyActionsMenu(relation: Relation) {
        val otherGuild = guildService.getGuild(relation.getOtherGuild(guild.id))
        val guildName = otherGuild?.name ?: lang.raw("menu.allies_list.fallback.unknown_guild")
        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(
                MenuSurface.DETAIL,
                lang.guiTitle("menu.allies_list.actions_title", "guild" to guildName),
            ),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val summary = createAllyItem(relation)
        pane.addItem(GuiItem(summary), 4, 1)

        val info = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.allies_list.actions.info.name"))
            .lore(lang.gui("menu.allies_list.actions.info.description"))
            .lore(lang.gui("menu.allies_list.actions.info.guild", "guild" to guildName))
        pane.addItem(GuiItem(info) {
            if (otherGuild != null) menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, otherGuild))
        }, 2, 3)

        val canManage = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RELATIONS)
        val breakItem = ItemStack.of(if (canManage) Material.RED_CONCRETE else Material.GRAY_DYE)
            .name(
                if (canManage) lang.gui("menu.allies_list.actions.break_alliance.name")
                else lang.gui("menu.allies_list.actions.break_alliance.disabled"),
            )
            .lore(
                if (canManage) lang.gui("menu.allies_list.actions.break_alliance.description")
                else lang.gui("menu.allies_list.permission.manage_relations"),
            )
        pane.addItem(GuiItem(breakItem) {
            if (canManage) openBreakConfirmMenu(relation, guildName)
            else player.sendMessage(lang.msg("menu.allies_list.feedback.no_permission"))
        }, 6, 3)

        addDetailFooter(pane)
        gui.show(player)
    }

    private fun openBreakConfirmMenu(relation: Relation, guildName: String) {
        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(MenuSurface.DANGER, lang.guiTitle("menu.allies_list.confirm.title")),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val warning = ItemStack.of(Material.RED_BANNER)
            .name(lang.gui("menu.allies_list.confirm.name"))
            .lore(lang.gui("menu.allies_list.confirm.description"))
            .lore(lang.gui("menu.allies_list.confirm.guild", "guild" to guildName))
            .lore(lang.gui("menu.allies_list.confirm.warning"))
        pane.addItem(GuiItem(warning), 4, 1)

        val confirm = ItemStack.of(Material.RED_CONCRETE)
            .name(lang.gui("menu.allies_list.confirm.name"))
            .lore(lang.gui("menu.allies_list.confirm.warning"))
        pane.addItem(GuiItem(confirm) { breakAlliance(relation, guildName) }, 3, 3)

        val cancel = ItemStack.of(Material.LIME_CONCRETE)
            .name(lang.gui("menu.allies_list.confirm.cancel.name"))
            .lore(lang.gui("menu.allies_list.confirm.cancel.description"))
        pane.addItem(GuiItem(cancel) { openAllyActionsMenu(relation) }, 5, 3)

        gui.show(player)
    }

    private fun breakAlliance(relation: Relation, guildName: String) {
        if (relationRepository.remove(relation.id)) {
            player.sendMessage(lang.msg("menu.allies_list.feedback.broken", "guild" to guildName))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f)
            notifyGuildMembers(
                relation.getOtherGuild(guild.id),
                lang.msg("menu.allies_list.notification.broken", "guild" to guild.name),
            )
        } else {
            player.sendMessage(lang.msg("menu.allies_list.feedback.break_failed"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
        open()
    }

    private fun addFooter(pane: StaticPane, totalAllies: Int, totalPages: Int) {
        if (currentPage > 0) {
            val previous = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.allies_list.navigation.previous.name"))
                .lore(lang.gui("menu.allies_list.navigation.previous.description"))
            pane.addItem(GuiItem(previous) { currentPage--; open() }, 1, 5)
        }

        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.allies_list.navigation.back.name"))
            .lore(lang.gui("menu.allies_list.navigation.back.description"))
        pane.addItem(GuiItem(back) {
            menuNavigator.openMenu(menuFactory.createGuildRelationsMenu(menuNavigator, player, guild))
        }, 0, 5)

        val page = ItemStack.of(Material.PAPER)
            .name(lang.gui("menu.allies_list.navigation.page", "page" to currentPage + 1, "pages" to totalPages))
            .lore(lang.gui("menu.allies_list.navigation.total", "count" to totalAllies))
        pane.addItem(GuiItem(page), 3, 5)

        val home = ItemStack.of(Material.COMPASS).name(lang.gui("menu.control_panel.item.home.name"))
        pane.addItem(GuiItem(home) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)

        if (currentPage < totalPages - 1) {
            val next = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.allies_list.navigation.next.name"))
                .lore(lang.gui("menu.allies_list.navigation.next.description"))
            pane.addItem(GuiItem(next) { currentPage++; open() }, 7, 5)
        }
    }

    private fun addDetailFooter(pane: StaticPane) {
        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.allies_list.actions.back.name"))
            .lore(lang.gui("menu.allies_list.actions.back.description"))
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
            days < 1 -> lang.gui("menu.allies_list.duration.less_than_day")
            days < 7 && days == 1L -> lang.gui("menu.allies_list.duration.day", "count" to days)
            days < 7 -> lang.gui("menu.allies_list.duration.days", "count" to days)
            days < 30 && days / 7 == 1L -> lang.gui("menu.allies_list.duration.week", "count" to days / 7)
            days < 30 -> lang.gui("menu.allies_list.duration.weeks", "count" to days / 7)
            days < 365 && days / 30 == 1L -> lang.gui("menu.allies_list.duration.month", "count" to days / 30)
            days < 365 -> lang.gui("menu.allies_list.duration.months", "count" to days / 30)
            days / 365 == 1L -> lang.gui("menu.allies_list.duration.year", "count" to days / 365)
            else -> lang.gui("menu.allies_list.duration.years", "count" to days / 365)
        }
    }

    private fun notifyGuildMembers(guildId: UUID, message: Component) {
        memberService.getGuildMembers(guildId).forEach { member ->
            Bukkit.getPlayer(member.playerId)?.takeIf { it.isOnline }?.sendMessage(message)
        }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
