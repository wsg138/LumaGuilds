package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.RankPermission
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
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Party hub with no coming-soon or chat-dump actions. */
class GuildPartyManagementMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
) : Menu, KoinComponent {

    private val partyService: PartyService by inject()
    private val memberService: MemberService by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(
                MenuSurface.GRID,
                lang.guiTitle("menu.party.management.title", "guild" to guild.name),
            ),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        if (!configService.loadConfig().partiesEnabled) {
            val disabled = ItemStack.of(Material.BARRIER)
                .name(Component.text("Parties Disabled", NamedTextColor.RED))
                .lore(lang.gui("menu.party.management.feedback.disabled"))
            pane.addItem(GuiItem(disabled), 4, 2)
            addFooter(pane)
            gui.show(player)
            return
        }

        addOverview(pane)
        addActions(pane)
        addFooter(pane)
        gui.show(player)
    }

    private fun addOverview(pane: StaticPane) {
        val active = partyService.getActivePartiesForGuild(guild.id)
            .filterNot { it.isPlayerBanned(player.uniqueId) }
        val incoming = partyService.getPendingRequestsForGuild(guild.id).size
        val outgoing = partyService.getPendingRequestsFromGuild(guild.id).size

        val activeItem = ItemStack.of(if (active.isEmpty()) Material.GRAY_DYE else Material.FIREWORK_ROCKET)
            .name(Component.text("Active Parties", NamedTextColor.WHITE))
            .lore(Component.text("${active.size} active", NamedTextColor.GRAY))
            .lore(Component.text("Click to browse every party", NamedTextColor.AQUA))
        pane.addItem(GuiItem(activeItem) {
            menuNavigator.openMenu(GuildPartyListMenu(menuNavigator, player, guild))
        }, 1, 1)

        val incomingItem = ItemStack.of(if (incoming == 0) Material.GRAY_DYE else Material.PAPER)
            .name(lang.gui("menu.party.management.incoming.name"))
            .lore(lang.gui("menu.party.management.request_count", "count" to incoming))
            .lore(Component.text("Click to review", NamedTextColor.AQUA))
        pane.addItem(GuiItem(incomingItem) {
            menuNavigator.openMenu(GuildPartyIncomingRequestsMenu(menuNavigator, player, guild))
        }, 4, 1)

        val outgoingItem = ItemStack.of(if (outgoing == 0) Material.GRAY_DYE else Material.WRITABLE_BOOK)
            .name(lang.gui("menu.party.management.outgoing.name"))
            .lore(lang.gui("menu.party.management.request_count", "count" to outgoing))
            .lore(Component.text("Click to review or cancel", NamedTextColor.AQUA))
        pane.addItem(GuiItem(outgoingItem) {
            menuNavigator.openMenu(GuildPartyOutgoingRequestsMenu(menuNavigator, player, guild))
        }, 7, 1)
    }

    private fun addActions(pane: StaticPane) {
        val canManage = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_PARTIES)

        val create = ItemStack.of(if (canManage) Material.NETHER_STAR else Material.GRAY_DYE)
            .name(
                if (canManage) lang.gui("menu.party.management.create.name")
                else lang.gui("menu.party.management.create.locked_name"),
            )
            .lore(
                if (canManage) lang.gui("menu.party.management.create.lore")
                else lang.gui("menu.party.management.create.locked_lore"),
            )
            .lore(Component.text(if (canManage) "Click to create" else "Requires Manage Parties", if (canManage) NamedTextColor.AQUA else NamedTextColor.RED))
        pane.addItem(GuiItem(create) {
            if (canManage) {
                menuNavigator.openMenu(menuFactory.createPartyCreationMenu(menuNavigator, player, guild))
            } else {
                player.sendMessage(lang.msg("menu.party.management.feedback.create_permission"))
            }
        }, 2, 3)

        val moderation = ItemStack.of(if (canManage) Material.ANVIL else Material.GRAY_DYE)
            .name(lang.gui("menu.party.management.moderate.name"))
            .lore(Component.text("Choose a party, then manage its players.", NamedTextColor.GRAY))
            .lore(Component.text(if (canManage) "Click to choose a party" else "Requires Manage Parties", if (canManage) NamedTextColor.AQUA else NamedTextColor.RED))
        pane.addItem(GuiItem(moderation) {
            if (canManage) {
                menuNavigator.openMenu(GuildPartyListMenu(menuNavigator, player, guild))
            } else {
                player.sendMessage(lang.msg("menu.party.management.permission.required"))
            }
        }, 4, 3)

        val lfg = ItemStack.of(Material.SPYGLASS)
            .name(Component.text("Looking for Group", NamedTextColor.WHITE))
            .lore(Component.text("Browse open guilds and group discovery.", NamedTextColor.GRAY))
            .lore(Component.text("Click to browse", NamedTextColor.AQUA))
        pane.addItem(GuiItem(lfg) {
            menuNavigator.openMenu(menuFactory.createLfgBrowserMenu(menuNavigator, player))
        }, 6, 3)

        val permissions = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.party.management.permissions.name"))
            .lore(lang.gui("menu.party.management.permissions.accept"))
            .lore(lang.gui("menu.party.management.permissions.send"))
            .lore(lang.gui("menu.party.management.permissions.manage"))
            .lore(lang.gui("menu.party.management.permissions.join"))
        pane.addItem(GuiItem(permissions), 4, 4)
    }

    private fun addFooter(pane: StaticPane) {
        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.common.item.back.name"))
            .lore(Component.text("Return to Parties & LFG", NamedTextColor.GRAY))
        pane.addItem(GuiItem(back) {
            menuNavigator.openMenu(
                GuildRedesignSectionMenu(menuNavigator, player, guild, GuildRedesignSectionMenu.Section.PARTIES),
            )
        }, 0, 5)

        val home = ItemStack.of(Material.COMPASS).name(Component.text("Guild Home", NamedTextColor.AQUA))
        pane.addItem(GuiItem(home) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

/** Dense active-party browser; replaces the old list_coming_soon path. */
class GuildPartyListMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
) : Menu, KoinComponent {

    private val partyService: PartyService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(6, MenuTitleBuilder.redesign(MenuSurface.LIST, "Active Parties"))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val parties = partyService.getActivePartiesForGuild(guild.id)
            .filterNot { it.isPlayerBanned(player.uniqueId) }
            .sortedBy { it.name ?: it.createdAt.toString() }

        if (parties.isEmpty()) {
            val empty = ItemStack.of(Material.GRAY_DYE)
                .name(lang.gui("menu.party.management.empty.name"))
                .lore(lang.gui("menu.party.management.empty.lore"))
            pane.addItem(GuiItem(empty), 4, 2)
        } else {
            parties.take(45).forEachIndexed { index, party ->
                val item = ItemStack.of(Material.FIREWORK_ROCKET)
                    .name(Component.text(party.name ?: "Unnamed Party", NamedTextColor.WHITE))
                    .lore(Component.text("Guilds: ${party.guildIds.size}", NamedTextColor.GRAY))
                    .lore(Component.text("Created: ${formatDate(party)}", NamedTextColor.GRAY))
                    .lore(Component.text(if (party.expiresAt == null) "No expiration" else "Expires: ${formatExpiry(party)}", NamedTextColor.GRAY))
                    .lore(Component.text("Click for details", NamedTextColor.AQUA))
                pane.addItem(GuiItem(item) {
                    menuNavigator.openMenu(GuildPartyDetailMenu(menuNavigator, player, guild, party))
                }, index % 9, index / 9)
            }
        }

        addStandardFooter(pane, menuNavigator, player, guild, menuFactory, lang) {
            menuNavigator.openMenu(menuFactory.createGuildPartyManagementMenu(menuNavigator, player, guild))
        }
        gui.show(player)
    }

    private fun formatDate(party: Party): String =
        party.createdAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))

    private fun formatExpiry(party: Party): String =
        party.expiresAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")) ?: "Never"
}

/** Contextual party detail page; replaces details_coming_soon. */
class GuildPartyDetailMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private var party: Party,
) : Menu, KoinComponent {

    private val partyService: PartyService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        party = partyService.getParty(party.id) ?: party
        val gui = ChestGui(6, MenuTitleBuilder.redesign(MenuSurface.DETAIL, party.name ?: "Party Details"))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val info = ItemStack.of(Material.FIREWORK_ROCKET)
            .name(Component.text(party.name ?: "Unnamed Party", NamedTextColor.WHITE))
            .lore(Component.text("Guilds: ${party.guildIds.size}", NamedTextColor.GRAY))
            .lore(Component.text("Role restrictions: ${party.restrictedRoles?.size ?: 0}", NamedTextColor.GRAY))
            .lore(Component.text("Muted players: ${party.getActiveMutes().size}", NamedTextColor.GRAY))
            .lore(Component.text("Banned players: ${party.bannedPlayers.size}", NamedTextColor.GRAY))
        pane.addItem(GuiItem(info), 4, 0)

        party.guildIds.take(10).forEachIndexed { index, guildId ->
            val memberGuild = guildService.getGuild(guildId)
            val item = ItemStack.of(if (guildId == guild.id) Material.LIME_BANNER else Material.WHITE_BANNER)
                .name(Component.text(memberGuild?.name ?: "Unknown Guild", NamedTextColor.WHITE))
                .lore(Component.text(if (guildId == guild.id) "Your guild" else "Party member", NamedTextColor.GRAY))
            pane.addItem(GuiItem(item), index % 5 + 2, 1 + index / 5)
        }

        val canManage = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_PARTIES)
        val moderation = ItemStack.of(if (canManage) Material.ANVIL else Material.GRAY_DYE)
            .name(lang.gui("menu.party.management.moderate.name"))
            .lore(Component.text(if (canManage) "Mute, kick and ban party players" else "Requires Manage Parties", if (canManage) NamedTextColor.AQUA else NamedTextColor.RED))
        pane.addItem(GuiItem(moderation) {
            if (canManage) {
                menuNavigator.openMenu(PartyModerationMenu(menuNavigator, player, guild, party))
            }
        }, 3, 4)

        val leave = ItemStack.of(if (canManage) Material.OAK_DOOR else Material.GRAY_DYE)
            .name(Component.text("Remove Guild from Party", NamedTextColor.RED))
            .lore(Component.text("Removes your guild from this party.", NamedTextColor.GRAY))
            .lore(Component.text(if (canManage) "Click to leave" else "Requires Manage Parties", if (canManage) NamedTextColor.RED else NamedTextColor.DARK_GRAY))
        pane.addItem(GuiItem(leave) {
            if (canManage) {
                partyService.leaveParty(party.id, guild.id, player.uniqueId)
                menuNavigator.openMenu(menuFactory.createGuildPartyManagementMenu(menuNavigator, player, guild))
            }
        }, 5, 4)

        addStandardFooter(pane, menuNavigator, player, guild, menuFactory, lang) {
            menuNavigator.openMenu(GuildPartyListMenu(menuNavigator, player, guild))
        }
        gui.show(player)
    }
}

class GuildPartyIncomingRequestsMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
) : Menu, KoinComponent {

    private val partyService: PartyService by inject()
    private val guildService: GuildService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(6, MenuTitleBuilder.redesign(MenuSurface.LIST, "Incoming Party Requests"))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val requests = partyService.getPendingRequestsForGuild(guild.id).toList()
        if (requests.isEmpty()) {
            val empty = ItemStack.of(Material.GRAY_DYE)
                .name(lang.gui("menu.party.management.incoming.name"))
                .lore(Component.text("No pending requests", NamedTextColor.GRAY))
            pane.addItem(GuiItem(empty), 4, 2)
        } else {
            requests.take(45).forEachIndexed { index, request ->
                val from = guildService.getGuild(request.fromGuildId)
                val item = ItemStack.of(Material.PAPER)
                    .name(lang.gui("menu.party.management.incoming_request.name", "guild" to (from?.name ?: "Unknown Guild")))
                    .lore(Component.text(request.message ?: "No message", NamedTextColor.GRAY))
                    .lore(Component.empty())
                    .lore(Component.text("Left click: Accept", NamedTextColor.GREEN))
                    .lore(Component.text("Shift click: Decline", NamedTextColor.RED))
                pane.addItem(GuiItem(item) { event ->
                    when (event.click) {
                        ClickType.LEFT -> {
                            if (partyService.acceptPartyRequest(request.id, guild.id, player.uniqueId) != null) {
                                player.sendMessage(lang.msg("menu.party.management.feedback.accepted"))
                                open()
                            } else {
                                player.sendMessage(lang.msg("menu.party.management.feedback.accept_failed"))
                            }
                        }
                        ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> {
                            if (partyService.rejectPartyRequest(request.id, guild.id, player.uniqueId)) {
                                player.sendMessage(lang.msg("menu.party.management.feedback.rejected"))
                                open()
                            } else {
                                player.sendMessage(lang.msg("menu.party.management.feedback.reject_failed"))
                            }
                        }
                        else -> Unit
                    }
                }, index % 9, index / 9)
            }
        }

        addStandardFooter(pane, menuNavigator, player, guild, menuFactory, lang) {
            menuNavigator.openMenu(menuFactory.createGuildPartyManagementMenu(menuNavigator, player, guild))
        }
        gui.show(player)
    }
}

class GuildPartyOutgoingRequestsMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
) : Menu, KoinComponent {

    private val partyService: PartyService by inject()
    private val guildService: GuildService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(6, MenuTitleBuilder.redesign(MenuSurface.LIST, "Outgoing Party Requests"))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val requests = partyService.getPendingRequestsFromGuild(guild.id).toList()
        if (requests.isEmpty()) {
            val empty = ItemStack.of(Material.GRAY_DYE)
                .name(lang.gui("menu.party.management.outgoing.name"))
                .lore(Component.text("No pending requests", NamedTextColor.GRAY))
            pane.addItem(GuiItem(empty), 4, 2)
        } else {
            requests.take(45).forEachIndexed { index, request ->
                val target = guildService.getGuild(request.toGuildId)
                val item = ItemStack.of(Material.WRITABLE_BOOK)
                    .name(lang.gui("menu.party.management.outgoing_request.name", "guild" to (target?.name ?: "Unknown Guild")))
                    .lore(Component.text(request.message ?: "No message", NamedTextColor.GRAY))
                    .lore(Component.empty())
                    .lore(Component.text("Shift click: Cancel request", NamedTextColor.RED))
                pane.addItem(GuiItem(item) { event ->
                    if (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT) {
                        if (partyService.cancelPartyRequest(request.id, guild.id, player.uniqueId)) {
                            player.sendMessage(lang.msg("menu.party.management.feedback.cancelled"))
                            open()
                        } else {
                            player.sendMessage(lang.msg("menu.party.management.feedback.cancel_failed"))
                        }
                    }
                }, index % 9, index / 9)
            }
        }

        addStandardFooter(pane, menuNavigator, player, guild, menuFactory, lang) {
            menuNavigator.openMenu(menuFactory.createGuildPartyManagementMenu(menuNavigator, player, guild))
        }
        gui.show(player)
    }
}

private fun addStandardFooter(
    pane: StaticPane,
    menuNavigator: MenuNavigator,
    player: Player,
    guild: Guild,
    menuFactory: MenuFactory,
    lang: LangService,
    backAction: () -> Unit,
) {
    val back = ItemStack.of(Material.ARROW).name(lang.gui("menu.common.item.back.name"))
    pane.addItem(GuiItem(back) { backAction() }, 0, 5)

    val home = ItemStack.of(Material.COMPASS).name(Component.text("Guild Home", NamedTextColor.AQUA))
    pane.addItem(GuiItem(home) {
        menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
    }, 4, 5)

    val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.common.item.close.name"))
    pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
}
