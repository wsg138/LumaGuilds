package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.NexoItemProvider
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Click-through redesign prototype for the entire player-facing guild UI.
 * Live guild mutations are deliberately disabled until the UX is approved in-game.
 */
class GuildRedesignSectionMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val section: Section,
) : Menu, KoinComponent {

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

    private data class Feature(
        val name: String,
        val material: Material,
        val description: String,
        val command: String? = null,
        val attention: String? = null,
        val special: Special = Special.GENERIC,
    )

    private enum class Special { GENERIC, MEMBER_DIRECTORY, RANKS, PERMISSION_OVERVIEW, DANGER }

    override fun open() {
        val gui = ChestGui(6, redesignTitle(section.title))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        val icon = NexoItemProvider.getItemStackOrFallback(section.iconId) { ItemStack.of(section.fallback) }
        setMeta(icon, Component.text(section.title, NamedTextColor.AQUA), listOf(
            Component.text("Everything in this section lives here.", NamedTextColor.GRAY),
            Component.text("No wiki or command knowledge required.", NamedTextColor.DARK_GRAY),
        ))
        pane.addItem(GuiItem(icon), 0, 0)

        val status = ItemStack.of(Material.PAPER)
        setMeta(status, Component.text(guild.name, NamedTextColor.GOLD), listOf(
            Component.text("Prototype screen", NamedTextColor.YELLOW),
            Component.text("Clicks navigate; live mutations are disabled.", NamedTextColor.GRAY),
        ))
        pane.addItem(GuiItem(status), 4, 0)

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
            lore += Component.text("Shortcut: $it", NamedTextColor.DARK_GRAY)
        }
        lore += Component.empty()
        lore += Component.text("Click to open", NamedTextColor.AQUA)
        setMeta(item, Component.text(feature.name, NamedTextColor.WHITE), lore)
        pane.addItem(GuiItem(item) {
            when (feature.special) {
                Special.MEMBER_DIRECTORY -> menuNavigator.openMenu(GuildRedesignMemberDirectoryMenu(menuNavigator, player, guild))
                Special.RANKS -> menuNavigator.openMenu(GuildRedesignRanksMenu(menuNavigator, player, guild))
                Special.PERMISSION_OVERVIEW -> menuNavigator.openMenu(GuildRedesignPermissionOverviewMenu(menuNavigator, player, guild))
                else -> menuNavigator.openMenu(GuildRedesignFeatureMenu(menuNavigator, player, guild, section, feature.name, feature.description))
            }
        }, x, y)
    }

    private fun addFooter(pane: StaticPane) {
        val back = ItemStack.of(Material.ARROW)
        setMeta(back, Component.text("Back", NamedTextColor.AQUA), listOf(Component.text("Return to Guild Home", NamedTextColor.GRAY)))
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 0, 5)

        val index = ItemStack.of(Material.KNOWLEDGE_BOOK)
        setMeta(index, Component.text("Feature Index", NamedTextColor.YELLOW), listOf(Component.text("Find any guild feature without knowing where it lives.", NamedTextColor.GRAY)))
        pane.addItem(GuiItem(index) { menuNavigator.openMenu(GuildRedesignFeatureIndexMenu(menuNavigator, player, guild)) }, 4, 5)

        val close = ItemStack.of(Material.BARRIER)
        setMeta(close, Component.text("Close", NamedTextColor.RED), emptyList())
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    private fun features(section: Section): List<Feature> = when (section) {
        Section.MEMBERS -> listOf(
            Feature("Member Directory", Material.PLAYER_HEAD, "See the full guild roster at once and open any member.", "/g members", special = Special.MEMBER_DIRECTORY),
            Feature("Invite Player", Material.LIME_DYE, "Invite a player without leaving the menu.", "/g invite <player>"),
            Feature("Ranks", Material.GOLDEN_HELMET, "View rank hierarchy, member counts and edit a rank.", "/g ranks", special = Special.RANKS),
            Feature("Permissions", Material.WRITABLE_BOOK, "Human-readable permission groups instead of one giant toggle wall.", special = Special.PERMISSION_OVERVIEW),
            Feature("Promote / Demote", Material.EXPERIENCE_BOTTLE, "Pick a member, then choose the rank they should have."),
            Feature("Kick / Moderate", Material.IRON_BOOTS, "Member actions are kept on that member's profile."),
            Feature("Join Requirements", Material.OAK_SIGN, "Control who can join an open guild."),
            Feature("Pending Invites", Material.PAPER, "See invitations and requests in one place.", attention = "Shows a badge when something is waiting."),
        )
        Section.MONEY -> listOf(
            Feature("Guild Bank", Material.GOLD_BLOCK, "Balance, deposit and withdraw are the first things you see.", "/g bal"),
            Feature("Deposit", Material.LIME_DYE, "Quick amounts or enter a custom amount."),
            Feature("Withdraw", Material.RED_DYE, "Quick amounts or enter a custom amount."),
            Feature("Physical Vault", Material.ENDER_CHEST, "Open, locate and manage the guild's physical vault.", "/g vault"),
            Feature("Transactions", Material.BOOK, "Readable transaction history with player, amount and time."),
            Feature("Contributions", Material.PLAYER_HEAD, "See who has contributed to the guild economy."),
            Feature("Budget", Material.CHEST_MINECART, "View or configure guild spending limits."),
            Feature("Automation & Rewards", Material.COMPARATOR, "Bank automation, interest and reward settings."),
            Feature("Security", Material.TRIPWIRE_HOOK, "Withdrawal permissions and safety controls."),
            Feature("Bank Statistics", Material.FILLED_MAP, "Economy trends and guild-bank statistics."),
        )
        Section.LEVEL -> listOf(
            Feature("Guild Level", Material.EXPERIENCE_BOTTLE, "Current level, XP bar and exactly how much XP remains."),
            Feature("Weekly Quests", Material.CLOCK, "Active quests, progress and completed rewards.", attention = "Completion badges appear here."),
            Feature("Unlocked Perks", Material.NETHER_STAR, "Everything the guild has already unlocked."),
            Feature("Next Rewards", Material.CHEST, "Preview what the next few levels unlock."),
            Feature("How to Earn XP", Material.DIAMOND_PICKAXE, "Short, scannable list of activities that award guild XP."),
            Feature("Guild Statistics", Material.BOOKSHELF, "Activity, wars, economy and membership stats in one place."),
        )
        Section.HOMES -> listOf(
            Feature("Guild Homes", Material.COMPASS, "All guild homes in a dense list with one-click teleport."),
            Feature("Set / Move Home", Material.RECOVERY_COMPASS, "Create or move a home, if your rank allows it."),
            Feature("Home Access", Material.OAK_DOOR, "Choose which ranks can use each guild home."),
            Feature("Ally Home", Material.ENDER_PEARL, "Manage the location allies are allowed to visit."),
            Feature("Ally Access", Material.IRON_DOOR, "Control which allied guilds may use the ally home."),
            Feature("Tracking", Material.SPYGLASS, "Guild tracking and Lunar HUD behavior."),
            Feature("Claims / Territory", Material.GRASS_BLOCK, "Claim tools live here when claims are enabled.", attention = "Hidden/disabled cleanly when the server disables claims."),
            Feature("Claim Trust & Flags", Material.OAK_FENCE_GATE, "Trust, permissions and flags when claims are enabled."),
        )
        Section.ALLIES -> listOf(
            Feature("Relations Overview", Material.FILLED_MAP, "Allies, enemies and truces summarized on one screen."),
            Feature("Allies", Material.DIAMOND, "View allies and open a guild relation profile."),
            Feature("Enemies", Material.REDSTONE, "View enemies and current hostile status."),
            Feature("Truces", Material.WHITE_BANNER, "View active truces and remaining duration."),
            Feature("Requests", Material.PAPER, "Incoming and outgoing diplomacy requests together.", attention = "Pending requests get a badge."),
            Feature("Declare War", Material.IRON_SWORD, "Choose a guild, objectives, wager and confirm."),
            Feature("Active Wars", Material.DIAMOND_SWORD, "Current wars with objective progress and live status.", attention = "Active wars stay obvious."),
            Feature("War History", Material.BOOKSHELF, "Past wars, results and objective summaries."),
            Feature("Peace", Material.WHITE_WOOL, "Peace proposals and pending agreements."),
            Feature("War Statistics", Material.TOTEM_OF_UNDYING, "Wins, losses, kills, deaths and war performance."),
        )
        Section.PARTIES -> listOf(
            Feature("Active Parties", Material.FIREWORK_ROCKET, "See every party your guild can access, not only the first one."),
            Feature("Create Party", Material.NETHER_STAR, "Create a party with clear visibility and access options."),
            Feature("Party Requests", Material.PAPER, "Incoming and outgoing party requests together.", attention = "Requests get a badge."),
            Feature("Looking for Group", Material.SPYGLASS, "Browse players/guilds actively looking for a group.", "/g lfg"),
            Feature("Party Chat", Material.OAK_SIGN, "Switch chat and see the current channel."),
            Feature("Party Moderation", Material.ANVIL, "Moderation actions for a selected party/member."),
            Feature("Party Access", Material.IRON_DOOR, "Invite-only and access rules."),
            Feature("Party Permissions", Material.WRITABLE_BOOK, "Plain-language explanation of party permissions."),
        )
        Section.CUSTOMIZE -> listOf(
            Feature("Description", Material.WRITABLE_BOOK, "Edit the public guild description with a preview."),
            Feature("Tag", Material.NAME_TAG, "Edit the short guild tag and preview chat appearance."),
            Feature("Emoji", Material.FIREWORK_STAR, "Browse unlocked emoji visually."),
            Feature("Banner", Material.WHITE_BANNER, "Set, preview or remove the guild banner."),
            Feature("Menu Theme", Material.PAINTING, "Preview themes before applying them."),
            Feature("Bannerman", Material.SHIELD, "Configure the guild banner displayed on members."),
            Feature("Appearance Preview", Material.ARMOR_STAND, "See tag, emoji, banner and theme together before saving."),
        )
        Section.SETTINGS -> listOf(
            Feature("Guild Information", Material.BOOK, "Name, owner, creation date and core guild details."),
            Feature("Open / Closed", Material.OAK_DOOR, "Control whether qualified players can join directly."),
            Feature("Peaceful / Hostile", Material.IRON_SWORD, "Current mode, consequences and cooldown shown before changing."),
            Feature("Join Requirements", Material.OAK_SIGN, "Requirements belong beside the open/closed setting."),
            Feature("Integrations", Material.REDSTONE_TORCH, "Server/Lunar/integration behavior in one advanced page."),
            Feature("Transfer Ownership", Material.GOLDEN_HELMET, "Select a member and confirm ownership transfer.", special = Special.DANGER),
            Feature("Leave Guild", Material.OAK_DOOR, "Leave with an explicit confirmation and consequence summary.", special = Special.DANGER),
            Feature("Disband Guild", Material.TNT, "Permanent destruction lives in a separated Danger Zone.", special = Special.DANGER),
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

class GuildRedesignMemberDirectoryMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
) : Menu, KoinComponent {
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private var page = 0

    override fun open() {
        val gui = ChestGui(6, GuildRedesignSectionMenu.redesignTitle("Members"))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)
        val members = memberService.getGuildMembers(guild.id)
            .sortedWith(compareBy({ rankService.getRank(it.rankId)?.priority ?: Int.MAX_VALUE }, { Bukkit.getOfflinePlayer(it.playerId).name ?: "" }))
        val pageSize = 45
        val totalPages = maxOf(1, (members.size + pageSize - 1) / pageSize)
        page = page.coerceIn(0, totalPages - 1)
        members.drop(page * pageSize).take(pageSize).forEachIndexed { index, member ->
            val name = Bukkit.getOfflinePlayer(member.playerId).name ?: "Unknown Player"
            val rank = rankService.getRank(member.rankId)?.name ?: "Unknown Rank"
            val item = ItemStack.of(Material.PLAYER_HEAD)
            GuildRedesignSectionMenu.setMeta(item, Component.text(name, NamedTextColor.WHITE), listOf(
                Component.text(rank, NamedTextColor.GOLD),
                Component.text("Joined: ${member.joinedAt.toString().take(10)}", NamedTextColor.GRAY),
                Component.empty(), Component.text("Click for member actions", NamedTextColor.AQUA),
            ))
            pane.addItem(GuiItem(item) { menuNavigator.openMenu(GuildRedesignMemberProfileMenu(menuNavigator, player, guild, member.playerId)) }, index % 9, index / 9)
        }
        addSimpleFooter(pane, menuNavigator, player, page, totalPages, { if (page > 0) { page--; open() } }, { if (page < totalPages - 1) { page++; open() } })
        gui.show(player)
    }
}

class GuildRedesignMemberProfileMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val memberId: java.util.UUID,
) : Menu, KoinComponent {
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    override fun open() {
        val member = memberService.getMember(memberId, guild.id) ?: run { menuNavigator.goBack(); return }
        val memberName = Bukkit.getOfflinePlayer(memberId).name ?: "Unknown Player"
        val rank = rankService.getRank(member.rankId)
        val gui = ChestGui(6, GuildRedesignSectionMenu.redesignTitle(memberName))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)
        val head = ItemStack.of(Material.PLAYER_HEAD)
        GuildRedesignSectionMenu.setMeta(head, Component.text(memberName, NamedTextColor.AQUA), listOf(
            Component.text("Rank: ${rank?.name ?: "Unknown"}", NamedTextColor.GOLD),
            Component.text("Joined: ${member.joinedAt.toString().take(10)}", NamedTextColor.GRAY),
        ))
        pane.addItem(GuiItem(head), 4, 0)
        val actions = listOf(
            Triple("Change Rank", Material.GOLDEN_HELMET, "Pick from ranks you are allowed to assign."),
            Triple("Promote / Demote", Material.EXPERIENCE_BOTTLE, "Rank changes happen from this member profile."),
            Triple("Moderation", Material.ANVIL, "Mute / moderation tools that apply to this member."),
            Triple("Contribution Stats", Material.GOLD_INGOT, "Economy and activity contribution for this member."),
            Triple("Kick Member", Material.RED_CONCRETE, "Destructive action, clearly separated and confirmed."),
        )
        actions.forEachIndexed { i, (name, mat, desc) ->
            val item = ItemStack.of(mat)
            GuildRedesignSectionMenu.setMeta(item, Component.text(name, if (name.startsWith("Kick")) NamedTextColor.RED else NamedTextColor.WHITE), listOf(
                Component.text(desc, NamedTextColor.GRAY), Component.text("Prototype - does not mutate", NamedTextColor.DARK_GRAY)
            ))
            pane.addItem(GuiItem(item), 1 + i + if (i > 2) 1 else 0, 2)
        }
        val back = ItemStack.of(Material.ARROW)
        GuildRedesignSectionMenu.setMeta(back, Component.text("Back", NamedTextColor.AQUA), emptyList())
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 0, 5)
        gui.show(player)
    }
}

class GuildRedesignRanksMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
) : Menu, KoinComponent {
    private val rankService: RankService by inject()
    private val memberService: MemberService by inject()
    override fun open() {
        val gui = ChestGui(6, GuildRedesignSectionMenu.redesignTitle("Ranks"))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)
        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        val members = memberService.getGuildMembers(guild.id)
        ranks.take(36).forEachIndexed { index, rank ->
            val item = ItemStack.of(Material.GOLDEN_HELMET)
            val count = members.count { it.rankId == rank.id }
            GuildRedesignSectionMenu.setMeta(item, Component.text(rank.name, NamedTextColor.GOLD), listOf(
                Component.text("Priority #${rank.priority + 1}", NamedTextColor.GRAY),
                Component.text("$count member${if (count == 1) "" else "s"}", NamedTextColor.GRAY),
                Component.text("${rank.permissions.size} permissions", NamedTextColor.GRAY),
                Component.empty(), Component.text("Click to inspect permissions", NamedTextColor.AQUA),
            ))
            pane.addItem(GuiItem(item) { menuNavigator.openMenu(GuildRedesignPermissionCategoryMenu(menuNavigator, player, guild, rank)) }, index % 9, index / 9)
        }
        val create = ItemStack.of(Material.LIME_DYE)
        GuildRedesignSectionMenu.setMeta(create, Component.text("Create Rank", NamedTextColor.GREEN), listOf(Component.text("Prototype entry point", NamedTextColor.GRAY)))
        pane.addItem(GuiItem(create), 4, 4)
        addSimpleFooter(pane, menuNavigator, player, 0, 1, {}, {})
        gui.show(player)
    }
}

class GuildRedesignPermissionOverviewMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
) : Menu, KoinComponent {
    private val rankService: RankService by inject()
    override fun open() {
        val gui = ChestGui(6, GuildRedesignSectionMenu.redesignTitle("Permissions"))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)
        rankService.listRanks(guild.id).sortedBy { it.priority }.take(36).forEachIndexed { index, rank ->
            val item = ItemStack.of(Material.WRITABLE_BOOK)
            GuildRedesignSectionMenu.setMeta(item, Component.text(rank.name, NamedTextColor.GOLD), listOf(
                Component.text("${rank.permissions.size} enabled", NamedTextColor.GRAY), Component.text("Open grouped permissions", NamedTextColor.AQUA)
            ))
            pane.addItem(GuiItem(item) { menuNavigator.openMenu(GuildRedesignPermissionCategoryMenu(menuNavigator, player, guild, rank)) }, index % 9, index / 9)
        }
        addSimpleFooter(pane, menuNavigator, player, 0, 1, {}, {})
        gui.show(player)
    }
}

class GuildRedesignPermissionCategoryMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val rank: Rank,
) : Menu {
    private data class PermissionGroup(val name: String, val material: Material, val permissions: List<RankPermission>, val description: String)
    override fun open() {
        val gui = ChestGui(6, GuildRedesignSectionMenu.redesignTitle("${rank.name} Permissions"))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)
        groups().forEachIndexed { index, group ->
            val enabled = group.permissions.count { it in rank.permissions }
            val item = ItemStack.of(group.material)
            GuildRedesignSectionMenu.setMeta(item, Component.text(group.name, NamedTextColor.WHITE), listOf(
                Component.text(group.description, NamedTextColor.GRAY),
                Component.text("$enabled / ${group.permissions.size} enabled", if (enabled == group.permissions.size) NamedTextColor.GREEN else NamedTextColor.YELLOW),
                Component.empty(), Component.text("Click to view toggles", NamedTextColor.AQUA),
            ))
            pane.addItem(GuiItem(item) { menuNavigator.openMenu(GuildRedesignPermissionToggleMenu(menuNavigator, player, guild, rank, group.name, group.permissions)) }, 1 + (index % 4) * 2, 1 + index / 4)
        }
        val back = ItemStack.of(Material.ARROW)
        GuildRedesignSectionMenu.setMeta(back, Component.text("Back", NamedTextColor.AQUA), emptyList())
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 0, 5)
        gui.show(player)
    }

    private fun groups(): List<PermissionGroup> = listOf(
        PermissionGroup("Members & Ranks", Material.PLAYER_HEAD, listOf(RankPermission.MANAGE_RANKS, RankPermission.MANAGE_MEMBERS), "Ranks, member management and staffing."),
        PermissionGroup("Guild Appearance", Material.PAINTING, listOf(RankPermission.MANAGE_BANNER, RankPermission.MANAGE_EMOJI, RankPermission.MANAGE_DESCRIPTION, RankPermission.MANAGE_GUILD_SETTINGS), "Identity, appearance and general settings."),
        PermissionGroup("Homes & Territory", Material.COMPASS, listOf(RankPermission.MANAGE_HOME, RankPermission.USE_ALLY_HOMES, RankPermission.MANAGE_CLAIMS, RankPermission.MANAGE_FLAGS, RankPermission.MANAGE_PERMISSIONS, RankPermission.CREATE_CLAIMS, RankPermission.DELETE_CLAIMS), "Homes, claims, flags and territory access."),
        PermissionGroup("Relations & War", Material.DIAMOND_SWORD, listOf(RankPermission.MANAGE_RELATIONS, RankPermission.DECLARE_WAR, RankPermission.ACCEPT_ALLIANCES, RankPermission.MANAGE_MODE), "Diplomacy, alliances and warfare."),
        PermissionGroup("Parties", Material.FIREWORK_ROCKET, listOf(RankPermission.MANAGE_PARTIES, RankPermission.SEND_PARTY_REQUESTS, RankPermission.ACCEPT_PARTY_INVITES), "Party creation, requests and management."),
        PermissionGroup("Bank", Material.GOLD_BLOCK, listOf(RankPermission.DEPOSIT_TO_BANK, RankPermission.WITHDRAW_FROM_BANK, RankPermission.VIEW_BANK_TRANSACTIONS, RankPermission.MANAGE_BANK_SETTINGS), "Virtual guild-bank access."),
        PermissionGroup("Physical Vault", Material.ENDER_CHEST, listOf(RankPermission.PLACE_VAULT, RankPermission.ACCESS_VAULT, RankPermission.DEPOSIT_TO_VAULT, RankPermission.WITHDRAW_FROM_VAULT, RankPermission.MANAGE_VAULT, RankPermission.BREAK_VAULT), "Physical guild-vault permissions."),
        PermissionGroup("Communication", Material.OAK_SIGN, listOf(RankPermission.SEND_ANNOUNCEMENTS, RankPermission.SEND_PINGS, RankPermission.MODERATE_CHAT), "Announcements, pings and chat moderation."),
        PermissionGroup("Advanced", Material.REDSTONE_TORCH, listOf(RankPermission.ACCESS_ADMIN_COMMANDS, RankPermission.BYPASS_RESTRICTIONS, RankPermission.VIEW_AUDIT_LOGS, RankPermission.MANAGE_INTEGRATIONS, RankPermission.ACCESS_SHOP_CHESTS, RankPermission.EDIT_SHOP_STOCK, RankPermission.MODIFY_SHOP_PRICES), "Advanced/integration permissions kept out of the normal flow."),
    )
}

class GuildRedesignPermissionToggleMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val rank: Rank,
    private val groupName: String,
    private val permissions: List<RankPermission>,
) : Menu {
    override fun open() {
        val gui = ChestGui(6, GuildRedesignSectionMenu.redesignTitle(groupName))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)
        permissions.take(45).forEachIndexed { index, permission ->
            val enabled = permission in rank.permissions
            val item = ItemStack.of(if (enabled) Material.LIME_STAINED_GLASS_PANE else Material.RED_STAINED_GLASS_PANE)
            val label = permission.name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
            GuildRedesignSectionMenu.setMeta(item, Component.text(label, if (enabled) NamedTextColor.GREEN else NamedTextColor.RED), listOf(
                Component.text(if (enabled) "Enabled" else "Disabled", NamedTextColor.GRAY),
                Component.empty(), Component.text("Prototype: click state is not saved yet.", NamedTextColor.DARK_GRAY),
            ))
            pane.addItem(GuiItem(item), index % 9, index / 9)
        }
        val back = ItemStack.of(Material.ARROW)
        GuildRedesignSectionMenu.setMeta(back, Component.text("Back", NamedTextColor.AQUA), emptyList())
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 0, 5)
        gui.show(player)
    }
}

class GuildRedesignFeatureMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val section: GuildRedesignSectionMenu.Section,
    private val featureName: String,
    private val description: String,
) : Menu {
    override fun open() {
        val gui = ChestGui(6, GuildRedesignSectionMenu.redesignTitle(featureName))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)
        val info = ItemStack.of(section.fallback)
        GuildRedesignSectionMenu.setMeta(info, Component.text(featureName, NamedTextColor.AQUA), listOf(
            Component.text(description, NamedTextColor.GRAY), Component.empty(),
            Component.text("This is the proposed destination screen.", NamedTextColor.YELLOW),
            Component.text("Live actions are intentionally disabled for the UX prototype.", NamedTextColor.DARK_GRAY),
        ))
        pane.addItem(GuiItem(info), 4, 1)
        val primary = ItemStack.of(Material.LIME_DYE)
        GuildRedesignSectionMenu.setMeta(primary, Component.text("Primary Action", NamedTextColor.GREEN), listOf(Component.text("The most common action stays obvious and central.", NamedTextColor.GRAY)))
        pane.addItem(GuiItem(primary), 3, 2)
        val secondary = ItemStack.of(Material.BOOK)
        GuildRedesignSectionMenu.setMeta(secondary, Component.text("Details / Status", NamedTextColor.WHITE), listOf(Component.text("State and explanation live beside the action, not buried in lore.", NamedTextColor.GRAY)))
        pane.addItem(GuiItem(secondary), 5, 2)
        val back = ItemStack.of(Material.ARROW)
        GuildRedesignSectionMenu.setMeta(back, Component.text("Back", NamedTextColor.AQUA), emptyList())
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 0, 5)
        gui.show(player)
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
            GuildRedesignSectionMenu.setMeta(item, Component.text(section.title, NamedTextColor.WHITE), listOf(Component.text("Open this category", NamedTextColor.AQUA)))
            pane.addItem(GuiItem(item) { menuNavigator.openMenu(GuildRedesignSectionMenu(menuNavigator, player, guild, section)) }, 1 + (index % 4) * 2, 1 + index / 4 * 2)
        }
        val back = ItemStack.of(Material.ARROW)
        GuildRedesignSectionMenu.setMeta(back, Component.text("Back", NamedTextColor.AQUA), emptyList())
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 0, 5)
        gui.show(player)
    }
}

private fun addSimpleFooter(
    pane: StaticPane,
    navigator: MenuNavigator,
    player: Player,
    page: Int,
    totalPages: Int,
    previous: () -> Unit,
    next: () -> Unit,
) {
    val back = ItemStack.of(Material.ARROW)
    GuildRedesignSectionMenu.setMeta(back, Component.text("Back", NamedTextColor.AQUA), emptyList())
    pane.addItem(GuiItem(back) { navigator.goBack() }, 0, 5)
    if (totalPages > 1) {
        val prev = ItemStack.of(Material.ARROW)
        GuildRedesignSectionMenu.setMeta(prev, Component.text("Previous", NamedTextColor.WHITE), emptyList())
        pane.addItem(GuiItem(prev) { previous() }, 3, 5)
        val pageItem = ItemStack.of(Material.PAPER)
        GuildRedesignSectionMenu.setMeta(pageItem, Component.text("Page ${page + 1} / $totalPages", NamedTextColor.YELLOW), emptyList())
        pane.addItem(GuiItem(pageItem), 4, 5)
        val nextItem = ItemStack.of(Material.ARROW)
        GuildRedesignSectionMenu.setMeta(nextItem, Component.text("Next", NamedTextColor.WHITE), emptyList())
        pane.addItem(GuiItem(nextItem) { next() }, 5, 5)
    }
    val close = ItemStack.of(Material.BARRIER)
    GuildRedesignSectionMenu.setMeta(close, Component.text("Close", NamedTextColor.RED), emptyList())
    pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
}
