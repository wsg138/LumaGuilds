package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PriorityDirection
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.i18n.GuiTextStyler
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuSurface
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Detail/edit screen for one rank. Permissions are grouped by player intent. */
class RankEditMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private var rank: Rank,
) : Menu, KoinComponent, ChatInputHandler {
    private val rankService: RankService by inject()
    private val memberService: MemberService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    private var inputMode = ""
    private var selectedIcon: Material = loadRankIcon()

    override fun open() {
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RANKS)) {
            player.sendMessage(lang.msg("menu.rank_edit.feedback.no_permission"))
            player.sendMessage(lang.msg("menu.rank_edit.feedback.required_permission"))
            menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
            return
        }

        rank = rankService.getRank(rank.id) ?: rank
        val gui = ChestGui(6, MenuTitleBuilder.redesign(
            MenuSurface.DETAIL,
            lang.guiTitle("menu.rank_edit.title", "rank" to rank.name),
        ))
        gui.setOnGlobalClick { it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        gui.addPane(pane)

        addRankSummary(pane)
        addPermissionGroups(pane)
        addActions(pane)
        gui.show(player)
    }

    private fun addRankSummary(pane: StaticPane) {
        val upNeighbor = siblingAt(PriorityDirection.UP)
        val downNeighbor = siblingAt(PriorityDirection.DOWN)
        val canUp = canActorReorder() && upNeighbor != null && !isOwnerRank()
        val canDown = canActorReorder() && downNeighbor != null && !isOwnerRank()

        val up = ItemStack.of(if (canUp) Material.SPECTRAL_ARROW else Material.GRAY_DYE)
            .name(if (canUp) lang.gui("menu.rank_edit.priority.up") else lang.gui("menu.rank_edit.priority.up_locked"))
            .lore(lang.gui("menu.rank_edit.priority.current", "priority" to rank.priority))
            .lore(if (upNeighbor != null) lang.gui("menu.rank_edit.priority.above", "rank" to upNeighbor.name) else lang.gui("menu.rank_edit.priority.no_above"))
        pane.addItem(GuiItem(up) {
            if (canUp && rankService.moveRankPriority(rank.id, PriorityDirection.UP, player.uniqueId)) {
                rank = rankService.getRank(rank.id) ?: rank
                player.sendMessage(lang.msg("menu.rank_edit.feedback.moved_up"))
                open()
            }
        }, 0, 0)

        val name = ItemStack.of(Material.NAME_TAG)
            .name(lang.gui("menu.rank_edit.info.name"))
            .lore(lang.gui("menu.rank_edit.info.rank_name", "rank" to rank.name))
            .lore(lang.gui("menu.rank_edit.info.members", "count" to getMemberCount()))
            .lore(lang.gui("menu.common.blank"))
            .lore(if (inputMode == "name") lang.gui("menu.rank_edit.info.type_name") else lang.gui("menu.rank_edit.info.rename"))
        pane.addItem(GuiItem(name) {
            if (inputMode != "name") startNameInput()
            else player.sendMessage(lang.msg("menu.rank_edit.feedback.already_waiting_name"))
        }, 2, 0)

        val displayIcon = if (selectedIcon == Material.AIR) Material.DIAMOND_SWORD else selectedIcon
        val icon = ItemStack.of(displayIcon)
            .name(lang.gui("menu.rank_edit.icon.name"))
            .lore(lang.gui("menu.rank_edit.icon.current", "material" to if (selectedIcon == Material.AIR) lang.gui("menu.rank_edit.icon.not_set") else selectedIcon.name))
            .lore(lang.gui("menu.common.blank"))
            .lore(if (inputMode == "icon") lang.gui("menu.rank_edit.icon.type_material") else lang.gui("menu.rank_edit.icon.change"))
        pane.addItem(GuiItem(icon) {
            if (inputMode != "icon") startIconInput()
            else player.sendMessage(lang.msg("menu.rank_edit.feedback.already_waiting_icon"))
        }, 4, 0)

        val summary = ItemStack.of(Material.WRITABLE_BOOK)
            .name(lang.gui("menu.rank_edit.summary.name"))
            .lore(lang.gui("menu.rank_edit.summary.total", "count" to rank.permissions.size))
            .lore(lang.gui("menu.permission_category.status.count", "enabled" to rank.permissions.size, "total" to visiblePermissionCount()))
        if (isEditingOwnRank()) {
            summary.lore(lang.gui("menu.common.blank"))
                .lore(lang.gui("menu.rank_edit.info.protection"))
                .lore(lang.gui("menu.rank_edit.info.permission_changes_blocked"))
        }
        pane.addItem(GuiItem(summary), 6, 0)

        val down = ItemStack.of(if (canDown) Material.SPECTRAL_ARROW else Material.GRAY_DYE)
            .name(if (canDown) lang.gui("menu.rank_edit.priority.down") else lang.gui("menu.rank_edit.priority.down_locked"))
            .lore(lang.gui("menu.rank_edit.priority.current", "priority" to rank.priority))
            .lore(if (downNeighbor != null) lang.gui("menu.rank_edit.priority.below", "rank" to downNeighbor.name) else lang.gui("menu.rank_edit.priority.no_below"))
        pane.addItem(GuiItem(down) {
            if (canDown && rankService.moveRankPriority(rank.id, PriorityDirection.DOWN, player.uniqueId)) {
                rank = rankService.getRank(rank.id) ?: rank
                player.sendMessage(lang.msg("menu.rank_edit.feedback.moved_down"))
                open()
            }
        }, 8, 0)
    }

    private fun addPermissionGroups(pane: StaticPane) {
        GuildPermissionGroups.all(configService.loadConfig().claimsEnabled).forEachIndexed { index, group ->
            val enabled = group.permissions.count(rank.permissions::contains)
            val allEnabled = enabled == group.permissions.size
            val someEnabled = enabled > 0 && !allEnabled
            val item = ItemStack.of(group.material)
                .name(lang.gui("menu.rank_edit.category.name", "category" to GuiTextStyler.style(Component.text(group.displayName))))
                .lore(lang.gui("menu.permission_category.status.count", "enabled" to enabled, "total" to group.permissions.size))
                .lore(lang.gui("menu.common.blank"))
                .lore(when {
                    allEnabled -> lang.gui("menu.rank_edit.category.all_enabled")
                    someEnabled -> lang.gui("menu.rank_edit.category.some_enabled")
                    else -> lang.gui("menu.rank_edit.category.none_enabled")
                })
                .lore(lang.gui("menu.rank_edit.category.open"))
            val x = (index % 3) * 3 + 1
            val y = 1 + index / 3
            pane.addItem(GuiItem(item) {
                if (isEditingOwnRank()) {
                    player.sendMessage(lang.msg("menu.rank_edit.feedback.cannot_modify_own"))
                    player.sendMessage(lang.msg("menu.rank_edit.feedback.contact_higher_rank"))
                } else {
                    menuNavigator.openMenu(PermissionCategoryMenu(
                        menuNavigator, player, guild, rank, group.displayName, group.permissions,
                    ))
                }
            }, x, y)
        }
    }

    private fun addActions(pane: StaticPane) {
        val reset = ItemStack.of(Material.YELLOW_DYE)
            .name(lang.gui("menu.rank_edit.action.reset.name"))
            .lore(lang.gui("menu.rank_edit.action.reset.description"))
            .lore(lang.gui("menu.rank_edit.action.irreversible"))
        pane.addItem(GuiItem(reset) { resetPermissions() }, 2, 4)

        val delete = ItemStack.of(Material.TNT)
            .name(lang.gui("menu.rank_edit.action.delete.name"))
            .lore(lang.gui("menu.rank_edit.action.delete.description"))
            .lore(lang.gui("menu.rank_edit.action.delete.warning"))
        pane.addItem(GuiItem(delete) { deleteRank() }, 6, 4)

        val back = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.rank_edit.action.back.name"))
            .lore(lang.gui("menu.rank_edit.action.back.description"))
        pane.addItem(GuiItem(back) {
            menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
        }, 0, 5)

        val members = ItemStack.of(Material.PLAYER_HEAD)
            .name(lang.gui("menu.member_management.item.rank_change.name"))
            .lore(lang.gui("menu.member_management.item.rank_change.lore.description"))
        pane.addItem(GuiItem(members) {
            menuNavigator.openMenu(menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild))
        }, 4, 5)

        val close = ItemStack.of(Material.BARRIER).name(lang.gui("menu.permission_category.action.cancel.name"))
        pane.addItem(GuiItem(close) { player.closeInventory() }, 8, 5)
    }

    private fun resetPermissions() {
        if (isOwnerRank()) {
            player.sendMessage(lang.msg("menu.rank_edit.feedback.cannot_reset_owner"))
            player.sendMessage(lang.msg("menu.rank_edit.feedback.owner_permissions_permanent"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }
        if (isEditingOwnRank()) {
            player.sendMessage(lang.msg("menu.rank_edit.feedback.cannot_reset_own"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }
        if (rankService.setRankPermissions(rank.id, emptySet(), player.uniqueId)) {
            rank = rankService.getRank(rank.id) ?: rank
            player.sendMessage(lang.msg("menu.rank_edit.feedback.reset"))
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
            open()
        } else {
            player.sendMessage(lang.msg("menu.rank_edit.feedback.reset_failed"))
        }
    }

    private fun deleteRank() {
        if (isOwnerRank()) {
            player.sendMessage(lang.msg("menu.rank_edit.feedback.cannot_delete_owner"))
            player.sendMessage(lang.msg("menu.rank_edit.feedback.owner_permanent"))
            return
        }
        if (isEditingOwnRank()) {
            player.sendMessage(lang.msg("menu.rank_edit.feedback.cannot_delete_own"))
            player.sendMessage(lang.msg("menu.rank_edit.feedback.lose_access"))
            return
        }
        val allRanks = rankService.listRanks(guild.id)
        if (allRanks.size <= 1) {
            player.sendMessage(lang.msg("menu.rank_edit.feedback.cannot_delete_last"))
            player.sendMessage(lang.msg("menu.rank_edit.feedback.rank_required"))
            return
        }
        val targetRank = allRanks.filter { it.id != rank.id }.maxByOrNull { it.priority }
        val members = memberService.getMembersByRank(guild.id, rank.id)
        if (members.isNotEmpty() && targetRank == null) {
            player.sendMessage(lang.msg("menu.rank_edit.feedback.no_target_rank"))
            return
        }
        targetRank?.let { target ->
            members.forEach { member ->
                memberService.changeMemberRank(member.playerId, guild.id, target.id, player.uniqueId)
            }
            if (members.isNotEmpty()) {
                player.sendMessage(lang.msg("menu.rank_edit.feedback.members_moved", "count" to members.size, "rank" to target.name))
            }
        }
        if (rankService.deleteRank(rank.id, player.uniqueId)) {
            player.sendMessage(lang.msg("menu.rank_edit.feedback.deleted", "rank" to rank.name))
        } else {
            player.sendMessage(lang.msg("menu.rank_edit.feedback.delete_failed"))
        }
        menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
    }

    private fun isOwnerRank(): Boolean = rank.id == rankService.getHighestRank(guild.id)?.id
    private fun isEditingOwnRank(): Boolean = rankService.isPlayerRank(player.uniqueId, guild.id, rank.id)
    private fun canActorReorder(): Boolean = (rankService.getPlayerRank(player.uniqueId, guild.id)?.priority ?: Int.MAX_VALUE) < rank.priority

    private fun siblingAt(direction: PriorityDirection): Rank? {
        val siblings = rankService.listRanks(guild.id).sortedBy { it.priority }
        val index = siblings.indexOfFirst { it.id == rank.id }
        return siblings.getOrNull(if (direction == PriorityDirection.UP) index - 1 else index + 1)
    }

    private fun visiblePermissionCount(): Int = GuildPermissionGroups.all(configService.loadConfig().claimsEnabled).sumOf { it.permissions.size }
    private fun getMemberCount(): Int = memberService.getMembersByRank(guild.id, rank.id).size

    private fun loadRankIcon(): Material = runCatching { rank.icon?.let(Material::valueOf) }.getOrNull() ?: Material.AIR

    private fun startNameInput() {
        inputMode = "name"
        chatInputListener.startInputMode(player, this)
        player.closeInventory()
        player.sendMessage(lang.msg("menu.rank_edit.input.name.header"))
        player.sendMessage(lang.msg("menu.rank_edit.input.name.prompt"))
        player.sendMessage(lang.msg("menu.rank_edit.input.name.current", "rank" to rank.name))
        player.sendMessage(lang.msg("menu.rank_edit.input.name.requirements"))
        player.sendMessage(lang.msg("menu.rank_edit.input.name.length"))
        player.sendMessage(lang.msg("menu.rank_edit.input.name.characters"))
        player.sendMessage(lang.msg("menu.rank_edit.input.cancel"))
    }

    private fun startIconInput() {
        inputMode = "icon"
        chatInputListener.startInputMode(player, this)
        player.closeInventory()
        player.sendMessage(lang.msg("menu.rank_edit.input.icon.header"))
        player.sendMessage(lang.msg("menu.rank_edit.input.icon.prompt"))
        player.sendMessage(lang.msg("menu.rank_edit.input.icon.examples"))
        val link = lang.msg("menu.rank_edit.input.icon.link_prefix")
            .color(NamedTextColor.YELLOW)
            .append(lang.msg("menu.rank_edit.input.icon.link_action")
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl("https://jd.papermc.io/paper/1.21.8/org/bukkit/Material.html")))
        player.sendMessage(link)
        player.sendMessage(lang.msg("menu.rank_edit.input.cancel"))
    }

    private fun validateRankName(name: String): Component? {
        if (name.length !in 1..24) return lang.msg("menu.rank_edit.validation.length", "length" to name.length)
        if (!name.matches(Regex("^[a-zA-Z0-9 ]+$"))) return lang.msg("menu.rank_edit.validation.characters")
        val existing = rankService.getRankByName(guild.id, name)
        if (existing != null && existing.id != rank.id) return lang.msg("menu.rank_edit.validation.duplicate")
        return null
    }

    override fun onChatInput(player: Player, input: String) {
        when (inputMode) {
            "name" -> {
                val error = validateRankName(input)
                if (error != null) {
                    player.sendMessage(lang.msg("menu.rank_edit.feedback.invalid_name", "error" to error))
                    player.sendMessage(lang.msg("menu.rank_edit.feedback.try_again"))
                } else {
                    rank = rank.copy(name = input)
                    if (rankService.updateRank(rank, player.uniqueId)) {
                        player.sendMessage(lang.msg("menu.rank_edit.feedback.name_updated", "rank" to input))
                    } else player.sendMessage(lang.msg("menu.rank_edit.feedback.name_update_failed"))
                    inputMode = ""
                }
            }
            "icon" -> {
                val material = runCatching { Material.valueOf(input.uppercase()) }.getOrNull()
                if (material == null) {
                    player.sendMessage(lang.msg("menu.rank_edit.feedback.invalid_material", "material" to input))
                    player.sendMessage(lang.msg("menu.rank_edit.feedback.try_again"))
                } else {
                    selectedIcon = material
                    rank = rank.copy(icon = material.name)
                    if (rankService.updateRank(rank, player.uniqueId)) {
                        player.sendMessage(lang.msg("menu.rank_edit.feedback.icon_updated", "material" to material.name))
                    } else player.sendMessage(lang.msg("menu.rank_edit.feedback.icon_update_failed"))
                    inputMode = ""
                }
            }
        }
        val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return
        Bukkit.getScheduler().runTask(plugin, Runnable { open() })
    }

    override fun onCancel(player: Player) {
        inputMode = ""
        player.sendMessage(lang.msg("menu.rank_edit.feedback.input_cancelled"))
        val plugin = Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return
        Bukkit.getScheduler().runTask(plugin, Runnable { open() })
    }

    override fun passData(data: Any?) {
        when (data) {
            is Guild -> guild = data
            is Rank -> rank = data
        }
    }
}
