package net.lumalyte.lg.interaction.menus.guild

import com.nexomc.nexo.utils.AdventureUtils
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Focused Guild Home visual preview for Paper 26.2.
 *
 * The complete top-screen presentation is one Nexo glyph texture. The frame, eight card wells,
 * Minecraft-themed artwork and labels are baked into that single skin, so no custom item models
 * sit on top of the GUI and no filler/hitbox items can render as missing textures.
 *
 * Slots remain empty. Bukkit still reports clicks on empty inventory slots, so the skin itself is
 * the UI while this class only supplies the invisible click map.
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
        val summary: String,
    ) {
        MEMBERS("Members & Ranks", "Roster, ranks, permissions and invites"),
        MONEY("Money & Vault", "Bank, vault, transactions and contributions"),
        LEVEL("Level & Quests", "XP, quests, perks and guild statistics"),
        HOMES("Homes & Land", "Homes, access, tracking and territory tools"),
        ALLIES("Allies & War", "Allies, enemies, truces, requests and wars"),
        PARTIES("Parties & LFG", "Parties, requests, LFG and party tools"),
        CUSTOMIZE("Customize Guild", "Description, banner, emoji, tag and appearance"),
        SETTINGS("Guild Settings", "Access, mode, integrations and advanced settings"),
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
        val inventory = Bukkit.createInventory(newHolder, 54, redesignTitle())
        newHolder.backingInventory = inventory
        holder = newHolder

        Card.entries.forEachIndexed { index, card ->
            mapCard((index % 4) * 2, if (index < 4) 1 else 3, card)
        }

        player.openInventory(inventory)
    }

    private fun mapCard(x: Int, y: Int, card: Card) {
        listOf(
            slot(x, y),
            slot(x + 1, y),
            slot(x, y + 1),
            slot(x + 1, y + 1),
        ).forEach { cardBySlot[it] = card }
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
                    .append(Component.text(" — ${card.summary}", NamedTextColor.GRAY)),
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

    /**
     * Nexo's serializer owns the <shift> and <glyph> tags. Passing the resolved Adventure
     * component to Paper is what actually renders the full custom GUI skin in the inventory title.
     */
    private fun redesignTitle(): Component = try {
        AdventureUtils.NEXO_SERIALIZER.deserialize(
            "<shift:-9><glyph:guild_redesign_bg_home_6_row>"
        )
    } catch (_: Throwable) {
        Component.text("Guild Home")
    }

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
