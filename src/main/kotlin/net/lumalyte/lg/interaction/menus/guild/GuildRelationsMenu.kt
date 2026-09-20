package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuSurface
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.Instant

/** Diplomacy hub. Every clickable entry stays inside the GUI flow. */
class GuildRelationsMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
) : Menu, KoinComponent {

    private val relationService: RelationService by inject()
    private val memberService: MemberService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(
                MenuSurface.GRID,
                lang.guiTitle("menu.guild_relations.title", "guild" to guild.name),
            ),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        addStatus(pane)
        addLists(pane)
        addRequests(pane)
        addActions(pane)
        addWarfare(pane)
        addFooter(pane)

        gui.show(player)
    }

    private fun addStatus(pane: StaticPane) {
        val relations = relationService.getGuildRelations(guild.id)
        val allies = relations.count { it.type == RelationType.ALLY && it.isActive() }
        val enemies = relations.count { it.type == RelationType.ENEMY && it.isActive() }
        val truces = relations.count { it.type == RelationType.TRUCE && it.isActive() }
        val incoming = relationService.getIncomingRequests(guild.id).size
        val outgoing = relationService.getOutgoingRequests(guild.id).size

        val status = ItemStack.of(Material.FILLED_MAP)
            .name(lang.gui("menu.guild_relations.overview.status.name"))
            .lore(lang.gui("menu.guild_relations.overview.status.summary", "allies" to allies, "enemies" to enemies, "truces" to truces))
            .lore(Component.text("Incoming requests: $incoming", if (incoming > 0) NamedTextColor.YELLOW else NamedTextColor.GRAY))
            .lore(Component.text("Outgoing requests: $outgoing", NamedTextColor.GRAY))
        pane.addItem(GuiItem(status), 4, 0)
    }

    private fun addLists(pane: StaticPane) {
        val relations = relationService.getGuildRelations(guild.id)
        val allies = relations.count { it.type == RelationType.ALLY && it.isActive() }
        val enemies = relations.count { it.type == RelationType.ENEMY && it.isActive() }
        val truces = relations.count { it.type == RelationType.TRUCE && it.isActive() }

        val alliesItem = ItemStack.of(if (allies > 0) Material.DIAMOND else Material.GRAY_DYE)
            .name(lang.gui("menu.guild_relations.overview.allies.name"))
            .lore(lang.gui("menu.guild_relations.count", "count" to allies))
            .lore(Component.text("Click to view allied guilds", NamedTextColor.AQUA))
        pane.addItem(GuiItem(alliesItem) {
            menuNavigator.openMenu(menuFactory.createAlliesListMenu(menuNavigator, player, guild))
        }, 1, 1)

        val enemiesItem = ItemStack.of(if (enemies > 0) Material.REDSTONE else Material.GRAY_DYE)
            .name(lang.gui("menu.guild_relations.overview.enemies.name"))
            .lore(lang.gui("menu.guild_relations.count", "count" to enemies))
            .lore(Component.text("Click to view enemy guilds", NamedTextColor.AQUA))
        pane.addItem(GuiItem(enemiesItem) {
            menuNavigator.openMenu(menuFactory.createEnemiesListMenu(menuNavigator, player, guild))
        }, 4, 1)

        val trucesItem = ItemStack.of(if (truces > 0) Material.WHITE_BANNER else Material.GRAY_DYE)
            .name(lang.gui("menu.guild_relations.overview.truces.name"))
            .lore(lang.gui("menu.guild_relations.count", "count" to truces))
            .lore(Component.text("Click to view active truces", NamedTextColor.AQUA))
        pane.addItem(GuiItem(trucesItem) {
            menuNavigator.openMenu(GuildTrucesMenu(menuNavigator, player, guild))
        }, 7, 1)
    }

    private fun addRequests(pane: StaticPane) {
        val incoming = relationService.getIncomingRequests(guild.id).size
        val outgoing = relationService.getOutgoingRequests(guild.id).size

        val incomingItem = ItemStack.of(if (incoming > 0) Material.PAPER else Material.GRAY_DYE)
            .name(lang.gui("menu.guild_relations.requests.incoming.name"))
            .lore(lang.gui("menu.guild_relations.count", "count" to incoming))
            .lore(Component.text("Review and respond", NamedTextColor.AQUA))
        pane.addItem(GuiItem(incomingItem) {
            menuNavigator.openMenu(menuFactory.createIncomingRequestsMenu(menuNavigator, player, guild))
        }, 2, 2)

        val outgoingItem = ItemStack.of(if (outgoing > 0) Material.WRITABLE_BOOK else Material.GRAY_DYE)
            .name(lang.gui("menu.guild_relations.requests.outgoing.name"))
            .lore(lang.gui("menu.guild_relations.count", "count" to outgoing))
            .lore(Component.text("Review requests you sent", NamedTextColor.AQUA))
        pane.addItem(GuiItem(outgoingItem) {
            menuNavigator.openMenu(menuFactory.createOutgoingRequestsMenu(menuNavigator, player, guild))
        }, 6, 2)
    }

    private fun addActions(pane: StaticPane) {
        val canManage = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RELATIONS)
        val canDeclare = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.DECLARE_WAR)

        val alliance = ItemStack.of(if (canManage) Material.GOLDEN_APPLE else Material.GRAY_DYE)
            .name(lang.gui("menu.guild_relations.action.alliance.name"))
            .lore(lang.gui("menu.guild_relations.action.alliance.description"))
            .lore(if (canManage) Component.text("Click to choose a guild", NamedTextColor.AQUA) else Component.text("Requires Manage Relations", NamedTextColor.RED))
        pane.addItem(GuiItem(alliance) {
            if (canManage) {
                menuNavigator.openMenu(menuFactory.createAllianceRequestMenu(menuNavigator, player, guild))
            } else {
                player.sendMessage(lang.msg("menu.guild_relations.feedback.no_manage_permission"))
            }
        }, 1, 3)

        val truce = ItemStack.of(if (canManage) Material.WHITE_WOOL else Material.GRAY_DYE)
            .name(lang.gui("menu.guild_relations.action.truce.name"))
            .lore(lang.gui("menu.guild_relations.action.truce.description"))
            .lore(if (canManage) Component.text("Click to choose a guild", NamedTextColor.AQUA) else Component.text("Requires Manage Relations", NamedTextColor.RED))
        pane.addItem(GuiItem(truce) {
            if (canManage) {
                menuNavigator.openMenu(menuFactory.createTruceRequestMenu(menuNavigator, player, guild))
            } else {
                player.sendMessage(lang.msg("menu.guild_relations.feedback.no_manage_permission"))
            }
        }, 4, 3)

        val enemy = ItemStack.of(if (canDeclare) Material.IRON_SWORD else Material.GRAY_DYE)
            .name(lang.gui("menu.guild_relations.action.enemy.name"))
            .lore(lang.gui("menu.guild_relations.action.enemy.description"))
            .lore(if (canDeclare) Component.text("Click to choose a guild", NamedTextColor.AQUA) else Component.text("Requires Declare War", NamedTextColor.RED))
        pane.addItem(GuiItem(enemy) {
            if (canDeclare) {
                menuNavigator.openMenu(menuFactory.createEnemyDeclarationMenu(menuNavigator, player, guild))
            } else {
                player.sendMessage(lang.msg("menu.guild_relations.feedback.no_enemy_permission"))
            }
        }, 7, 3)
    }

    private fun addWarfare(pane: StaticPane) {
        val wars = ItemStack.of(Material.DIAMOND_SWORD)
            .name(Component.text("Wars & Peace", NamedTextColor.WHITE))
            .lore(Component.text("Active wars, declarations, history and peace agreements.", NamedTextColor.GRAY))
            .lore(Component.text("Click to open warfare", NamedTextColor.AQUA))
        pane.addItem(GuiItem(wars) {
            menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
        }, 4, 4)
    }

    private fun addFooter(pane: StaticPane) {
        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.common.item.back.name"))
            .lore(Component.text("Return to Allies & War", NamedTextColor.GRAY))
        pane.addItem(GuiItem(back) {
            menuNavigator.openMenu(
                GuildRedesignSectionMenu(menuNavigator, player, guild, GuildRedesignSectionMenu.Section.ALLIES),
            )
        }, 0, 5)

        val home = ItemStack.of(Material.COMPASS)
            .name(Component.text("Guild Home", NamedTextColor.AQUA))
        pane.addItem(GuiItem(home) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

/** GUI replacement for the old truce chat dump. */
class GuildTrucesMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
) : Menu, KoinComponent {

    private val relationService: RelationService by inject()
    private val guildService: GuildService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(MenuSurface.LIST, lang.gui("menu.guild_relations.overview.truces.name")),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val truces = relationService.getGuildRelationsByType(guild.id, RelationType.TRUCE)
            .filter { it.isActive() }
            .sortedBy { it.expiresAt ?: Instant.MAX }

        if (truces.isEmpty()) {
            val empty = ItemStack.of(Material.GRAY_DYE)
                .name(lang.gui("menu.guild_relations.overview.truces.name"))
                .lore(lang.gui("menu.guild_relations.truces.none"))
            pane.addItem(GuiItem(empty), 4, 2)
        } else {
            truces.take(45).forEachIndexed { index, relation ->
                val other = guildService.getGuild(relation.getOtherGuild(guild.id))
                val expiresAt = relation.expiresAt
                val remaining = expiresAt?.let { Duration.between(Instant.now(), it) }
                val days = remaining?.toDays()?.coerceAtLeast(0) ?: 0
                val hours = remaining?.toHours()?.rem(24)?.coerceAtLeast(0) ?: 0
                val item = ItemStack.of(Material.WHITE_BANNER)
                    .name(Component.text(other?.name ?: "Unknown Guild", NamedTextColor.WHITE))
                    .lore(lang.gui("menu.guild_relations.truces.row", "guild" to (other?.name ?: "Unknown Guild"), "days" to days, "hours" to hours))
                pane.addItem(GuiItem(item), index % 9, index / 9)
            }
        }

        val back = ItemStack.of(Material.ARROW).name(lang.gui("menu.common.item.back.name"))
        pane.addItem(GuiItem(back) {
            menuNavigator.openMenu(menuFactory.createGuildRelationsMenu(menuNavigator, player, guild))
        }, 0, 5)

        val home = ItemStack.of(Material.COMPASS).name(Component.text("Guild Home", NamedTextColor.AQUA))
        pane.addItem(GuiItem(home) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)

        gui.show(player)
    }
}
