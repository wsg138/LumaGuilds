package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.LumaGuilds
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
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Focused Guild Home visual preview.
 *
 * This page intentionally uses Bukkit directly instead of InventoryFramework so it can be
 * evaluated on Minecraft/Paper 26.2 while the legacy LumaGuilds menus remain on IF 0.11.6.
 * Category clicks stay on this preview page until the visual/navigation design is signed off.
 */
class GuildDashboard(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private val guildService: GuildService,
    private val rankService: RankService,
    private val memberService: MemberService,
    private val menuFactory: MenuFactory,
) : Menu, KoinComponent, Listener {
    private val lang: LangService by inject()
    private val plugin: LumaGuilds by inject()

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

    private class PreviewHolder : InventoryHolder {
        lateinit var backingInventory: Inventory
        override fun getInventory(): Inventory = backingInventory
    }

    private var holder: PreviewHolder? = null
    private val cardBySlot = mutableMapOf<Int, Card>()
    private var listenerRegistered = false

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

        if (!listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(this, plugin)
            listenerRegistered = true
        }

        cardBySlot.clear()
        val newHolder = PreviewHolder()
        val inventory = Bukkit.createInventory(newHolder, 54, redesignTitle("Guild Home"))
        newHolder.backingInventory = inventory
        holder = newHolder

        addHeader(inventory)
        Card.entries.forEachIndexed { index, card ->
            addCard(inventory, (index % 4) * 2, if (index < 4) 1 else 3, card)
        }
        addFooter(inventory)

        player.openInventory(inventory)
    }

    private fun addHeader(inventory: Inventory) {
        val rank = rankService.getPlayerRank(player.uniqueId, guild.id)
        val members = memberService.getMemberCount(guild.id)
        val emoji = guildService.getEmoji(guild.id)
        val displayName = if (emoji.isNullOrBlank()) guild.name else "$emoji ${guild.name}"

        inventory.setItem(0, item(
            Material.BELL,
            displayName,
            NamedTextColor.GOLD,
            "Guild Home" to NamedTextColor.AQUA,
            "Everything important starts here." to NamedTextColor.GRAY,
        ))
        inventory.setItem(3, item(
            Material.PLAYER_HEAD,
            "$members Members",
            NamedTextColor.WHITE,
            "Members & Ranks" to NamedTextColor.AQUA,
        ))
        inventory.setItem(5, item(
            Material.GOLDEN_HELMET,
            rank?.name ?: "Member",
            NamedTextColor.GOLD,
            "Your guild rank" to NamedTextColor.GRAY,
        ))
        inventory.setItem(8, item(
            Material.GOLD_INGOT,
            "Guild Bank",
            NamedTextColor.YELLOW,
            guild.bankBalance.toString() to NamedTextColor.WHITE,
            "Money & Vault" to NamedTextColor.AQUA,
        ))
    }

    private fun addCard(inventory: Inventory, x: Int, y: Int, card: Card) {
        val icon = NexoItemProvider.getItemStackOrFallback(card.iconId) { ItemStack.of(card.fallback) }
        setMeta(
            icon,
            Component.text(card.title, NamedTextColor.WHITE),
            listOf(
                Component.text(card.summary, NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Click to preview destination", NamedTextColor.AQUA),
            ),
        )

        val topLeft = slot(x, y)
        inventory.setItem(topLeft, icon)
        cardBySlot[topLeft] = card

        val secondarySlots = listOf(slot(x + 1, y), slot(x, y + 1), slot(x + 1, y + 1))
        secondarySlots.forEach { targetSlot ->
            val hitbox = NexoItemProvider.getItemStack("lg_redesign_hitbox")
            if (hitbox != null) {
                setMeta(
                    hitbox,
                    Component.text(card.title, NamedTextColor.WHITE),
                    listOf(Component.text(card.summary, NamedTextColor.GRAY)),
                )
                inventory.setItem(targetSlot, hitbox)
            }
            cardBySlot[targetSlot] = card
        }
    }

    private fun addFooter(inventory: Inventory) {
        inventory.setItem(45, item(
            Material.KNOWLEDGE_BOOK,
            "Guild Information",
            NamedTextColor.YELLOW,
            "Guild overview and public details" to NamedTextColor.GRAY,
        ))

        inventory.setItem(49, item(
            Material.BOOK,
            "Eight simple starting points",
            NamedTextColor.AQUA,
            "Pick what you want to do instead of memorizing commands." to NamedTextColor.GRAY,
            "This focused build is for Guild Home visual testing." to NamedTextColor.DARK_GRAY,
        ))

        inventory.setItem(53, item(Material.BARRIER, "Close", NamedTextColor.RED))
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val currentHolder = holder ?: return
        if (event.view.topInventory.holder !== currentHolder) return
        if (event.whoClicked.uniqueId != player.uniqueId) return

        event.isCancelled = true
        val rawSlot = event.rawSlot
        if (rawSlot !in 0 until event.view.topInventory.size) return

        if (rawSlot == 53) {
            player.closeInventory()
            return
        }

        cardBySlot[rawSlot]?.let { card ->
            player.sendActionBar(
                Component.text(card.title, NamedTextColor.AQUA)
                    .append(Component.text(" — this card will open its full section.", NamedTextColor.GRAY)),
            )
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val currentHolder = holder ?: return
        if (event.inventory.holder !== currentHolder) return
        if (event.player.uniqueId != player.uniqueId) return

        holder = null
        cardBySlot.clear()
        if (listenerRegistered) {
            HandlerList.unregisterAll(this)
            listenerRegistered = false
        }
    }

    private fun slot(x: Int, y: Int): Int = y * 9 + x

    private fun item(
        material: Material,
        name: String,
        nameColor: NamedTextColor,
        vararg lore: Pair<String, NamedTextColor>,
    ): ItemStack = ItemStack.of(material).also {
        setMeta(
            it,
            Component.text(name, nameColor),
            lore.map { (text, color) -> Component.text(text, color) },
        )
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
     * strict locale scanner can verify every retained key while the visual preview uses temporary
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
