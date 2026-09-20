package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
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
import org.koin.core.component.inject

/**
 * Player-intent navigation for the guild UI.
 *
 * This layer does not replace the existing guild feature implementations. It reorganizes them so
 * players can discover every major feature from /guild without learning commands or reading a wiki.
 * Clicking a feature always opens the live implementation (or, for the physical vault command,
 * executes the existing command that opens that implementation); there are no prototype dead ends.
 */
class GuildRedesignSectionMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val section: Section,
) : Menu, KoinComponent {

    private val menuFactory: MenuFactory by inject()

    enum class Section(val title: String, val iconId: String, val fallback: Material) {
        MEMBERS("Members & Ranks", "lg_redesign_members", Material.PLAYER_HEAD),
        MONEY("Money & Vault", "lg_redesign_money", Material.GOLD_BLOCK),
        LEVEL("Level & Quests", "lg_redesign_level", Material.EXPERIENCE_BOTTLE),
        HOMES("Homes & Land", "lg_redesign_homes", Material.COMPASS),
        ALLIES("Allies & War", "lg_redesign_allies", Material.DIAMOND_SWORD),
        PARTIES("Parties & LFG", "lg_redesign_parties", Material.FIREWORK_ROCKET),
        CUSTOMIZE("Customize Guild", "lg_redesign_customize", Material.PAINTING),
        SETTINGS("Guild Settings", "lg_redesign_settings", Material.COMPARATOR),
    }

    private enum class Action {
        MEMBER_MANAGEMENT,
        INVITE,
        RANK_MANAGEMENT,
        PROMOTION,
        GUILD_SETTINGS,
        BANK,
        PHYSICAL_VAULT,
        TRANSACTIONS,
        CONTRIBUTIONS,
        BUDGET,
        AUTOMATION,
        SECURITY,
        PROGRESSION,
        QUESTS,
        STATISTICS,
        HOMES,
        ALLY_HOME_ACCESS,
        CLAIMS,
        RELATIONS,
        ALLIES,
        ENEMIES,
        REQUESTS,
        DECLARE_WAR,
        WAR_MANAGEMENT,
        PEACE,
        PARTY_MANAGEMENT,
        PARTY_CREATE,
        LFG,
        DESCRIPTION,
        TAG,
        EMOJI,
        BANNER,
        MODE,
        INFO,
        LEAVE,
        DISBAND,
    }

    private data class Feature(
        val name: String,
        val material: Material,
        val description: String,
        val action: Action,
        val command: String? = null,
        val attention: String? = null,
    )

    override fun open() {
        val gui = ChestGui(6, redesignTitle(section.title))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val icon = NexoItemProvider.getItemStackOrFallback(section.iconId) { ItemStack.of(section.fallback) }
        setMeta(
            icon,
            Component.text(section.title, NamedTextColor.AQUA),
            listOf(
                Component.text("Everything related to this task is grouped here.", NamedTextColor.GRAY),
                Component.text("Commands are optional shortcuts, not required knowledge.", NamedTextColor.DARK_GRAY),
            ),
        )
        pane.addItem(GuiItem(icon), 0, 0)

        val guildItem = ItemStack.of(Material.BELL)
        setMeta(
            guildItem,
            Component.text(guild.name, NamedTextColor.GOLD),
            listOf(Component.text("Guild Home → ${section.title}", NamedTextColor.GRAY)),
        )
        pane.addItem(GuiItem(guildItem), 4, 0)

        features(section).forEachIndexed { index, feature ->
            val x = 1 + (index % 4) * 2
            val y = 1 + (index / 4)
            if (y <= 4) addFeature(pane, x, y, feature)
        }

        addFooter(pane)
        gui.show(player)
    }

    private fun addFeature(pane: StaticPane, x: Int, y: Int, feature: Feature) {
        val item = ItemStack.of(feature.material)
        val lore = mutableListOf<Component>(Component.text(feature.description, NamedTextColor.GRAY))
        feature.attention?.let {
            lore += Component.empty()
            lore += Component.text(it, NamedTextColor.YELLOW)
        }
        feature.command?.let {
            lore += Component.empty()
            lore += Component.text("Command shortcut: $it", NamedTextColor.DARK_GRAY)
        }
        lore += Component.empty()
        lore += Component.text("Click to open", NamedTextColor.AQUA)
        setMeta(item, Component.text(feature.name, NamedTextColor.WHITE), lore)
        pane.addItem(GuiItem(item) { openFeature(feature.action) }, x, y)
    }

    private fun openFeature(action: Action) {
        val destination: Menu? = when (action) {
            Action.MEMBER_MANAGEMENT -> menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild)
            Action.INVITE -> menuFactory.createGuildInviteMenu(menuNavigator, player, guild)
            Action.RANK_MANAGEMENT -> menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild)
            Action.PROMOTION -> menuFactory.createGuildPromotionMenu(menuNavigator, player, guild)
            Action.GUILD_SETTINGS -> menuFactory.createGuildSettingsMenu(menuNavigator, player, guild)
            Action.BANK -> menuFactory.createGuildBankMenu(menuNavigator, player, guild)
            Action.PHYSICAL_VAULT -> {
                player.closeInventory()
                player.performCommand("guild vault")
                null
            }
            Action.TRANSACTIONS -> menuFactory.createGuildBankTransactionHistoryMenu(menuNavigator, player, guild)
            Action.CONTRIBUTIONS -> menuFactory.createGuildMemberContributionsMenu(menuNavigator, player, guild)
            Action.BUDGET -> menuFactory.createGuildBankBudgetMenu(menuNavigator, player, guild)
            Action.AUTOMATION -> menuFactory.createGuildBankAutomationMenu(menuNavigator, player, guild)
            Action.SECURITY -> menuFactory.createGuildBankSecurityMenu(menuNavigator, player, guild)
            Action.PROGRESSION -> menuFactory.createGuildProgressionMenu(menuNavigator, player, guild)
            Action.QUESTS -> menuFactory.createGuildQuestsMenu(menuNavigator, player, guild)
            Action.STATISTICS -> menuFactory.createGuildStatisticsMenu(menuNavigator, player, guild)
            Action.HOMES -> menuFactory.createGuildHomeMenu(menuNavigator, player, guild)
            Action.ALLY_HOME_ACCESS -> menuFactory.createAllyHomeAccessMenu(menuNavigator, player, guild)
            Action.CLAIMS -> menuFactory.createClaimListMenu(menuNavigator, player)
            Action.RELATIONS -> menuFactory.createGuildRelationsMenu(menuNavigator, player, guild)
            Action.ALLIES -> menuFactory.createAlliesListMenu(menuNavigator, player, guild)
            Action.ENEMIES -> menuFactory.createEnemiesListMenu(menuNavigator, player, guild)
            Action.REQUESTS -> menuFactory.createIncomingRequestsMenu(menuNavigator, player, guild)
            Action.DECLARE_WAR -> menuFactory.createGuildWarDeclarationMenu(menuNavigator, player, guild)
            Action.WAR_MANAGEMENT -> menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild)
            Action.PEACE -> menuFactory.createPeaceAgreementMenu(menuNavigator, player, guild)
            Action.PARTY_MANAGEMENT -> menuFactory.createGuildPartyManagementMenu(menuNavigator, player, guild)
            Action.PARTY_CREATE -> menuFactory.createPartyCreationMenu(menuNavigator, player, guild)
            Action.LFG -> menuFactory.createLfgBrowserMenu(menuNavigator, player)
            Action.DESCRIPTION -> menuFactory.createDescriptionEditorMenu(menuNavigator, player, guild)
            Action.TAG -> menuFactory.createTagEditorMenu(menuNavigator, player, guild)
            Action.EMOJI -> menuFactory.createGuildEmojiMenu(menuNavigator, player, guild)
            Action.BANNER -> menuFactory.createGuildBannerMenu(menuNavigator, player, guild)
            Action.MODE -> menuFactory.createGuildModeMenu(menuNavigator, player, guild)
            Action.INFO -> menuFactory.createGuildInfoMenu(menuNavigator, player, guild)
            Action.LEAVE -> menuFactory.createGuildLeaveConfirmationMenu(menuNavigator, player, guild)
            Action.DISBAND -> menuFactory.createGuildDisbandConfirmationMenu(menuNavigator, player, guild)
        }
        destination?.let(menuNavigator::openMenu)
    }

    private fun addFooter(pane: StaticPane) {
        val back = ItemStack.of(Material.ARROW)
        setMeta(back, Component.text("Back", NamedTextColor.AQUA), listOf(Component.text("Return to Guild Home", NamedTextColor.GRAY)))
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 0, 5)

        val index = ItemStack.of(Material.KNOWLEDGE_BOOK)
        setMeta(index, Component.text("Feature Index", NamedTextColor.YELLOW), listOf(Component.text("Browse all eight guild feature groups.", NamedTextColor.GRAY)))
        pane.addItem(GuiItem(index) { menuNavigator.openMenu(GuildRedesignFeatureIndexMenu(menuNavigator, player, guild)) }, 4, 5)

        val close = ItemStack.of(Material.BARRIER)
        setMeta(close, Component.text("Close", NamedTextColor.RED), emptyList())
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    private fun features(section: Section): List<Feature> = when (section) {
        Section.MEMBERS -> listOf(
            Feature("Member Directory", Material.PLAYER_HEAD, "View the full roster and open member actions.", Action.MEMBER_MANAGEMENT, "/g members"),
            Feature("Invite Player", Material.LIME_DYE, "Invite someone to the guild.", Action.INVITE, "/g invite <player>"),
            Feature("Ranks & Permissions", Material.GOLDEN_HELMET, "Create, edit and inspect the rank hierarchy and permissions.", Action.RANK_MANAGEMENT, "/g ranks"),
            Feature("Promote / Demote", Material.EXPERIENCE_BOTTLE, "Choose a member and change their rank.", Action.PROMOTION),
            Feature("Kick / Moderate", Material.ANVIL, "Open member management for staffing actions.", Action.MEMBER_MANAGEMENT),
            Feature("Join Requirements", Material.OAK_SIGN, "Manage guild access and joining rules.", Action.GUILD_SETTINGS),
            Feature("Pending Invites", Material.PAPER, "Review member and invitation activity from the member tools.", Action.MEMBER_MANAGEMENT, attention = "Keep staffing actions in one predictable place."),
        )
        Section.MONEY -> listOf(
            Feature("Guild Bank", Material.GOLD_BLOCK, "Balance, deposits, withdrawals and quick actions.", Action.BANK, "/g bal"),
            Feature("Deposit / Withdraw", Material.EMERALD, "Use the bank's quick or custom amount controls.", Action.BANK),
            Feature("Physical Vault", Material.ENDER_CHEST, "Open the guild's physical shared vault.", Action.PHYSICAL_VAULT, "/g vault"),
            Feature("Transactions", Material.BOOK, "View guild-bank transaction history.", Action.TRANSACTIONS),
            Feature("Contributions", Material.PLAYER_HEAD, "See member economy contributions.", Action.CONTRIBUTIONS),
            Feature("Budget", Material.CHEST_MINECART, "Configure guild spending limits and budgets.", Action.BUDGET),
            Feature("Automation & Rewards", Material.COMPARATOR, "Manage bank automation and reward rules.", Action.AUTOMATION),
            Feature("Security", Material.TRIPWIRE_HOOK, "Withdrawal permissions and bank safety controls.", Action.SECURITY),
            Feature("Economy Statistics", Material.FILLED_MAP, "Open guild statistics for economy trends and totals.", Action.STATISTICS),
        )
        Section.LEVEL -> listOf(
            Feature("Guild Level & XP", Material.EXPERIENCE_BOTTLE, "Current level, XP progress and progression details.", Action.PROGRESSION),
            Feature("Weekly Quests", Material.CLOCK, "Active quests, progress and rewards.", Action.QUESTS, attention = "Completed quests stay visible here."),
            Feature("Unlocked Perks", Material.NETHER_STAR, "See progression rewards already unlocked.", Action.PROGRESSION),
            Feature("Next Rewards", Material.CHEST, "Preview upcoming guild-level rewards.", Action.PROGRESSION),
            Feature("How to Earn XP", Material.DIAMOND_PICKAXE, "See progression sources without searching the wiki.", Action.PROGRESSION),
            Feature("Guild Statistics", Material.BOOKSHELF, "Activity, wars, economy and membership statistics.", Action.STATISTICS),
        )
        Section.HOMES -> listOf(
            Feature("Guild Homes", Material.COMPASS, "View and manage guild homes.", Action.HOMES),
            Feature("Set / Move Home", Material.RECOVERY_COMPASS, "Create or move a home if your rank allows it.", Action.HOMES),
            Feature("Home Access", Material.OAK_DOOR, "Manage access rules for guild homes.", Action.HOMES),
            Feature("Ally Home Access", Material.ENDER_PEARL, "Choose which allied guilds may use the ally home.", Action.ALLY_HOME_ACCESS),
            Feature("Tracking", Material.SPYGLASS, "Open settings for guild tracking and integrations.", Action.GUILD_SETTINGS),
            Feature("Claims / Territory", Material.GRASS_BLOCK, "Open the claim list and territory tools when claims are enabled.", Action.CLAIMS),
            Feature("Claim Trust & Flags", Material.OAK_FENCE_GATE, "Start from the claim list, then choose a claim's trust and flag tools.", Action.CLAIMS),
        )
        Section.ALLIES -> listOf(
            Feature("Relations Overview", Material.FILLED_MAP, "Allies, enemies, truces and diplomatic status.", Action.RELATIONS),
            Feature("Allies", Material.DIAMOND, "View and manage allied guilds.", Action.ALLIES),
            Feature("Enemies", Material.REDSTONE, "View enemy guilds and hostile relations.", Action.ENEMIES),
            Feature("Truces", Material.WHITE_BANNER, "Open diplomacy tools for active truces.", Action.RELATIONS),
            Feature("Requests", Material.PAPER, "Review incoming diplomatic requests.", Action.REQUESTS, attention = "Pending diplomacy is no longer hidden in chat."),
            Feature("Declare War", Material.IRON_SWORD, "Choose a target and configure a declaration.", Action.DECLARE_WAR),
            Feature("Active Wars", Material.DIAMOND_SWORD, "Current wars, declarations, objectives and status.", Action.WAR_MANAGEMENT),
            Feature("War History & Stats", Material.BOOKSHELF, "Past wars and warfare statistics.", Action.WAR_MANAGEMENT),
            Feature("Peace", Material.WHITE_WOOL, "Review or propose peace agreements.", Action.PEACE),
        )
        Section.PARTIES -> listOf(
            Feature("Party Management", Material.FIREWORK_ROCKET, "Active parties, requests, moderation and access settings.", Action.PARTY_MANAGEMENT),
            Feature("Create Party", Material.NETHER_STAR, "Create a new private or shared party.", Action.PARTY_CREATE),
            Feature("Party Requests", Material.PAPER, "Incoming and outgoing party requests.", Action.PARTY_MANAGEMENT, attention = "Requests live with the rest of the party controls."),
            Feature("Looking for Group", Material.SPYGLASS, "Browse the LFG system.", Action.LFG, "/g lfg"),
            Feature("Party Chat", Material.OAK_SIGN, "Open party management and current communication controls.", Action.PARTY_MANAGEMENT),
            Feature("Party Moderation", Material.ANVIL, "Moderation actions for parties and members.", Action.PARTY_MANAGEMENT),
            Feature("Party Access & Permissions", Material.IRON_DOOR, "Invite-only rules, access and permission information.", Action.PARTY_MANAGEMENT),
        )
        Section.CUSTOMIZE -> listOf(
            Feature("Description", Material.WRITABLE_BOOK, "Edit the public guild description.", Action.DESCRIPTION),
            Feature("Tag", Material.NAME_TAG, "Edit the short guild tag and preview formatting.", Action.TAG),
            Feature("Emoji", Material.FIREWORK_STAR, "Browse and select guild emoji.", Action.EMOJI),
            Feature("Banner", Material.WHITE_BANNER, "Set, preview or remove the guild banner.", Action.BANNER),
            Feature("Menu Theme", Material.PAINTING, "Open guild settings for the menu theme.", Action.GUILD_SETTINGS),
            Feature("Bannerman / Appearance", Material.SHIELD, "Open appearance and guild settings.", Action.GUILD_SETTINGS),
        )
        Section.SETTINGS -> listOf(
            Feature("Guild Information", Material.BOOK, "Name, owner, creation date and core details.", Action.INFO),
            Feature("Open / Closed", Material.OAK_DOOR, "Control whether qualified players can join directly.", Action.GUILD_SETTINGS),
            Feature("Peaceful / Hostile", Material.IRON_SWORD, "View the consequences and cooldown before switching mode.", Action.MODE),
            Feature("Join Requirements", Material.OAK_SIGN, "Keep joining rules beside the access setting.", Action.GUILD_SETTINGS),
            Feature("Integrations", Material.REDSTONE_TORCH, "Tracking, Lunar and advanced integration behavior.", Action.GUILD_SETTINGS),
            Feature("Transfer / Ownership Tools", Material.GOLDEN_HELMET, "Open guild settings for ownership-level management.", Action.GUILD_SETTINGS),
            Feature("Leave Guild", Material.OAK_DOOR, "Leave only after an explicit confirmation.", Action.LEAVE),
            Feature("Disband Guild", Material.TNT, "Permanently delete the guild after confirmation.", Action.DISBAND, attention = "Danger Zone"),
        )
    }

    companion object {
        internal fun redesignTitle(title: String): String = "<shift:-9><glyph:guild_redesign_bg_6_row><shift:-161>$title"

        internal fun setMeta(item: ItemStack, name: Component, lore: List<Component>) {
            val meta = item.itemMeta ?: return
            meta.displayName(name)
            meta.lore(lore)
            item.itemMeta = meta
        }
    }
}

class GuildRedesignFeatureIndexMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
) : Menu {
    override fun open() {
        val gui = ChestGui(6, GuildRedesignSectionMenu.redesignTitle("Feature Index"))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        GuildRedesignSectionMenu.Section.entries.forEachIndexed { index, section ->
            val item = NexoItemProvider.getItemStackOrFallback(section.iconId) { ItemStack.of(section.fallback) }
            GuildRedesignSectionMenu.setMeta(
                item,
                Component.text(section.title, NamedTextColor.WHITE),
                listOf(Component.text("Open this category", NamedTextColor.AQUA)),
            )
            pane.addItem(
                GuiItem(item) { menuNavigator.openMenu(GuildRedesignSectionMenu(menuNavigator, player, guild, section)) },
                1 + (index % 4) * 2,
                1 + (index / 4) * 2,
            )
        }

        val back = ItemStack.of(Material.ARROW)
        GuildRedesignSectionMenu.setMeta(back, Component.text("Back", NamedTextColor.AQUA), emptyList())
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 0, 5)

        val close = ItemStack.of(Material.BARRIER)
        GuildRedesignSectionMenu.setMeta(close, Component.text("Close", NamedTextColor.RED), emptyList())
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
        gui.show(player)
    }
}
