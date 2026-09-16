package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.CapPeriod
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuItemBuilder
import net.lumalyte.lg.utils.MenuSurface
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Guild progression detail screen.
 *
 * The old spiral/track layout made XP sources hard to scan. The redesign keeps the level summary
 * fixed at the top, uses three complete rows for source progress, and keeps supporting information
 * in one predictable action row.
 */
class GuildProgressionMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    @Suppress("UNUSED_PARAMETER") private val guildService: GuildService,
    private val memberService: MemberService,
    private val progressionService: ProgressionService,
    private val menuFactory: MenuFactory,
    @Suppress("UNUSED_PARAMETER") private val menuItemBuilder: MenuItemBuilder,
    @Suppress("UNUSED_PARAMETER") private val configService: ConfigService,
) : Menu, KoinComponent {

    private val lang: LangService by inject()
    private var currentPage = 0
    private val itemsPerPage = 27

    override fun open() {
        if (memberService.getMember(player.uniqueId, guild.id) == null) {
            player.sendMessage(lang.msg("menu.guild_progression.feedback.no_access"))
            menuNavigator.goBack()
            return
        }

        val progression = repoProgression() ?: run {
            player.sendMessage(lang.msg("menu.guild_progression.feedback.load_failed"))
            menuNavigator.goBack()
            return
        }
        val sourceUsage = progressionService.getSourceUsage(guild.id)
            .filter { it.source != ExperienceSource.ADMIN_BONUS }
        val totalPages = maxOf(1, (sourceUsage.size + itemsPerPage - 1) / itemsPerPage)
        currentPage = currentPage.coerceIn(0, totalPages - 1)

        val gui = ChestGui(
            6,
            MenuTitleBuilder.redesign(
                MenuSurface.DETAIL,
                lang.guiTitle(
                    "menu.guild_progression.title",
                    "page" to currentPage + 1,
                    "pages" to totalPages,
                ),
            ),
        )
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        addBackButton(pane, 0, 0)
        addGuildLevelHeader(pane, progression, sourceUsage)
        addCloseButton(pane, 8, 0)

        sourceUsage.drop(currentPage * itemsPerPage).take(itemsPerPage).forEachIndexed { index, usage ->
            addSourceItem(pane, index % 9, 1 + index / 9, usage)
        }

        addSupportingActions(pane)

        if (currentPage > 0) addPreviousPageButton(pane, 0, 5)
        if (currentPage + 1 < totalPages) addNextPageButton(pane, 8, 5)

        gui.show(player)
    }

    private fun repoProgression(): GuildProgressionDisplay? {
        val repo = org.koin.core.context.GlobalContext.get()
            .get<net.lumalyte.lg.application.persistence.ProgressionRepository>()
        val prog = repo.getGuildProgression(guild.id) ?: return null
        val (currentXp, neededXp) = progressionService.getLevelProgress(prog.totalExperience)
        val level = progressionService.getLevelFromExperience(prog.totalExperience)
        val unlockedPerks = progressionService.getUnlockedPerks(guild.id)
        return GuildProgressionDisplay(level, prog.totalExperience, currentXp, neededXp, unlockedPerks.size)
    }

    private fun addGuildLevelHeader(
        pane: StaticPane,
        prog: GuildProgressionDisplay,
        sourceUsage: List<SourceUsageView>,
    ) {
        val (_, totalXp, currentXp, neededXp, perksCount) = prog
        val percent = if (neededXp > 0) {
            (currentXp.toDouble() / neededXp.toDouble() * 100).toInt()
        } else {
            0
        }
        val totalToday = sourceUsage.filter { it.period == CapPeriod.DAILY }.sumOf { it.awardedXp }
        val bars = buildProgressBar(percent, 20)

        val item = NexoItemProvider.getItemStackOrFallback("lg_level") {
            ItemStack.of(Material.EXPERIENCE_BOTTLE)
        }.also {
            it.editMeta { meta ->
                meta.displayName(lang.gui("menu.guild_progression.level.name", "level" to prog.level))
                meta.lore(
                    listOf(
                        lang.gui(
                            "menu.guild_progression.level.progress",
                            "current" to currentXp,
                            "needed" to neededXp,
                            "percent" to percent,
                        ),
                        lang.gui("menu.guild_progression.level.bar", "bar" to bars),
                        lang.gui("menu.guild_progression.level.today", "xp" to totalToday),
                        Component.empty(),
                        lang.gui("menu.guild_progression.level.perks", "count" to perksCount),
                        lang.gui("menu.guild_progression.level.total", "xp" to totalXp),
                    ),
                )
            }
        }
        pane.addItem(GuiItem(item), 4, 0)
    }

    private fun addSupportingActions(pane: StaticPane) {
        addRankInfo(pane, 1, 4)
        addPerksInfo(pane, 3, 4)

        val quests = ItemStack.of(Material.CLOCK).also {
            it.editMeta { meta ->
                meta.displayName(lang.gui("menu.quests.item.header.name"))
                meta.lore(listOf(lang.gui("menu.guild_progression.sources.description")))
            }
        }
        pane.addItem(GuiItem(quests) {
            menuNavigator.openMenu(menuFactory.createGuildQuestsMenu(menuNavigator, player, guild))
        }, 5, 4)

        addPrestigeInfo(pane, 7, 4)
    }

    private fun buildProgressBar(percent: Int, length: Int): Component {
        val filled = (percent * length / 100).coerceIn(0, length)
        val empty = length - filled
        val filledBar = "█".repeat(filled)
        val emptyBar = "█".repeat(empty)
        return when {
            percent >= 100 -> lang.gui("menu.guild_progression.bar.green", "filled" to filledBar, "empty" to emptyBar)
            percent >= 70 -> lang.gui("menu.guild_progression.bar.yellow", "filled" to filledBar, "empty" to emptyBar)
            percent >= 40 -> lang.gui("menu.guild_progression.bar.gold", "filled" to filledBar, "empty" to emptyBar)
            else -> lang.gui("menu.guild_progression.bar.red", "filled" to filledBar, "empty" to emptyBar)
        }
    }

    private fun addSourceItem(pane: StaticPane, x: Int, y: Int, usage: SourceUsageView) {
        val source = usage.source
        val cap = usage.capXp
        val usedXp = usage.awardedXp
        val percent = if (cap != null && cap > 0) {
            (usedXp.toDouble() / cap.toDouble() * 100).toInt().coerceAtMost(100)
        } else {
            0
        }
        val bars = buildProgressBar(percent, 10)
        val item = NexoItemProvider.getItemStackOrFallback(sourceToIconId(source)) {
            ItemStack.of(sourceToMaterial(source))
        }.also {
            it.editMeta { meta ->
                meta.displayName(
                    lang.gui(
                        "menu.guild_progression.source.name",
                        "source" to sourcePoolDisplayName(usage.pool),
                    ),
                )
                meta.lore(
                    if (cap != null) {
                        listOf(
                            when {
                                percent >= 100 -> lang.gui("menu.guild_progression.source.progress.capped", "bar" to bars, "percent" to percent)
                                percent >= 80 -> lang.gui("menu.guild_progression.source.progress.near_cap", "bar" to bars, "percent" to percent)
                                percent >= 40 -> lang.gui("menu.guild_progression.source.progress.moderate", "bar" to bars, "percent" to percent)
                                else -> lang.gui("menu.guild_progression.source.progress.available", "bar" to bars, "percent" to percent)
                            },
                            lang.gui("menu.guild_progression.source.today", "today" to usedXp, "cap" to cap),
                        )
                    } else {
                        listOf(lang.gui("menu.guild_progression.source.tracked", "xp" to usedXp))
                    },
                )
            }
        }
        pane.addItem(GuiItem(item), x, y)
    }

    private fun addRankInfo(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack.of(Material.GOLD_INGOT).also {
            it.editMeta { meta ->
                meta.displayName(lang.gui("menu.guild_progression.rank.name"))
                meta.lore(
                    listOf(
                        lang.gui("menu.guild_progression.rank.description"),
                        lang.gui("menu.guild_progression.rank.scope"),
                    ),
                )
            }
        }
        pane.addItem(GuiItem(item), x, y)
    }

    private fun addPerksInfo(pane: StaticPane, x: Int, y: Int) {
        val perks = progressionService.getUnlockedPerks(guild.id)
        val item = NexoItemProvider.getItemStackOrFallback("lg_reward") {
            ItemStack.of(Material.DIAMOND)
        }.also {
            it.editMeta { meta ->
                meta.displayName(lang.gui("menu.guild_progression.perks.name"))
                val lore = mutableListOf<Component>()
                if (perks.isEmpty()) {
                    lore += lang.gui("menu.guild_progression.perks.none")
                    lore += lang.gui("menu.guild_progression.perks.hint")
                } else {
                    perks.take(7).forEach { perk ->
                        lore += lang.gui("menu.guild_progression.perks.entry", "perk" to perkToDisplayName(perk))
                    }
                }
                meta.lore(lore)
            }
        }
        pane.addItem(GuiItem(item), x, y)
    }

    private fun addPrestigeInfo(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_prestige") {
            ItemStack.of(Material.NETHER_STAR)
        }.also {
            it.editMeta { meta ->
                meta.displayName(lang.gui("menu.guild_progression.prestige.name"))
                meta.lore(
                    listOf(
                        lang.gui("menu.guild_progression.prestige.description"),
                        lang.gui("menu.guild_progression.prestige.rewards"),
                        lang.gui("menu.guild_progression.prestige.requirement"),
                        Component.empty(),
                        lang.gui("menu.guild_progression.prestige.coming_soon"),
                    ),
                )
            }
        }
        pane.addItem(GuiItem(item), x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_page_prev") {
            ItemStack.of(Material.ARROW).name(lang.gui("menu.guild_progression.navigation.back_fallback"))
        }.also { it.editMeta { meta -> meta.displayName(lang.gui("menu.guild_progression.navigation.back")) } }
        pane.addItem(GuiItem(item) { menuNavigator.goBack() }, x, y)
    }

    private fun addCloseButton(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_close") {
            ItemStack.of(Material.BARRIER).name(lang.gui("menu.guild_progression.navigation.close"))
        }.also { it.editMeta { meta -> meta.displayName(lang.gui("menu.guild_progression.navigation.close")) } }
        pane.addItem(GuiItem(item) {
            menuNavigator.clearMenuStack()
            player.closeInventory()
        }, x, y)
    }

    private fun addPreviousPageButton(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_page_prev") {
            ItemStack.of(Material.ARROW).name(lang.gui("menu.guild_progression.navigation.previous_fallback"))
        }.also { it.editMeta { meta -> meta.displayName(lang.gui("menu.guild_progression.navigation.previous")) } }
        pane.addItem(GuiItem(item) {
            currentPage--
            open()
        }, x, y)
    }

    private fun addNextPageButton(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_page_next") {
            ItemStack.of(Material.ARROW).name(lang.gui("menu.guild_progression.navigation.next_fallback"))
        }.also { it.editMeta { meta -> meta.displayName(lang.gui("menu.guild_progression.navigation.next")) } }
        pane.addItem(GuiItem(item) {
            currentPage++
            open()
        }, x, y)
    }

    private fun sourceToIconId(source: ExperienceSource): String = when (source) {
        ExperienceSource.BANK_DEPOSIT -> "lg_deposit"
        ExperienceSource.MEMBER_JOINED -> "lg_invite"
        ExperienceSource.WAR_WON, ExperienceSource.WAR_LOST -> "lg_war_stats"
        ExperienceSource.PLAYER_KILL, ExperienceSource.MOB_KILL -> "lg_combat"
        ExperienceSource.CROP_BREAK, ExperienceSource.FISHING -> "lg_farming"
        ExperienceSource.BLOCK_BREAK, ExperienceSource.BLOCK_PLACE -> "lg_mining"
        ExperienceSource.CRAFTING, ExperienceSource.SMELTING -> "lg_crafting"
        ExperienceSource.ENCHANTING -> "lg_enchanting"
        ExperienceSource.CLAIM_CREATED, ExperienceSource.CLAIM_DESTROYED -> "lg_claiming"
        ExperienceSource.WEEKLY_ACTIVITY, ExperienceSource.ADMIN_BONUS -> "lg_reward"
        else -> "lg_reward"
    }

    private fun sourceToMaterial(source: ExperienceSource): Material = when (source) {
        ExperienceSource.BANK_DEPOSIT -> Material.GOLD_NUGGET
        ExperienceSource.MEMBER_JOINED -> Material.PLAYER_HEAD
        ExperienceSource.WAR_WON -> Material.DIAMOND_SWORD
        ExperienceSource.WAR_LOST -> Material.STONE_SWORD
        ExperienceSource.PLAYER_KILL -> Material.IRON_SWORD
        ExperienceSource.MOB_KILL -> Material.ROTTEN_FLESH
        ExperienceSource.CROP_BREAK -> Material.WHEAT
        ExperienceSource.BLOCK_BREAK -> Material.STONE_PICKAXE
        ExperienceSource.BLOCK_PLACE -> Material.STONE
        ExperienceSource.CRAFTING -> Material.CRAFTING_TABLE
        ExperienceSource.SMELTING -> Material.FURNACE
        ExperienceSource.FISHING -> Material.FISHING_ROD
        ExperienceSource.ENCHANTING -> Material.ENCHANTING_TABLE
        ExperienceSource.CLAIM_CREATED, ExperienceSource.CLAIM_DESTROYED -> Material.GOLDEN_SHOVEL
        ExperienceSource.WEEKLY_ACTIVITY, ExperienceSource.ADMIN_BONUS -> Material.NETHER_STAR
        else -> Material.PAPER
    }

    private fun perkToDisplayName(perk: net.lumalyte.lg.domain.values.PerkType): Component = when (perk) {
        net.lumalyte.lg.domain.values.PerkType.HIGHER_BANK_BALANCE -> lang.gui("menu.guild_progression.perks.names.higher_bank_balance")
        net.lumalyte.lg.domain.values.PerkType.BANK_INTEREST -> lang.gui("menu.guild_progression.perks.names.bank_interest")
        net.lumalyte.lg.domain.values.PerkType.INCREASED_BANK_LIMIT -> lang.gui("menu.guild_progression.perks.names.increased_bank_limit")
        net.lumalyte.lg.domain.values.PerkType.REDUCED_WITHDRAWAL_FEES -> lang.gui("menu.guild_progression.perks.names.reduced_withdrawal_fees")
        net.lumalyte.lg.domain.values.PerkType.ADDITIONAL_HOMES -> lang.gui("menu.guild_progression.perks.names.additional_homes")
        net.lumalyte.lg.domain.values.PerkType.TELEPORT_COOLDOWN_REDUCTION -> lang.gui("menu.guild_progression.perks.names.teleport_cooldown_reduction")
        net.lumalyte.lg.domain.values.PerkType.HOME_TELEPORT_SOUND_EFFECTS -> lang.gui("menu.guild_progression.perks.names.home_teleport_sound_effects")
        net.lumalyte.lg.domain.values.PerkType.SPECIAL_PARTICLES -> lang.gui("menu.guild_progression.perks.names.special_particles")
        net.lumalyte.lg.domain.values.PerkType.ANNOUNCEMENT_SOUND_EFFECTS -> lang.gui("menu.guild_progression.perks.names.announcement_sound_effects")
        net.lumalyte.lg.domain.values.PerkType.WAR_DECLARATION_SOUND_EFFECTS -> lang.gui("menu.guild_progression.perks.names.war_declaration_sound_effects")
        net.lumalyte.lg.domain.values.PerkType.INCREASED_CLAIM_BLOCKS -> lang.gui("menu.guild_progression.perks.names.increased_claim_blocks")
        net.lumalyte.lg.domain.values.PerkType.INCREASED_CLAIM_COUNT -> lang.gui("menu.guild_progression.perks.names.increased_claim_count")
        net.lumalyte.lg.domain.values.PerkType.FASTER_CLAIM_REGEN -> lang.gui("menu.guild_progression.perks.names.faster_claim_regen")
        net.lumalyte.lg.domain.values.PerkType.CUSTOM_BANNER_COLORS -> lang.gui("menu.guild_progression.perks.names.custom_banner_colors")
        net.lumalyte.lg.domain.values.PerkType.ANIMATED_EMOJIS -> lang.gui("menu.guild_progression.perks.names.animated_emojis")
        net.lumalyte.lg.domain.values.PerkType.ALLY_HOME_ACCESS -> lang.gui("menu.guild_progression.perks.names.ally_home_access")
    }

    private data class GuildProgressionDisplay(
        val level: Int,
        val totalXp: Int,
        val currentXp: Int,
        val neededXp: Int,
        val unlockedPerks: Int,
    )
}

internal fun sourcePoolDisplayName(pool: String): Component =
    Component.text(pool.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() })
