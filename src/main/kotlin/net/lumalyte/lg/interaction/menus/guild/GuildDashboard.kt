package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.NexoItemProvider
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent

/**
 * Full visual guild dashboard redesign prototype.
 *
 * Eight player-task categories replace the old code-oriented category grid. The Nexo items are
 * deliberately oversized in GUI view, so each card visually occupies a 2x2 region. Transparent
 * hitbox items make the full visual card clickable when the Nexo pack is loaded.
 */
class GuildDashboard(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private val guildService: GuildService,
    private val rankService: RankService,
    private val memberService: MemberService,
    @Suppress("UNUSED_PARAMETER") private val menuFactory: MenuFactory,
) : Menu, KoinComponent {

    override fun open() {
        if (memberService.getMember(player.uniqueId, guild.id) == null) {
            player.sendMessage(Component.text("You are no longer a member of this guild.", NamedTextColor.RED))
            menuNavigator.goBack()
            return
        }
        guild = guildService.getGuild(guild.id) ?: run {
            player.sendMessage(Component.text("That guild no longer exists.", NamedTextColor.RED))
            menuNavigator.goBack()
            return
        }

        val gui = ChestGui(6, GuildRedesignSectionMenu.redesignTitle("Guild Home"))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        addStatusRow(pane)
        val cards = listOf(
            GuildRedesignSectionMenu.Section.MEMBERS,
            GuildRedesignSectionMenu.Section.MONEY,
            GuildRedesignSectionMenu.Section.LEVEL,
            GuildRedesignSectionMenu.Section.HOMES,
            GuildRedesignSectionMenu.Section.ALLIES,
            GuildRedesignSectionMenu.Section.PARTIES,
            GuildRedesignSectionMenu.Section.CUSTOMIZE,
            GuildRedesignSectionMenu.Section.SETTINGS,
        )
        cards.forEachIndexed { index, section ->
            val x = (index % 4) * 2
            val y = if (index < 4) 1 else 3
            addCard(pane, x, y, section)
        }
        addFooter(pane)
        gui.show(player)
    }

    private fun addStatusRow(pane: StaticPane) {
        val rank = rankService.getPlayerRank(player.uniqueId, guild.id)
        val members = memberService.getMemberCount(guild.id)
        val identity = ItemStack.of(Material.BELL)
        GuildRedesignSectionMenu.setMeta(identity, Component.text(guild.name, NamedTextColor.GOLD), listOf(
            Component.text("Your rank: ${rank?.name ?: "Member"}", NamedTextColor.AQUA),
            Component.text("$members members", NamedTextColor.GRAY),
            Component.text("Bank: ${guild.bankBalance}", NamedTextColor.GRAY),
        ))
        pane.addItem(GuiItem(identity), 0, 0)

        val membersItem = ItemStack.of(Material.PLAYER_HEAD)
        GuildRedesignSectionMenu.setMeta(membersItem, Component.text("$members Members", NamedTextColor.WHITE), listOf(Component.text("Click to open the full directory", NamedTextColor.GRAY)))
        pane.addItem(GuiItem(membersItem) {
            menuNavigator.openMenu(GuildRedesignMemberDirectoryMenu(menuNavigator, player, guild))
        }, 3, 0)

        val rankItem = ItemStack.of(Material.GOLDEN_HELMET)
        GuildRedesignSectionMenu.setMeta(rankItem, Component.text(rank?.name ?: "Member", NamedTextColor.GOLD), listOf(Component.text("Your guild rank", NamedTextColor.GRAY)))
        pane.addItem(GuiItem(rankItem), 5, 0)

        val alert = NexoItemProvider.getItemStackOrFallback("lg_redesign_alert") { ItemStack.of(Material.BELL) }
        GuildRedesignSectionMenu.setMeta(alert, Component.text("Guild Activity", NamedTextColor.YELLOW), listOf(
            Component.text("Requests, completed quests and wars", NamedTextColor.GRAY),
            Component.text("surface here as badges instead of being hidden.", NamedTextColor.GRAY),
        ))
        pane.addItem(GuiItem(alert), 8, 0)
    }

    private fun addCard(pane: StaticPane, x: Int, y: Int, section: GuildRedesignSectionMenu.Section) {
        val card = NexoItemProvider.getItemStackOrFallback(section.iconId) { ItemStack.of(section.fallback) }
        GuildRedesignSectionMenu.setMeta(card, Component.text(section.title, NamedTextColor.WHITE), listOf(
            Component.text(sectionSummary(section), NamedTextColor.GRAY),
            Component.empty(),
            Component.text("Click to open", NamedTextColor.AQUA),
        ))
        val action = { menuNavigator.openMenu(GuildRedesignSectionMenu(menuNavigator, player, guild, section)) }
        pane.addItem(GuiItem(card) { action() }, x, y)

        if (NexoItemProvider.isAvailable()) {
            listOf(x + 1 to y, x to y + 1, x + 1 to y + 1).forEach { (hx, hy) ->
                if (hx <= 8 && hy <= 4) {
                    val hitbox = NexoItemProvider.getItemStack("lg_redesign_hitbox") ?: return@forEach
                    GuildRedesignSectionMenu.setMeta(hitbox, Component.text(section.title), emptyList())
                    pane.addItem(GuiItem(hitbox) { action() }, hx, hy)
                }
            }
        }
    }

    private fun sectionSummary(section: GuildRedesignSectionMenu.Section): String = when (section) {
        GuildRedesignSectionMenu.Section.MEMBERS -> "Roster, ranks, permissions and invites"
        GuildRedesignSectionMenu.Section.MONEY -> "Bank, vault, transactions and budgets"
        GuildRedesignSectionMenu.Section.LEVEL -> "XP, quests, perks and statistics"
        GuildRedesignSectionMenu.Section.HOMES -> "Homes, ally access, tracking and land"
        GuildRedesignSectionMenu.Section.ALLIES -> "Relations, requests, wars and peace"
        GuildRedesignSectionMenu.Section.PARTIES -> "Parties, requests, LFG and party chat"
        GuildRedesignSectionMenu.Section.CUSTOMIZE -> "Description, tag, emoji, banner and theme"
        GuildRedesignSectionMenu.Section.SETTINGS -> "Access, mode, integrations and danger zone"
    }

    private fun addFooter(pane: StaticPane) {
        val index = ItemStack.of(Material.KNOWLEDGE_BOOK)
        GuildRedesignSectionMenu.setMeta(index, Component.text("Feature Index", NamedTextColor.YELLOW), listOf(
            Component.text("Can't find something? Every guild feature is indexed here.", NamedTextColor.GRAY),
        ))
        pane.addItem(GuiItem(index) { menuNavigator.openMenu(GuildRedesignFeatureIndexMenu(menuNavigator, player, guild)) }, 0, 5)

        val help = ItemStack.of(Material.BOOK)
        GuildRedesignSectionMenu.setMeta(help, Component.text("How Guilds Work", NamedTextColor.AQUA), listOf(
            Component.text("Short contextual help instead of a giant /help wall.", NamedTextColor.GRAY),
        ))
        pane.addItem(GuiItem(help), 4, 5)

        val close = NexoItemProvider.getItemStackOrFallback("lg_redesign_close") { ItemStack.of(Material.BARRIER) }
        GuildRedesignSectionMenu.setMeta(close, Component.text("Close", NamedTextColor.RED), emptyList())
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }
}
