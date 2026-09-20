package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.domain.entities.RankPermission
import org.bukkit.Material

/**
 * The permission information architecture used by every redesigned rank screen.
 *
 * These are player concepts, not implementation buckets. Keeping the groups in one place prevents
 * the rank editor, permission editor and future Bedrock surfaces from drifting apart.
 */
object GuildPermissionGroups {
    data class Group(
        val id: String,
        val displayName: String,
        val material: Material,
        val permissions: List<RankPermission>,
    )

    fun all(claimsEnabled: Boolean): List<Group> = buildList {
        add(Group("members_ranks", "Members & Ranks", Material.PLAYER_HEAD, listOf(
            RankPermission.MANAGE_RANKS,
            RankPermission.MANAGE_MEMBERS,
        )))
        add(Group("appearance", "Guild Appearance", Material.LOOM, listOf(
            RankPermission.MANAGE_BANNER,
            RankPermission.MANAGE_EMOJI,
            RankPermission.MANAGE_DESCRIPTION,
            RankPermission.MANAGE_GUILD_SETTINGS,
        )))
        add(Group("homes_territory", "Homes & Territory", Material.COMPASS, buildList {
            add(RankPermission.MANAGE_HOME)
            add(RankPermission.USE_ALLY_HOMES)
            if (claimsEnabled) {
                add(RankPermission.MANAGE_CLAIMS)
                add(RankPermission.MANAGE_FLAGS)
                add(RankPermission.MANAGE_PERMISSIONS)
                add(RankPermission.CREATE_CLAIMS)
                add(RankPermission.DELETE_CLAIMS)
            }
        }))
        add(Group("relations_war", "Relations & War", Material.SHIELD, listOf(
            RankPermission.MANAGE_RELATIONS,
            RankPermission.DECLARE_WAR,
            RankPermission.ACCEPT_ALLIANCES,
            RankPermission.MANAGE_MODE,
        )))
        add(Group("parties", "Parties", Material.FIREWORK_ROCKET, listOf(
            RankPermission.MANAGE_PARTIES,
            RankPermission.SEND_PARTY_REQUESTS,
            RankPermission.ACCEPT_PARTY_INVITES,
        )))
        add(Group("bank", "Bank", Material.GOLD_INGOT, listOf(
            RankPermission.DEPOSIT_TO_BANK,
            RankPermission.WITHDRAW_FROM_BANK,
            RankPermission.VIEW_BANK_TRANSACTIONS,
            RankPermission.MANAGE_BANK_SETTINGS,
        )))
        add(Group("vault", "Physical Vault", Material.ENDER_CHEST, listOf(
            RankPermission.PLACE_VAULT,
            RankPermission.ACCESS_VAULT,
            RankPermission.DEPOSIT_TO_VAULT,
            RankPermission.WITHDRAW_FROM_VAULT,
            RankPermission.MANAGE_VAULT,
            RankPermission.BREAK_VAULT,
        )))
        add(Group("communication", "Communication", Material.BELL, listOf(
            RankPermission.SEND_ANNOUNCEMENTS,
            RankPermission.SEND_PINGS,
            RankPermission.MODERATE_CHAT,
        )))
        add(Group("advanced", "Advanced", Material.REDSTONE_TORCH, listOf(
            RankPermission.ACCESS_ADMIN_COMMANDS,
            RankPermission.BYPASS_RESTRICTIONS,
            RankPermission.VIEW_AUDIT_LOGS,
            RankPermission.MANAGE_INTEGRATIONS,
            RankPermission.ACCESS_SHOP_CHESTS,
            RankPermission.EDIT_SHOP_STOCK,
            RankPermission.MODIFY_SHOP_PRICES,
        )))
    }

    fun displayName(categoryName: String): String =
        all(true).firstOrNull { it.id == categoryName || it.displayName == categoryName }?.displayName ?: categoryName
}
