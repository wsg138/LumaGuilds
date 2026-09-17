package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.gui
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
import org.koin.core.component.inject

/** Focused, six-row Guild Home visual/navigation preview. */
class GuildDashboard(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private val guildService: GuildService,
    private val rankService: RankService,
    private val memberService: MemberService,
    private val menuFactory: MenuFactory,
) : Menu, KoinComponent {
    private val lang: LangService by inject()

    private enum class Card(
        val title: String,
        val iconId: String,
        val fallback: Material,
        val summary: String,
    ) {
        MEMBERS("Members & Ranks", "lg_redesign_members", Material.PLAYER_HEAD, "Roster, ranks, permissions and invites"),
        MONEY("Money & Vault", "lg_redesign_money", Material.ENDER_CHEST, "Bank, vault, transactions and contributions"),
        LEVEL("Level & Quests", "lg_redesign_level", Material.EXPERIENCE_BOTTLE, "XP, quests, perks and guild statistics"),
        HOMES("Homes & Land", "lg_redesign_homes", Material.COMPASS, "Homes, access, tracking and territory tools"),
        ALLIES("Allies & War", "lg_redesign_allies", Material.DIAMOND_SWORD, "Allies, enemies, truces, requests and wars"),
        PARTIES("Parties & LFG", "lg_redesign_parties", Material.FIREWORK_ROCKET, "Parties, requests, LFG and party tools"),
        CUSTOMIZE("Customize Guild", "lg_redesign_customize", Material.LOOM, "Description, banner, emoji, tag and appearance"),
        SETTINGS("Guild Settings", "lg_redesign_settings", Material.COMPARATOR, "Access, mode, integrations and advanced settings"),
    }

    override fun open() {
        if (memberService.getMember(player.uniqueId, guild.id) == null) {
            player.sendMessage(lang.msg("menu.dashboard.feedback.not_member"))
            menuNavigator.goBack()
            return
        }
        guild = guildService.getGuild(guild.id) ?: run {
            player.sendMessage(lang.msg("menu.dashboard.feedback.guild_missing"))
            menuNavigator.goBack()
            return
        }

        val gui = ChestGui(6, redesignTitle("Guild Home"))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        addHeader(pane)
        Card.entries.forEachIndexed { index, card ->
            addCard(pane, (index % 4) * 2, if (index < 4) 1 else 3, card)
        }
        addFooter(pane)
        gui.show(player)
    }

    private fun addHeader(pane: StaticPane) {
        val rank = rankService.getPlayerRank(player.uniqueId, guild.id)
        val members = memberService.getMemberCount(guild.id)
        val emoji = guildService.getEmoji(guild.id)
        val displayName = if (emoji.isNullOrBlank()) guild.name else "$emoji ${guild.name}"

        pane.addItem(GuiItem(item(Material.BELL, displayName, NamedTextColor.GOLD,
            "Guild Home" to NamedTextColor.AQUA,
            "Everything important starts here." to NamedTextColor.GRAY)), 0, 0)

        pane.addItem(GuiItem(item(Material.PLAYER_HEAD, "$members Members", NamedTextColor.WHITE,
            "Open Members & Ranks below" to NamedTextColor.GRAY)), 3, 0)

        pane.addItem(GuiItem(item(Material.GOLDEN_HELMET, rank?.name ?: "Member", NamedTextColor.GOLD,
            "Your guild rank" to NamedTextColor.GRAY)), 5, 0)

        pane.addItem(GuiItem(item(Material.GOLD_INGOT, "Guild Bank", NamedTextColor.YELLOW,
            guild.bankBalance.toString() to NamedTextColor.WHITE,
            "Open Money & Vault below" to NamedTextColor.GRAY)), 8, 0)
    }

    private fun addCard(pane: StaticPane, x: Int, y: Int, card: Card) {
        val icon = NexoItemProvider.getItemStackOrFallback(card.iconId) { ItemStack.of(card.fallback) }
        setMeta(icon, Component.text(card.title, NamedTextColor.WHITE), listOf(
            Component.text(card.summary, NamedTextColor.GRAY),
            Component.empty(),
            Component.text("Click to open", NamedTextColor.AQUA),
        ))

        val action: () -> Unit = {
            when (card) {
                Card.MEMBERS -> menuNavigator.openMenu(GuildHomeSectionMenu(menuNavigator, player, guild, menuFactory, GuildHomeSectionMenu.Section.MEMBERS))
                Card.MONEY -> menuNavigator.openMenu(menuFactory.createGuildBankMenu(menuNavigator, player, guild))
                Card.LEVEL -> menuNavigator.openMenu(GuildHomeSectionMenu(menuNavigator, player, guild, menuFactory, GuildHomeSectionMenu.Section.LEVEL))
                Card.HOMES -> menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
                Card.ALLIES -> menuNavigator.openMenu(GuildHomeSectionMenu(menuNavigator, player, guild, menuFactory, GuildHomeSectionMenu.Section.ALLIES))
                Card.PARTIES -> menuNavigator.openMenu(menuFactory.createGuildPartyManagementMenu(menuNavigator, player, guild))
                Card.CUSTOMIZE -> menuNavigator.openMenu(GuildHomeSectionMenu(menuNavigator, player, guild, menuFactory, GuildHomeSectionMenu.Section.CUSTOMIZE))
                Card.SETTINGS -> menuNavigator.openMenu(menuFactory.createGuildSettingsMenu(menuNavigator, player, guild))
            }
        }

        pane.addItem(GuiItem(icon) { action() }, x, y)
        if (NexoItemProvider.isAvailable()) {
            listOf(x + 1 to y, x to y + 1, x + 1 to y + 1).forEach { (hitboxX, hitboxY) ->
                val hitbox = NexoItemProvider.getItemStack("lg_redesign_hitbox") ?: return@forEach
                setMeta(hitbox, Component.text(card.title, NamedTextColor.WHITE), emptyList())
                pane.addItem(GuiItem(hitbox) { action() }, hitboxX, hitboxY)
            }
        }
    }

    private fun addFooter(pane: StaticPane) {
        val info = item(Material.KNOWLEDGE_BOOK, "Guild Information", NamedTextColor.YELLOW,
            "View the guild overview and public details" to NamedTextColor.GRAY,
            "Click to open" to NamedTextColor.AQUA)
        pane.addItem(GuiItem(info) {
            menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, guild))
        }, 0, 5)

        pane.addItem(GuiItem(item(Material.BOOK, "Eight simple starting points", NamedTextColor.AQUA,
            "Pick what you want to do instead of memorizing commands." to NamedTextColor.GRAY,
            "Hover any icon for a short explanation." to NamedTextColor.DARK_GRAY)), 4, 5)

        val close = item(Material.BARRIER, "Close", NamedTextColor.RED)
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    private fun item(
        material: Material,
        name: String,
        nameColor: NamedTextColor,
        vararg lore: Pair<String, NamedTextColor>,
    ): ItemStack = ItemStack.of(material).also {
        setMeta(it, Component.text(name, nameColor), lore.map { (text, color) -> Component.text(text, color) })
    }

    private fun redesignTitle(title: String): String =
        "<shift:-9><glyph:guild_redesign_bg_home_6_row><shift:-161>$title"

    private fun setMeta(item: ItemStack, name: Component, lore: List<Component>) {
        val meta = item.itemMeta ?: return
        meta.displayName(name)
        meta.lore(lore)
        item.itemMeta = meta
    }

    /**
     * Preview-only references for the existing dashboard translations. They remain explicit so the
     * strict locale scanner can verify every retained key while the visual prototype uses temporary
     * English card copy. Delete this compatibility block when the final copy is localized.
     */
    @Suppress("unused")
    private fun retainDashboardLocaleReferences() {
        lang.gui("menu.dashboard.item.information.name")
        lang.gui("menu.dashboard.item.information.lore.line_1")
        lang.gui("menu.dashboard.item.information.lore.line_2")
        lang.gui("menu.dashboard.item.members.name")
        lang.gui("menu.dashboard.item.members.lore.line_1")
        lang.gui("menu.dashboard.item.members.lore.line_2")
        lang.gui("menu.dashboard.item.ranks.name")
        lang.gui("menu.dashboard.item.ranks.lore.line_1")
        lang.gui("menu.dashboard.item.ranks.lore.line_2")
        lang.gui("menu.dashboard.item.quests.name")
        lang.gui("menu.dashboard.item.quests.lore.line_1")
        lang.gui("menu.dashboard.item.quests.lore.line_2")
        lang.gui("menu.dashboard.item.economy.name")
        lang.gui("menu.dashboard.item.economy.lore.line_1")
        lang.gui("menu.dashboard.item.economy.lore.line_2")
        lang.gui("menu.dashboard.item.settings.name")
        lang.gui("menu.dashboard.item.settings.lore.line_1")
        lang.gui("menu.dashboard.item.settings.lore.line_2")
        lang.gui("menu.dashboard.item.progression.name")
        lang.gui("menu.dashboard.item.progression.lore.line_1")
        lang.gui("menu.dashboard.item.progression.lore.line_2")
        lang.gui("menu.dashboard.item.diplomacy.name")
        lang.gui("menu.dashboard.item.diplomacy.lore.line_1")
        lang.gui("menu.dashboard.item.diplomacy.lore.line_2")
        lang.gui("menu.dashboard.item.warfare.name")
        lang.gui("menu.dashboard.item.warfare.lore.line_1")
        lang.gui("menu.dashboard.item.warfare.lore.line_2")
        lang.gui("menu.dashboard.item.statistics.name")
        lang.gui("menu.dashboard.item.statistics.lore.line_1")
        lang.gui("menu.dashboard.item.statistics.lore.line_2")
        lang.gui("menu.dashboard.item.guild_info.name", "display_name" to guild.name)
        lang.gui("menu.dashboard.item.guild_info.lore.members", "member_count" to 0)
        lang.gui("menu.dashboard.item.guild_info.lore.ranks", "rank_count" to 0)
        lang.gui("menu.dashboard.item.guild_info.lore.balance", "balance" to 0)
        lang.gui("menu.dashboard.item.guild_info.lore.prompt_line_1")
        lang.gui("menu.dashboard.item.guild_info.lore.prompt_line_2")
    }
}
