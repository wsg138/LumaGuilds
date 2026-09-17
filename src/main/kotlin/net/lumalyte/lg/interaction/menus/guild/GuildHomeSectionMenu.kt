package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Small bridge menus used only by the focused Guild Home preview where one home card groups several
 * existing live LumaGuilds destinations. These screens are intentionally simple: only Guild Home is
 * under visual review on this branch.
 */
class GuildHomeSectionMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val menuFactory: MenuFactory,
    private val section: Section,
) : Menu {

    enum class Section(val title: String) {
        MEMBERS("Members & Ranks"),
        LEVEL("Level & Quests"),
        ALLIES("Allies & War"),
        CUSTOMIZE("Customize Guild"),
    }

    override fun open() {
        val gui = ChestGui(3, section.title)
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 3)
        gui.addPane(pane)

        when (section) {
            Section.MEMBERS -> {
                addButton(pane, 2, 1, Material.PLAYER_HEAD, "Members", "Roster, invites and member management") {
                    menuNavigator.openMenu(menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild))
                }
                addButton(pane, 6, 1, Material.GOLDEN_HELMET, "Ranks & Permissions", "Ranks, hierarchy and permissions") {
                    menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
                }
            }

            Section.LEVEL -> {
                addButton(pane, 2, 1, Material.EXPERIENCE_BOTTLE, "Progression", "Guild level, XP and perks") {
                    menuNavigator.openMenu(menuFactory.createGuildProgressionMenu(menuNavigator, player, guild))
                }
                addButton(pane, 4, 1, Material.CLOCK, "Quests", "Active and completed guild quests") {
                    menuNavigator.openMenu(menuFactory.createGuildQuestsMenu(menuNavigator, player, guild))
                }
                addButton(pane, 6, 1, Material.BOOKSHELF, "Statistics", "Guild activity and performance statistics") {
                    menuNavigator.openMenu(menuFactory.createGuildStatisticsMenu(menuNavigator, player, guild))
                }
            }

            Section.ALLIES -> {
                addButton(pane, 2, 1, Material.BOOK, "Diplomacy", "Allies, enemies, truces and requests") {
                    menuNavigator.openMenu(menuFactory.createGuildRelationsMenu(menuNavigator, player, guild))
                }
                addButton(pane, 6, 1, Material.DIAMOND_SWORD, "Warfare", "Declarations, active wars and peace") {
                    menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
                }
            }

            Section.CUSTOMIZE -> {
                addButton(pane, 1, 1, Material.WRITABLE_BOOK, "Description", "Edit the public guild description") {
                    menuNavigator.openMenu(menuFactory.createDescriptionEditorMenu(menuNavigator, player, guild))
                }
                addButton(pane, 3, 1, Material.WHITE_BANNER, "Banner", "Set or edit the guild banner") {
                    menuNavigator.openMenu(menuFactory.createGuildBannerMenu(menuNavigator, player, guild))
                }
                addButton(pane, 5, 1, Material.FIREWORK_STAR, "Emoji", "Choose the guild emoji") {
                    menuNavigator.openMenu(menuFactory.createGuildEmojiMenu(menuNavigator, player, guild))
                }
                addButton(pane, 7, 1, Material.PAINTING, "More Appearance", "Tag, theme and remaining appearance settings") {
                    menuNavigator.openMenu(menuFactory.createGuildSettingsMenu(menuNavigator, player, guild))
                }
            }
        }

        val back = ItemStack.of(Material.ARROW)
        setMeta(back, Component.text("Back", NamedTextColor.AQUA), listOf(Component.text("Return to Guild Home", NamedTextColor.GRAY)))
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 0, 2)

        val close = ItemStack.of(Material.BARRIER)
        setMeta(close, Component.text("Close", NamedTextColor.RED), emptyList())
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 2)

        gui.show(player)
    }

    private fun addButton(
        pane: StaticPane,
        x: Int,
        y: Int,
        material: Material,
        name: String,
        description: String,
        action: () -> Unit,
    ) {
        val item = ItemStack.of(material)
        setMeta(
            item,
            Component.text(name, NamedTextColor.WHITE),
            listOf(
                Component.text(description, NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Click to open", NamedTextColor.AQUA),
            ),
        )
        pane.addItem(GuiItem(item) { action() }, x, y)
    }

    private fun setMeta(item: ItemStack, name: Component, lore: List<Component>) {
        val meta = item.itemMeta ?: return
        meta.displayName(name)
        meta.lore(lore)
        item.itemMeta = meta
    }
}
