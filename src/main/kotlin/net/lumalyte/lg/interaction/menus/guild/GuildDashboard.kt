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

/**
 * Focused Guild Home redesign preview.
 *
 * The background owns the frame and eight 2x2 card wells. Nexo items are transparent Minecraft-
 * themed symbols rendered oversized inside those wells; invisible neighboring hitboxes make each
 * apparent card clickable without baking a separate frame into every icon.
 *
 * This preview intentionally leaves the deeper feature screens on their existing implementations
 * so the home page can be judged in isolation before the full menu migration continues.
 */
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

        val cards = Card.entries
        cards.forEachIndexed { index, card ->
            val x = (index % 4) * 2
            val y = if (index < 4) 1 else 3
            addCard(pane, x, y, card)
        }

        addFooter(pane)
        gui.show(player)
    }

    private fun addHeader(pane: StaticPane) {
        val rank = rankService.getPlayerRank(player.uniqueId, guild.id)
        val members = memberService.getMemberCount(guild.id)
        val emoji = guildService.getEmoji(guild.id)
        val displayName = if (emoji.isNullOrBlank()) guild.name else "$emoji ${guild.name}"

        val identity = ItemStack.of(Material.BELL)
        setMeta(
            identity,
            Component.text(displayName, NamedTextColor.GOLD),
            listOf(
                Component.text("Guild Home", NamedTextColor.AQUA),
                Component.text("Everything important starts here.", NamedTextColor.GRAY),
            ),
        )
        pane.addItem(GuiItem(identity), 0, 0)

        val memberItem = ItemStack.of(Material.PLAYER_HEAD)
        setMeta(
            memberItem,
            Component.text("$members Members", NamedTextColor.WHITE),
            listOf(Component.text("Open Members & Ranks below", NamedTextColor.GRAY)),
        )
        pane.addItem(GuiItem(memberItem), 3, 0)

        val rankItem = ItemStack.of(Material.GOLDEN_HELMET)
        setMeta(
            rankItem,
            Component.text(rank?.name ?: "Member", NamedTextColor.GOLD),
            listOf(Component.text("Your guild rank", NamedTextColor.GRAY)),
        )
        pane.addItem(GuiItem(rankItem), 5, 0)

        val bankItem = ItemStack.of(Material.GOLD_INGOT)
        setMeta(
            bankItem,
            Component.text("Guild Bank", NamedTextColor.YELLOW),
            listOf(
                Component.text(guild.bankBalance.toString(), NamedTextColor.WHITE),
                Component.text("Open Money & Vault below", NamedTextColor.GRAY),
            ),
        )
        pane.addItem(GuiItem(bankItem), 8, 0)
    }

    private fun addCard(pane: StaticPane, x: Int, y: Int, card: Card) {
        val icon = NexoItemProvider.getItemStackOrFallback(card.iconId) { ItemStack.of(card.fallback) }
        setMeta(
            icon,
            Component.text(card.title, NamedTextColor.WHITE),
            listOf(
                Component.text(card.summary, NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Click to open", NamedTextColor.AQUA),
            ),
        )

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
        val info = ItemStack.of(Material.KNOWLEDGE_BOOK)
        setMeta(
            info,
            Component.text("Guild Information", NamedTextColor.YELLOW),
            listOf(
                Component.text("View the guild overview and public details", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Click to open", NamedTextColor.AQUA),
            ),
        )
        pane.addItem(GuiItem(info) {
            menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, guild))
        }, 0, 5)

        val guide = ItemStack.of(Material.BOOK)
        setMeta(
            guide,
            Component.text("Eight simple starting points", NamedTextColor.AQUA),
            listOf(
                Component.text("Pick what you want to do instead of", NamedTextColor.GRAY),
                Component.text("memorizing commands or plugin terminology.", NamedTextColor.GRAY),
                Component.text("Hover any icon for a short explanation.", NamedTextColor.DARK_GRAY),
            ),
        )
        pane.addItem(GuiItem(guide), 4, 5)

        val close = ItemStack.of(Material.BARRIER)
        setMeta(close, Component.text("Close", NamedTextColor.RED), emptyList())
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
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
     * Temporary preview-branch compatibility references. The focused preview uses new English card
     * labels, but the normal dashboard locale entries remain part of the production locale contract.
     * Keeping the references here avoids deleting translations just to test this one screen.
     */
    @Suppress("unused")
    private fun retainDashboardLocaleReferences() {
        listOf(
            "information", "members", "ranks", "quests", "economy",
            "settings", "progression", "diplomacy", "warfare", "statistics",
        ).forEach { key ->
            lang.gui("menu.dashboard.item.$key.name")
            lang.gui("menu.dashboard.item.$key.lore.line_1")
            lang.gui("menu.dashboard.item.$key.lore.line_2")
        }
        lang.gui("menu.dashboard.item.guild_info.name", "display_name" to guild.name)
        lang.gui("menu.dashboard.item.guild_info.lore.members", "member_count" to 0)
        lang.gui("menu.dashboard.item.guild_info.lore.ranks", "rank_count" to 0)
        lang.gui("menu.dashboard.item.guild_info.lore.balance", "balance" to 0)
        lang.gui("menu.dashboard.item.guild_info.lore.prompt_line_1")
        lang.gui("menu.dashboard.item.guild_info.lore.prompt_line_2")
    }
}
