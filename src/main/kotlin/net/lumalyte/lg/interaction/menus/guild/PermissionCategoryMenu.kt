package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.i18n.GuiTextStyler
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuSurface
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** One small, focused permission group. No giant permission wall and no giant lore wall. */
class PermissionCategoryMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private var rank: Rank,
    private val categoryName: String,
    private val categoryPermissions: List<RankPermission>,
) : Menu, KoinComponent {
    private val rankService: RankService by inject()
    private val lang: LangService by inject()
    private var modifiedPermissions = rank.permissions.toMutableSet()

    override fun open() {
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RANKS)) {
            player.sendMessage(lang.msg("menu.rank_edit.feedback.no_permission"))
            menuNavigator.openMenu(RankEditMenu(menuNavigator, player, guild, rank))
            return
        }

        val gui = ChestGui(6, MenuTitleBuilder.redesign(MenuSurface.DETAIL, categoryName))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        addHeader(pane)
        addPermissions(pane)
        addFooter(pane)
        gui.show(player)
    }

    private fun addHeader(pane: StaticPane) {
        val enabledCount = categoryPermissions.count(modifiedPermissions::contains)
        val group = GuildPermissionGroups.all(true).firstOrNull { it.displayName == categoryName }
        val info = ItemStack.of(group?.material ?: Material.WRITABLE_BOOK)
            .name(lang.gui("menu.rank_edit.category.name", "category" to GuiTextStyler.style(Component.text(categoryName))))
            .lore(lang.gui("menu.permission_category.info.rank", "rank" to rank.name))
            .lore(lang.gui("menu.permission_category.status.count", "enabled" to enabledCount, "total" to categoryPermissions.size))
        if (isEditingOwnRank()) {
            info.lore(lang.gui("menu.common.blank"))
                .lore(lang.gui("menu.permission_category.info.protection"))
                .lore(lang.gui("menu.permission_category.info.prevent_lockout"))
        }
        pane.addItem(GuiItem(info), 4, 0)

        val enableAll = ItemStack.of(Material.LIME_DYE)
            .name(lang.gui("menu.permission_category.enable_all.name"))
            .lore(lang.gui("menu.permission_category.enable_all.description", "category" to GuiTextStyler.style(Component.text(categoryName))))
        pane.addItem(GuiItem(enableAll) {
            if (guardSelfEdit()) return@GuiItem
            modifiedPermissions.addAll(categoryPermissions)
            open()
        }, 2, 0)

        val disableAll = ItemStack.of(Material.RED_DYE)
            .name(lang.gui("menu.permission_category.disable_all.name"))
            .lore(lang.gui("menu.permission_category.disable_all.description", "category" to GuiTextStyler.style(Component.text(categoryName))))
        pane.addItem(GuiItem(disableAll) {
            if (guardSelfEdit()) return@GuiItem
            modifiedPermissions.removeAll(categoryPermissions.toSet())
            open()
        }, 6, 0)
    }

    private fun addPermissions(pane: StaticPane) {
        categoryPermissions.forEachIndexed { index, permission ->
            val enabled = permission in modifiedPermissions
            val permissionKey = "permission.${permission.name.lowercase().replace("_", ".")}"
            val displayName = lang.gui(permissionKey)
            val item = ItemStack.of(if (enabled) Material.LIME_STAINED_GLASS_PANE else Material.RED_STAINED_GLASS_PANE)
                .name(if (enabled) {
                    lang.gui("menu.rank_edit.category.permission_enabled", "permission" to displayName)
                } else {
                    lang.gui("menu.rank_edit.category.permission_disabled", "permission" to displayName)
                })
                .lore(if (enabled) lang.gui("menu.permission_category.permission.enabled") else lang.gui("menu.permission_category.permission.disabled"))
                .lore(lang.gui("menu.common.blank"))
                .lore(if (enabled) lang.gui("menu.permission_category.permission.disable") else lang.gui("menu.permission_category.permission.enable"))
            val x = 1 + index % 7
            val y = 1 + index / 7
            if (y <= 4) {
                pane.addItem(GuiItem(item) {
                    if (guardSelfEdit()) return@GuiItem
                    if (enabled) modifiedPermissions.remove(permission) else modifiedPermissions.add(permission)
                    open()
                }, x, y)
            }
        }
    }

    private fun addFooter(pane: StaticPane) {
        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.rank_edit.action.back.name"))
            .lore(lang.gui("menu.permission_category.action.cancel.description"))
        pane.addItem(GuiItem(back) {
            menuNavigator.openMenu(RankEditMenu(menuNavigator, player, guild, rank))
        }, 0, 5)

        val reset = ItemStack.of(Material.YELLOW_DYE)
            .name(lang.gui("menu.permission_category.action.reset.name"))
            .lore(lang.gui("menu.permission_category.action.reset.description"))
        pane.addItem(GuiItem(reset) {
            categoryPermissions.forEach { permission ->
                if (permission in rank.permissions) modifiedPermissions.add(permission)
                else modifiedPermissions.remove(permission)
            }
            open()
        }, 3, 5)

        val save = ItemStack.of(Material.EMERALD)
            .name(lang.gui("menu.rank_edit.action.save.name"))
            .lore(lang.gui("menu.permission_category.action.save.description"))
            .lore(lang.gui("menu.permission_category.action.save.click"))
        pane.addItem(GuiItem(save) {
            val updated = rank.copy(permissions = modifiedPermissions.toSet())
            if (rankService.updateRank(updated, player.uniqueId)) {
                rank = updated
                player.sendMessage(lang.msg("menu.permission_category.feedback.saved", "rank" to rank.name))
                menuNavigator.openMenu(RankEditMenu(menuNavigator, player, guild, rank))
            } else {
                player.sendMessage(lang.msg("menu.permission_category.feedback.save_failed"))
            }
        }, 5, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.permission_category.action.cancel.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    private fun guardSelfEdit(): Boolean {
        if (!isEditingOwnRank()) return false
        player.sendMessage(lang.msg("menu.permission_category.feedback.self_edit_denied"))
        player.sendMessage(lang.msg("menu.permission_category.feedback.prevent_lockout"))
        return true
    }

    private fun isEditingOwnRank(): Boolean = rankService.isPlayerRank(player.uniqueId, guild.id, rank.id)

    override fun passData(data: Any?) {
        when (data) {
            is Guild -> guild = data
            is Rank -> rank = data
        }
    }
}
